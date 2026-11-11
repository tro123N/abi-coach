package de.abicoach

import de.abicoach.data.SessionRecord
import de.abicoach.data.SessionStore
import de.abicoach.profile.Skill
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionStoreTest {
    private val day = 86_400_000L

    @Test
    fun aSavedSessionIsThereAfterARestart() {
        val dir = Fixtures.tempDir()
        SessionStore(dir).save(Fixtures.record(id = "a", debrief = Fixtures.debrief(inhalt = 10, darstellung = 11, skills = mapOf(Skill.ANALYSE to 10))))
        val again = SessionStore(dir)
        val back = again.get("a")
        assertNotNull(back)
        assertEquals(10, back!!.debrief!!.inhaltPoints)
        assertEquals(10, back.debrief!!.skills[Skill.ANALYSE])
        assertEquals(400, back.studentText.split(" ").size)
    }

    @Test
    fun sessionsAreSortedNewestFirstAndSavingTwiceKeepsOne() {
        val store = SessionStore(Fixtures.tempDir())
        store.save(Fixtures.record(id = "old", startedAt = 1_000L))
        store.save(Fixtures.record(id = "new", startedAt = 9_000L))
        store.save(Fixtures.record(id = "old", startedAt = 1_000L))
        assertEquals(listOf("new", "old"), store.sessions.value.map { it.id })
    }

    @Test
    fun aBrokenSessionFileFallsBackToTheVersionBefore() {
        val dir = Fixtures.tempDir()
        val store = SessionStore(dir)
        store.save(Fixtures.record(id = "a"))
        store.save(Fixtures.record(id = "a", debrief = Fixtures.debrief()))
        File(dir, "sessions/a.json").writeText("{\"id\":\"a\",\"kin")
        val again = SessionStore(dir)
        // Not lost: the version without the evaluation is back and can be evaluated again.
        assertNotNull(again.get("a"))
        assertNull(again.get("a")!!.debrief)
    }

    @Test
    fun aDeletedSessionDoesNotComeBack() {
        val dir = Fixtures.tempDir()
        val store = SessionStore(dir)
        store.save(Fixtures.record(id = "a"))
        store.save(Fixtures.record(id = "a", debrief = Fixtures.debrief()))
        store.audioFile("a", 1).writeBytes(ByteArray(100))
        store.delete("a")
        assertNull(store.get("a"))
        assertNull(SessionStore(dir).get("a"))
        assertFalse(store.audioFile("a", 1).exists())
    }

    @Test
    fun deleteAllLeavesNothing() {
        val dir = Fixtures.tempDir()
        val store = SessionStore(dir)
        store.save(Fixtures.record(id = "a"))
        store.save(Fixtures.record(id = "b"))
        store.deleteAll()
        assertTrue(SessionStore(dir).sessions.value.isEmpty())
    }

    @Test
    fun tidyingRemovesOnlyOldRecordingsOfEvaluatedSessions() {
        val dir = Fixtures.tempDir()
        val store = SessionStore(dir)
        val now = 100 * day
        store.save(Fixtures.record(id = "oldDone", debrief = Fixtures.debrief()))
        store.save(Fixtures.record(id = "oldOpen"))
        store.save(Fixtures.record(id = "freshDone", debrief = Fixtures.debrief()))
        fun audio(id: String, age: Long) = store.audioFile(id, 1).apply { writeBytes(ByteArray(1000)); setLastModified(now - age) }
        val oldDone = audio("oldDone", 40 * day)
        val oldOpen = audio("oldOpen", 40 * day)
        val freshDone = audio("freshDone", 2 * day)
        val orphan = audio("gone", 3 * day)
        val freed = store.tidyAudio(30, now)
        assertFalse("old recording of an evaluated session goes", oldDone.exists())
        assertTrue("a session that is not evaluated keeps its recording", oldOpen.exists())
        assertTrue("a fresh recording stays", freshDone.exists())
        assertFalse("a recording without a session goes", orphan.exists())
        assertEquals(2000L, freed)
        // The sessions themselves are untouched.
        assertNotNull(store.get("oldDone")!!.debrief)
    }

    @Test
    fun keepingForEverDeletesNothing() {
        val store = SessionStore(Fixtures.tempDir())
        store.save(Fixtures.record(id = "a", debrief = Fixtures.debrief()))
        val f = store.audioFile("a", 1).apply { writeBytes(ByteArray(10)); setLastModified(1L) }
        assertEquals(0L, store.tidyAudio(0))
        assertTrue(f.exists())
    }

    @Test
    fun aSessionFromAnOlderVersionStillLoads() {
        // A record as an early version wrote it: none of the fields that came later.
        val full = Fixtures.record(id = "legacy", debrief = Fixtures.debrief()).toJson()
        listOf("focusTask", "focusCategories", "examiner", "examinerSeconds", "levelId", "retryOf", "tech", "goals", "extraTopics", "focusSkill", "talkStyle", "strictness").forEach { full.remove(it) }
        val d = full.getJSONObject("debrief")
        listOf("guardNotes", "reliability", "secondOpinion", "observations", "goalResults", "model", "progress", "fillers", "fillerWords", "readingAloud", "segments").forEach { d.remove(it) }
        val back = SessionRecord.fromJson(JSONObject(full.toString()))
        assertEquals("legacy", back.id)
        assertEquals(1.0, back.debrief!!.reliability, 0.0001)
        assertTrue(back.debrief!!.guardNotes.isEmpty())
        assertEquals(-1, back.debrief!!.fillers)
    }
}
