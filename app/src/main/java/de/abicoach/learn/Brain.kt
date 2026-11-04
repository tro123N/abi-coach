package de.abicoach.learn

import de.abicoach.AbiCoachApp
import de.abicoach.R
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionGoal
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.memory.ExamUnits
import de.abicoach.profile.Skill
import de.abicoach.ui.Planner
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Where the student is on the way to the exam; the brain plans differently in each phase. */
enum class Phase(val label: String, val text: String) {
    START("Start", "Zuerst misst die Einstufung deinen Stand – danach plant die App genau für dich."),
    AUFBAU("Aufbau", "Breit üben: alle Aufgabentypen und Themenfelder, Fehler gezielt abbauen."),
    VERTIEFUNG("Vertiefung", "Die größten Hebel gezielt üben und regelmäßig eine ganze Prüfung."),
    GENERALPROBE("Generalprobe", "Prüfungen unter echten Bedingungen, Original-Aufgaben, letzte Fehler abstellen."),
    ENDSPURT("Endspurt", "Kurz und sicher: wiederholen, Selbstvertrauen, keine neuen Baustellen."),
}

enum class ActivityKind(val action: String, val icon: Int) {
    PLACEMENT("einstufung", R.drawable.flag_24dp),
    DRILLS("training", R.drawable.spellcheck_24dp),
    TASK1("aufgabe1", R.drawable.summarize_24dp),
    TASK2("aufgabe2", R.drawable.insights_24dp),
    TASK3("aufgabe3", R.drawable.rate_review_24dp),
    PRESENTATION("vortrag", R.drawable.co_present_24dp),
    CONVERSATION("gespraech", R.drawable.forum_24dp),
    SIMULATION("simulation", R.drawable.workspace_premium_24dp),
    REAL_TASK("echt", R.drawable.workspace_premium_24dp),
    VOCAB("wortschatz", R.drawable.note_stack_24dp),
    PRONUNCIATION("aussprache", R.drawable.graphic_eq_24dp),
    DOSSIER("dossier", R.drawable.library_books_24dp),
    SPRINT("sprint", R.drawable.speed_24dp),
}

/** A possible next activity with its expected benefit (roughly in grade points) and the reason, in German. */
data class PlanStep(
    val kind: ActivityKind,
    val title: String,
    val reason: String,
    val minutes: Int,
    val gain: Double,
    val topic: Int = -1,
) {
    /** Benefit per time, with diminishing returns: long activities train more at once, but not proportionally more. */
    val value: Double get() = gain / minutes.coerceAtLeast(3).toDouble().pow(0.6)

    /** The coach action for this step ("aufgabe2", "dossier:4", "gespraech:7"). */
    val action: String
        get() =
            when {
                kind == ActivityKind.DOSSIER -> "dossier:$topic"
                kind == ActivityKind.CONVERSATION && topic >= 0 -> "gespraech:$topic"
                else -> kind.action
            }
}

data class DayPlan(val phase: Phase, val budget: Int, val steps: List<PlanStep>)

/** Where the student will probably stand on the day of the exam if he goes on like this. */
data class Projection(val days: Long, val points: Double, val low: Double, val high: Double, val perWeek: Double)

/**
 * The learning brain: one model of the student built from every signal the app has – the rating of each criterion
 * with its uncertainty and age, trends, points per task, topic mastery, the error memory, the learner memory,
 * vocabulary and pronunciation, the practice history and the exam date. From it, it estimates for every possible
 * activity how many points it is likely to bring per minute right now, plans the day, sets concrete goals for each
 * session, adapts new exam material and projects the result on the day of the exam.
 */
object LearningBrain {
    private const val DAY = 86_400_000L

