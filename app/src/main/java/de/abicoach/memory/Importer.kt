package de.abicoach.memory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.max

/** Copies picked files into the app, counts their pages and later renders pages for the reader. */
class Importer(private val context: Context, private val store: MemoryStore) {
    data class Picked(val uri: Uri, val name: String, val mime: String, val size: Long)

    fun describe(uri: Uri): Picked {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment ?: "Datei"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        // Some file managers only say "some file": then the ending of the name decides.
        val told = resolver.getType(uri)
        val guessed = guessMime(name)
        val mime = if (told == null || told == "application/octet-stream" || told == "*/*") guessed else told
        return Picked(uri, name, mime, size)
    }

    private fun guessMime(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "txt", "md", "csv" -> "text/plain"
            "html", "htm" -> "text/html"
            "rtf" -> "application/rtf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odt" -> "application/vnd.oasis.opendocument.text"
            else -> "application/octet-stream"
        }

    /** What an import brought: the new documents and, in plain words, the files that could not be read. */
    data class Result(val docs: List<CourseDoc>, val problems: List<String>)

    /**
     * Creates documents from the picked files – any mix of types at once. Photos picked together become one
     * document (one page per photo); every other file becomes its own document. A file that cannot be read does
     * not stop the others: it is listed in [Result.problems].
     */
    fun import(files: List<Picked>, kind: DocKind, title: String, halbjahr: String): Result {
        val problems = ArrayList<String>()
        val images = files.filter { it.mime.startsWith("image/") }
        val others = files.filter { !it.mime.startsWith("image/") }
        val created = ArrayList<CourseDoc>()
        if (images.isNotEmpty()) {
            val id = UUID.randomUUID().toString()
            var pages = 0
            var bytes = 0L
            images.forEach { picked ->
                val jpeg = normalizedJpeg(picked.uri)
                if (jpeg == null) {
                    problems += "„${picked.name}“: Das Bild lässt sich nicht öffnen."
                    return@forEach
                }
                pages++
                store.imageFile(id, pages).writeBytes(jpeg)
                bytes += jpeg.size
            }
            if (pages > 0) {
                val doc =
                    CourseDoc(
                        id = id,
                        title = title.ifBlank { images.first().name.substringBeforeLast('.') },
                        kind = kind,
                        halbjahr = halbjahr,
                        addedAt = System.currentTimeMillis(),
                        sourceName = if (images.size == 1) images.first().name else "${images.size} Fotos",
                        format = "images",
                        pageCount = pages,
                        sizeBytes = bytes,
                    )
                store.add(doc)
                created += doc
            }
        }
        others.forEachIndexed { i, picked ->
            val docTitle =
                when {
                    title.isBlank() -> picked.name.substringBeforeLast('.')
                    others.size + (if (images.isEmpty()) 0 else 1) > 1 -> "$title (${i + 1})"
                    else -> title
                }
            try {
                val doc = createFromFile(picked, kind, docTitle, halbjahr)
                if (doc != null) created += doc else problems += "„${picked.name}“ lässt sich nicht öffnen."
            } catch (e: Exception) {
                problems += e.message?.takeIf { it.contains(picked.name) } ?: "„${picked.name}“ lässt sich nicht lesen."
            }
        }
        return Result(created, problems)
    }

    /** The kind of file, decided by its type and – if that says nothing – by the ending of its name. */
    private fun typeOf(picked: Picked): String {
        val ext = picked.name.substringAfterLast('.', "").lowercase()
        val mime = picked.mime.lowercase()
        return when {
            mime == "application/pdf" || ext == "pdf" -> "pdf"
            mime.contains("wordprocessingml") || ext == "docx" -> "docx"
            mime.contains("presentationml") || ext == "pptx" -> "pptx"
            mime.contains("opendocument.text") || ext == "odt" -> "odt"
            mime.contains("rtf") || ext == "rtf" -> "rtf"
            mime == "text/html" || ext == "html" || ext == "htm" -> "html"
            mime.startsWith("text/") || ext in setOf("txt", "md", "csv") -> "text"
            ext in setOf("doc", "ppt", "pages", "key") -> "old"
            else -> "unknown"
        }
    }

