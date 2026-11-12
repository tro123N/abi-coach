package de.abicoach.ui

import de.abicoach.AbiCoachApp
import de.abicoach.R
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.learn.ActivityKind
import de.abicoach.learn.LearningBrain
import de.abicoach.learn.PlanStep
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a recommendation leads. */
sealed interface Target {
    data object Keys : Target

    data object Course : Target

    data object Placement : Target

    data object ExamDate : Target

    data object Vocab : Target

    /** The speaking training of the student's own error patterns. */
    data object Drills : Target

    data object Pronunciation : Target

    /** The Fehler-Werkstatt for one error pattern. */
    data class Workshop(val patternKey: String) : Target

    /** A level of the Abi-Weg. */
    data class Level(val id: String) : Target

    data class Dossier(val topic: Int) : Target

    data class Setup(val setup: SessionSetup) : Target
}

enum class Tint { GREEN, ACCENT, YELLOW, RED, NEUTRAL }

data class Recommendation(val title: String, val subtitle: String, val icon: Int, val tint: Tint, val target: Target)

data class Milestone(val title: String, val done: Boolean, val detail: String? = null)

/** Everything a practice session needs to start. */
data class SessionSetup(
    val kind: SessionKind,
    /** Topic field id, [Knowledge.RANDOM] or [Knowledge.FROM_COURSE]. */
    val topicChoice: Int = Knowledge.FROM_COURSE,
    val source: Knowledge.Source = Knowledge.Source.COURSE,
    val textType: Knowledge.TextType = Knowledge.TextType.GEMISCHT,
    val prepMinutes: Int = 30,
    val feedback: FeedbackMode = FeedbackMode.PRUEFUNG,
    /** 0 = all three tasks, 1–3 = only this task of part 1. */
    val focusTask: Int = 0,
    val focusCategories: List<String> = emptyList(),
    /** A specific text from the material: document id and its start page. */
    val fixedDocId: String? = null,
    val fixedPage: Int = 0,
    /** The Abi-Weg level this session belongs to. */
    val levelId: String? = null,
    /** Extra requirement of the level for the material or the examiner (English). */
    val instruction: String = "",
    /** Length of a conversation (part 2 or training) in minutes. */
    val talkMinutes: Int = if (kind == SessionKind.PART2) 10 else 8,
    /** A specific original exam task from the uploaded Abitur material (with source REAL). */
    val unitId: String = "",
    /**
     * Further topic fields for the conversation, examined together (part 2 of the Abitur links several and must not
     * stay with one term). Empty = the app picks the ones the student needs most.
     */
    val extraTopics: List<Int> = emptyList(),
    /** A competence this session trains on its own (Skill key); empty = the whole exam. */
    val focusSkill: String = "",
    /** How the examiner leads the conversation; null = the default from the settings. */
    val talkStyle: de.abicoach.data.TalkStyle? = null,
) {
    val isSprint: Boolean get() = instruction == SPRINT_INSTRUCTION

    companion object {
        const val SPRINT_INSTRUCTION =
            "This is a 3-minute speaking sprint to build fluency. Ask one demanding open question at a time and keep your own turns to one short sentence. " +
                "Push the student to answer at length with reasons and examples (\"Why?\", \"Can you give an example?\", \"What would critics say?\"), and move quickly to a new aspect when an answer is complete. " +
                "Do not summarise or give feedback at the end."

        /** A short, intense conversation: 3 minutes of speaking as much as possible. */
        fun sprint(defaultFeedback: FeedbackMode): SessionSetup =
            SessionSetup(SessionKind.TRAINING, feedback = defaultFeedback, instruction = SPRINT_INSTRUCTION, talkMinutes = 3)

        fun defaults(kind: SessionKind, focusTask: Int = 0, defaultFeedback: FeedbackMode): SessionSetup =
            when (kind) {
                SessionKind.SIMULATION -> SessionSetup(kind, prepMinutes = 30, feedback = FeedbackMode.PRUEFUNG)
                SessionKind.PLACEMENT -> SessionSetup(kind, prepMinutes = 8, feedback = FeedbackMode.PRUEFUNG)
                SessionKind.PART1 -> SessionSetup(kind, prepMinutes = if (focusTask == 0) 20 else 8, feedback = defaultFeedback, focusTask = focusTask)
                SessionKind.PART2 -> SessionSetup(kind, feedback = defaultFeedback)
                SessionKind.TRAINING -> SessionSetup(kind, feedback = defaultFeedback)
            }
    }
}