    /** How strongly an activity trains each skill (0..1). */
    private val IMPACT: Map<ActivityKind, Map<Skill, Double>> =
        mapOf(
            ActivityKind.TASK1 to mapOf(Skill.ZUSAMMENFASSEN to 1.0, Skill.AUFGABENBEZUG to 0.5, Skill.STRUKTUR to 0.3, Skill.FLUESSIGKEIT to 0.3, Skill.GRAMMATIK to 0.3, Skill.WORTSCHATZ to 0.3),
            ActivityKind.TASK2 to mapOf(Skill.ANALYSE to 1.0, Skill.AUFGABENBEZUG to 0.5, Skill.WORTSCHATZ to 0.4, Skill.STRUKTUR to 0.3, Skill.GRAMMATIK to 0.3, Skill.FLUESSIGKEIT to 0.3),
            ActivityKind.TASK3 to mapOf(Skill.KOMMENTAR to 1.0, Skill.SACHWISSEN to 0.5, Skill.AUFGABENBEZUG to 0.4, Skill.WORTSCHATZ to 0.4, Skill.GRAMMATIK to 0.3, Skill.FLUESSIGKEIT to 0.3),
            ActivityKind.PRESENTATION to mapOf(Skill.STRUKTUR to 1.0, Skill.FLUESSIGKEIT to 0.7, Skill.AUFGABENBEZUG to 0.6, Skill.ZUSAMMENFASSEN to 0.4, Skill.ANALYSE to 0.4, Skill.KOMMENTAR to 0.4, Skill.AUSSPRACHE to 0.3, Skill.GRAMMATIK to 0.3),
            ActivityKind.REAL_TASK to mapOf(Skill.AUFGABENBEZUG to 0.9, Skill.STRUKTUR to 0.8, Skill.ANALYSE to 0.6, Skill.KOMMENTAR to 0.6, Skill.ZUSAMMENFASSEN to 0.5, Skill.FLUESSIGKEIT to 0.5),
            ActivityKind.CONVERSATION to mapOf(Skill.INTERAKTION to 1.0, Skill.SACHWISSEN to 0.8, Skill.FLUESSIGKEIT to 0.7, Skill.KOMMENTAR to 0.4, Skill.WORTSCHATZ to 0.4, Skill.GRAMMATIK to 0.3, Skill.AUSSPRACHE to 0.3),
            ActivityKind.SIMULATION to Skill.entries.associateWith { 0.55 },
            ActivityKind.DRILLS to mapOf(Skill.GRAMMATIK to 0.6, Skill.WORTSCHATZ to 0.3),
            ActivityKind.VOCAB to mapOf(Skill.WORTSCHATZ to 0.7),
            ActivityKind.PRONUNCIATION to mapOf(Skill.AUSSPRACHE to 1.0),
            ActivityKind.DOSSIER to mapOf(Skill.SACHWISSEN to 0.8, Skill.KOMMENTAR to 0.2),
            ActivityKind.SPRINT to mapOf(Skill.FLUESSIGKEIT to 1.0, Skill.INTERAKTION to 0.4),
        )

    private val MAINS = setOf(ActivityKind.TASK1, ActivityKind.TASK2, ActivityKind.TASK3, ActivityKind.PRESENTATION, ActivityKind.CONVERSATION, ActivityKind.SIMULATION, ActivityKind.REAL_TASK, ActivityKind.PLACEMENT)
    private val WARMUPS = setOf(ActivityKind.DRILLS, ActivityKind.VOCAB, ActivityKind.SPRINT, ActivityKind.PRONUNCIATION)

    fun phase(app: AbiCoachApp): Phase {
        if (!app.profiles.profile.value.placementDone && app.store.graded().size < 2) return Phase.START
        val days = Planner.daysUntilExam(app) ?: return Phase.AUFBAU
        return when {
            days <= 4 -> Phase.ENDSPURT
            days <= 21 -> Phase.GENERALPROBE
            days <= 60 -> Phase.VERTIEFUNG
            else -> Phase.AUFBAU
        }
    }

    /** How many points each skill can still bring: gap × weight in the grade × uncertainty × time since it was measured. */
    private fun skillValues(app: AbiCoachApp, now: Long): Map<Skill, Double> {
        val p = app.profiles.profile.value
        val perArea = Skill.entries.groupBy { it.area }.mapValues { it.value.size }
        return Skill.entries.associateWith { s ->
            val st = p.skills[s]
            val score = st?.score ?: 8.0
            val unsure = 1.0 - (st?.let { p.effectiveConfidence(it, now) } ?: 0.0)
            val stale = st?.let { min(0.5, (now - it.updatedAt) / DAY / 40.0) } ?: 0.3
            val falling = if ((p.skillTrend(s) ?: 0.0) < -0.3) 1.2 else 1.0
            (15.0 - score) * s.area.weight / (perArea[s.area] ?: 1) * (1.0 + 0.6 * unsure) * (1.0 + stale) * falling
        }
    }

    /** Average points per task of part 1 over the last reviews (null = never practised). */
    private fun taskPoints(sessions: List<SessionRecord>, number: Int): Double? {
        val points = sessions.mapNotNull { s -> s.debrief?.taskReviews?.firstOrNull { it.number == number }?.points }.take(3)
        return if (points.isEmpty()) null else points.average()
    }

    private fun daysSince(at: Long?, now: Long): Long? = at?.let { (now - it) / DAY }

    private fun SessionRecord.sprintLike(): Boolean = kind == SessionKind.TRAINING && durationSec in 1..300

