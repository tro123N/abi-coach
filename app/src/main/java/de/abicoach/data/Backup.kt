package de.abicoach.data

import de.abicoach.AbiCoachApp
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The whole learning progress as one file: sessions with their evaluations, the learning profile, the error memory and
 * the tutor's notes, the learner memory, the Abi-Weg, vocabulary, pronunciation, dossiers and the settings – without
 * the API keys. The course material can go along (it is much bigger). On another phone the file brings it all back.
 */
object Backup {
    private const val MANIFEST = "abicoach-backup.json"
    private const val SETTINGS = "settings.json"
    private const val FORMAT = 1

    private val FILES =
        listOf(
            "profile.json", "path.json", "error_memory.json", "error_memory_sessions.json", "insights.json",
            "pronunciation.json", "vocab.json", "briefing.json", "workshop.json",
        )
    private val DIRS = listOf("sessions", "dossiers")
    private const val MATERIAL = "memory"

    data class Info(val exportedAt: Long, val sessions: Int, val withMaterial: Boolean, val appVersion: String)

    /** Size of the course material in bytes (for the question whether it should go along). */
    fun materialBytes(app: AbiCoachApp): Long = File(app.filesDir, MATERIAL).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun export(app: AbiCoachApp, out: OutputStream, withMaterial: Boolean): Info {
        val info =
            Info(
                exportedAt = System.currentTimeMillis(),
                sessions = app.store.sessions.value.size,
                withMaterial = withMaterial,
                appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty(),
            )
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                JSONObject().put("format", FORMAT).put("app", "AbiCoach").put("exportedAt", info.exportedAt).put("sessions", info.sessions)
                    .put("withMaterial", withMaterial).put("appVersion", info.appVersion).toString(2).toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(SETTINGS))
            zip.write(app.settings.exportPrefs().toString(2).toByteArray())
            zip.closeEntry()
            FILES.map { File(app.filesDir, it) }.filter { it.isFile }.forEach { f -> addFile(zip, f, f.name) }
            // Backup copies and half-written files of the safe writer stay on the phone.
            (DIRS + if (withMaterial) listOf(MATERIAL) else emptyList()).forEach { dir ->
                val root = File(app.filesDir, dir)
                root.walkTopDown().filter { it.isFile && !SafeFile.isHelper(it) }.forEach { f -> addFile(zip, f, dir + "/" + f.relativeTo(root).invariantSeparatorsPath) }
            }
        }
        return info
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Reads only the description of a backup file; null if it is not one. */
    fun inspect(input: InputStream): Info? =
        runCatching {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST) {
                        val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        if (o.optString("app") != "AbiCoach") return@use null
                        return@use Info(o.optLong("exportedAt"), o.optInt("sessions"), o.optBoolean("withMaterial"), o.optString("appVersion"))
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }.getOrNull()

    /**
     * Replaces the learning progress on this phone by the one in the file. The API keys stay. Material on this phone is
     * replaced only if the file brings its own. The app must be restarted afterwards.
     */
    fun restore(app: AbiCoachApp, input: InputStream): Info {
        val staging = File(app.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val root = staging.canonicalFile
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = File(staging, entry.name).canonicalFile
                    // Never write outside the staging folder (a crafted file could try "../").
                    if (!target.path.startsWith(root.path + File.separator)) throw IllegalStateException("Ungültige Datei im Archiv: ${entry.name}")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
        val manifest = File(staging, MANIFEST)
        if (!manifest.isFile) throw IllegalStateException("Das ist keine AbiCoach-Sicherung.")
        val o = JSONObject(manifest.readText())
        if (o.optString("app") != "AbiCoach") throw IllegalStateException("Das ist keine AbiCoach-Sicherung.")
        val info = Info(o.optLong("exportedAt"), o.optInt("sessions"), o.optBoolean("withMaterial"), o.optString("appVersion"))
        FILES.forEach { name ->
            val from = File(staging, name)
            val to = File(app.filesDir, name)
            // The backup copy of the old progress goes too – otherwise it would come back when a file is missing.
            SafeFile.delete(to)
            if (from.isFile) from.copyTo(to, overwrite = true)
        }
        (DIRS + if (info.withMaterial) listOf(MATERIAL) else emptyList()).forEach { dir ->
            val to = File(app.filesDir, dir)
            to.deleteRecursively()
            val from = File(staging, dir)
            if (from.isDirectory) from.copyRecursively(to, overwrite = true) else to.mkdirs()
        }
        File(staging, SETTINGS).takeIf { it.isFile }?.let { f -> runCatching { app.settings.importPrefs(JSONObject(f.readText())) } }
        // Evaluations that were running belong to the old progress.
        File(app.filesDir, "cloud_jobs.json").delete()
        SafeFile.delete(File(app.filesDir, "pending_folds.json"))
        File(app.filesDir, "live_draft.json").delete()
        staging.deleteRecursively()
        return info
    }
}
