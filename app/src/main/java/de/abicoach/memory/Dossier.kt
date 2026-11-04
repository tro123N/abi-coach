package de.abicoach.memory

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.abicoach.AbiCoachApp
import de.abicoach.ai.AiException
import de.abicoach.ai.arr
import de.abicoach.ai.enumOf
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Knowledge
import de.abicoach.data.objects
import de.abicoach.data.strings
import de.abicoach.data.toJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class DossierItem(val text: String, val source: String) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("source", source)

    companion object {
        fun fromJson(o: JSONObject) = DossierItem(o.optString("text"), o.optString("source"))
    }
}

/** An argument on a central debate of the topic field: pro, contra or a nuance, with a fact or example. */
data class DossierArgument(val claim: String, val side: String, val support: String) {
    fun toJson(): JSONObject = JSONObject().put("claim", claim).put("side", side).put("support", support)

    companion object {
        fun fromJson(o: JSONObject) = DossierArgument(o.optString("claim"), o.optString("side"), o.optString("support"))
    }
}

/** A bridge to another topic field – what the examiner uses for the topic change in part 2. */
data class DossierLink(val topic: Int, val bridge: String) {
    fun toJson(): JSONObject = JSONObject().put("topic", topic).put("bridge", bridge)

    companion object {
        fun fromJson(o: JSONObject) = DossierLink(o.optInt("topic", -1), o.optString("bridge"))
    }
}

/**
 * Everything the student's material says about one topic field, condensed into a learning dossier: the app's
 * long-term memory on that topic. Examiner, exam texts, grading, coach and speaking training draw on it.
 */
data class TopicDossier(
    val topic: Int,
    val madeAt: Long,
    /** Changes when new material on the topic arrives; then the dossier is renewed. */
    val signature: String,
    val by: String,
    val overview: String,
    val keyPoints: List<String>,
    val examples: List<DossierItem>,
    val quotes: List<DossierItem>,
    val terms: List<Vocab>,
    val arguments: List<DossierArgument>,
    val links: List<DossierLink>,
    val questions: List<String>,
    val ownIdeas: List<String>,
) {
    /** A compact version for prompts. */
    fun compact(maxChars: Int = 3_000): String {
        val sb = StringBuilder("Learning dossier of the student's course on \"${Knowledge.topic(topic)?.english ?: "this topic"}\":\n")
        if (overview.isNotBlank()) sb.append(overview.trim()).append('\n')
        if (keyPoints.isNotEmpty()) sb.append("Key points: ").append(keyPoints.joinToString(" | ")).append('\n')
        if (examples.isNotEmpty()) sb.append("Examples from class: ").append(examples.joinToString(" | ") { "${it.text} [${it.source}]" }).append('\n')
        if (arguments.isNotEmpty()) sb.append("Arguments: ").append(arguments.joinToString(" | ") { "(${it.side}) ${it.claim} – ${it.support}" }).append('\n')
        if (links.isNotEmpty()) sb.append("Links to other topic fields: ").append(links.joinToString(" | ") { "${Knowledge.topic(it.topic)?.english ?: it.topic}: ${it.bridge}" }).append('\n')
        if (terms.isNotEmpty()) sb.append("Topic words: ").append(terms.take(15).joinToString(", ") { it.en }).append('\n')
        if (ownIdeas.isNotEmpty()) sb.append("Good ideas the student used before: ").append(ownIdeas.joinToString(" | ")).append('\n')
        return sb.toString().take(maxChars)
    }

    fun toJson(): JSONObject =
        JSONObject().put("topic", topic).put("madeAt", madeAt).put("signature", signature).put("by", by).put("overview", overview)
            .put("keyPoints", keyPoints.toJsonArray()).put("examples", examples.toJsonArray { it.toJson() })
            .put("quotes", quotes.toJsonArray { it.toJson() })
            .put("terms", terms.toJsonArray { JSONObject().put("en", it.en).put("de", it.de).put("topic", it.topic) })
            .put("arguments", arguments.toJsonArray { it.toJson() }).put("links", links.toJsonArray { it.toJson() })
            .put("questions", questions.toJsonArray()).put("ownIdeas", ownIdeas.toJsonArray())

    companion object {
        fun fromJson(o: JSONObject) =
            TopicDossier(
                topic = o.optInt("topic", -1),
                madeAt = o.optLong("madeAt"),
                signature = o.optString("signature"),
                by = o.optString("by"),
                overview = o.optString("overview"),
                keyPoints = o.optJSONArray("keyPoints").strings(),
                examples = o.optJSONArray("examples").objects().map { DossierItem.fromJson(it) },
                quotes = o.optJSONArray("quotes").objects().map { DossierItem.fromJson(it) },
                terms = o.optJSONArray("terms").objects().map { Vocab(it.optString("en"), it.optString("de"), it.optInt("topic", -1)) },
                arguments = o.optJSONArray("arguments").objects().map { DossierArgument.fromJson(it) },
                links = o.optJSONArray("links").objects().map { DossierLink.fromJson(it) },
                questions = o.optJSONArray("questions").strings(),
                ownIdeas = o.optJSONArray("ownIdeas").strings(),
            )
    }
}

