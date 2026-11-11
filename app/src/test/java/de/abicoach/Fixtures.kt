package de.abicoach

import de.abicoach.data.Debrief
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.LiveError
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.data.TaskItem
import de.abicoach.data.TaskReview
import de.abicoach.data.Utterance
import de.abicoach.profile.Skill
import java.io.File
import java.nio.file.Files

/** Small builders for the tests: only what a test cares about has to be given. */
object Fixtures {
    fun tempDir(): File = Files.createTempDirectory("abicoach-test").toFile()

    fun debrief(
        inhalt: Int = 9,
        darstellung: Int = 9,
        skills: Map<Skill, Int> = emptyMap(),
        taskReviews: List<TaskReview> = emptyList(),
        errors: List<LiveError> = emptyList(),
    ) = Debrief(
        inhaltPoints = inhalt,
        inhaltReason = "",
        darstellungPoints = darstellung,
        darstellungReason = "",
        totalPoints = Math.round(0.4 * inhalt + 0.6 * darstellung).toInt(),
        strengths = emptyList(),
        levers = emptyList(),
        errors = errors,
        upgrades = emptyList(),
        pronunciation = emptyList(),
        contentFeedback = emptyList(),
        phrases = emptyList(),
        droppedUnverified = 0,
        audioAnalysed = false,
        skills = skills,
        taskReviews = taskReviews,
    )

    fun task(): ExamTask =
        ExamTask(
            topicId = 1,
            title = "A speech",
            textType = "speech",
            text = "Some text.",
            sourceNote = "",
            tasks = listOf(TaskItem("summarize", "I", "Summarize."), TaskItem("analyse", "II", "Analyse."), TaskItem("comment", "III", "Comment.")),
            part2 = emptyList(),
        )

    /** A session in which the student said [words] words. */
    fun record(
        id: String = "s1",
        kind: SessionKind = SessionKind.PART1,
        words: Int = 400,
        part1Sec: Int = 540,
        startedAt: Long = 1_000_000L,
        debrief: Debrief? = null,
        withTask: Boolean = true,
    ) = SessionRecord(
        id = id,
        startedAt = startedAt,
        kind = kind,
        feedback = FeedbackMode.PRUEFUNG,
        topicId = 1,
        task = if (withTask) task() else null,
        notes = "",
        utterances = listOf(Utterance(Speaker.STUDENT, (1..words).joinToString(" ") { "word" }, 1, 0L)),
        liveErrors = emptyList(),
        part1Sec = part1Sec,
        durationSec = part1Sec,
        debrief = debrief,
    )

    fun error(quote: String, correction: String, rule: String, category: String = "GRAMMAR") =
        LiveError(quote = quote, correction = correction, explanation = "", category = category, severity = "major", rule = rule)
}
