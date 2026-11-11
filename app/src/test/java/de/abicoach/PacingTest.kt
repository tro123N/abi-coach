package de.abicoach

import de.abicoach.data.SessionKind
import de.abicoach.data.Speaker
import de.abicoach.data.Utterance
import de.abicoach.session.Pacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacingTest {
    private fun filler(n: Int, word: String) = (1..n).joinToString(" ") { word }

    /** A talk with [a], [b], [c] words for the three tasks (0 = task left out). */
    private fun talk(a: Int, b: Int, c: Int, seconds: Int = 600): Pair<de.abicoach.data.SessionRecord, de.abicoach.data.Debrief> {
        val text =
            buildString {
                append("Good morning. The text deals with climate change, ").append(filler(a, "summary"))
                if (b > 0) append(". Moving on to the analysis, the author uses ").append(filler(b, "device"))
                if (c > 0) append(". In my opinion, we should act now because ").append(filler(c, "reason"))
            }
        val record = Fixtures.record(part1Sec = seconds).copy(utterances = listOf(Utterance(Speaker.STUDENT, text, 1, 0L)))
        val segments =
            buildList {
                add(1 to "The text deals with climate change")
                if (b > 0) add(2 to "Moving on to the analysis, the author")
                if (c > 0) add(3 to "In my opinion, we should act")
            }
        return record to Fixtures.debrief().copy(segments = segments)
    }

    @Test
    fun aBalancedTalkIsPraised() {
        val (r, d) = talk(240, 400, 340)
        val split = Pacing.split(r, d)!!
        assertEquals(3, split.parts.size)
        assertTrue(split.balanced)
        assertEquals(600, split.parts.sumOf { it.seconds }.let { if (it in 598..602) 600 else it })
        assertTrue(split.parts[1].share > split.parts[0].share)
    }

    @Test
    fun aLongSummaryIsNamed() {
        val (r, d) = talk(600, 250, 150)
        val split = Pacing.split(r, d)!!
        assertFalse(split.balanced)
        assertTrue(split.advice.any { it.contains("Zusammenfassung") })
        assertTrue(split.advice.any { it.contains("Stellungnahme") })
        assertTrue(split.parts[0].percent > 50)
    }

    @Test
    fun aMissingCommentIsTheFirstAdvice() {
        val (r, d) = talk(400, 500, 0)
        val split = Pacing.split(r, d)!!
        assertEquals(2, split.parts.size)
        assertTrue(split.advice.first().contains("Aufgabe 3"))
    }

    @Test
    fun aShortTalkIsToldToUseTheTime() {
        val (r, d) = talk(60, 100, 90, seconds = 200)
        assertTrue(Pacing.split(r, d)!!.advice.any { it.contains("3:20") })
    }

    @Test
    fun theGreetingBelongsToTheFirstTask() {
        val (r, d) = talk(240, 400, 340)
        val split = Pacing.split(r, d)!!
        assertEquals(1.0, split.parts.sumOf { it.share }, 0.0001)
    }

    @Test
    fun aSlightlyDifferentTranscriptStillMatches() {
        val (r, d) = talk(240, 400, 340)
        // The grader quotes with other punctuation and a word the transcript does not have at the end.
        val loose = d.copy(segments = listOf(1 to "The text deals", 2 to "Moving on to THE analysis – the writer", 3 to "In my opinion: we"))
        assertNotNull(Pacing.split(r, loose))
        assertEquals(3, Pacing.split(r, loose)!!.parts.size)
    }

    @Test
    fun withoutTaskStartsThereIsNoSplit() {
        val (r, d) = talk(240, 400, 340)
        assertNull(Pacing.split(r, d.copy(segments = emptyList())))
        assertNull(Pacing.split(r, d.copy(segments = listOf(2 to "words that were never said at all"))))
    }

    @Test
    fun singleTasksAndConversationsHaveNoSplit() {
        val (r, d) = talk(240, 400, 340)
        assertNull(Pacing.split(r.copy(focusTask = 2), d))
        assertNull(Pacing.split(r.copy(kind = SessionKind.PART2), d))
        assertNull(Pacing.split(r.copy(part1Sec = 30), d))
    }

    @Test
    fun aWrongMatchThatJumpsBackIsLeftOut() {
        val (r, d) = talk(240, 400, 340)
        // "Task 3" is said to begin with words from the very start: not believable, so only tasks 1 and 2 are split.
        val wrong = d.copy(segments = listOf(1 to "The text deals with", 2 to "Moving on to the analysis", 3 to "Good morning"))
        assertEquals(listOf(1, 2), Pacing.split(r, wrong)!!.parts.map { it.number })
    }

    @Test
    fun theLiveHintFollowsTheClock() {
        assertTrue(Pacing.guide(30, 600).contains("Aufgabe 1"))
        assertTrue(Pacing.guide(200, 600).contains("Aufgabe 2"))
        assertTrue(Pacing.guide(500, 600).contains("Aufgabe 3"))
        assertTrue(Pacing.guide(700, 600).contains("Schluss"))
        assertEquals("", Pacing.guide(10, 0))
    }
}
