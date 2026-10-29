package de.abicoach.memory

import android.util.LruCache
import de.abicoach.ai.AiHub
import de.abicoach.data.Knowledge
import de.abicoach.data.Settings
import de.abicoach.profile.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Hybrid search over the student's material: keywords (BM25) and meaning (vectors), fused by rank. */
class Retriever(private val store: MemoryStore, private val hub: AiHub) {
    private val queryVectors = LruCache<String, FloatArray>(64)

    suspend fun search(
        query: String,
        topics: Set<Int> = emptySet(),
        kinds: Set<DocKind>? = null,
        k: Int = 8,
        useVectors: Boolean = true,
    ): List<Hit> {
        val index = withContext(Dispatchers.Default) { store.index() }
        if (index.chunks.isEmpty()) return emptyList()
        val queryVector =
            if (useVectors && index.hasVectors && hub.hasGemini) {
                queryVectors.get(query) ?: runCatching { hub.embed(listOf(query), query = true).first() }.getOrNull()?.also { queryVectors.put(query, it) }
            } else {
                null
            }
        return withContext(Dispatchers.Default) {
            val n = index.chunks.size
            val allowed =
                BooleanArray(n) { i ->
                    val kind = index.docs[index.chunks[i].docId]?.kind
                    kinds == null || (kind != null && kinds.contains(kind))
                }
            val keyword = index.keywordScores(query)
            val byKeyword = (0 until n).filter { allowed[it] && keyword[it] > 0.0 }.sortedByDescending { keyword[it] }.take(60)
            val byMeaning =
                if (queryVector != null) {
                    val sim = index.vectorScores(queryVector)
                    (0 until n).filter { allowed[it] && sim[it] > 0.15 }.sortedByDescending { sim[it] }.take(60)
                } else {
                    emptyList()
                }
            val score = HashMap<Int, Double>()
            byKeyword.forEachIndexed { rank, i -> score[i] = (score[i] ?: 0.0) + 1.0 / (60 + rank) }
            byMeaning.forEachIndexed { rank, i -> score[i] = (score[i] ?: 0.0) + 1.2 / (60 + rank) }
            if (topics.isNotEmpty()) {
                score.keys.toList().forEach { i -> if (index.chunks[i].topics.any { it in topics }) score[i] = score.getValue(i) * 1.35 }
                // Nothing matched the words: fall back to passages tagged with the topic.
                if (score.isEmpty()) {
                    (0 until n).filter { allowed[it] && index.chunks[it].topics.any { t -> t in topics } }.shuffled(Random(query.hashCode())).take(k).forEach { score[it] = 0.001 }
                }
            }
            score.entries.sortedByDescending { it.value }.take(k).map { (i, s) ->
                val chunk = index.chunks[i]
                val doc = index.docs[chunk.docId]
                Hit(chunk, doc?.title ?: "Material", doc?.kind ?: DocKind.SONSTIGES, s)
            }
        }
    }
}

/**
 * Turns the memory into context for the prompts: what the course covered, facts and passages for a topic,
 * the teacher's comments from the Klausuren and candidate texts for part 1.
 */
