package de.abicoach

import de.abicoach.data.SafeFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SafeFileTest {
    private fun parse(text: String): String? = if (text.startsWith("ok:")) text else throw IllegalStateException("broken")

    @Test
    fun writeThenLoadGivesTheSameText() {
        val f = File(Fixtures.tempDir(), "a.json")
        assertTrue(SafeFile.write(f, "ok:1"))
        assertEquals("ok:1", SafeFile.load(f, ::parse))
    }

    @Test
    fun missingFileGivesNull() {
        assertNull(SafeFile.load(File(Fixtures.tempDir(), "none.json"), ::parse))
    }

    @Test
    fun brokenMainFileFallsBackToTheVersionBefore() {
        val f = File(Fixtures.tempDir(), "a.json")
        SafeFile.write(f, "ok:1")
        SafeFile.write(f, "ok:2")
        // The phone died in the middle of a write: the main file is garbage.
        f.writeText("{\"half")
        assertEquals("ok:1", SafeFile.load(f, ::parse))
        // The good version is the main file again, and the broken one was put aside.
        assertEquals("ok:1", f.readText())
        assertTrue(File(f.parentFile, "a.json.corrupt").exists())
    }

    @Test
    fun crashBetweenTheTwoRenamesLosesNothing() {
        val f = File(Fixtures.tempDir(), "a.json")
        SafeFile.write(f, "ok:1")
        // State after "old file moved to backup" and before "new file moved into place".
        f.renameTo(File(f.parentFile, "a.json.bak"))
        File(f.parentFile, "a.json.tmp").writeText("ok:2")
        assertEquals("ok:2", SafeFile.load(f, ::parse))
    }

    @Test
    fun halfWrittenTemporaryFileIsIgnored() {
        val f = File(Fixtures.tempDir(), "a.json")
        SafeFile.write(f, "ok:1")
        File(f.parentFile, "a.json.tmp").writeText("{\"half")
        assertEquals("ok:1", SafeFile.load(f, ::parse))
    }

    @Test
    fun deleteRemovesTheBackupToo() {
        val f = File(Fixtures.tempDir(), "a.json")
        SafeFile.write(f, "ok:1")
        SafeFile.write(f, "ok:2")
        SafeFile.delete(f)
        assertNull(SafeFile.load(f, ::parse))
        assertFalse(File(f.parentFile, "a.json.bak").exists())
    }

    @Test
    fun dropBackupForgetsTheOldContent() {
        val f = File(Fixtures.tempDir(), "a.json")
        SafeFile.write(f, "ok:secret")
        SafeFile.write(f, "ok:empty")
        SafeFile.dropBackup(f)
        f.delete()
        assertNull(SafeFile.load(f, ::parse))
    }

    @Test
    fun helperFilesAreRecognised() {
        assertTrue(SafeFile.isHelper(File("x.json.bak")))
        assertTrue(SafeFile.isHelper(File("x.json.tmp")))
        assertTrue(SafeFile.isHelper(File("x.json.corrupt")))
        assertFalse(SafeFile.isHelper(File("x.json")))
    }
}
