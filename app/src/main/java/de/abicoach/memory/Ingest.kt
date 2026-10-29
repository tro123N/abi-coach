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
import de.abicoach.ai.GeminiRest
import de.abicoach.ai.Sch
import de.abicoach.ai.arr
import de.abicoach.ai.bool
import de.abicoach.ai.enumOf
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import de.abicoach.data.ints
import de.abicoach.data.objects
import de.abicoach.data.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Starts (or continues) reading the course material in the background. */
object IngestScheduler {
    private const val WORK = "ingest"

    fun kick(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<IngestWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

/** Background job: works through the documents step by step and hands over to a new job after about 8 minutes. */
class IngestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AbiCoachApp
        val pipeline = IngestPipeline(app)
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < 8 * 60_000L) {
            when (pipeline.step()) {
                IngestPipeline.Step.IDLE -> return Result.success()
                IngestPipeline.Step.OFFLINE -> return Result.retry()
                IngestPipeline.Step.WORKED -> Unit
            }
        }
        IngestScheduler.kick(applicationContext)
        return Result.success()
    }
}

/**
 * The reading pipeline for one document: read pages (Gemini looks at each page image) → cut into passages →
 * vectors for the search → digest (summary, topic fields, facts, vocabulary, exam texts, Klausur errors).
 * Every step is saved, so the work survives restarts.
 */
class IngestPipeline(private val app: AbiCoachApp) {
    enum class Step { WORKED, IDLE, OFFLINE }

    private val store = app.memory

    suspend fun step(): Step {
        val doc = store.docs.value.firstOrNull { it.isActive || it.status == DocStatus.WAITING_KEY } ?: return Step.IDLE
        if (!app.ai.hasGemini) {
            store.docs.value.filter { it.isActive }.forEach { d -> store.update(d.id) { it.copy(status = DocStatus.WAITING_KEY) } }
            return Step.IDLE
        }
        try {
            when {
                doc.pagesRead < doc.pageCount -> readNext(doc)
                !doc.chunked -> chunk(doc)
                !doc.vectorless && doc.embedded < doc.chunkCount -> embedNext(doc)
                !doc.digested -> digest(doc)
                else -> store.update(doc.id) { it.copy(status = DocStatus.DONE, error = null) }
            }
            store.update(doc.id) { if (it.failures != 0 || it.error != null) it.copy(failures = 0, error = null) else it }
            return Step.WORKED
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            if (e.code == 0) return Step.OFFLINE
            if (e.code == 429) {
                store.update(doc.id) { it.copy(error = "Kontingent erschöpft – es geht gleich weiter") }
                delay(45_000)
                return Step.WORKED
            }
            fail(doc, e.message ?: "Fehler")
            return Step.WORKED
        } catch (e: Exception) {
            fail(doc, e.message ?: e.javaClass.simpleName)
            return Step.WORKED
        }
    }

    private suspend fun fail(doc: CourseDoc, message: String) {
        val updated = store.update(doc.id) { it.copy(failures = it.failures + 1, error = message) } ?: return
        if (updated.failures >= 3) {
            store.update(doc.id) { it.copy(status = DocStatus.FAILED) }
        } else {
            delay(5_000L * updated.failures)
        }
    }

    // ---------- 1. reading ----------