    /** Every possible activity, scored for right now (best value per minute first). */
    fun candidates(app: AbiCoachApp, now: Long = System.currentTimeMillis()): List<PlanStep> {
        val profile = app.profiles.profile.value
        val sessions = app.store.sessions.value
        val phase = phase(app)
        if (phase == Phase.START) {
            return listOf(PlanStep(ActivityKind.PLACEMENT, "Einstufung machen", "12 Minuten: kurzer Vortrag und Gespräch – danach kennt die App deinen Stand und plant genau für dich.", 12, 10.0))
        }
        val values = skillValues(app, now)

        fun impact(kind: ActivityKind): Double = IMPACT[kind].orEmpty().entries.sumOf { (s, w) -> w * (values[s] ?: 0.0) }

        fun score(s: Skill): String = profile.skills[s]?.score?.let { "%.0f".format(it) } ?: "?"

        fun trendText(s: Skill): String {
            val t = profile.skillTrend(s) ?: 0.0
            return if (t >= 0.35) " (Trend ↑)" else if (t <= -0.35) " (Trend ↓)" else ""
        }

        /** Variety: what was done today counts less, yesterday a little less. */
        fun recency(match: (SessionRecord) -> Boolean): Double {
            val days = daysSince(sessions.firstOrNull(match)?.startedAt, now) ?: return 1.0
            return when (days) {
                0L -> 0.55
                1L -> 0.85
                else -> 1.0
            }
        }
        val out = ArrayList<PlanStep>()

        // Part 1, one task at a time.
        val taskSkill = mapOf(1 to Skill.ZUSAMMENFASSEN, 2 to Skill.ANALYSE, 3 to Skill.KOMMENTAR)
        val taskKind = mapOf(1 to ActivityKind.TASK1, 2 to ActivityKind.TASK2, 3 to ActivityKind.TASK3)
        val taskName = mapOf(1 to "Zusammenfassen", 2 to "Analysieren", 3 to "Kommentieren")
        for (n in 1..3) {
            val kind = taskKind.getValue(n)
            val skill = taskSkill.getValue(n)
            val tp = taskPoints(sessions, n)
            val bonus = if (tp == null) 0.4 else max(0.0, (11.0 - tp) * 0.09)
            val gain = (impact(kind) + bonus) * recency { it.kind == SessionKind.PART1 && it.focusTask == n }
            val reason =
                if (tp == null) {
                    "${skill.label} ${score(skill)} P.${trendText(skill)} – Aufgabe $n hast du einzeln noch nie geübt."
                } else {
                    "Aufgabe $n zuletzt Ø ${"%.0f".format(tp)} P. · ${skill.label} ${score(skill)} P.${trendText(skill)}"
                }
            out += PlanStep(kind, "Aufgabe $n: ${taskName.getValue(n)}", reason, 12, gain)
        }
        // The whole presentation.
        out +=
            PlanStep(
                ActivityKind.PRESENTATION,
                "Ganzer Vortrag",
                "Aufbau & Vortrag ${score(Skill.STRUKTUR)} P., Flüssigkeit ${score(Skill.FLUESSIGKEIT)} P. – alle drei Aufgaben am Stück, mit Zeitgefühl.",
                30,
                impact(ActivityKind.PRESENTATION) * 1.1 * recency { it.kind == SessionKind.PART1 && it.focusTask == 0 },
            )
        // A conversation on the topic field that needs it most.
        val topic = pickTopic(app, now, random = false)
        val topicState = profile.topics[topic]
        val practisedTopic = (app.store.topicCounts()[topic] ?: 0) > 0
        val topicBonus = if (topicState == null) 0.6 else max(0.0, (10.0 - topicState.score) * 0.08)
        out +=
            PlanStep(
                ActivityKind.CONVERSATION,
                "Gespräch: ${Knowledge.topicShort(topic)}",
                if (!practisedTopic) {
                    "Noch nie im Gespräch geübt · Gesprächsführung ${score(Skill.INTERAKTION)} P., Sachwissen ${score(Skill.SACHWISSEN)} P."
                } else {
                    "Dieses Themenfeld sitzt erst mit ${"%.0f".format(topicState?.score ?: 0.0)} P. · Gesprächsführung ${score(Skill.INTERAKTION)} P."
                },
                15,
                (impact(ActivityKind.CONVERSATION) + topicBonus) * recency { it.kind == SessionKind.PART2 || (it.kind == SessionKind.TRAINING && !it.sprintLike()) },
                topic = topic,
            )
        // A whole exam, regularly – more often close to the exam.
        val sinceSim = daysSince(sessions.firstOrNull { it.kind == SessionKind.SIMULATION }?.startedAt, now)
        val interval =
            when (phase) {
                Phase.GENERALPROBE -> 4.0
                Phase.VERTIEFUNG -> 9.0
                Phase.ENDSPURT -> 3.0
                else -> 14.0
            }
        val due = if (sinceSim == null) 1.5 else ((sinceSim - interval) / interval + 0.6).coerceIn(0.0, 1.8)
        out +=
            PlanStep(
                ActivityKind.SIMULATION,
                "Prüfungssimulation",
                if (sinceSim == null) {
                    "Noch nie eine ganze Prüfung gemacht – sie misst alles auf einmal und trainiert das Zeitgefühl."
                } else {
                    "Letzte ganze Prüfung vor $sinceSim Tagen – in der Phase „${phase.label}“ etwa alle ${interval.toInt()} Tage."
                },
                60,
                impact(ActivityKind.SIMULATION) * (0.6 + due) * (if (Planner.daysUntilExam(app) == 1L) 0.3 else 1.0),
            )
        // Original exam tasks.
        val used = ExamUnits.used(app)
        val realOpen = ExamUnits.all(app).count { it.id !in used }
        if (realOpen > 0) {
            val phaseBoost = if (phase == Phase.GENERALPROBE || phase == Phase.ENDSPURT) 1.4 else 1.0
            out +=
                PlanStep(
                    ActivityKind.REAL_TASK,
                    "Echte Prüfungsaufgabe",
                    "$realOpen Original-Aufgaben aus deinem Abi-Material warten – bewertet nach dem Original-Erwartungshorizont.",
                    30,
                    impact(ActivityKind.REAL_TASK) * 1.15 * phaseBoost * recency { it.task?.isReal == true },
                )
        }
        // The error memory: due patterns.
        val duePatterns = app.errorMemory.due(now)
        if (duePatterns.isNotEmpty()) {
            val weight = duePatterns.take(4).sumOf { it.weight(now) }
            out +=
                PlanStep(
                    ActivityKind.DRILLS,
                    "Sprech-Training: ${duePatterns.size} Fehler fällig",
                    "Z. B. " + duePatterns.take(2).joinToString(", ") { "${it.title} (${it.total}×)" } + " – kostet dich jedes Mal Punkte in der Darstellung.",
                    6,
                    impact(ActivityKind.DRILLS) + weight * 0.35,
                )
        }
        // Vocabulary.
        val dueCards = app.vocab.due().size
        if (dueCards >= 3) {
            out +=
                PlanStep(
                    ActivityKind.VOCAB,
                    "Wortschatz: $dueCards Karten",
                    "$dueCards Karten sind fällig – Wortschatz ${score(Skill.WORTSCHATZ)} P.",
                    (dueCards / 6 + 2).coerceIn(3, 10),
                    impact(ActivityKind.VOCAB) + min(1.2, dueCards * 0.05),
                )
        }
        // Pronunciation.
        val weakWords = app.pron.scores.value.filterValues { it < 70 }.keys.take(3)
        val pronScore = profile.skills[Skill.AUSSPRACHE]?.score ?: 9.0
        if (weakWords.isNotEmpty() || pronScore < 9.5) {
            out +=
                PlanStep(
                    ActivityKind.PRONUNCIATION,
                    "Aussprache-Labor",
                    "Aussprache ${score(Skill.AUSSPRACHE)} P." + if (weakWords.isNotEmpty()) " · noch unsicher: " + weakWords.joinToString(", ") else "",
                    5,
                    impact(ActivityKind.PRONUNCIATION) * (if (weakWords.isNotEmpty()) 1.2 else 0.8),
                )
        }
        // A dossier on the weakest topic field (knowledge for task 3 and part 2).
        val dossierTopic = pickTopic(app, now, random = false, needDossier = true)
        if (dossierTopic >= 0) {
            val st = profile.topics[dossierTopic]
            out +=
                PlanStep(
                    ActivityKind.DOSSIER,
                    "Dossier lesen: ${Knowledge.topicShort(dossierTopic)}",
                    if (st == null) {
                        "Wissen auffrischen, bevor du darüber sprichst – Sachwissen ${score(Skill.SACHWISSEN)} P."
                    } else {
                        "Dieses Themenfeld sitzt erst mit ${"%.0f".format(st.score)} P. – Kernwissen, Beispiele und Argumente wiederholen."
                    },
                    6,
                    impact(ActivityKind.DOSSIER) * (if (st == null) 1.1 else 0.9),
                    topic = dossierTopic,
                )
        }
        // A sprint when the answers are short or not fluent.
        val shortAnswers = app.insights.weaknesses(6).any { it.text.contains("kurz", ignoreCase = true) || it.text.contains("knapp", ignoreCase = true) }
        out +=
            PlanStep(
                ActivityKind.SPRINT,
                "Gesprächs-Sprint",
                "Flüssigkeit ${score(Skill.FLUESSIGKEIT)} P." + if (shortAnswers) " · deine Antworten sind oft zu knapp" else " – 3 Minuten am Stück sprechen.",
                5,
                impact(ActivityKind.SPRINT) * (if (shortAnswers) 1.3 else 0.9) * recency { it.sprintLike() },
            )

        // The phase decides what matters most now.
        val tuned =
            out.map { a ->
                val m =
                    when (phase) {
                        Phase.AUFBAU -> if (a.kind in setOf(ActivityKind.TASK1, ActivityKind.TASK2, ActivityKind.TASK3, ActivityKind.CONVERSATION, ActivityKind.DOSSIER)) 1.1 else 1.0
                        Phase.VERTIEFUNG -> if (a.kind == ActivityKind.DRILLS) 1.1 else 1.0
                        Phase.GENERALPROBE ->
                            when (a.kind) {
                                ActivityKind.SIMULATION -> 1.5
                                ActivityKind.REAL_TASK -> 1.3
                                ActivityKind.DRILLS -> 1.2
                                else -> 1.0
                            }
                        Phase.ENDSPURT ->
                            when (a.kind) {
                                ActivityKind.DRILLS, ActivityKind.VOCAB, ActivityKind.DOSSIER -> 1.35
                                ActivityKind.SIMULATION -> 0.8
                                else -> 0.9
                            }
                        Phase.START -> 1.0
                    }
                a.copy(gain = a.gain * m)
            }
        return tuned.sortedByDescending { it.value }
    }

