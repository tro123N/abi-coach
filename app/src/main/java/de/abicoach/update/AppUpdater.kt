package de.abicoach.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import de.abicoach.AbiCoachApp
import de.abicoach.session.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Updates the app from inside the app: looks at the release "latest" on GitHub, downloads the APK and hands it to
 * Android's package installer – the student never has to leave the app or look for the file. The repository is
 * private, so GitHub wants a read-only token (entered in the app, kept encrypted, never part of a backup).
 */
object AppUpdater {
    /** One release on GitHub: its build number (= versionCode) and where the APK is. */
    data class Release(val build: Int, val title: String, val notes: String, val assetUrl: String, val size: Long, val publishedAt: String)

    sealed interface State {
        data object Idle : State

        data object Checking : State

        data class UpToDate(val build: Int) : State

        data class Available(val release: Release) : State

        /** [progress] from 0 to 1, or null while the size is unknown. */
        data class Downloading(val release: Release, val progress: Float?) : State

        /** Android's installer has the APK; it may ask the student once more. */
        data class Installing(val release: Release) : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private const val API = "https://api.github.com/repos/"
    const val ACTION_STATUS = "de.abicoach.update.STATUS"

    /** The build number of the installed app. */
    fun installedBuild(context: Context): Long =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        }.getOrDefault(0L)

    /** May this app install APKs (Android's "unknown apps" permission for AbiCoach)? */
    fun canInstall(context: Context): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Asks GitHub for the newest release. [quiet] keeps errors out of the state (the check at app start). */
    suspend fun check(app: AbiCoachApp, quiet: Boolean = false): State {
        val busy = _state.value
        if (busy is State.Downloading || busy is State.Installing) return busy
        if (!quiet) _state.value = State.Checking
        val result =
            try {
                val release = latest(app)
                app.settings.lastUpdateCheck = System.currentTimeMillis()
                if (release.build > installedBuild(app)) State.Available(release) else State.UpToDate(release.build)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed(e.message ?: "Unbekannter Fehler")
            }
        if (!quiet || result is State.Available) _state.value = result
        return result
    }

    private suspend fun latest(app: AbiCoachApp): Release =
        withContext(Dispatchers.IO) {
            val repo = app.settings.updateRepo.trim().trim('/')
            val request =
                github(app, Request.Builder().url("$API$repo/releases/tags/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .build()
            app.http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 && app.settings.githubToken.isBlank() ->
                        throw IOException("Das Projekt auf GitHub ist privat – trag unten einen GitHub-Schlüssel (nur Lesen) ein.")
                    response.code == 404 -> throw IOException("Keine Version gefunden. Darf der GitHub-Schlüssel das Projekt $repo lesen?")
                    response.code == 401 -> throw IOException("Der GitHub-Schlüssel ist ungültig oder abgelaufen.")
                    response.code == 403 -> throw IOException("GitHub lässt gerade keine Abfrage zu (Limit oder fehlende Berechtigung). Versuch es später noch einmal.")
                    !response.isSuccessful -> throw IOException("GitHub antwortet mit Fehler ${response.code}.")
                }
                val o = JSONObject(body)
                val title = o.optString("name")
                val asset =
                    o.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
                        .firstOrNull { it.optString("name").endsWith(".apk") }
                        ?: throw IOException("In der neuesten Version ist keine APK.")
                val build = Regex("Build (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: throw IOException("Unbekannte Versionsnummer: $title")
                Release(build, title, o.optString("body"), asset.optString("url"), asset.optLong("size"), o.optString("published_at"))
            }
        }

    private fun github(app: AbiCoachApp, builder: Request.Builder): Request.Builder {
        builder.header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "AbiCoach-Updater")
        val token = app.settings.githubToken.trim()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        return builder
    }

    /** Downloads the APK (with progress), checks that it really is a newer AbiCoach, and starts the installation. */
    suspend fun update(app: AbiCoachApp, release: Release) {
        _state.value = State.Downloading(release, 0f)
        try {
            val apk = download(app, release)
            verify(app, apk, release)
            _state.value = State.Installing(release)
            withContext(Dispatchers.IO) { install(app, apk) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = State.Available(release)
            throw e
        } catch (e: Exception) {
            _state.value = State.Failed("Update hat nicht geklappt: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun download(app: AbiCoachApp, release: Release): File =
        withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
            val file = File(dir, "AbiCoach.apk")
            // GitHub answers with a redirect to its file server; OkHttp drops the token there by itself.
            val client = app.http.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(15, TimeUnit.MINUTES).build()
            val request = github(app, Request.Builder().url(release.assetUrl)).header("Accept", "application/octet-stream").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download fehlgeschlagen (Fehler ${response.code}).")
                val body = response.body ?: throw IOException("Leere Antwort von GitHub.")
                val total = body.contentLength().takeIf { it > 0 } ?: release.size
                var done = 0L
                var shown = -1
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            if (percent != shown) {
                                shown = percent
                                _state.value = State.Downloading(release, if (total > 0) done.toFloat() / total else null)
                            }
                        }
                    }
                }
            }
            file
        }

    /** The file must be AbiCoach and newer than what is installed; Android checks the signature itself. */
    private fun verify(context: Context, apk: File, release: Release) {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: throw IOException("Die heruntergeladene Datei ist keine gültige App.")
        if (info.packageName != context.packageName) throw IOException("Die Datei ist nicht AbiCoach.")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (code <= installedBuild(context)) throw IOException("Die Datei ist nicht neuer als die installierte Version (Build $code).")
        if (code != release.build.toLong()) throw IOException("Die Datei hat Build $code statt ${release.build}.")
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                // Since Android 12 an app may update itself without asking again – if Android allows it.
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("AbiCoach.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    internal fun report(state: State) {
        _state.value = state
    }

    /** For the screenshots of the app test. */
    fun demo(state: State) {
        _state.value = state
    }
}

/** Hears what Android's installer says (ask the student, done, failed) and greets the new version afterwards. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppUpdater.ACTION_STATUS -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm =
                            if (Build.VERSION.SDK_INT >= 33) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(confirm) }
                                .onFailure { AppUpdater.report(AppUpdater.State.Failed("Android konnte die Installation nicht anzeigen: ${it.message}")) }
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> Unit
                    else -> {
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                        val text =
                            when (status) {
                                PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation abgebrochen."
                                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                                    "Die neue Version passt nicht zur installierten (andere Signatur). Lösche AbiCoach einmal und installiere die APK neu – sichere vorher deinen Lernstand."
                                PackageInstaller.STATUS_FAILURE_STORAGE -> "Zu wenig Speicher auf dem Handy."
                                else -> "Installation fehlgeschlagen" + if (message.isNotBlank()) " ($message)." else "."
                            }
                        AppUpdater.report(AppUpdater.State.Failed(text))
                    }
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                runCatching { File(context.cacheDir, "update").deleteRecursively() }
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                Notifications.done(context, 4911, "AbiCoach ist aktualisiert", "Version $version ist installiert – tippe, um weiterzumachen.")
            }
        }
    }
}