    private suspend fun readNext(doc: CourseDoc) {
        store.update(doc.id) { it.copy(status = DocStatus.READING) }
        if (doc.format == "text") {
            val text = store.textFile(doc.id).readText()
            val pages =
                (0 until doc.pageCount).map { i ->
                    val from = i * Importer.TEXT_PAGE
                    PageText(i + 1, text.substring(from.coerceAtMost(text.length), ((i + 1) * Importer.TEXT_PAGE).coerceAtMost(text.length)), "text", emptyList())
                }
            store.appendPages(doc.id, pages)
            store.update(doc.id) { it.copy(pagesRead = doc.pageCount) }
            return
        }
        val from = doc.pagesRead + 1
        val to = minOf(doc.pageCount, from + BATCH - 1)
        val images: List<Pair<Int, ByteArray>> =
            if (doc.format == "pdf") {
                app.importer.renderPdfPages(store.pdfFile(doc.id), from, to)
            } else {
                (from..to).mapNotNull { p -> store.imageFile(doc.id, p).takeIf { it.exists() }?.let { p to it.readBytes() } }
            }
        val pages =
            try {
                read(doc, images, notesMode = false)
            } catch (e: AiException) {
                // Google blocks verbatim copies of well-known texts: then keep detailed notes instead.
                if (e.code == -2 || e.code == -3) {
                    runCatching { read(doc, images, notesMode = true) }.getOrElse { images.map { (p, _) -> PageText(p, "", "skipped", emptyList()) } }
                } else {
                    throw e
                }
            }
        store.appendPages(doc.id, pages)
        store.update(doc.id) { it.copy(pagesRead = to) }
    }

    private suspend fun read(doc: CourseDoc, images: List<Pair<Int, ByteArray>>, notesMode: Boolean): List<PageText> {
        if (images.isEmpty()) return emptyList()
        val parts = ArrayList<JSONObject>()
        parts += GeminiRest.textPart(
            "Document: \"${doc.title}\" (${doc.kind.label}, ${doc.halbjahr}). Pages ${images.first().first}–${images.last().first} follow, " +
                "each introduced by its page number.",
        )
        images.forEach { (page, jpeg) ->
            parts += GeminiRest.textPart("Page $page:")
            parts += GeminiRest.inlinePart("image/jpeg", jpeg)
        }
        val json = app.ai.readJson(if (notesMode) READ_NOTES_SYSTEM else READ_SYSTEM, parts, readSchema)
        val returned = json.optJSONArray("pages").objects()
        val expected = images.map { it.first }
        return returned.mapIndexed { i, o ->
            val number = o.optInt("page", -1).takeIf { it in expected } ?: expected.getOrElse(i) { expected.last() }
            PageText(
                page = number,
                text = o.optString("text").trim(),
                kind = o.optString("kind", "text"),
                topics = o.optJSONArray("topics").ints().filter { it in 0..13 }.distinct(),
            )
        }.distinctBy { it.page }
    }

    // ---------- 2. passages ----------

    private fun chunk(doc: CourseDoc) {
        store.update(doc.id) { it.copy(status = DocStatus.INDEXING) }
        val chunks = Chunker.chunk(doc.id, store.pages(doc.id), doc.topics)
        store.saveChunks(doc.id, chunks)
        store.update(doc.id) { it.copy(chunked = true, chunkCount = chunks.size, embedded = 0) }
    }

    private suspend fun embedNext(doc: CourseDoc) {
        store.update(doc.id) { it.copy(status = DocStatus.INDEXING) }
        val chunks = store.chunks(doc.id)
        if (doc.embedded >= chunks.size) {
            store.update(doc.id) { it.copy(embedded = chunks.size, chunkCount = chunks.size) }
            return
        }
        val batch = chunks.subList(doc.embedded, minOf(chunks.size, doc.embedded + EMBED_BATCH))
        try {
            val vectors = app.ai.embed(batch.map { it.text.take(6000) }, query = false, titles = batch.map { doc.title })
            store.appendVectors(doc.id, vectors)
            store.update(doc.id) { it.copy(embedded = it.embedded + batch.size) }
        } catch (e: AiException) {
            // Without vectors the search still works with keywords; do not block the document.
            if (e.isPermanent) store.update(doc.id) { it.copy(vectorless = true) } else throw e
        }
    }

    // ---------- 3. digest ----------

