package de.abicoach

import de.abicoach.learn.DailyStore
import de.abicoach.learn.Day
import de.abicoach.learn.DayStep
import de.abicoach.learn.ErrorMemoryStore
import de.abicoach.learn.ErrorPattern
import de.abicoach.learn.ErrorStage
import de.abicoach.learn.PatternState
import de.abicoach.learn.StepRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ladder a mistake climbs when it keeps coming back, and comes down when it stays away. */
class ErrorLadderTest {
    private val day = ErrorPattern.DAY
    private val rule = "third person -s"

    private fun mistake() = listOf(Fixtures.error("he go", "he goes", rule))

    private fun ErrorMemoryStore.session(n: Int, withMistake: Boolean) =
        ingest(if (withMistake) mistake() else emptyList(), "s$n", ErrorMemoryStore.SOURCE_SESSION, n * day, 300)

    private fun ErrorMemoryStore.only() = patterns.value.single()

    @Test
    fun seenOnceIsOnlyNoted() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        assertEquals(ErrorStage.MERKEN, store.only().stage)
    }

    @Test
    fun seenAgainGoesIntoTraining() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        store.session(2, true)
        assertEquals(ErrorStage.TRAINING, store.only().stage)
    }

    @Test
    fun threeSessionsWithTheMistakeNeedTheWorkshop() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        (1..3).forEach { store.session(it, true) }
        assertEquals(ErrorStage.WERKSTATT, store.only().stage)
    }

    @Test
    fun comingBackAfterItGotBetterIsARelapseAndNeedsTheWorkshop() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        store.session(2, false)
        assertEquals(PatternState.BESSER, store.only().state)
        store.session(3, true)
        assertEquals(1, store.only().relapses)
        assertEquals(ErrorStage.WERKSTATT, store.only().stage)
    }

    @Test
    fun afterTheWorkshopItIsTrainedAgainAndBecomesTheGoalIfItStillComesBack() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        (1..3).forEach { store.session(it, true) }
        val key = store.only().key
        store.lessonDone(key, 3 * day + 1000)
        assertEquals(ErrorStage.TRAINING, store.only().stage)
        store.session(5, true)
        assertEquals(ErrorStage.PRUEFUNGSZIEL, store.only().stage)
    }

    @Test
    fun threeCleanSessionsFixItAndPlanACheck() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        store.session(2, true)
        (3..5).forEach { store.session(it, false) }
        val p = store.only()
        assertEquals(PatternState.BEHOBEN, p.state)
        assertEquals(ErrorStage.KONTROLLE, p.stage)
        assertEquals(5 * day + ErrorPattern.FIRST_CHECK_DAYS * day, p.checkAt)
        // Not due before the check, due at the check.
        assertFalse(p.isDue(6 * day))
        assertTrue(p.isDue(p.checkAt))
    }

    @Test
    fun twoPassedChecksMakeItSafe() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        (2..4).forEach { store.session(it, false) }
        val key = store.only().key
        store.reviewDrill(key, true, store.only().checkAt)
        assertEquals(1, store.only().checks)
        assertEquals(ErrorStage.KONTROLLE, store.only().stage)
        assertTrue("the second check is planned", store.only().checkAt > 0)
        store.reviewDrill(key, true, store.only().checkAt)
        assertEquals(ErrorStage.SICHER, store.only().stage)
        assertFalse(store.only().isDue(Long.MAX_VALUE / 2))
    }

    @Test
    fun aFailedCheckOpensTheMistakeAgain() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        (2..4).forEach { store.session(it, false) }
        val key = store.only().key
        store.reviewDrill(key, false, store.only().checkAt)
        val p = store.only()
        assertFalse(p.state == PatternState.BEHOBEN)
        assertEquals(1, p.relapses)
        assertEquals(0L, p.checkAt)
        assertEquals(ErrorStage.WERKSTATT, p.stage)
    }

    @Test
    fun aFixedMistakeThatComesBackInASessionCountsAsRelapse() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        store.session(1, true)
        (2..4).forEach { store.session(it, false) }
        store.session(5, true)
        val p = store.only()
        assertEquals(1, p.relapses)
        assertEquals(0L, p.checkAt)
        assertEquals(0, p.checks)
    }

    @Test
    fun theLadderSurvivesARestart() {
        val dir = Fixtures.tempDir()
        val store = ErrorMemoryStore(dir)
        (1..3).forEach { store.session(it, true) }
        store.lessonDone(store.only().key, 4 * day)
        val again = ErrorMemoryStore(dir).patterns.value.single()
        assertEquals(4 * day, again.lessonAt)
        assertEquals(ErrorStage.TRAINING, again.stage)
    }

    @Test
    fun atStageListsTheRightMistakes() {
        val store = ErrorMemoryStore(Fixtures.tempDir())
        (1..3).forEach { store.session(it, true) }
        store.ingest(listOf(Fixtures.error("since two years", "for two years", "since vs for", "PREPOSITION")), "x", ErrorMemoryStore.SOURCE_SESSION, 4 * day, 300)
        assertEquals(listOf(rule), store.atStage(ErrorStage.WERKSTATT, 5 * day).map { it.rule })
        assertEquals(listOf("since vs for"), store.atStage(ErrorStage.MERKEN, 5 * day).map { it.rule })
    }
}