class CourseContext(
    private val store: MemoryStore,
    val retriever: Retriever,
    private val settings: Settings,
    private val profiles: ProfileStore,
) {
    fun hasMaterial(): Boolean = store.docs.value.any { it.chunked }

    /** Topic fields with the number of passages about them, most material first. */
    fun coveredTopics(): List<Pair<Int, Int>> =
        store.stats().chunksPerTopic.mapIndexed { id, count -> id to count }.filter { it.second > 0 }.sortedByDescending { it.second }

    /** A topic field the course covered, chosen at random but weighted by the amount of material. */
    fun pickTopicFromCourse(exclude: Set<Int> = emptySet()): Int {
        val covered = coveredTopics().filter { it.first !in exclude }
        if (covered.isEmpty()) return Knowledge.TOPICS.map { it.id }.filter { it !in exclude }.random()
        val total = covered.sumOf { it.second }
        var r = Random.nextInt(total)
        for ((id, count) in covered) {
            if (r < count) return id
            r -= count
        }
        return covered.first().first
    }

    /** Short overview of the course for any prompt. */
    fun overview(maxChars: Int = 2_500): String {
        val docs = store.docs.value.filter { it.chunked || it.digested }
        val sb = StringBuilder()
        if (docs.isNotEmpty()) {
            sb.append("The student's own course material (uploaded to the app):\n")
            docs.forEach { d ->
                val topics = d.topics.take(3).joinToString(", ") { Knowledge.topicShort(it) }
                sb.append("- ").append(d.title).append(" (").append(d.kind.label)
                if (d.halbjahr != "unbekannt") sb.append(", ").append(d.halbjahr)
                if (topics.isNotEmpty()) sb.append("; ").append(topics)
                sb.append(")\n")
            }
            val covered = coveredTopics().take(8).joinToString(", ") { Knowledge.TOPICS[it.first].english }
            if (covered.isNotEmpty()) sb.append("Topic fields covered most: ").append(covered).append('\n')
        }
        if (settings.courseTopics.isNotBlank()) sb.append("Topics, texts and films from class (noted by the student): ").append(settings.courseTopics).append('\n')
        return sb.toString().take(maxChars)
    }

    /** Facts from the digests and the best passages for one topic field. */
    suspend fun forTopic(topicId: Int, focus: String? = null, maxChars: Int = 6_000): String {
        val topic = Knowledge.topic(topicId) ?: return ""
        val facts =
            store.allDigests().flatMap { (doc, digest) -> digest.facts.filter { it.topic == topicId }.map { doc to it.fact } }
        val hits = retriever.search(listOfNotNull(topic.english, focus).joinToString(" "), setOf(topicId), k = 8)
        if (facts.isEmpty() && hits.isEmpty()) return ""
        val sb = StringBuilder("From the student's own course on \"${topic.english}\":\n")
        facts.take(25).forEach { (doc, fact) -> sb.append("- ").append(fact).append(" [").append(doc.title).append("]\n") }
        if (hits.isNotEmpty()) {
            sb.append("Passages from the material:\n")
            hits.forEach { h -> sb.append("[").append(h.citation).append("] ").append(h.chunk.text.replace('\n', ' ').take(700)).append('\n') }
        }
        return sb.toString().take(maxChars)
    }

    /** Passages for a free question, with their citations. */
    suspend fun forQuery(query: String, maxChars: Int = 7_000, k: Int = 10): Pair<String, List<Hit>> {
        val hits = retriever.search(query, k = k)
        val sb = StringBuilder()
        hits.forEachIndexed { i, h -> sb.append("[").append(i + 1).append("] ").append(h.citation).append(":\n").append(h.chunk.text.take(1_200)).append("\n\n") }
        return sb.toString().take(maxChars) to hits
    }

    /** What the teacher wrote under the Klausuren: tells the examiner and the grading what she values. */
    fun teacherNotes(max: Int = 12): String {
        val notes = profiles.profile.value.teacherNotes
        if (notes.isEmpty()) return ""
        return "Comments of the student's English teacher in earlier Klausuren (German): " + notes.takeLast(max).joinToString(" | ")
    }

    /** Texts in the material that could be exam material, optionally for one topic field. */
    fun examTexts(topicId: Int?): List<Pair<CourseDoc, ExamTextRef>> =
        store.allDigests().flatMap { (doc, digest) -> digest.examTexts.map { doc to it } }
            .filter { (doc, ref) -> doc.kind != DocKind.KLAUSUR && (topicId == null || topicId < 0 || ref.topic == topicId || (ref.topic < 0 && topicId in doc.topics)) }

    /** The page texts of a document between two pages (inclusive). */
    fun pageText(docId: String, from: Int, to: Int): String =
        store.pages(docId).filter { it.page in from..maxOf(from, to) }.joinToString("\n\n") { it.text }

    /** Names and terms from the material for the speech recogniser. */
    fun vocabulary(limit: Int = 60): List<String> =
        store.allDigests().flatMap { (_, d) -> d.vocabulary.map { it.en } }.filter { it.length in 3..40 }.distinct().take(limit)

    /** Short facts for the examiner's part-2 topics. */
    fun factsFor(topicId: Int, limit: Int = 8): List<String> =
        store.allDigests().flatMap { (_, d) -> d.facts.filter { it.topic == topicId }.map { it.fact } }.distinct().take(limit)
}