    /** Today's plan: a short warm-up, the main exercise and – if there is time – a short closer. */
    fun plan(app: AbiCoachApp, now: Long = System.currentTimeMillis()): DayPlan {
        val phase = phase(app)
        val budget = (app.settings.dailyMinutes - app.store.minutesToday()).coerceAtLeast(10)
        val all = candidates(app, now)
        if (phase == Phase.START) return DayPlan(phase, budget, all.take(1))
        val steps = ArrayList<PlanStep>()
        all.firstOrNull { it.kind in WARMUPS && it.minutes <= 8 }?.let { steps += it }
        val mains = all.filter { it.kind in MAINS }
        val long = phase == Phase.GENERALPROBE || phase == Phase.ENDSPURT
        val main = mains.firstOrNull { it.minutes <= budget + 10 || long } ?: mains.firstOrNull()
        main?.let { steps += it }
        // A whole exam that is due but too long for today's time: suggest it anyway, for a day with more time.
        val sim = mains.firstOrNull()?.takeIf { it.kind == ActivityKind.SIMULATION && it != main }
        if (sim != null) steps += sim.copy(reason = sim.reason + " Plane dafür eine Stunde ein, z. B. am Wochenende.")
        if (steps.size < 3) {
            val used = steps.sumOf { it.minutes }
            all.firstOrNull { it !in steps && it.kind !in MAINS && it.kind != steps.firstOrNull()?.kind && it.minutes <= max(5, budget - used) }?.let { steps += it }
        }
        return DayPlan(phase, budget, steps.ifEmpty { all.take(3) }.take(3))
    }