    private suspend fun digest(doc: CourseDoc) {
        store.update(doc.id) { it.copy(status = DocStatus.DIGESTING) }
        val pages = store.pages(doc.id).filter { it.text.isNotBlank() }
        val total = pages.sumOf { it.text.length }
        val perPage = if (total <= DIGEST_CHARS) Int.MAX_VALUE else maxOf(400, DIGEST_CHARS / maxOf(1, pages.size))
        val text =
            pages.joinToString("\n\n") { p -> "=== S. ${p.page} (${p.kind}) ===\n" + if (p.text.length > perPage) p.text.take(perPage) + " […]" else p.text }
        val user = "Dokument: \"${doc.title}\" (${doc.kind.label}, Halbjahr: ${doc.halbjahr}, ${doc.pageCount} Seiten)\n\n$text"
        val json =
            if (doc.kind == DocKind.KLAUSUR) {
                app.ai.brainJson(DIGEST_SYSTEM, user, digestSchema, maxTokens = 12_000, thinking = false).json
            } else {
                app.ai.cheapJson(DIGEST_SYSTEM, user, digestSchema)
            }
        val digest = parseDigest(json, doc)
        store.saveDigest(doc.id, digest)
        store.retagChunks(doc.id, digest.topics)
        store.update(doc.id) {
            it.copy(
                summary = digest.summary,
                topics = digest.topics,
                halbjahr = if (it.halbjahr == "unbekannt" && digest.halbjahr in Knowledge.HALBJAHRE) digest.halbjahr else it.halbjahr,
                digested = true,
                status = DocStatus.DONE,
            )
        }
        digest.klausur?.let { k -> app.profiles.update { p -> ProfileFromKlausur.apply(p, k) } }
    }

    private fun parseDigest(o: JSONObject, doc: CourseDoc): Digest {
        val k = o.optJSONObject("klausur")
        val isKlausur = k != null && (k.optBoolean("is_klausur") || doc.kind == DocKind.KLAUSUR)
        return Digest(
            summary = o.optString("summary"),
            topics = o.optJSONArray("topics").ints().filter { it in 0..13 }.distinct(),
            halbjahr = o.optString("halbjahr"),
            facts = o.optJSONArray("facts").objects().map { Fact(it.optInt("topic", -1), it.optString("fact")) }.filter { it.fact.isNotBlank() },
            vocabulary = o.optJSONArray("vocabulary").objects().map { Vocab(it.optString("en"), it.optString("de"), it.optInt("topic", -1)) }.filter { it.en.isNotBlank() },
            examTexts =
                o.optJSONArray("exam_texts").objects().map {
                    ExamTextRef(it.optString("title"), it.optInt("start_page"), it.optInt("end_page"), it.optInt("topic", -1), it.optBoolean("literary"), it.optString("why"))
                }.filter { it.startPage in 1..doc.pageCount },
            klausur =
                if (!isKlausur || k == null) {
                    null
                } else {
                    KlausurInfo(
                        grade = k.optString("grade"),
                        teacherComments = k.optJSONArray("teacher_comments").strings(),
                        errors =
                            k.optJSONArray("errors").objects().map {
                                LiveError(it.optString("quote"), it.optString("correction"), it.optString("explanation"), it.optString("category", "OTHER"), "major")
                            }.filter { it.quote.isNotBlank() },
                        strengths = k.optJSONArray("strengths").strings(),
                        weaknesses = k.optJSONArray("weaknesses").strings(),
                    )
                },
        )
    }

