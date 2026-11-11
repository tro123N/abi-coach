package de.abicoach.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The app's black box. A crash is written to a file before the app dies, so the next start can show what happened
 * and the report can be copied. Errors in background work that do not have to end the app are caught, noted here
 * and the app keeps running. Nothing leaves the phone.
 */
object CrashLog {
    private const val MAX_ISSUES_BYTES = 60_000L
    private const val MAX_CRASH_FILES = 5

    private var dir: File? = null
    private var version = "?"
    private val crumbs = ArrayDeque<String>()
    private val stamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

    /** Called for every background error that was caught (for a banner in the app). */
    @Volatile var onIssue: ((String) -> Unit)? = null

    /** Catches errors of background work: the app notes them and keeps running instead of closing. */
    val handler = CoroutineExceptionHandler { _, e -> nonFatal("Hintergrundarbeit", e) }

    fun install(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "crash").apply { mkdirs() }
        version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** A short note about what the app is doing (the last ones are part of a crash report). */
    @Synchronized
    fun breadcrumb(text: String) {
        crumbs.addLast("${stamp.format(Instant.now())} $text")
        while (crumbs.size > 40) crumbs.removeFirst()
    }

    private fun head(): String = "AbiCoach $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

    private fun trace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    @Synchronized
    private fun writeCrash(thread: Thread, error: Throwable) {
        val d = dir ?: return
        val text =
            buildString {
                append("ABSTURZ am ").append(stamp.format(Instant.now())).append('\n')
                append(head()).append('\n')
                append("Thread: ").append(thread.name).append("\n\n")
                append(trace(error).take(12_000)).append('\n')
                if (crumbs.isNotEmpty()) append("Davor:\n").append(crumbs.joinToString("\n")).append('\n')
            }
        File(d, "crash-${System.currentTimeMillis()}.txt").writeText(text)
        File(d, "unseen").writeText("1")
        d.listFiles { f -> f.name.startsWith("crash-") }?.sortedByDescending { it.name }?.drop(MAX_CRASH_FILES)?.forEach { it.delete() }
    }

    /** Notes an error that was caught. Cancellations are normal and never noted. */
    fun nonFatal(where: String, error: Throwable) {
        if (error is CancellationException) return
        val line = "${error.javaClass.simpleName}: ${error.message.orEmpty().take(200)}"
        runCatching {
            synchronized(this) {
                val d = dir ?: return@synchronized
                val file = File(d, "issues.log")
                if (file.length() > MAX_ISSUES_BYTES) file.writeText(file.readText().takeLast((MAX_ISSUES_BYTES / 2).toInt()))
                file.appendText("${stamp.format(Instant.now())} [$where] $line\n${trace(error).lineSequence().drop(1).take(6).joinToString("\n")}\n")
            }
        }
        runCatching { onIssue?.invoke("$where: $line") }
    }

    private fun crashFiles(): List<File> = dir?.listFiles { f -> f.name.startsWith("crash-") }?.sortedByDescending { it.name }.orEmpty()

    /** The report of a crash the student has not seen yet (null = the last run ended normally). */
    fun unseenCrash(): String? {
        val d = dir ?: return null
        if (!File(d, "unseen").exists()) return null
        return crashFiles().firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }
    }

    fun markSeen() {
        dir?.let { File(it, "unseen").delete() }
    }

    /** How many crashes and caught errors are stored. */
    fun counts(): Pair<Int, Int> {
        val issues = dir?.let { File(it, "issues.log") }?.takeIf { it.exists() }?.let { f -> runCatching { f.readLines().count { it.contains("] ") && it.firstOrNull()?.isDigit() == true } }.getOrDefault(0) } ?: 0
        return crashFiles().size to issues
    }

    /** Everything for copying: the crashes (newest first) and the last caught errors. */
    fun report(): String {
        val sb = StringBuilder()
        sb.append(head()).append("\n\n")
        crashFiles().forEach { f -> sb.append(runCatching { f.readText() }.getOrDefault("")).append("\n----\n") }
        val issues = dir?.let { File(it, "issues.log") }?.takeIf { it.exists() }?.let { runCatching { it.readText().takeLast(8_000) }.getOrNull() }
        if (!issues.isNullOrBlank()) sb.append("Abgefangene Fehler (die App lief weiter):\n").append(issues)
        if (crashFiles().isEmpty() && issues.isNullOrBlank()) sb.append("Keine Abstürze und keine Fehler gespeichert.")
        return sb.toString().take(30_000)
    }

    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
    }
}
