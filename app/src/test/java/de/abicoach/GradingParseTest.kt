package de.abicoach

import de.abicoach.profile.Skill
import de.abicoach.session.Prompts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GradingParseTest {
    private val student = "I think that the author want to show us a problem. He go on to explain it."

    private fun answer(inhalt: String, darstellung: String) =
        JSONObject(
            """
            {"inhalt": $inhalt, "darstellung": $darstellung,
             "skills": {"analysis": 9, "grammar": 7, "vocabulary": -1, "fluency": 40},
             "errors": [
               {"quote": "the author want to show", "correction": "the author wants to show", "explanation": "", "category": "GRAMMAR", "severity": "major", "rule": "third person -s"},
               {"quote": "this sentence was never said", "correction": "x", "explanation": "", "category": "GRAMMAR", "severity": "major", "rule": "made up"}
             ]}
            """.trimIndent(),
        )

    @Test
    fun aCompleteAnswerBecomesADebrief() {
        val d = Prompts.parseDebrief(answer("{\"points\": 10, \"reason\": \"ok\"}", "{\"points\": 8, \"reason\": \"ok\"}"), student, false, null, "Test-KI", "")
        assertEquals(10, d.inhaltPoints)
        assertEquals(8, d.darstellungPoints)
        // 40:60
        assertEquals(9, d.totalPoints)
        assertEquals(9, d.skills[Skill.ANALYSE])
        // "Not assessable" (-1) and nonsense (40) are not ratings.
        assertFalse(d.skills.containsKey(Skill.WORTSCHATZ))
        assertFalse(d.skills.containsKey(Skill.FLUESSIGKEIT))
    }

    @Test
    fun mistakesTheStudentNeverSaidAreDropped() {
        val d = Prompts.parseDebrief(answer("{\"points\": 10}", "{\"points\": 8}"), student, false, null, "Test-KI", "")
        assertEquals(1, d.errors.size)
        assertEquals("third person -s", d.errors[0].rule)
        assertEquals(1, d.droppedUnverified)
    }

    @Test
    fun anAnswerWithoutPointsIsAnErrorAndNotZeroPoints() {
        try {
            Prompts.parseDebrief(answer("{\"reason\": \"no points here\"}", "{\"points\": 8}"), student, false, null, "Test-KI", "")
            fail("an incomplete grading must not be accepted")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("unvollständig"))
        }
    }

    @Test
    fun pointsOutsideTheScaleAreClamped() {
        val d = Prompts.parseDebrief(answer("{\"points\": 19}", "{\"points\": -2}"), student, false, null, "Test-KI", "")
        assertEquals(15, d.inhaltPoints)
        assertEquals(0, d.darstellungPoints)
    }

    @Test
    fun zeroPointsThatWereReallyGivenAreKept() {
        val d = Prompts.parseDebrief(answer("{\"points\": 0}", "{\"points\": 0}"), student, false, null, "Test-KI", "")
        assertEquals(0, d.totalPoints)
    }
}
