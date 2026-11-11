package de.abicoach

import de.abicoach.audio.Wav
import de.abicoach.learn.ErrorMemoryStore
import de.abicoach.path.Level
import de.abicoach.path.LevelType
import de.abicoach.path.PathEngine
import de.abicoach.path.PathProgress
import de.abicoach.practice.CardSource
import de.abicoach.practice.Grade
import de.abicoach.practice.Scheduler
import de.abicoach.practice.VocabCard
import de.abicoach.practice.VocabStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class LearningTest {
    private val day = 86_400_000L

    // ---- Error memory ----

    @Test
    fun theSameMistakeInTwoSessionsBecomesOnePattern() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.ingest(listOf(Fixtures.error("he go to school", "he goes to school", "third person -s")), "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        store.ingest(listOf(Fixtures.error("she like it", "she likes it", "third person -s")), "s2", ErrorMemoryStore.SOURCE_SESSION, 2_000L, 300)
        val patterns = store.patterns.value
        assertEquals(1, patterns.size)
        assertEquals(2, patterns[0].sessions)
        assertEquals(2, patterns[0].total)
    }

    @Test
    fun aSessionIsNeverCountedTwice() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        val errors = listOf(Fixtures.error("he go", "he goes", "third person -s"))
        store.ingest(errors, "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        store.ingest(errors, "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        assertEquals(1, store.patterns.value[0].total)
    }

    @Test
    fun countingTwiceIsPreventedAcrossARestartToo() {
        val dir = Fixtures.tempDir()
        val errors = listOf(Fixtures.error("he go", "he goes", "third person -s"))
        ErrorMemoryStore(dir).ingest(errors, "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        val again = ErrorMemoryStore(dir)
        again.ingest(errors, "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        assertEquals(1, again.patterns.value.size)
        assertEquals(1, again.patterns.value[0].total)
    }

    @Test
    fun aCleanSessionWithEnoughSpeechCountsForThePattern() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.ingest(listOf(Fixtures.error("he go", "he goes", "third person -s")), "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        // Too little said: no proof that the mistake is gone.
        store.ingest(emptyList(), "s2", ErrorMemoryStore.SOURCE_SESSION, 2_000L, 20)
        assertEquals(0, store.patterns.value[0].cleanStreak)
        store.ingest(emptyList(), "s3", ErrorMemoryStore.SOURCE_SESSION, 3_000L, 300)
        assertEquals(1, store.patterns.value[0].cleanStreak)
    }

    @Test
    fun contentRemarksAreNotLanguagePatterns() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.ingest(listOf(Fixtures.error("x", "y", "wrong fact", category = "CONTENT")), "s1", ErrorMemoryStore.SOURCE_SESSION, 1_000L, 300)
        assertTrue(store.patterns.value.isEmpty())
    }

    // ---- Vocabulary (spaced repetition) ----

    private fun card() = VocabCard(id = "c", front = "to tackle a problem", back = "ein Problem angehen", example = "", source = CardSource.PHRASE)

    @Test
    fun goodAnswersMakeTheIntervalGrow() {
        var c = card()
        val intervals = ArrayList<Double>()
        repeat(4) {
            c = Scheduler.review(c, Grade.GOOD, 0L)
            intervals += c.intervalDays
        }
        assertEquals(intervals.sorted(), intervals)
        assertTrue(intervals.last() > intervals.first())
    }

    @Test
    fun aForgottenCardComesBackInMinutes() {
        var c = card()
        repeat(3) { c = Scheduler.review(c, Grade.GOOD, 0L) }
        c = Scheduler.review(c, Grade.AGAIN, 1_000L)
        assertEquals(0.0, c.intervalDays, 0.0001)
        assertEquals(1, c.lapses)
        assertTrue(c.dueAt - 1_000L <= 15 * 60_000L)
    }

    @Test
    fun theIntervalNeverExceedsHalfAYear() {
        var c = card()
        repeat(40) { c = Scheduler.review(c, Grade.EASY, 0L) }
        assertTrue(c.intervalDays <= 180.0)
    }

    @Test
    fun theVocabularyStoreDoesNotAddTheSameCardTwice() {
        val store = VocabStore(Fixtures.tempDir())
        store.addAll(listOf(card()))
        store.addAll(listOf(card().copy(id = "other")))
        assertEquals(1, store.cards.value.size)
    }

    // ---- Abi-Weg ----

    private val level = Level(id = "x1", title = "T", method = "", type = LevelType.SIMULATION, learn = "", goal = 9)

    @Test
    fun starsFollowThePoints() {
        assertEquals(0, PathEngine.stars(level, 8))
        assertEquals(1, PathEngine.stars(level, 9))
        assertEquals(3, PathEngine.stars(level, 13))
        assertTrue(PathEngine.stars(level, 11) in 1..2)
    }

    @Test
    fun passingALevelSchedulesAReview() {
        val p = PathEngine.applyResult(PathProgress(), level, 10, 1_000L)
        val lp = p.of("x1")
        assertTrue(lp.passed)
        assertTrue(lp.nextReviewAt > 1_000L)
        assertEquals(1, lp.attempts)
        assertEquals(10, lp.best)
    }

    @Test
    fun failingAPassedLevelMakesItRustyButKeepsTheStars() {
        var p = PathEngine.applyResult(PathProgress(), level, 13, 1_000L)
        val stars = p.of("x1").stars
        p = PathEngine.applyResult(p, level, 4, 2_000L)
        assertTrue(p.of("x1").rusty)
        assertEquals(stars, p.of("x1").stars)
        assertEquals(13, p.of("x1").best)
    }

    @Test
    fun aFailedFirstTryIsOnlyCounted() {
        val p = PathEngine.applyResult(PathProgress(), level, 3, 1_000L)
        assertFalse(p.of("x1").passed)
        assertEquals(1, p.of("x1").attempts)
    }

    // ---- Recordings ----

    @Test
    fun wavRoundTrip() {
        val pcm = ByteArray(3200) { (it % 50).toByte() }
        val wav = Wav.of(pcm, 16_000)
        assertEquals(44 + 3200, wav.size)
        val back = Wav.toPcm(wav, 16_000)
        assertNotNull(back)
        assertEquals(pcm.toList(), back!!.toList())
    }

    @Test
    fun aRecordingThatWasNeverClosedIsRepaired() {
        val f = File(Fixtures.tempDir(), "s_p1.wav")
        // What a killed app leaves behind: a header that says "0 bytes of sound" and then the sound.
        val sound = ByteArray(32_000) { (it % 7).toByte() }
        f.writeBytes(Wav.of(ByteArray(0), 16_000) + sound)
        assertEquals(1, Wav.repair(f))
        val pcm = Wav.toPcm(f.readBytes(), 16_000)
        assertNotNull(pcm)
        assertEquals(32_000, pcm!!.size)
        // A healthy file is left alone.
        val before = f.readBytes().toList()
        assertEquals(1, Wav.repair(f))
        assertEquals(before, f.readBytes().toList())
    }

    @Test
    fun anEmptyRecordingIsNotUsable() {
        val f = File(Fixtures.tempDir(), "empty.wav")
        RandomAccessFile(f, "rw").use { it.write(Wav.of(ByteArray(0), 16_000)) }
        assertEquals(0, Wav.repair(f))
        assertEquals(0, Wav.repair(File(f.parentFile, "missing.wav")))
    }
}