    /** The single best next step (shown after a debrief). */
    fun next(app: AbiCoachApp): PlanStep? = candidates(app).firstOrNull()

    /** The computed plan as text for the AI coach, which then words it. */
    fun explain(app: AbiCoachApp): String {
        val plan = plan(app)
        val sb = StringBuilder()
        sb.append("Lern-Gehirn der App (berechnet aus dem ganzen Lernstand): Phase „").append(plan.phase.label).append("“ – ").append(plan.phase.text)
        sb.append(" Zeit heute noch etwa ").append(plan.budget).append(" Min.\n")
        sb.append("Empfohlene Schritte in dieser Reihenfolge (höchster erwarteter Punktgewinn pro Minute):\n")
        plan.steps.forEachIndexed { i, a -> sb.append(i + 1).append(". [").append(a.action).append("] ").append(a.title).append(" – ").append(a.reason).append(" (ca. ").append(a.minutes).append(" Min.)\n") }
        val others = candidates(app).filter { it !in plan.steps }.take(4)
        if (others.isNotEmpty()) sb.append("Weitere sinnvolle Kandidaten: ").append(others.joinToString("; ") { "[${it.action}] ${it.title}" }).append('\n')
        projection(app)?.let { p ->
            sb.append("Hochrechnung auf den Prüfungstag (in ").append(p.days).append(" Tagen): etwa ").append("%.0f".format(p.points))
                .append(" Punkte (Tempo ").append("%+.1f".format(p.perWeek)).append(" P. pro Woche).\n")
        }
        val habits = app.insights.summary(5)
        if (habits.isNotBlank()) sb.append("Was die App über seine Gewohnheiten weiß:\n").append(habits).append('\n')
        return sb.toString().trim()
    }

