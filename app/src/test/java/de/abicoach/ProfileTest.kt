package de.abicoach

import de.abicoach.profile.Area
import de.abicoach.profile.Profile
import de.abicoach.profile.ProfileStore
import de.abicoach.profile.Skill
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileTest {
    private val day = 86_400_000L

    @Test
    fun anEmptyProfileHasNoForecast() {
        assertNull(Profile().prognosis())
        assertTrue(Profile().skills.isEmpty())
    }

    @Test
    fun theFirstRatingIsTakenAsItIs() {
        val p = Profile().withRatings(mapOf(Skill.GRAMMATIK to 8), 1.0, 1_000L, "Test")
        assertEquals(8.0, p.skills[Skill.GRAMMATIK]!!.score, 0.0001)
        assertEquals(1, p.skills[Skill.GRAMMATIK]!!.samples)
    }

    @Test
    fun laterRatingsMoveTheScoreStepByStep() {
        var p = Profile().withRatings(mapOf(Skill.GRAMMATIK to 8), 1.0, 1_000L, "Test")
        p = p.withRatings(mapOf(Skill.GRAMMATIK to 12), 1.0, 2_000L, "Test")
        val score = p.skills[Skill.GRAMMATIK]!!.score
        // One good session does not make a new level, but it moves the score towards it.
        assertTrue("score $score", score > 8.0 && score < 12.0)
    }

    @Test
    fun aSessionThatCountsLessMovesTheScoreLess() {
        val start = Profile().withRatings(mapOf(Skill.GRAMMATIK to 8), 1.0, 1_000L, "Test")
        val full = start.withRatings(mapOf(Skill.GRAMMATIK to 14), 1.0, 2_000L, "Test").skills[Skill.GRAMMATIK]!!.score
        val half = start.withRatings(mapOf(Skill.GRAMMATIK to 14), 0.4, 2_000L, "Test").skills[Skill.GRAMMATIK]!!.score
        assertTrue(half < full)
        assertTrue(half > 8.0)
    }

    @Test
    fun pointsOutOfRangeNeverLeaveTheScale() {
        val p = Profile().withRatings(mapOf(Skill.GRAMMATIK to 99, Skill.ANALYSE to -4), 1.0, 1_000L, "Test")
        assertEquals(15.0, p.skills[Skill.GRAMMATIK]!!.score, 0.0001)
        assertEquals(0.0, p.skills[Skill.ANALYSE]!!.score, 0.0001)
    }

    @Test
    fun theForecastWeighsContentAndPresentationFortyToSixty() {
        val p = Profile().withRatings(mapOf(Skill.ANALYSE to 5, Skill.GRAMMATIK to 15), 1.0, 1_000L, "Test")
        val forecast = p.prognosis()!!
        assertEquals(Area.INHALT.weight * 5 + Area.DARSTELLUNG.weight * 15, forecast.total, 0.0001)
        assertEquals(5.0, forecast.inhalt!!, 0.0001)
        assertEquals(15.0, forecast.darstellung!!, 0.0001)
        assertTrue(forecast.low <= forecast.total && forecast.total <= forecast.high)
    }

    @Test
    fun everyRatedSessionAddsAPointToTheForecastHistory() {
        var p = Profile()
        repeat(3) { i -> p = p.withRatings(mapOf(Skill.ANALYSE to 8 + i, Skill.GRAMMATIK to 8 + i), 1.0, 1_000L * (i + 1), "Vortrag") }
        assertEquals(3, p.history.size)
        assertEquals("Vortrag", p.history.last().source)
    }

    @Test
    fun confidenceFadesWithoutPractice() {
        val p = Profile().withRatings(mapOf(Skill.GRAMMATIK to 10), 1.0, 0L, "Test")
        val state = p.skills[Skill.GRAMMATIK]!!
        val fresh = p.effectiveConfidence(state, day)
        val old = p.effectiveConfidence(state, 60 * day)
        assertTrue("fresh $fresh old $old", old < fresh)
        assertTrue(old >= 0.0)
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        var p = Profile().withRatings(mapOf(Skill.ANALYSE to 9, Skill.GRAMMATIK to 11), 1.0, 5_000L, "Simulation")
        p = p.withRatings(mapOf(Skill.ANALYSE to 10), 0.7, 6_000L, "Vortrag")
        val back = Profile.fromJson(JSONObject(p.toJson().toString()))
        assertEquals(p.prognosis()!!.total, back.prognosis()!!.total, 0.0001)
        assertEquals(p.history.size, back.history.size)
        assertEquals(p.skills[Skill.ANALYSE]!!.samples, back.skills[Skill.ANALYSE]!!.samples)
    }

    @Test
    fun aProfileFromAnOlderVersionStillLoads() {
        // Only the fields the very first version wrote.
        val old = JSONObject("{\"skills\":{\"grammar\":{\"score\":9.5,\"confidence\":0.5,\"samples\":2,\"updatedAt\":1}}}")
        val p = Profile.fromJson(old)
        assertEquals(9.5, p.skills[Skill.GRAMMATIK]!!.score, 0.0001)
        assertNotNull(p.prognosis())
    }

    @Test
    fun unknownCompetencesInTheFileAreSkipped() {
        val p = Profile.fromJson(JSONObject("{\"skills\":{\"telepathy\":{\"score\":15,\"confidence\":1,\"samples\":9,\"updatedAt\":1}}}"))
        assertTrue(p.skills.isEmpty())
    }

    @Test
    fun theStoreSurvivesABrokenFile() {
        val dir = Fixtures.tempDir()
        val store = ProfileStore(dir)
        store.update { it.withRatings(mapOf(Skill.GRAMMATIK to 9), 1.0, 1_000L, "Test") }
        store.update { it.withRatings(mapOf(Skill.GRAMMATIK to 11), 1.0, 2_000L, "Test") }
        // The file is destroyed (crash in the middle of a write) …
        File(dir, "profile.json").writeText("{\"skills\":{\"gram")
        // … the next start still has the progress of the save before.
        val again = ProfileStore(dir)
        assertNotNull(again.profile.value.skills[Skill.GRAMMATIK])
        assertEquals(9.0, again.profile.value.skills[Skill.GRAMMATIK]!!.score, 0.0001)
    }

    @Test
    fun resetReallyForgets() {
        val dir = Fixtures.tempDir()
        val store = ProfileStore(dir)
        store.update { it.withRatings(mapOf(Skill.GRAMMATIK to 9), 1.0, 1_000L, "Test") }
        store.reset()
        File(dir, "profile.json").delete()
        assertFalse(ProfileStore(dir).profile.value.skills.isNotEmpty())
    }
}

