package de.abicoach

import de.abicoach.data.SessionKind
import de.abicoach.data.TaskReview
import de.abicoach.profile.Skill
import de.abicoach.session.GradeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeGuardTest {
    private fun reviews(vararg points: Int) = points.mapIndexed { i, p -> TaskReview(i + 1, p, emptyList(), emptyList(), "") }

    @Test
    fun aConsistentGradingStaysAsItIs() {
        val d =
            Fixtures.debrief(
                inhalt = 10,
                darstellung = 9,
                skills = mapOf(Skill.ANALYSE to 10, Skill.KOMMENTAR to 9, Skill.GRAMMATIK to 9, Skill.WORTSCHATZ to 10),
                taskReviews = reviews(10, 10, 9),
            )
        val v = GradeGuard.review(Fixtures.record(), d)
        assertEquals(10, v.debrief.inhaltPoints)
        assertEquals(9, v.debrief.darstellungPoints)
        assertEquals(9, v.debrief.totalPoints)
        assertTrue(v.notes.isEmpty())
        assertEquals(1.0, v.reliability, 0.0001)
    }

    @Test
    fun areaPointsFarAboveTheCompetencesArePulledBack() {
        // The grader gave 13 for content but rated the content competences around 8.
        val d = Fixtures.debrief(inhalt = 13, darstellung = 9, skills = mapOf(Skill.ANALYSE to 8, Skill.KOMMENTAR to 8, Skill.ZUSAMMENFASSEN to 8))
        val v = GradeGuard.review(Fixtures.record(kind = SessionKind.PART2, withTask = false), d)
        assertEquals(10, v.debrief.inhaltPoints)
        assertEquals(1, v.notes.size)
        // The total follows the corrected points, 40:60.
        assertEquals(Math.round(0.4 * 10 + 0.6 * 9).toInt(), v.debrief.totalPoints)
    }

    @Test
    fun areaPointsFarBelowTheCompetencesAreLifted() {
        val d = Fixtures.debrief(inhalt = 9, darstellung = 5, skills = mapOf(Skill.GRAMMATIK to 10, Skill.WORTSCHATZ to 10, Skill.FLUESSIGKEIT to 10))
        val v = GradeGuard.review(Fixtures.record(kind = SessionKind.PART2, withTask = false), d)
        assertEquals(8, v.debrief.darstellungPoints)
    }

    @Test
    fun oneRatedCompetenceIsNotEnoughToCorrect() {
        val d = Fixtures.debrief(inhalt = 13, darstellung = 9, skills = mapOf(Skill.ANALYSE to 6))
        val v = GradeGuard.review(Fixtures.record(kind = SessionKind.PART2, withTask = false), d)
        assertEquals(13, v.debrief.inhaltPoints)
    }

    @Test
    fun aPresentationWithoutTheCommentCannotBeGood() {
        val d = Fixtures.debrief(inhalt = 12, darstellung = 11, taskReviews = reviews(12, 12, 0))
        val v = GradeGuard.review(Fixtures.record(), d)
        assertEquals(9, v.debrief.inhaltPoints)
        assertTrue(v.notes.any { it.contains("Aufgabe 3") })
    }

    @Test
    fun twoMissingTasksCapEvenLower() {
        val d = Fixtures.debrief(inhalt = 11, darstellung = 11, taskReviews = reviews(11, 0, 0))
        assertEquals(6, GradeGuard.review(Fixtures.record(), d).debrief.inhaltPoints)
    }

    @Test
    fun practisingASingleTaskIsNotCapped() {
        val d = Fixtures.debrief(inhalt = 12, darstellung = 11, taskReviews = reviews(12))
        val v = GradeGuard.review(Fixtures.record().copy(focusTask = 1), d)
        assertEquals(12, v.debrief.inhaltPoints)
    }

    @Test
    fun aFewWordsDoNotCountForTheForecast() {
        val v = GradeGuard.review(Fixtures.record(words = 8, part1Sec = 20), Fixtures.debrief())
        assertEquals(0.0, v.reliability, 0.0001)
        assertEquals(0.0, v.debrief.reliability, 0.0001)
    }

    @Test
    fun littleSpeechCountsLess() {
        assertEquals(0.3, GradeGuard.review(Fixtures.record(words = 30, kind = SessionKind.TRAINING), Fixtures.debrief()).reliability, 0.0001)
        assertEquals(0.6, GradeGuard.review(Fixtures.record(words = 100, kind = SessionKind.TRAINING), Fixtures.debrief()).reliability, 0.0001)
        assertEquals(1.0, GradeGuard.review(Fixtures.record(words = 300, kind = SessionKind.TRAINING), Fixtures.debrief()).reliability, 0.0001)
    }

    @Test
    fun aVeryShortWholePresentationCountsLess() {
        val d = Fixtures.debrief(taskReviews = reviews(9, 9, 9))
        val v = GradeGuard.review(Fixtures.record(words = 300, part1Sec = 150), d)
        assertEquals(0.8, v.reliability, 0.0001)
    }

    @Test
    fun impossiblePointsAreBroughtIntoRange() {
        val v = GradeGuard.review(Fixtures.record(kind = SessionKind.PART2, withTask = false), Fixtures.debrief(inhalt = 22, darstellung = -3))
        assertEquals(15, v.debrief.inhaltPoints)
        assertEquals(0, v.debrief.darstellungPoints)
    }

    @Test
    fun theSameInputAlwaysGivesTheSameResult() {
        val d = Fixtures.debrief(inhalt = 13, darstellung = 12, skills = mapOf(Skill.ANALYSE to 7, Skill.KOMMENTAR to 9), taskReviews = reviews(9, 8, 0))
        val r = Fixtures.record()
        assertEquals(GradeGuard.review(r, d).debrief, GradeGuard.review(r, d).debrief)
    }
}