    /**
     * The topic field that needs practice most: covered in class but weak, unsure or never practised – not the one of
     * the last two sessions. With [random], one of the three most needed is drawn, so the student meets variety.
     */
    fun pickTopic(app: AbiCoachApp, now: Long = System.currentTimeMillis(), random: Boolean = true, needDossier: Boolean = false, exclude: Set<Int> = emptySet()): Int {
        val profile = app.profiles.profile.value
        val covered = app.course.coveredTopics()
        val recent = app.store.sessions.value.take(2).map { it.topicId }.toSet()
        var pool = covered.map { it.first }.ifEmpty { Knowledge.TOPICS.map { it.id } }.filter { it !in exclude }
        if (needDossier) pool = pool.filter { app.dossiers.get(it) != null }
        if (pool.isEmpty()) return if (needDossier) -1 else Knowledge.TOPICS.map { it.id }.filter { it !in exclude }.ifEmpty { listOf(0) }.random()
        val need =
            pool.associateWith { t ->
                val st = profile.topics[t]
                val mastery = st?.score ?: 7.0
                val unsure = 1.0 - (st?.let { profile.effectiveConfidence(it, now) } ?: 0.0)
                val material = covered.firstOrNull { it.first == t }?.second ?: 0
                (15.0 - mastery) * (1.0 + 0.8 * unsure) * (1.0 + ln(1.0 + material) / 6.0) * (if (t in recent) 0.35 else 1.0)
            }
        val ranked = need.entries.sortedByDescending { it.value }
        if (!random) return ranked.first().key
        val top = ranked.take(3)
        val total = top.sumOf { it.value }
        var r = Random.nextDouble() * total
        for (e in top) {
            r -= e.value
            if (r <= 0) return e.key
        }
        return top.first().key
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Session goals
    // ---------------------------------------------------------------------------------------------------------------

    private data class SkillGoal(val text: String, val check: String)

    private val SKILL_GOALS: Map<Skill, SkillGoal> =
        mapOf(
            Skill.ZUSAMMENFASSEN to SkillGoal("Aufgabe 1: nur die Kernaussagen, im simple present, ohne eigene Meinung (2–3 Min.)", "Task 1: only the main points, in the simple present, without his own opinion, in about 2–3 minutes?"),
            Skill.ANALYSE to SkillGoal("Aufgabe 2: jede Beobachtung mit Zitat oder Textverweis belegen und die Wirkung erklären", "Task 2: was every claim supported by a quotation or a reference to the text, and was its effect explained?"),
            Skill.KOMMENTAR to SkillGoal("Aufgabe 3: klare eigene Position, zwei Argumente und ein Beispiel aus dem Unterricht", "Task 3: a clear position of his own with at least two arguments and one example from class?"),
            Skill.AUFGABENBEZUG to SkillGoal("Jede Aufgabe genau so bearbeiten, wie der Operator es verlangt", "Were all tasks answered completely and exactly as their operators demand?"),
            Skill.STRUKTUR to SkillGoal("Mit klaren Überleitungen von Aufgabe zu Aufgabe führen („Moving on to …“)", "Clear signposting between the parts of the presentation?"),
            Skill.FLUESSIGKEIT to SkillGoal("Frei sprechen: kurze Pausen statt „ähm“, nicht ablesen", "Did he speak freely and fluently, with few fillers and without reading out?"),
            Skill.INTERAKTION to SkillGoal("Antworten ausbauen (Begründung + Beispiel) und einmal selbst einen Impuls setzen", "Did he develop his answers with reasons and examples and take the initiative at least once?"),
            Skill.SACHWISSEN to SkillGoal("Mindestens zwei Fakten oder Beispiele aus dem Unterricht einbringen", "Did he use at least two facts or examples from the course?"),
            Skill.WORTSCHATZ to SkillGoal("Präzise Themenwörter und Redemittel statt allgemeiner Wörter", "Did he use precise topic vocabulary and phrases instead of vague general words?"),
            Skill.GRAMMATIK to SkillGoal("Sauber in Zeitformen und Satzbau bleiben", "Were tenses and sentence structure mostly correct?"),
            Skill.AUSSPRACHE to SkillGoal("Deutlich sprechen, auf „th“ und „w“ achten", "Clear pronunciation, especially th and w?"),
        )

    /** Up to three concrete goals for a session: an error pattern, the weakest criterion of this task, a habit. */
    fun goals(app: AbiCoachApp, kind: SessionKind, focusTask: Int): List<SessionGoal> {
        if (kind == SessionKind.PLACEMENT) return emptyList()
        val now = System.currentTimeMillis()
        val profile = app.profiles.profile.value
        val out = ArrayList<SessionGoal>()
        val pattern = app.errorMemory.due(now).firstOrNull() ?: app.errorMemory.top(1, now).firstOrNull()
        if (pattern != null) {
            val ex = pattern.examples.firstOrNull()
            out +=
                SessionGoal(
                    "pattern",
                    "Fehler vermeiden: ${pattern.title}" + (ex?.let { " – also „${it.right}“" } ?: ""),
                    "Did the student avoid the mistake type '${pattern.rule.ifBlank { pattern.categoryLabel }}'" +
                        (ex?.let { " (typical for him: \"${it.wrong}\" instead of \"${it.right}\")" } ?: "") +
                        "? yes = it did not occur (or he corrected himself), partly = once, no = several times. If the structure never came up, judge by related structures.",
                    "pattern",
                    pattern.key,
                )
        }
        val relevant =
            when {
                kind.hasPart1 && focusTask == 1 -> listOf(Skill.ZUSAMMENFASSEN, Skill.FLUESSIGKEIT)
                kind.hasPart1 && focusTask == 2 -> listOf(Skill.ANALYSE, Skill.WORTSCHATZ)
                kind.hasPart1 && focusTask == 3 -> listOf(Skill.KOMMENTAR, Skill.SACHWISSEN)
                kind.hasPart1 -> listOf(Skill.STRUKTUR, Skill.AUFGABENBEZUG, Skill.ANALYSE, Skill.KOMMENTAR, Skill.FLUESSIGKEIT)
                else -> listOf(Skill.INTERAKTION, Skill.SACHWISSEN, Skill.FLUESSIGKEIT, Skill.WORTSCHATZ)
            }
        val weakest = relevant.minByOrNull { profile.skills[it]?.score ?: 9.0 } ?: relevant.first()
        SKILL_GOALS[weakest]?.let { out += SessionGoal("skill", it.text, it.check, "skill", weakest.key) }
        val wantedArea = if (kind.hasPart1 && kind != SessionKind.SIMULATION) setOf("vortrag", "inhalt", "strategie", "sprache") else setOf("gespraech", "inhalt", "strategie", "sprache")
        val habit = app.insights.weaknesses(6).firstOrNull { it.area in wantedArea }
        if (habit != null) {
            out += SessionGoal("habit", "Daran arbeiten: ${habit.text}", "Did the student overcome this habit in this session: \"${habit.text}\"? yes / partly / no.", "habit", habit.text)
        } else {
            val lever = app.store.sessions.value.firstOrNull { it.debrief != null && it.kind.hasPart1 == kind.hasPart1 }?.debrief?.levers?.firstOrNull()
            if (lever != null) out += SessionGoal("lever", lever.take(160), "Did the student put this advice into practice: \"$lever\"? yes / partly / no.", "lever")
        }
        return out.take(3)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Adaptive exam material
    // ---------------------------------------------------------------------------------------------------------------

    data class Adaptation(val textType: Knowledge.TextType, val instruction: String, val summary: String)

    private data class Operator(val key: String, val pattern: String, val label: String)

    private val TASK2 =
        listOf(
            Operator("examine", "Examine how the author presents / perceives …", "examine how …"),
            Operator("means", "Analyse the means the author uses to convince the reader / to create an effect …", "analyse the means"),
            Operator("character", "Analyse how the main character is presented …", "characterise / analyse a character"),
            Operator("attitude", "Explain the author's attitude towards …", "explain the attitude"),
        )
    private val TASK3 =
        listOf(
            Operator("comment", "Comment on …", "comment on"),
            Operator("discuss", "Discuss whether …", "discuss"),
            Operator("assess", "Assess … in the light of what you have learnt in class", "assess"),
        )

    /**
     * Shapes new exam material for this student: the text type he practised less or did worse in, the task-2 and
     * task-3 operators he has met least, and a level that challenges him without overwhelming him. [withText] false
     * keeps the text as it is (a text from his own material) and adapts only the tasks.
     */
    fun adaptMaterial(app: AbiCoachApp, wanted: Knowledge.TextType, withText: Boolean = true): Adaptation {
        val sessions = app.store.sessions.value.filter { it.task != null }.take(10)
        fun literary(type: String): Boolean = listOf("lit", "story", "novel", "fiction", "extract", "excerpt", "play", "drama").any { type.contains(it, ignoreCase = true) }
        val lit = sessions.count { literary(it.task!!.textType) }
        val nonFiction = sessions.size - lit
        // Text type: the one he met less often, or the one he did worse in.
        val litPoints = sessions.filter { literary(it.task!!.textType) }.mapNotNull { it.debrief?.inhaltPoints }.takeIf { it.isNotEmpty() }?.average()
        val nfPoints = sessions.filter { !literary(it.task!!.textType) }.mapNotNull { it.debrief?.inhaltPoints }.takeIf { it.isNotEmpty() }?.average()
        val textType =
            when {
                wanted != Knowledge.TextType.GEMISCHT -> wanted
                sessions.size >= 3 && lit * 3 < nonFiction -> Knowledge.TextType.LITERARISCH
                litPoints != null && nfPoints != null && litPoints + 1.5 < nfPoints -> Knowledge.TextType.LITERARISCH
                litPoints != null && nfPoints != null && nfPoints + 1.5 < litPoints -> Knowledge.TextType.SACHTEXT
                else -> if (Random.nextDouble() < 0.6) Knowledge.TextType.SACHTEXT else Knowledge.TextType.LITERARISCH
            }
        val task2Texts = sessions.mapNotNull { it.task?.tasks?.getOrNull(1)?.instruction?.lowercase() }
        fun used2(key: String): Int =
            task2Texts.count { t ->
                when (key) {
                    "examine" -> t.contains("examine")
                    "means" -> t.contains("means") || t.contains("technique") || t.contains("devices")
                    "character" -> t.contains("characteri") || t.contains("character is presented")
                    else -> t.contains("attitude")
                }
            }
        val options2 = TASK2.filter { textType == Knowledge.TextType.LITERARISCH || it.key != "character" }
        val op2 = options2.minByOrNull { used2(it.key) * 10 + Random.nextInt(5) } ?: options2.first()
        val task3Texts = sessions.mapNotNull { it.task?.tasks?.getOrNull(2)?.instruction?.lowercase() }
        val op3 = TASK3.minByOrNull { o -> task3Texts.count { it.contains(o.key) } * 10 + Random.nextInt(5) } ?: TASK3.first()
        val level = app.profiles.profile.value.prognosis()?.points ?: 9
        val levelLine =
            when {
                level >= 12 -> "a demanding C1 text with an implicit attitude, irony or ambivalence that a top student can uncover"
                level >= 9 -> "a B2/C1 text with a clear perspective and some stylistic devices worth analysing"
                else -> "an accessible B2 text with a clear structure and an obvious perspective"
            }
        val levelLabel =
            when {
                level >= 12 -> "C1, anspruchsvoll"
                level >= 9 -> "B2/C1"
                else -> "B2, gut zugänglich"
            }
        val instruction =
            "ADAPTATION to this student: task 2 must follow the operator pattern \"${op2.pattern}\" (he has practised it least); " +
                "task 3 must follow \"${op3.pattern}\"" + if (withText) "; the text should be $levelLine." else "."
        val summary =
            if (withText) {
                "Text: ${if (textType == Knowledge.TextType.LITERARISCH) "literarisch" else "Sachtext"} · Aufgabe 2: „${op2.label}“ · Aufgabe 3: „${op3.label}“ · Niveau $levelLabel"
            } else {
                "Aufgabe 2: „${op2.label}“ · Aufgabe 3: „${op3.label}“ – die Operatoren, die du am wenigsten geübt hast"
            }
        return Adaptation(textType, instruction, summary)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Projection to the exam day
    // ---------------------------------------------------------------------------------------------------------------

    /** Extends the trend of the last weeks to the exam day (learning slows down, so the trend is damped). */
    fun projection(app: AbiCoachApp, now: Long = System.currentTimeMillis()): Projection? {
        val days = Planner.daysUntilExam(app) ?: return null
        val profile = app.profiles.profile.value
        val current = profile.prognosis() ?: return null
        val history = profile.history.filter { now - it.at <= 45 * DAY }
        if (history.size < 3) return null
        val xs = history.map { (it.at - now) / DAY.toDouble() }
        val ys = history.map { it.total }
        val ws = history.indices.map { 0.5 + it.toDouble() / history.size }
        val sw = ws.sum()
        val mx = xs.zip(ws).sumOf { (x, w) -> x * w } / sw
        val my = ys.zip(ws).sumOf { (y, w) -> y * w } / sw
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += ws[i] * (xs[i] - mx) * (ys[i] - my)
            den += ws[i] * (xs[i] - mx) * (xs[i] - mx)
        }
        // Points that all lie on one day say nothing about a trend.
        val slope = if (den < 0.5) 0.0 else num / den
        val perWeek = (slope * 7).coerceIn(-1.5, 1.5)
        val gain = (slope * days * 0.6).coerceIn(-3.0, 4.0)
        val points = (current.total + gain).coerceIn(0.0, 15.0)
        val spread = (current.high - current.low) / 2.0 + min(2.0, days / 60.0)
        return Projection(days, points, (points - spread).coerceAtLeast(0.0), (points + spread).coerceAtMost(15.0), perWeek)
    }
}