/** What the dossier job is doing (shown on the course page). */
data class DossierStatus(val working: Int = -1, val text: String = "", val error: String? = null)

/** The dossiers on disk, one JSON file per topic field. */
class DossierStore(context: Context) {
    private val dir = File(context.filesDir, "dossiers").apply { mkdirs() }
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<Int, TopicDossier>> = _all.asStateFlow()
    private val _status = MutableStateFlow(DossierStatus())
    val status: StateFlow<DossierStatus> = _status.asStateFlow()

    private fun load(): Map<Int, TopicDossier> =
        dir.listFiles()?.mapNotNull { f -> runCatching { TopicDossier.fromJson(JSONObject(f.readText())) }.getOrNull() }
            ?.filter { it.topic in 0..13 }?.associateBy { it.topic }.orEmpty()

    fun get(topic: Int): TopicDossier? = _all.value[topic]

    @Synchronized
    fun save(d: TopicDossier) {
        runCatching {
            val f = File(dir, "${d.topic}.json")
            val tmp = File(dir, "${d.topic}.json.tmp")
            tmp.writeText(d.toJson().toString())
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        }
        _all.value = _all.value + (d.topic to d)
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        _all.value = emptyMap()
    }

    fun setStatus(s: DossierStatus) {
        _status.value = s
    }
}

/** Builds and renews the dossiers from the whole memory. */
object DossierMaker {
    private val SYSTEM =
        """
Du verdichtest das gesamte Kursmaterial eines Schülers zu EINEM Themenfeld zu einem Lern-Dossier für die mündliche Abiturprüfung Englisch (NRW, Grundkurs, Ziel 15 Punkte). Das Dossier ist das Langzeitgedächtnis der App: Prüfer, Prüfungstexte, Bewertung und Coach greifen darauf zu, und der Schüler lernt damit für Teil 2 (Prüfungsgespräch über größere Zusammenhänge) und für Aufgabe 3 (Stellungnahme).
Nutze die Inhalte aus dem Material und gib die Fundstelle an (Form: „Titel, S. n“). Allgemeinwissen nur, wo es zum Verständnis nötig ist, mit source = "Allgemeinwissen".
Felder (Inhalte auf Englisch, außer wo anders angegeben):
- overview: 3–5 sentences: what this topic field is about in this course (texts, perspectives, central questions).
- key_points: 8–15 core facts, concepts and positions the student must know, as short sentences.
- examples: 4–10 concrete examples, case studies, works, characters or events, each with source.
- quotes: up to 6 short quotable lines (at most 25 words) from the material, each with source.
- terms: 10–25 topic words and phrases with German meaning (de).
- arguments: 4–8 arguments on the central debates; side = "pro", "contra" or "nuance"; support = a fact or example that backs it.
- links: 2–5 connections to OTHER topic fields (topic = number from the list, bridge = one sentence) – the examiner uses them for the topic change in part 2.
- questions: 5–8 questions an examiner could ask about this topic field in part 2 (open, demanding, AFB II/III).
- own_ideas: up to 5 good ideas or examples the student himself used in his practice sessions (given below) that are worth keeping; empty if none are given.
        """.trimIndent()

    private val schema =
        obj(
            "overview" to str(),
            "key_points" to arr(str()),
            "examples" to arr(obj("text" to str(), "source" to str())),
            "quotes" to arr(obj("text" to str(), "source" to str())),
            "terms" to arr(obj("en" to str(), "de" to str())),
            "arguments" to arr(obj("claim" to str(), "side" to enumOf(listOf("pro", "contra", "nuance")), "support" to str())),
            "links" to arr(obj("topic" to int(), "bridge" to str())),
            "questions" to arr(str()),
            "own_ideas" to arr(str()),
        )

    /** Changes whenever the material on [topic] changes (new pages, new digests). */
    fun signature(app: AbiCoachApp, topic: Int): String {
        val chunks = app.memory.stats().chunksPerTopic.getOrElse(topic) { 0 }
        val facts = app.memory.allDigests().sumOf { (_, d) -> d.facts.count { it.topic == topic } }
        return "$chunks|$facts"
    }

    fun isStale(app: AbiCoachApp, topic: Int): Boolean {
        val d = app.dossiers.get(topic) ?: return true
        return d.signature != signature(app, topic)
    }