class CalibrationTest {
    private fun profile(points: Int) =
        Profile().withRatings(Skill.entries.associateWith { points }, 1.0, System.currentTimeMillis(), "Test")

    @Test
    fun withoutRealGradesThereIsNoComparison() {
        assertNull(profile(10).calibration())
        assertNull(Profile().withRealGrade(8, "Probe", 1L).calibration())
    }

    @Test
    fun oneRealGradeCountsHalf() {
        // The app says 10, the teacher gave 8: one grade moves the forecast by one point, not by two.
        val c = profile(10).withRealGrade(8, "Probe-Prüfung", 1L).calibration()!!
        assertEquals(-1.0, c.offset, 0.0001)
        assertEquals(9, c.adjustedPoints)
        assertEquals(1, c.count)
        assertFalse(c.confirmed)
    }

    @Test
    fun moreGradesCountMore() {
        var p = profile(10)
        repeat(3) { p = p.withRealGrade(8, "Probe", it.toLong()) }
        assertEquals(-1.5, p.calibration()!!.offset, 0.0001)
    }

    @Test
    fun aMatchingGradeConfirmsTheForecast() {
        val c = profile(10).withRealGrade(10, "Kommunikationsprüfung", 1L).calibration()!!
        assertTrue(c.confirmed)
        assertEquals(10, c.adjustedPoints)
    }

    @Test
    fun theAdjustedForecastStaysOnTheScale() {
        var p = profile(14)
        repeat(5) { p = p.withRealGrade(15, "Probe", it.toLong()) }
        assertTrue(p.calibration()!!.adjusted <= 15.0)
    }

    @Test
    fun realGradesSurviveSavingAndCanBeRemoved() {
        val p = profile(10).withRealGrade(8, "Probe-Prüfung", 42L)
        val back = Profile.fromJson(JSONObject(p.toJson().toString()))
        assertEquals(1, back.realGrades.size)
        assertEquals(8, back.realGrades[0].points)
        assertEquals("Probe-Prüfung", back.realGrades[0].label)
        assertEquals(10.0, back.realGrades[0].forecast!!, 0.0001)
        assertTrue(back.withoutRealGrade(42L).realGrades.isEmpty())
    }

    @Test
    fun aGradeEnteredBeforeAnyPracticeHasNoForecastAndIsIgnored() {
        val p = Profile().withRealGrade(8, "Probe", 1L).withRatings(mapOf(Skill.GRAMMATIK to 10), 1.0, System.currentTimeMillis(), "Test")
        assertNull(p.calibration())
    }
}