    /** The text of a non-PDF file, or null if the app cannot read this kind of file. */
    private fun readText(picked: Picked): String? {
        val resolver = context.contentResolver
        return when (typeOf(picked)) {
            "docx" -> resolver.openInputStream(picked.uri)?.use { zipText(it, { n -> n == "word/document.xml" }, Regex("</w:p>")) }
            "pptx" -> resolver.openInputStream(picked.uri)?.use { zipText(it, { n -> n.startsWith("ppt/slides/slide") && n.endsWith(".xml") }, Regex("</a:p>")) }
            "odt" -> resolver.openInputStream(picked.uri)?.use { zipText(it, { n -> n == "content.xml" }, Regex("</text:(p|h)>")) }
            "rtf" -> resolver.openInputStream(picked.uri)?.use { TextExtract.rtfText(it.readBytes().toString(Charsets.ISO_8859_1)) }
            "html" -> resolver.openInputStream(picked.uri)?.use { TextExtract.htmlText(it.readBytes().toString(Charsets.UTF_8)) }
            "text" -> resolver.openInputStream(picked.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            "unknown" -> {
                // An unknown ending can still be plain text (notes, exports): take it if it reads like text.
                if (picked.size > MAX_GUESS_BYTES) return null
                val bytes = resolver.openInputStream(picked.uri)?.use { it.readBytes() } ?: return null
                if (bytes.size > MAX_GUESS_BYTES || !TextExtract.looksLikeText(bytes)) null else bytes.toString(Charsets.UTF_8)
            }
            else -> null
        }
    }

    private fun createFromFile(picked: Picked, kind: DocKind, title: String, halbjahr: String): CourseDoc? {
        val id = UUID.randomUUID().toString()
        val resolver = context.contentResolver
        return when {
            typeOf(picked) == "pdf" -> {
                val target = store.pdfFile(id)
                resolver.openInputStream(picked.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
                val pages = pdfPageCount(target)
                if (pages <= 0) {
                    store.docDir(id).deleteRecursively()
                    throw IllegalStateException("„${picked.name}“ lässt sich nicht öffnen (geschützt oder beschädigt).")
                }
                CourseDoc(id, title, kind, halbjahr, System.currentTimeMillis(), picked.name, "pdf", pages, target.length()).also { store.add(it) }
            }
            typeOf(picked) == "old" ->
                throw IllegalStateException("„${picked.name}“: Dieses ältere Format kann die App nicht lesen. Speichere die Datei als PDF und lade sie dann hoch.")
            else -> {
                val text =
                    readText(picked)
                        ?: throw IllegalStateException("„${picked.name}“: Dieses Format kann die App nicht lesen. Es gehen PDF, Fotos, Word, PowerPoint, OpenDocument und Text.")
                if (text.isBlank()) throw IllegalStateException("„${picked.name}“ enthält keinen lesbaren Text.")
                store.textFile(id).writeText(text)
                val pages = max(1, (text.length + TEXT_PAGE - 1) / TEXT_PAGE)
                CourseDoc(id, title, kind, halbjahr, System.currentTimeMillis(), picked.name, "text", pages, text.length.toLong()).also { store.add(it) }
            }
        }
    }

    /** Text out of the XML parts of a zipped office file (Word, PowerPoint, OpenDocument) without the markup. */
    private fun zipText(input: java.io.InputStream, wanted: (String) -> Boolean, paragraphEnd: Regex): String {
        val parts = ArrayList<Pair<String, String>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (wanted(entry.name)) parts += entry.name to zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        // Slides in their order: slide2 before slide10.
        val ordered = parts.sortedWith(compareBy({ it.first.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }, { it.first }))
        return ordered.joinToString("\n\n") { TextExtract.plainText(it.second, paragraphEnd) }.trim()
    }

    companion object {
        /** Longest side of a rendered PDF page: enough for small print and handwriting. */
        const val PAGE_PIXELS = 1600

        /** Longest side of a stored photo. */
        const val PHOTO_PIXELS = 2000

        /** Characters of a text file that count as one "page". */
        const val TEXT_PAGE = 3000

        /** Largest file of an unknown kind that is tried as plain text. */
        const val MAX_GUESS_BYTES = 2_000_000L
    }

    fun pdfPageCount(file: File): Int =
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    renderer.pageCount
                } finally {
                    renderer.close()
                }
            }
        }.getOrElse { -1 }

    /** Renders PDF pages [from, to] (1-based, inclusive) as JPEGs for the reader. */
    fun renderPdfPages(file: File, from: Int, to: Int): List<Pair<Int, ByteArray>> {
        val out = ArrayList<Pair<Int, ByteArray>>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                for (p in from..minOf(to, renderer.pageCount)) {
                    val page = renderer.openPage(p - 1)
                    try {
                        val scale = PAGE_PIXELS.toFloat() / max(page.width, page.height)
                        val w = max(1, (page.width * scale).toInt())
                        val h = max(1, (page.height * scale).toInt())
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out += p to jpeg(bitmap)
                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
        return out
    }

    /** Reads a photo, turns it upright, scales it down and stores it as JPEG. */
    private fun normalizedJpeg(uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= PHOTO_PIXELS) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Foto nicht lesbar")
            val rotation =
                resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            val scale = minOf(1f, PHOTO_PIXELS.toFloat() / max(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val upright =
                if (scale < 1f || rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded
            val bytes = jpeg(upright)
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
            bytes
        }.getOrNull()
    }

    private fun jpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return out.toByteArray()
    }
}
