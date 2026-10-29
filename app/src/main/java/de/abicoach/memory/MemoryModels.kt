package de.abicoach.memory

import de.abicoach.R
import de.abicoach.data.LiveError
import de.abicoach.data.ints
import de.abicoach.data.objects
import de.abicoach.data.strings
import de.abicoach.data.toIntJsonArray
import de.abicoach.data.toJsonArray
import org.json.JSONArray
import org.json.JSONObject

/** What kind of material a document is. Klausuren also feed the learner profile. */
enum class DocKind(val label: String, val plural: String, val icon: Int) {
    SCHULBUCH("Schulbuch", "Schulbuch", R.drawable.menu_book_24dp),
    KLAUSUR("Klausur", "Klausuren", R.drawable.grading_24dp),
    UNTERRICHT("Unterrichtsmaterial", "Unterrichtsmaterial", R.drawable.description_24dp),
    MITSCHRIFT("Mitschrift / Notizen", "Mitschriften und Notizen", R.drawable.edit_note_24dp),
    ABI_BUCH("Abi-Vorbereitung", "Abi-Vorbereitung", R.drawable.library_books_24dp),
    LEKTUERE("Lektüre / Roman", "Lektüren", R.drawable.auto_stories_24dp),
    SONSTIGES("Sonstiges", "Sonstiges", R.drawable.folder_24dp),
}

enum class DocStatus(val label: String) {
    QUEUED("Wartet"),
    READING("Wird gelesen"),
    INDEXING("Wird verknüpft"),
    DIGESTING("Wird ausgewertet"),
    DONE("Fertig"),
    PAUSED("Pausiert"),
    FAILED("Fehler"),
    WAITING_KEY("Wartet auf Gemini-Schlüssel"),
}

/** One uploaded document (a PDF, a set of photos or a text) and how far the app has read it. */
data class CourseDoc(
    val id: String,
    val title: String,
    val kind: DocKind,
    val halbjahr: String,
    val addedAt: Long,
    val sourceName: String,
    /** "pdf", "images" or "text". */
    val format: String,
    val pageCount: Int,
    val sizeBytes: Long = 0,
    val pagesRead: Int = 0,
    val chunked: Boolean = false,
    val embedded: Int = 0,
    val chunkCount: Int = 0,
    val vectorless: Boolean = false,
    val digested: Boolean = false,
    val status: DocStatus = DocStatus.QUEUED,
    val error: String? = null,
    val failures: Int = 0,
    val summary: String = "",
    val topics: List<Int> = emptyList(),
    /** Number of passages per topic field (14 numbers), for the knowledge map. */
    val topicCounts: List<Int> = emptyList(),
) {
    val isActive: Boolean get() = status == DocStatus.QUEUED || status == DocStatus.READING || status == DocStatus.INDEXING || status == DocStatus.DIGESTING

    /** 0..1 over all steps: reading counts most. */
    val progress: Float
        get() {
            if (status == DocStatus.DONE) return 1f
            val read = if (pageCount == 0) 1f else pagesRead.toFloat() / pageCount
            val embed = if (!chunked) 0f else if (chunkCount == 0 || vectorless) 1f else embedded.toFloat() / chunkCount
            return 0.75f * read + 0.15f * embed + if (digested) 0.1f else 0f
        }

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id).put("title", title).put("kind", kind.name).put("halbjahr", halbjahr).put("addedAt", addedAt)
            .put("sourceName", sourceName).put("format", format).put("pageCount", pageCount).put("sizeBytes", sizeBytes)
            .put("pagesRead", pagesRead).put("chunked", chunked).put("embedded", embedded).put("chunkCount", chunkCount)
            .put("vectorless", vectorless).put("digested", digested).put("status", status.name)
            .put("error", error ?: JSONObject.NULL).put("failures", failures).put("summary", summary).put("topics", topics.toIntJsonArray())
            .put("topicCounts", topicCounts.toIntJsonArray())

    companion object {
        fun fromJson(o: JSONObject) =
            CourseDoc(
                id = o.optString("id"),
                title = o.optString("title"),
                kind = runCatching { DocKind.valueOf(o.optString("kind")) }.getOrDefault(DocKind.SONSTIGES),
                halbjahr = o.optString("halbjahr", "unbekannt"),
                addedAt = o.optLong("addedAt"),
                sourceName = o.optString("sourceName"),
                format = o.optString("format", "pdf"),
                pageCount = o.optInt("pageCount"),
                sizeBytes = o.optLong("sizeBytes"),
                pagesRead = o.optInt("pagesRead"),
                chunked = o.optBoolean("chunked"),
                embedded = o.optInt("embedded"),
                chunkCount = o.optInt("chunkCount"),
                vectorless = o.optBoolean("vectorless"),
                digested = o.optBoolean("digested"),
                status = runCatching { DocStatus.valueOf(o.optString("status")) }.getOrDefault(DocStatus.QUEUED),
                error = if (o.isNull("error")) null else o.optString("error").ifBlank { null },
                failures = o.optInt("failures"),
                summary = o.optString("summary"),
                topics = o.optJSONArray("topics").ints(),
                topicCounts = o.optJSONArray("topicCounts").ints(),
            )
    }
}