    suspend fun make(app: AbiCoachApp, topic: Int): TopicDossier {
        val field = Knowledge.topic(topic) ?: throw IllegalArgumentException("Unbekanntes Themenfeld")
        val facts = app.memory.allDigests().flatMap { (doc, d) -> d.facts.filter { it.topic == topic }.map { "${it.fact} [${doc.title}]" } }.distinct()
        val hits = app.course.retriever.search(field.english, setOf(topic), k = 16)
        val sb = StringBuilder()
        sb.append("Themenfeld ").append(topic).append(": ").append(field.english).append(" (").append(field.german).append(")\n\n")
        sb.append(app.course.overview(1_500)).append('\n')
        if (facts.isNotEmpty()) {
            sb.append("Gespeicherte Fakten aus dem Material:\n")
            facts.take(60).forEach { sb.append("- ").append(it).append('\n') }
        }
        if (hits.isNotEmpty()) {
            sb.append("\nPassagen aus dem Material:\n")
            var used = 0
            for (h in hits) {
                val text = h.chunk.text.replace('\n', ' ').take(1_100)
                if (used + text.length > 16_000) break
                used += text.length
                sb.append("[").append(h.citation).append("] ").append(text).append('\n')
            }
        }
        val own =
            app.store.sessions.value.filter { it.topicId == topic && it.debrief != null }.take(6)
                .flatMap { s -> s.debrief!!.strengths.map { "Stärke: $it" } + s.debrief.upgrades.take(2).map { "Satz: ${it.upgraded}" } }
        if (own.isNotEmpty()) {
            sb.append("\nAus seinen Übungssitzungen zu diesem Themenfeld:\n")
            own.take(12).forEach { sb.append("- ").append(it).append('\n') }
        }
        sb.append("\nThemenfelder:\n").append(Knowledge.TOPICS.joinToString("\n") { "${it.id}: ${it.english}" })
        val answer = app.ai.brainJson(SYSTEM, sb.toString(), schema, thinking = true)
        val o = answer.json
        val dossier =
            TopicDossier(
                topic = topic,
                madeAt = System.currentTimeMillis(),
                signature = signature(app, topic),
                by = answer.by,
                overview = o.optString("overview").trim(),
                keyPoints = o.optJSONArray("key_points").strings().take(15),
                examples = o.optJSONArray("examples").objects().map { DossierItem.fromJson(it) }.filter { it.text.isNotBlank() }.take(10),
                quotes = o.optJSONArray("quotes").objects().map { DossierItem.fromJson(it) }.filter { it.text.isNotBlank() }.take(6),
                terms = o.optJSONArray("terms").objects().map { Vocab(it.optString("en"), it.optString("de"), topic) }.filter { it.en.isNotBlank() }.take(25),
                arguments = o.optJSONArray("arguments").objects().map { DossierArgument.fromJson(it) }.filter { it.claim.isNotBlank() }.take(8),
                links = o.optJSONArray("links").objects().map { DossierLink.fromJson(it) }.filter { it.topic in 0..13 && it.topic != topic && it.bridge.isNotBlank() }.take(5),
                questions = o.optJSONArray("questions").strings().take(8),
                ownIdeas = o.optJSONArray("own_ideas").strings().take(5),
            )
        if (dossier.keyPoints.isEmpty()) throw IllegalStateException("Das Dossier war leer.")
        app.dossiers.save(dossier)
        return dossier
    }
}

/** Starts the background job that keeps the dossiers up to date. */
object DossierScheduler {
    private const val WORK = "dossiers"

    fun kick(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<DossierWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }
}

/** Renews up to four outdated dossiers per run (topic fields with the most material first). */
class DossierWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AbiCoachApp
        if (!app.ai.ready) return Result.success()
        // While pages are still being read, the dossier would be incomplete: wait for the reader.
        if (app.memory.docs.value.any { it.isActive }) return Result.success()
        val stale = app.course.coveredTopics().filter { (_, chunks) -> chunks >= 3 }.map { it.first }.filter { DossierMaker.isStale(app, it) }
        var made = 0
        for (topic in stale) {
            if (made >= 4) {
                DossierScheduler.kick(app)
                break
            }
            app.dossiers.setStatus(DossierStatus(working = topic, text = "Dossier „${Knowledge.topicShort(topic)}“ wird erstellt …"))
            try {
                DossierMaker.make(app, topic)
                made++
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                app.dossiers.setStatus(DossierStatus(error = "Dossier „${Knowledge.topicShort(topic)}“: ${e.message}"))
                return if (e.isTransient && runAttemptCount < 3) Result.retry() else Result.success()
            } catch (e: Exception) {
                app.dossiers.setStatus(DossierStatus(error = "Dossier „${Knowledge.topicShort(topic)}“: ${e.message}"))
            }
        }
        app.dossiers.setStatus(DossierStatus())
        return Result.success()
    }
}
