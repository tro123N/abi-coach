package de.abicoach.data

import java.io.File
import java.io.FileOutputStream

/**
 * Writes the app's data files so that a crash, a full disk or a dead battery in the middle of a write never destroys
 * what was there before: the new text goes into a temporary file first, the old file is kept as a backup, and only
 * then the new one takes its place. Reading falls back to the backup when the main file is missing or broken.
 */
object SafeFile {
    private fun tmp(file: File) = File(file.parentFile, file.name + ".tmp")

    private fun bak(file: File) = File(file.parentFile, file.name + ".bak")

    /** Replaces the content of [file]; false if nothing could be written (the old content is then still there). */
    fun write(file: File, text: String): Boolean {
        val tmp = tmp(file)
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                // Really on the disk before the old file is touched.
                runCatching { out.fd.sync() }
            }
            val bak = bak(file)
            if (file.exists()) {
                bak.delete()
                if (!file.renameTo(bak)) file.delete()
            }
            if (tmp.renameTo(file)) {
                true
            } else {
                // Renaming failed (very rare): write directly, the backup is still there.
                file.writeText(text)
                tmp.delete()
                true
            }
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    /** The files that may hold a valid version, best first. */
    private fun candidates(file: File): List<File> =
        listOfNotNull(file.takeIf { it.exists() }, tmp(file).takeIf { !file.exists() && it.exists() }, bak(file).takeIf { it.exists() })

    /**
     * Reads [file] with [parse]. A broken main file is not fatal: the backup is used instead (and the broken file is
     * put aside as ".corrupt", so nothing is silently lost). Null when there is no readable version at all.
     */
    fun <T : Any> load(file: File, parse: (String) -> T?): T? {
        for (candidate in candidates(file)) {
            val value = runCatching { parse(candidate.readText()) }.getOrNull()
            if (value != null) {
                if (candidate != file) runCatching { candidate.copyTo(file, overwrite = true) }
                return value
            }
            if (candidate == file) runCatching { file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true) }
        }
        return null
    }

    /** Removes the file with its backup and temporary copies. */
    fun delete(file: File) {
        file.delete()
        tmp(file).delete()
        bak(file).delete()
    }

    /** Forgets the older versions of [file] (after a deliberate reset, so the old content is really gone). */
    fun dropBackup(file: File) {
        tmp(file).delete()
        bak(file).delete()
        File(file.parentFile, file.name + ".corrupt").delete()
    }

    /** True for the helper files of this class (so that a directory listing can skip them). */
    fun isHelper(file: File): Boolean = file.name.endsWith(".tmp") || file.name.endsWith(".bak") || file.name.endsWith(".corrupt")
}