    companion object {
        const val BATCH = 5
        const val EMBED_BATCH = 48
        const val DIGEST_CHARS = 420_000

        private val topicList = Knowledge.TOPICS.joinToString("\n") { "${it.id}: ${it.english}" }

        val READ_SYSTEM =
            """
You read pages of a German student's material for the English course of the Oberstufe in North Rhine-Westphalia: textbook pages, worksheets, exam papers (Klausuren) with the teacher's corrections, handouts and notes.
For every page return:
- page: the page number given before the image
- text: the complete content of the page as clean Markdown. Use # for headings, keep paragraphs, lists and the numbering of tasks. Keep the original language. Transcribe texts (articles, stories, speeches, poems) completely and exactly. Write vocabulary boxes as "word – translation" lines. Mark the student's handwriting as [Schüler: …], corrections by the teacher as [Korrektur: wrong → right] at the place where they occur, marginal symbols of the teacher (e.g. Gr, T, W, Sb, A) as [Fehlerzeichen: …] and comments of the teacher as [Kommentar: …]. Describe pictures in one line as [Bild: …]. Leave out running headers, footers and page numbers.
- kind: "text" (mainly a text to read), "tasks" (exercises), "vocabulary", "exam" (a Klausur or a student's written answer), "notes" (notes or a summary), "contents" (title page, table of contents, index) or "other"
- topics: the numbers of the topic fields the page belongs to (none, one or several):
$topicList
Return one entry per page, in order. If a page is empty, return an empty text for it.
            """.trimIndent()

        val READ_NOTES_SYSTEM =
            """
You read pages of a German student's material for the English course of the Oberstufe in North Rhine-Westphalia.
Do NOT copy longer passages word for word. Instead write detailed, faithful study notes for every page in Markdown: the topic, the main points and arguments, names, dates, facts, the tasks on the page, important vocabulary ("word – translation") and short quotes of at most one sentence. Teacher comments or corrections: [Kommentar: …], [Korrektur: wrong → right].
For every page return page, text (the notes), kind ("text", "tasks", "vocabulary", "exam", "notes", "contents" or "other") and topics (numbers of the topic fields):
$topicList
            """.trimIndent()

        private val readSchema: Sch =
            obj(
                "pages" to
                    arr(
                        obj(
                            "page" to int(),
                            "text" to str(),
                            "kind" to enumOf(listOf("text", "tasks", "vocabulary", "exam", "notes", "contents", "other")),
                            "topics" to arr(int()),
                        ),
                    ),
            )

        val DIGEST_SYSTEM =
            """
Du wertest ein Dokument aus dem Englischunterricht der Oberstufe (NRW, Grundkurs) für eine Lern-App aus, die einen Schüler auf die mündliche Abiturprüfung vorbereitet. Die App nutzt deine Auswertung als dauerhaftes Gedächtnis: Prüfungsaufgaben, Fragen des Prüfers und Rückmeldungen beziehen sich später darauf.
Themenfelder (Nummern):
$topicList
Liefere:
- summary: 2–4 Sätze auf Deutsch: Was ist das für ein Dokument, welche Inhalte, wofür ist es in der Prüfungsvorbereitung nützlich?
- topics: die Nummern der Themenfelder, die das Dokument behandelt (die wichtigsten zuerst).
- halbjahr: EF, Q1.1, Q1.2, Q2.1, Q2.2 oder unbekannt (nur wenn es erkennbar ist).
- facts: bis zu 40 wichtige Inhalte als kurze englische Sätze, die ein Prüfling im Gespräch nutzen könnte (Fakten, Positionen, Beispiele, Werke, Figuren, Begriffe), jeweils mit dem passenden Themenfeld (topic, -1 wenn keins).
- vocabulary: bis zu 60 wichtige englische Wörter und Wendungen aus dem Dokument mit deutscher Bedeutung und Themenfeld.
- exam_texts: Texte im Dokument (Sachtexte oder literarische Auszüge von etwa 150–450 Wörtern), die sich als Material für Teil 1 der mündlichen Prüfung eignen, mit Titel, Start- und Endseite (Seitenangaben wie im Dokument, „S. n“), Themenfeld, literary (true bei Literatur) und why (kurz auf Deutsch). Leer, wenn es keine gibt.
- klausur: is_klausur = true, wenn es eine Klausur oder eine korrigierte Schülerarbeit ist. Dann: grade (Note oder Punkte, falls erkennbar, sonst leer), teacher_comments (Kommentare der Lehrkraft wörtlich oder sinngemäß), errors (bis zu 40 von der Lehrkraft markierte oder klar erkennbare sprachliche Fehler des Schülers: quote = die fehlerhaften Wörter, correction, category aus ${Knowledge.ERROR_CATEGORIES.keys.joinToString(", ")}, explanation kurz auf Deutsch), strengths und weaknesses (je bis zu 5, auf Deutsch). Sonst is_klausur = false und leere Listen.
            """.trimIndent()

        private val digestSchema: Sch =
            obj(
                "summary" to str(),
                "topics" to arr(int()),
                "halbjahr" to str(),
                "facts" to arr(obj("topic" to int(), "fact" to str())),
                "vocabulary" to arr(obj("en" to str(), "de" to str(), "topic" to int())),
                "exam_texts" to arr(obj("title" to str(), "start_page" to int(), "end_page" to int(), "topic" to int(), "literary" to bool(), "why" to str())),
                "klausur" to
                    obj(
                        "is_klausur" to bool(),
                        "grade" to str(),
                        "teacher_comments" to arr(str()),
                        "errors" to
                            arr(
                                obj(
                                    "quote" to str(),
                                    "correction" to str(),
                                    "category" to enumOf(Knowledge.ERROR_CATEGORIES.keys.toList()),
                                    "explanation" to str(),
                                ),
                            ),
                        "strengths" to arr(str()),
                        "weaknesses" to arr(str()),
                    ),
            )
    }
}