/** Decides what the student should do next, from the profile, the sessions, the material and the exam date. */
object Planner {
    fun daysUntilExam(app: AbiCoachApp): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(app.settings.examDate.trim())) }.getOrNull()?.takeIf { it >= 0 }

    /** What has to be set up before a plan makes sense: the key, the material, the exam date. */
    fun setup(app: AbiCoachApp): List<Recommendation> {
        val out = ArrayList<Recommendation>()
        if (!app.ai.ready) {
            out += Recommendation("KI-Schlüssel eintragen", "Ohne Schlüssel deiner KI (Google, OpenAI oder Anthropic) kann die App nicht zuhören.", R.drawable.key_24dp, Tint.RED, Target.Keys)
        }
        if (app.memory.docs.value.isEmpty()) {
            out += Recommendation("Kursmaterial hochladen", "Schulbuch, Klausuren, Arbeitsblätter – damit alles zu deinem Kurs passt.", R.drawable.cloud_upload_24dp, Tint.YELLOW, Target.Course)
        }
        if (daysUntilExam(app) == null && LearningBrain.phase(app) != de.abicoach.learn.Phase.START) {
            out += Recommendation("Prüfungstermin eintragen", "Dann plant die App bis zum Tag der Prüfung – mit Generalprobe und Endspurt.", R.drawable.event_24dp, Tint.NEUTRAL, Target.ExamDate)
        }
        return out
    }

    /** A step of today's plan as the place it leads to. */
    fun target(app: AbiCoachApp, step: de.abicoach.learn.DayStep): Target {
        val kind = runCatching { ActivityKind.valueOf(step.kind) }.getOrDefault(ActivityKind.CONVERSATION)
        return recommendation(app, PlanStep(kind, step.title, step.reason, step.minutes, 0.0, step.topic, step.patternKey)).target
    }

    fun today(app: AbiCoachApp): List<Recommendation> {
        val out = ArrayList<Recommendation>()
        val profile = app.profiles.profile.value
        val sessions = app.store.sessions.value
        val docs = app.memory.docs.value
        val feedback = app.settings.defaultFeedback

        if (!app.ai.ready) {
            out += Recommendation("KI-Schlüssel eintragen", "Ohne Schlüssel deiner KI (Google, OpenAI oder Anthropic) kann die App nicht zuhören.", R.drawable.key_24dp, Tint.RED, Target.Keys)
        }
        if (docs.isEmpty()) {
            out += Recommendation("Kursmaterial hochladen", "Schulbuch, Klausuren, Arbeitsblätter – damit alles zu deinem Kurs passt.", R.drawable.cloud_upload_24dp, Tint.YELLOW, Target.Course)
        }
        if (LearningBrain.phase(app) == de.abicoach.learn.Phase.START) {
            out += Recommendation("Einstufung machen", "12 Minuten: kurzer Vortrag und Gespräch – danach kennt die App deinen Stand.", R.drawable.flag_24dp, Tint.GREEN, Target.Placement)
            return out.take(3)
        }
        // The learning brain: what brings the most points per minute today.
        LearningBrain.plan(app).steps.forEach { out += recommendation(app, it, feedback) }
        if (daysUntilExam(app) == null) {
            out += Recommendation("Prüfungstermin eintragen", "Dann plant die App bis zum Tag der Prüfung – mit Generalprobe und Endspurt.", R.drawable.event_24dp, Tint.NEUTRAL, Target.ExamDate)
        }
        if (sessions.isEmpty() && profile.skills.isEmpty()) return out.take(3)
        return out.distinctBy { it.title }.take(4)
    }

    /** One step of the learning brain as a button. */
    fun recommendation(app: AbiCoachApp, step: PlanStep, defaultFeedback: FeedbackMode = app.settings.defaultFeedback): Recommendation {
        // Shortly before the exam, whole presentations and conversations are practised under exam conditions.
        val phase = LearningBrain.phase(app)
        val examConditions =
            (phase == de.abicoach.learn.Phase.GENERALPROBE || phase == de.abicoach.learn.Phase.ENDSPURT) &&
                step.kind in setOf(ActivityKind.PRESENTATION, ActivityKind.CONVERSATION, ActivityKind.REAL_TASK)
        val feedback = if (examConditions) FeedbackMode.PRUEFUNG else defaultFeedback
        val target =
            when (step.kind) {
                ActivityKind.PLACEMENT -> Target.Placement
                ActivityKind.DRILLS -> Target.Drills
                ActivityKind.TASK1 -> Target.Setup(SessionSetup.defaults(SessionKind.PART1, 1, feedback))
                ActivityKind.TASK2 -> Target.Setup(SessionSetup.defaults(SessionKind.PART1, 2, feedback))
                ActivityKind.TASK3 -> Target.Setup(SessionSetup.defaults(SessionKind.PART1, 3, feedback))
                ActivityKind.PRESENTATION -> Target.Setup(SessionSetup.defaults(SessionKind.PART1, 0, feedback))
                ActivityKind.CONVERSATION ->
                    Target.Setup(SessionSetup.defaults(SessionKind.PART2, 0, feedback).copy(topicChoice = if (step.topic in 0..13) step.topic else Knowledge.FROM_COURSE))
                ActivityKind.SIMULATION -> Target.Setup(SessionSetup.defaults(SessionKind.SIMULATION, 0, feedback))
                ActivityKind.REAL_TASK -> Target.Setup(SessionSetup.defaults(SessionKind.PART1, 0, feedback).copy(source = Knowledge.Source.REAL, prepMinutes = 20))
                ActivityKind.VOCAB -> Target.Vocab
                ActivityKind.PRONUNCIATION -> Target.Pronunciation
                ActivityKind.DOSSIER -> Target.Dossier(step.topic)
                ActivityKind.SPRINT -> Target.Setup(SessionSetup.sprint(feedback))
                ActivityKind.WORKSHOP -> Target.Workshop(step.ref)
                ActivityKind.REFRESH -> Target.Level(step.ref)
            }
        val tint =
            when (step.kind) {
                ActivityKind.SIMULATION, ActivityKind.REAL_TASK, ActivityKind.PLACEMENT -> Tint.GREEN
                ActivityKind.DRILLS, ActivityKind.VOCAB, ActivityKind.WORKSHOP, ActivityKind.REFRESH -> Tint.YELLOW
                else -> Tint.ACCENT
            }
        val conditions = if (examConditions) " · unter Prüfungsbedingungen" else ""
        return Recommendation(step.title, step.reason + " · ca. ${step.minutes} Min.$conditions", step.kind.icon, tint, target)
    }

    fun milestones(app: AbiCoachApp): List<Milestone> {
        val profile = app.profiles.profile.value
        val graded = app.store.graded()
        val simulations = graded.filter { it.kind == SessionKind.SIMULATION }
        val best = simulations.maxOfOrNull { it.debrief?.totalPoints ?: 0 } ?: 0
        val docs = app.memory.docs.value
        val prognosis = profile.prognosis()?.points ?: 0
        return listOf(
            Milestone("KI startklar", app.ai.ready),
            Milestone("Kursmaterial im Gedächtnis", docs.isNotEmpty(), if (docs.isNotEmpty()) "${docs.size} Dokumente" else null),
            Milestone("Einstufung gemacht", profile.placementDone),
            Milestone("Erste Prüfungssimulation", simulations.isNotEmpty()),
            Milestone("10 Punkte in einer Simulation", best >= 10, if (simulations.isNotEmpty()) "bisher bestes Ergebnis: $best P." else null),
            Milestone("13 Punkte in einer Simulation", best >= 13),
            Milestone("Prognose 13 Punkte oder mehr", prognosis >= 13),
        )
    }

    /** The covered topic field that was practised least. */
    fun leastPractisedTopic(app: AbiCoachApp): Int {
        val practised = app.store.topicCounts()
        val covered = app.course.coveredTopics().map { it.first }.ifEmpty { Knowledge.TOPICS.map { it.id } }
        return covered.minByOrNull { practised[it] ?: 0 } ?: Knowledge.TOPICS.random().id
    }

    fun topErrorCategories(app: AbiCoachApp, limit: Int): List<String> {
        val counts = HashMap<String, Int>()
        app.store.categoryCounts().forEach { (k, v) -> counts[k] = (counts[k] ?: 0) + v }
        app.profiles.profile.value.klausurErrors.forEach { (k, v) -> counts[k] = (counts[k] ?: 0) + v }
        return counts.filterKeys { it != "CONTENT" && it != "OTHER" }.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun gradeLabel(points: Int): String =
        when (points) {
            in 13..15 -> "sehr gut"
            in 10..12 -> "gut"
            in 7..9 -> "befriedigend"
            in 5..6 -> "ausreichend"
            4 -> "schwach ausreichend"
            in 1..3 -> "mangelhaft"
            else -> "ungenügend"
        }
}
