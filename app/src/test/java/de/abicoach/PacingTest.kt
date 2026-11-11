package de.abicoach

import de.abicoach.session.Pacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacingTest {
    @Test
    fun aBalancedTalkGetsNoComplaint() {
        assertTrue(Pacing.advice(listOf(1 to 130, 2 to 250, 3 to 190), 570).isEmpty())
    }

    @Test
    fun aLongSummaryIsNamedAndSoIsTheSqueezedComment() {
        val advice = Pacing.advice(listOf(1 to 330, 2 to 180, 3 to 60), 570)
        assertTrue(advice.any { it.contains("Zusammenfassung") && it.contains("58 %") })
        assertTrue(advice.any { it.contains("Stellungnahme") })
    }

    @Test
    fun aMissingCommentIsTheFirstAdvice() {
        val advice = Pacing.advice(listOf(1 to 200, 2 to 300), 500)
        assertTrue(advice.first().contains("Aufgabe 3"))
    }

    @Test
    fun materialWithTwoTasksDoesNotAskForAThird() {
        assertTrue(Pacing.advice(listOf(1 to 200, 2 to 300), 500, expectsThree = false).none { it.contains("Aufgabe 3") })
    }

    @Test
    fun aShortAnalysisIsNamed() {
        assertTrue(Pacing.advice(listOf(1 to 150, 2 to 80, 3 to 250), 480).any { it.contains("Analyse") })
    }

    @Test
    fun aShortTalkIsToldToUseTheTime() {
        assertTrue(Pacing.advice(listOf(1 to 50, 2 to 80, 3 to 70), 200).any { it.contains("3:20") })
    }

    @Test
    fun whenTheTasksWereNotFoundOnlyTheLengthIsJudged() {
        assertTrue(Pacing.advice(emptyList(), 600).isEmpty())
        assertEquals(1, Pacing.advice(listOf(1 to 100), 100).size)
    }

    @Test
    fun theLiveHintFollowsTheClock() {
        assertTrue(Pacing.guide(30, 600).contains("Aufgabe 1"))
        assertTrue(Pacing.guide(200, 600).contains("Aufgabe 2"))
        assertTrue(Pacing.guide(500, 600).contains("Aufgabe 3"))
        assertTrue(Pacing.guide(700, 600).contains("Schluss"))
        assertEquals("", Pacing.guide(10, 0))
    }

    @Test
    fun theHintUsesTheSameTargetsAsTheDebrief() {
        // 2 : 4 : 3 minutes of a nine-minute talk.
        assertTrue(Pacing.guide(0, 540).contains("2:00"))
        assertTrue(Pacing.guide(130, 540).contains("6:00"))
    }
}