/** Cuts page texts into passages of about 1,200 characters, without crossing page borders. */
object Chunker {
    private const val TARGET = 1_200
    private const val MAX = 1_800

    fun chunk(docId: String, pages: List<PageText>, docTopics: List<Int>): List<Chunk> {
        val out = ArrayList<Chunk>()
        pages.filter { it.kind != "contents" && it.kind != "skipped" && it.text.isNotBlank() }.forEach { page ->
            val paragraphs = page.text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { splitLong(it) }
            val current = StringBuilder()
            fun flush() {
                val t = current.toString().trim()
                if (t.length >= 40) {
                    out += Chunk(docId, out.size, page.page, t, page.topics.ifEmpty { docTopics }, page.kind)
                } else if (t.isNotEmpty() && out.isNotEmpty() && out.last().page == page.page) {
                    val last = out.removeAt(out.lastIndex)
                    out += last.copy(text = last.text + "\n\n" + t)
                }
                current.setLength(0)
            }
            paragraphs.forEach { p ->
                if (current.isNotEmpty() && current.length + p.length > TARGET) flush()
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(p)
            }
            flush()
        }
        return out
    }

    private fun splitLong(paragraph: String): List<String> {
        if (paragraph.length <= MAX) return listOf(paragraph)
        val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        sentences.forEach { s ->
            if (sb.isNotEmpty() && sb.length + s.length > TARGET) {
                parts += sb.toString()
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        // A single endless "sentence" (e.g. a word list without full stops) is cut hard.
        return parts.flatMap { if (it.length > MAX * 2) it.chunked(TARGET) else listOf(it) }
    }
}

/** What a Klausur adds to the learner profile: the error categories and the teacher's comments. */
object ProfileFromKlausur {
    fun apply(profile: de.abicoach.profile.Profile, k: KlausurInfo): de.abicoach.profile.Profile {
        val errors = profile.klausurErrors.toMutableMap()
        k.errors.forEach { e -> val c = e.category.ifBlank { "OTHER" }; errors[c] = (errors[c] ?: 0) + 1 }
        val notes = (profile.teacherNotes + k.teacherComments + k.weaknesses.map { "Schwäche: $it" }).map { it.trim() }.filter { it.isNotEmpty() }.distinct().takeLast(60)
        return profile.copy(klausurErrors = errors, teacherNotes = notes)
    }
}
