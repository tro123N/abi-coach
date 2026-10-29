package de.abicoach.memory

import android.content.Context
import de.abicoach.data.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The permanent memory of the course: every uploaded document with its pages, passages, vectors and digest,
 * stored in the app's private folder on the phone.
 */
class MemoryStore(context: Context) {
    private val root = File(context.filesDir, "memory").apply { mkdirs() }
    private val indexFile = File(root, "docs.json")

    private val _docs = MutableStateFlow(loadDocs())
    val docs: StateFlow<List<CourseDoc>> = _docs.asStateFlow()

    @Volatile private var cachedIndex: MemoryIndex? = null

    private fun loadDocs(): List<CourseDoc> =
        runCatching { JSONArray(indexFile.readText()).let { a -> (0 until a.length()).map { CourseDoc.fromJson(a.getJSONObject(it)) } } }
            .getOrElse { emptyList() }
            .sortedBy { it.addedAt }

    @Synchronized
    private fun saveDocs(list: List<CourseDoc>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val tmp = File(root, "docs.json.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(indexFile)
        _docs.value = list.sortedBy { it.addedAt }
    }

    fun docDir(id: String): File = File(root, id).apply { mkdirs() }

    fun doc(id: String): CourseDoc? = _docs.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(doc: CourseDoc) {
        saveDocs(_docs.value.filter { it.id != doc.id } + doc)
    }

    /** Changes one document; returns the new version (or null if it was deleted meanwhile). */
    @Synchronized
    fun update(id: String, change: (CourseDoc) -> CourseDoc): CourseDoc? {
        val current = _docs.value.firstOrNull { it.id == id } ?: return null
        val next = change(current)
        saveDocs(_docs.value.map { if (it.id == id) next else it })
        return next
    }

    @Synchronized
    fun delete(id: String) {
        saveDocs(_docs.value.filter { it.id != id })
        File(root, id).deleteRecursively()
        invalidate()
    }

    // ---------- pages ----------

    fun pages(id: String): List<PageText> =
        runCatching { JSONArray(File(docDir(id), "pages.json").readText()).objects().map { PageText.fromJson(it) } }.getOrElse { emptyList() }

    @Synchronized
    fun appendPages(id: String, newPages: List<PageText>) {
        val all = (pages(id) + newPages).associateBy { it.page }.values.sortedBy { it.page }
        val array = JSONArray()
        all.forEach { array.put(it.toJson()) }
        File(docDir(id), "pages.json").writeText(array.toString())
    }

    // ---------- chunks and vectors ----------

    fun chunks(id: String): List<Chunk> =
        runCatching { JSONArray(File(docDir(id), "chunks.json").readText()).objects().map { Chunk.fromJson(id, it) } }.getOrElse { emptyList() }

    @Synchronized
    fun saveChunks(id: String, chunks: List<Chunk>) {
        val array = JSONArray()
        chunks.forEach { array.put(it.toJson()) }
        File(docDir(id), "chunks.json").writeText(array.toString())
        File(docDir(id), "vectors.f32").delete()
        update(id) { it.copy(topicCounts = countTopics(chunks)) }
        invalidate()
    }

    private fun countTopics(chunks: List<Chunk>): List<Int> {
        val counts = IntArray(14)
        chunks.forEach { c -> c.topics.filter { it in 0..13 }.forEach { counts[it]++ } }
        return counts.toList()
    }

    /** Gives passages without their own topic the topics of the whole document (keeps the vectors). */
    @Synchronized
    fun retagChunks(id: String, topics: List<Int>) {
        if (topics.isEmpty()) return
        val updated = chunks(id).map { if (it.topics.isEmpty()) it.copy(topics = topics) else it }
        val array = JSONArray()
        updated.forEach { array.put(it.toJson()) }
        File(docDir(id), "chunks.json").writeText(array.toString())
        update(id) { it.copy(topicCounts = countTopics(updated)) }
        invalidate()
    }

    /** Vectors in chunk order; the file holds dims, count and then the floats. */
    fun vectors(id: String): List<FloatArray> {
        val file = File(docDir(id), "vectors.f32")
        if (!file.exists()) return emptyList()
        return runCatching {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                val dims = input.readInt()
                val count = input.readInt()
                val bytes = ByteArray(dims * 4)
                (0 until count).map {
                    input.readFully(bytes)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    FloatArray(dims) { buffer.getFloat() }
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun appendVectors(id: String, newVectors: List<FloatArray>) {
        if (newVectors.isEmpty()) return
        val all = vectors(id) + newVectors
        val dims = all.first().size
        val file = File(docDir(id), "vectors.f32")
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            out.writeInt(dims)
            out.writeInt(all.size)
            val buffer = ByteBuffer.allocate(dims * 4).order(ByteOrder.LITTLE_ENDIAN)
            all.forEach { v ->
                buffer.clear()
                for (i in 0 until dims) buffer.putFloat(v.getOrElse(i) { 0f })
                out.write(buffer.array())
            }
        }
        invalidate()
    }

    // ---------- digest ----------

    fun digest(id: String): Digest? = runCatching { Digest.fromJson(JSONObject(File(docDir(id), "digest.json").readText())) }.getOrNull()

    fun saveDigest(id: String, digest: Digest) {
        File(docDir(id), "digest.json").writeText(digest.toJson().toString())
        invalidate()
    }

    fun allDigests(): List<Pair<CourseDoc, Digest>> = _docs.value.mapNotNull { d -> digest(d.id)?.let { d to it } }

    // ---------- sources for reading ----------

    fun pdfFile(id: String): File = File(docDir(id), "source.pdf")

    fun textFile(id: String): File = File(docDir(id), "source.txt")

    fun imageFile(id: String, page: Int): File = File(docDir(id), String.format(java.util.Locale.ROOT, "page_%04d.jpg", page))

    // ---------- search index ----------

    fun invalidate() {
        cachedIndex = null
    }

    /** All searchable passages with their vectors; rebuilt after every change. */
    fun index(): MemoryIndex {
        cachedIndex?.let { return it }
        synchronized(this) {
            cachedIndex?.let { return it }
            val docs = _docs.value.filter { it.chunked }
            val chunks = ArrayList<Chunk>()
            val vectors = ArrayList<FloatArray?>()
            docs.forEach { d ->
                val c = chunks(d.id)
                val v = vectors(d.id)
                chunks += c
                c.indices.forEach { i -> vectors += v.getOrNull(i) }
            }
            val index = MemoryIndex(chunks, vectors, docs.associateBy { it.id })
            cachedIndex = index
            return index
        }
    }

    /** Totals for the screens (cheap: only the document list is read, not the passages). */
    fun stats(): MemoryStats {
        val list = _docs.value
        val perTopic = IntArray(14)
        list.filter { it.chunked }.forEach { d -> d.topicCounts.forEachIndexed { i, n -> if (i < 14) perTopic[i] += n } }
        return MemoryStats(
            documents = list.size,
            pages = list.sumOf { it.pageCount },
            pagesRead = list.sumOf { it.pagesRead },
            passages = list.filter { it.chunked }.sumOf { it.chunkCount },
            chunksPerTopic = perTopic.toList(),
            busy = list.count { it.isActive },
        )
    }
}

data class MemoryStats(val documents: Int, val pages: Int, val pagesRead: Int, val passages: Int, val chunksPerTopic: List<Int>, val busy: Int)

/** In-memory search index over all passages: BM25 for words, cosine similarity for meaning. */
class MemoryIndex(val chunks: List<Chunk>, val vectors: List<FloatArray?>, val docs: Map<String, CourseDoc>) {
    private val tokens: List<List<String>> = chunks.map { Text.tokens(it.text) }
    private val docFreq: Map<String, Int>
    private val avgLen: Double

    init {
        val df = HashMap<String, Int>()
        tokens.forEach { t -> t.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        docFreq = df
        avgLen = if (tokens.isEmpty()) 1.0 else tokens.sumOf { it.size }.toDouble() / tokens.size
    }

    val hasVectors: Boolean get() = vectors.any { it != null }

    /** BM25 scores for all chunks (k1 = 1.4, b = 0.75). */
    fun keywordScores(query: String): DoubleArray {
        val q = Text.tokens(query).distinct()
        val scores = DoubleArray(chunks.size)
        if (q.isEmpty()) return scores
        val n = chunks.size.toDouble()
        val idf = q.associateWith { term -> val df = docFreq[term] ?: 0; kotlin.math.ln(1 + (n - df + 0.5) / (df + 0.5)) }
        tokens.forEachIndexed { i, t ->
            if (t.isEmpty()) return@forEachIndexed
            val tf = HashMap<String, Int>()
            t.forEach { if (it in idf) tf[it] = (tf[it] ?: 0) + 1 }
            var s = 0.0
            tf.forEach { (term, f) ->
                val norm = f * 2.4 / (f + 1.4 * (1 - 0.75 + 0.75 * t.size / avgLen))
                s += (idf[term] ?: 0.0) * norm
            }
            scores[i] = s
        }
        return scores
    }

    fun vectorScores(query: FloatArray): DoubleArray =
        DoubleArray(chunks.size) { i ->
            val v = vectors[i] ?: return@DoubleArray -1.0
            var dot = 0.0
            val n = minOf(v.size, query.size)
            for (k in 0 until n) dot += v[k] * query[k]
            dot
        }
}

/** Small text helpers for the search. */
object Text {
    private val stop =
        setOf(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with", "is", "are", "was", "were", "be", "been",
            "it", "this", "that", "these", "those", "as", "by", "from", "he", "she", "they", "we", "you", "i", "his", "her", "their",
            "our", "your", "its", "not", "no", "do", "does", "did", "have", "has", "had", "will", "would", "can", "could", "there",
            "what", "which", "who", "how", "why", "when", "where", "about", "into", "than", "then", "so", "if", "also", "very",
            "der", "die", "das", "und", "oder", "ein", "eine", "einen", "ist", "sind", "war", "mit", "von", "zu", "im", "in", "den",
            "dem", "des", "auf", "für", "nicht", "es", "sie", "er", "wir", "ich", "du", "auch", "wie", "was", "als", "bei", "aus",
        )

    fun tokens(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it !in stop }
            .map { stem(it) }

    private fun stem(w: String): String =
        when {
            w.length > 5 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
            w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
            w.length > 4 && w.endsWith("es") -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }
}
