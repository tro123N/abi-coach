package de.abicoach.ui

import de.abicoach.AbiCoachApp
import de.abicoach.R
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.profile.Skill
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Where a recommendation leads. */
sealed interface Target {
    data object Keys : Target

    data object Course : Target

    data object Placement : Target

    data object ExamDate : Target

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
) {
    companion object {
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

    fun today(app: AbiCoachApp): List<Recommendation> {
        val out = ArrayList<Recommendation>()
        val profile = app.profiles.profile.value
        val sessions = app.store.sessions.value
        val docs = app.memory.docs.value
        val feedback = app.settings.defaultFeedback

        if (!app.ai.hasGemini) {
            out += Recommendation("KI-Schlüssel eintragen", "Ohne Gemini-Schlüssel kann die App nicht zuhören.", R.drawable.key_24dp, Tint.RED, Target.Keys)
        }
        if (docs.isEmpty()) {
            out += Recommendation("Kursmaterial hochladen", "Schulbuch, Klausuren, Arbeitsblätter – damit alles zu deinem Kurs passt.", R.drawable.cloud_upload_24dp, Tint.YELLOW, Target.Course)
        }
        if (!profile.placementDone) {
            out += Recommendation("Einstufung machen", "12 Minuten: kurzer Vortrag und Gespräch – danach kennt die App deinen Stand.", R.drawable.flag_24dp, Tint.GREEN, Target.Placement)
            return out.take(3)
        }

        // The biggest lever of the profile, turned into the matching exercise.
        profile.levers(2).forEach { skill ->
            val score = profile.skills[skill]?.score?.roundToInt()
            val sub = "Dein größter Hebel: ${skill.label}" + (score?.let { " ($it P.)" } ?: "")
            out +=
                when (skill) {
                    Skill.ZUSAMMENFASSEN -> Recommendation("Aufgabe 1 üben: Zusammenfassen", sub, R.drawable.summarize_24dp, Tint.ACCENT, Target.Setup(SessionSetup.defaults(SessionKind.PART1, 1, feedback)))
                    Skill.ANALYSE -> Recommendation("Aufgabe 2 üben: Analysieren", sub, R.drawable.insights_24dp, Tint.ACCENT, Target.Setup(SessionSetup.defaults(SessionKind.PART1, 2, feedback)))
                    Skill.KOMMENTAR -> Recommendation("Aufgabe 3 üben: Kommentieren", sub, R.drawable.rate_review_24dp, Tint.ACCENT, Target.Setup(SessionSetup.defaults(SessionKind.PART1, 3, feedback)))
                    Skill.AUFGABENBEZUG, Skill.STRUKTUR -> Recommendation("Ganzen Vortrag üben", sub, R.drawable.co_present_24dp, Tint.ACCENT, Target.Setup(SessionSetup.defaults(SessionKind.PART1, 0, feedback)))
                    Skill.SACHWISSEN -> {
                        val topic = leastPractisedTopic(app)
                        Recommendation(
                            "Gespräch: ${Knowledge.topicShort(topic)}",
                            sub,
                            R.drawable.forum_24dp,
                            Tint.ACCENT,
                            Target.Setup(SessionSetup.defaults(SessionKind.PART2, 0, feedback).copy(topicChoice = topic)),
                        )
                    }
                    Skill.INTERAKTION, Skill.FLUESSIGKEIT ->
                        Recommendation("Prüfungsgespräch üben", sub, R.drawable.forum_24dp, Tint.ACCENT, Target.Setup(SessionSetup.defaults(SessionKind.PART2, 0, feedback)))
                    Skill.WORTSCHATZ, Skill.GRAMMATIK -> {
                        val categories = topErrorCategories(app, 2)
                        Recommendation(
                            "Gesprächstraining mit Coach",
                            sub + if (categories.isNotEmpty()) " · Fokus: " + categories.joinToString(", ") { Knowledge.categoryLabel(it) } else "",
                            R.drawable.spellcheck_24dp,
                            Tint.ACCENT,
                            Target.Setup(SessionSetup.defaults(SessionKind.TRAINING, 0, FeedbackMode.COACH).copy(focusCategories = categories)),
                        )
                    }
                    Skill.AUSSPRACHE -> Recommendation("Gesprächstraining (Aussprache)", sub, R.drawable.graphic_eq_24dp, Tint.ACCENT, Target.Setup(SessionSetup.defaults(SessionKind.TRAINING, 0, feedback)))
                }
        }

        val lastSimulation = sessions.filter { it.kind == SessionKind.SIMULATION && it.debrief != null }.maxOfOrNull { it.startedAt }
        val daysSince = lastSimulation?.let { (System.currentTimeMillis() - it) / 86_400_000L }
        if (daysSince == null || daysSince >= 7) {
            out.add(
                0,
                Recommendation(
                    "Prüfungssimulation",
                    if (daysSince == null) "Einmal komplett wie im Abi: 30 Min. Vorbereitung, Vortrag, Gespräch." else "Die letzte ist $daysSince Tage her.",
                    R.drawable.workspace_premium_24dp,
                    Tint.GREEN,
                    Target.Setup(SessionSetup.defaults(SessionKind.SIMULATION, 0, feedback)),
                ),
            )
        }
        if (daysUntilExam(app) == null) {
            out += Recommendation("Prüfungstermin eintragen", "Dann plant die App bis zum Tag der Prüfung.", R.drawable.event_24dp, Tint.NEUTRAL, Target.ExamDate)
        }
        return out.distinctBy { it.title }.take(3)
    }

    fun milestones(app: AbiCoachApp): List<Milestone> {
        val profile = app.profiles.profile.value
        val graded = app.store.graded()
        val simulations = graded.filter { it.kind == SessionKind.SIMULATION }
        val best = simulations.maxOfOrNull { it.debrief?.totalPoints ?: 0 } ?: 0
        val docs = app.memory.docs.value
        val prognosis = profile.prognosis()?.points ?: 0
        return listOf(
            Milestone("KI startklar", app.ai.hasGemini),
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