/** Today's plan: ticking off, the ledger, the next day. */
class DailyStoreTest {
    private fun step(kind: String, role: StepRole = StepRole.SCHWERPUNKT) = DayStep(id = kind.lowercase(), role = role, kind = kind, action = kind.lowercase(), title = kind, reason = "", minutes = 10)

    private fun plan(date: String, vararg kinds: String) = Day(date, "Aufbau", kinds.map { step(it) })

    @Test
    fun theFirstOpenStepIsNext() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "VOCAB", "DRILLS", "TASK2"))
        assertEquals("VOCAB", store.today("2026-11-12")!!.next!!.kind)
        store.complete("2026-11-12", "VOCAB", "Wortschatz", "6 Karten", 1L)
        val d = store.today("2026-11-12")!!
        assertEquals("DRILLS", d.next!!.kind)
        assertEquals(1, d.doneCount)
        assertEquals("6 Karten", d.steps[0].effect)
    }

    @Test
    fun practiceOutsideThePlanStillCounts() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "VOCAB"))
        assertNull(store.complete("2026-11-12", "PRONUNCIATION", "Aussprache", "geübt", 1L))
        val d = store.today("2026-11-12")!!
        assertEquals(0, d.doneCount)
        assertEquals(1, d.ledger.size)
        assertFalse(d.ledger[0].planned)
    }

    @Test
    fun aWholeExamAlsoTicksOffPresentationAndConversation() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "CONVERSATION"))
        assertNotNull(store.complete("2026-11-12", "SIMULATION", "Simulation", "", 1L))
        assertTrue(store.today("2026-11-12")!!.allDone)
    }

    @Test
    fun aWholePresentationDoesNotTickOffASingleTask() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "TASK2"))
        assertNull(store.complete("2026-11-12", "PRESENTATION", "Vortrag", "", 1L))
    }

    @Test
    fun theResultOfTheEvaluationIsWrittenNextToTheStepLater() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "TASK2"))
        store.complete("2026-11-12", "TASK2", "Aufgabe 2", "wird ausgewertet …", 1L, ref = "session-1")
        store.effect("session-1", "9 Punkte · Prognose 9,8 → 10,0")
        val d = store.today("2026-11-12")!!
        assertEquals("9 Punkte · Prognose 9,8 → 10,0", d.steps[0].effect)
        assertEquals("9 Punkte · Prognose 9,8 → 10,0", d.ledger[0].effect)
    }

    @Test
    fun theNextDayKnowsWhatStayedOpen() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "VOCAB", "TASK2"))
        store.complete("2026-11-12", "VOCAB", "Wortschatz", "", 1L)
        store.begin(plan("2026-11-13", "DRILLS"))
        assertEquals(listOf("TASK2"), store.openFromBefore("2026-11-13"))
        assertNull(store.today("2026-11-12"))
    }

    @Test
    fun theStreakCountsFinishedDaysInARow() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-10", "VOCAB"))
        store.begin(plan("2026-11-11", "VOCAB"))
        store.complete("2026-11-11", "VOCAB", "", "", 1L)
        store.begin(plan("2026-11-12", "VOCAB"))
        store.complete("2026-11-12", "VOCAB", "", "", 1L)
        store.begin(plan("2026-11-13", "VOCAB"))
        // The 10th was not finished, the 11th and 12th were; today is still open.
        assertEquals(2, store.finishedStreak("2026-11-13"))
        store.complete("2026-11-13", "VOCAB", "", "", 1L)
        assertEquals(3, store.finishedStreak("2026-11-13"))
    }

    @Test
    fun replanningKeepsWhatIsDone() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "VOCAB", "TASK2"))
        store.complete("2026-11-12", "VOCAB", "", "", 1L)
        store.replan("2026-11-12", listOf(step("VOCAB"), step("CONVERSATION")))
        assertEquals(listOf("VOCAB", "CONVERSATION"), store.today("2026-11-12")!!.steps.map { it.kind })
        assertTrue(store.today("2026-11-12")!!.steps[0].done)
    }

    @Test
    fun thePlanSurvivesARestart() {
        val dir = Fixtures.tempDir()
        val store = DailyStore(dir)
        store.begin(plan("2026-11-12", "VOCAB", "TASK2"))
        store.complete("2026-11-12", "VOCAB", "Wortschatz", "6 Karten", 5L)
        val again = DailyStore(dir).today("2026-11-12")!!
        assertEquals(1, again.doneCount)
        assertEquals("TASK2", again.next!!.kind)
        assertEquals(1, again.ledger.size)
    }

    @Test
    fun aBonusStepIsNotAddedTwice() {
        val store = DailyStore(Fixtures.tempDir())
        store.begin(plan("2026-11-12", "VOCAB"))
        store.add("2026-11-12", step("SPRINT", StepRole.EXTRA))
        store.add("2026-11-12", step("SPRINT", StepRole.EXTRA))
        assertEquals(2, store.today("2026-11-12")!!.steps.size)
    }
}