/** The text of one page as the reader understood it. */
data class PageText(val page: Int, val text: String, val kind: String, val topics: List<Int>) {
    fun toJson(): JSONObject = JSONObject().put("page", page).put("text", text).put("kind", kind).put("topics", topics.toIntJsonArray())

    companion object {
        fun fromJson(o: JSONObject) = PageText(o.optInt("page"), o.optString("text"), o.optString("kind", "text"), o.optJSONArray("topics").ints())
    }
}

/** A passage of about a third of a page: the unit the memory searches in. */
data class Chunk(val docId: String, val index: Int, val page: Int, val text: String, val topics: List<Int>, val kind: String) {
    fun toJson(): JSONObject =
        JSONObject().put("index", index).put("page", page).put("text", text).put("topics", topics.toIntJsonArray()).put("kind", kind)

    companion object {
        fun fromJson(docId: String, o: JSONObject) =
            Chunk(docId, o.optInt("index"), o.optInt("page"), o.optString("text"), o.optJSONArray("topics").ints(), o.optString("kind", "text"))
    }
}

data class Fact(val topic: Int, val fact: String)

data class Vocab(val en: String, val de: String, val topic: Int)

/** A text in the material that could serve as exam material for part 1. */
data class ExamTextRef(val title: String, val startPage: Int, val endPage: Int, val topic: Int, val literary: Boolean, val why: String)

/** What a Klausur says about the student: grade, the teacher's comments and the marked errors. */
data class KlausurInfo(
    val grade: String,
    val teacherComments: List<String>,
    val errors: List<LiveError>,
    val strengths: List<String>,
    val weaknesses: List<String>,
)

/** Everything the app learned from one document. */
data class Digest(
    val summary: String,
    val topics: List<Int>,
    val halbjahr: String,
    val facts: List<Fact>,
    val vocabulary: List<Vocab>,
    val examTexts: List<ExamTextRef>,
    val klausur: KlausurInfo?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("summary", summary)
            .put("topics", topics.toIntJsonArray())
            .put("halbjahr", halbjahr)
            .put("facts", facts.toJsonArray { JSONObject().put("topic", it.topic).put("fact", it.fact) })
            .put("vocabulary", vocabulary.toJsonArray { JSONObject().put("en", it.en).put("de", it.de).put("topic", it.topic) })
            .put(
                "examTexts",
                examTexts.toJsonArray {
                    JSONObject().put("title", it.title).put("startPage", it.startPage).put("endPage", it.endPage)
                        .put("topic", it.topic).put("literary", it.literary).put("why", it.why)
                },
            )
            .put(
                "klausur",
                klausur?.let { k ->
                    JSONObject().put("grade", k.grade).put("teacherComments", k.teacherComments.toJsonArray())
                        .put("errors", k.errors.toJsonArray { it.toJson() }).put("strengths", k.strengths.toJsonArray())
                        .put("weaknesses", k.weaknesses.toJsonArray())
                } ?: JSONObject.NULL,
            )

    companion object {
        fun fromJson(o: JSONObject): Digest =
            Digest(
                summary = o.optString("summary"),
                topics = o.optJSONArray("topics").ints(),
                halbjahr = o.optString("halbjahr"),
                facts = o.optJSONArray("facts").objects().map { Fact(it.optInt("topic", -1), it.optString("fact")) },
                vocabulary = o.optJSONArray("vocabulary").objects().map { Vocab(it.optString("en"), it.optString("de"), it.optInt("topic", -1)) },
                examTexts =
                    o.optJSONArray("examTexts").objects().map {
                        ExamTextRef(it.optString("title"), it.optInt("startPage"), it.optInt("endPage"), it.optInt("topic", -1), it.optBoolean("literary"), it.optString("why"))
                    },
                klausur =
                    o.optJSONObject("klausur")?.let { k ->
                        KlausurInfo(
                            grade = k.optString("grade"),
                            teacherComments = k.optJSONArray("teacherComments").strings(),
                            errors = k.optJSONArray("errors").objects().map { LiveError.fromJson(it) },
                            strengths = k.optJSONArray("strengths").strings(),
                            weaknesses = k.optJSONArray("weaknesses").strings(),
                        )
                    },
            )
    }
}

/** One search result. */
data class Hit(val chunk: Chunk, val docTitle: String, val docKind: DocKind, val score: Double) {
    /** "Green Line, S. 34" – how the app cites the student's material. */
    val citation: String get() = "$docTitle, S. ${chunk.page}"
}

internal fun JSONArray.toFloatList(): List<Float> = (0 until length()).map { optDouble(it).toFloat() }
