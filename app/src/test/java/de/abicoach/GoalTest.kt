package de.abicoach

import de.abicoach.learn.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The student's goal: what is shown about it and how it steers the plan. */
class GoalTest {
    @Test
    fun withoutGoalTheAppAsksForOne() {
        assertEquals(Goal.Status.UNSET, Goal.state(0, 9.0, 10.0).status)
    }

    @Test
    fun goalBeforeTheFirstRating() {
        val s = Goal.state(12, null, null)
        assertEquals(Goal.Status.NO_DATA, s.status)
        assertTrue(s.headline.contains("12"))
    }

    @Test
    fun gapIsShown() {
        val s = Goal.state(12, 9.8, null)
        assertEquals(Goal.Status.OPEN, s.status)
        assertEquals(2.2, s.gap, 0.001)
        assertTrue(s.headline, s.headline.contains("noch 2,2"))
    }

    @Test
    fun paceDecidesTheMessage() {
        assertEquals(Goal.Status.ON_TRACK, Goal.state(12, 10.0, 12.3).status)
        assertEquals(Goal.Status.TIGHT, Goal.state(12, 10.0, 11.4).status)
        assertEquals(Goal.Status.BEHIND, Goal.state(12, 10.0, 10.5).status)
    }

    @Test
    fun reachedGoalIsSecured() {
        val s = Goal.state(11, 11.4, 12.0)
        assertEquals(Goal.Status.REACHED, s.status)
        assertEquals(0.0, s.gap, 0.0)
    }

    @Test
    fun withoutGoalEveryPointCountsTheSame() {
        assertEquals(5.0, Goal.gap(10.0, 0), 0.0)
    }

    @Test
    fun skillsBelowTheGoalCountMoreThanSkillsAboveIt() {
        // Goal 10: a skill at 7 is far more urgent than one at 12, although both are far from 15.
        val weak = Goal.gap(7.0, 10)
        val strong = Goal.gap(12.0, 10)
        assertTrue(weak > strong * 4)
        assertTrue(strong > 0.0)
    }

    @Test
    fun highGoalKeepsEverythingInPlay() {
        assertEquals(Goal.gap(9.0, 0), Goal.gap(9.0, 15), 0.001)
        assertEquals(Goal.gap(9.0, 0), Goal.gap(9.0, 14), 0.001)
    }
}
