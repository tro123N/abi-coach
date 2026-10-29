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
        val mime = resolver.getType(uri) ?: guessMime(name)
        return Picked(uri, name, mime, size)
    }

    private fun guessMime(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "txt", "md" -> "text/plain"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }

    /**
     * Creates documents from the picked files. Photos picked together become one document (one page per photo);
     * every PDF or text file becomes its own document.
     */
    fun import(files: List<Picked>, kind: DocKind, title: String, halbjahr: String): List<CourseDoc> {
        val images = files.filter { it.mime.startsWith("image/") }
        val others = files.filter { !it.mime.startsWith("image/") }
        val created = ArrayList<CourseDoc>()
        if (images.isNotEmpty()) {
            val id = UUID.randomUUID().toString()
            var pages = 0
            var bytes = 0L
            images.forEach { picked ->
                val jpeg = normalizedJpeg(picked.uri) ?: return@forEach
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
            createFromFile(picked, kind, docTitle, halbjahr)?.let { created += it }
        }
        return created
    }

    private fun createFromFile(picked: Picked, kind: DocKind, title: String, halbjahr: String): CourseDoc? {
        val id = UUID.randomUUID().toString()
        val resolver = context.contentResolver
        return when {
            picked.mime == "application/pdf" -> {
                val target = store.pdfFile(id)
                resolver.openInputStream(picked.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
                val pages = pdfPageCount(target)
                if (pages <= 0) {
                    store.docDir(id).deleteRecursively()
                    throw IllegalStateException("„${picked.name}“ lässt sich nicht öffnen (geschützt oder beschädigt).")
                }
                CourseDoc(id, title, kind, halbjahr, System.currentTimeMillis(), picked.name, "pdf", pages, target.length()).also { store.add(it) }
            }
            picked.mime.startsWith("text/") || picked.mime.contains("wordprocessingml") -> {
                val text =
                    if (picked.mime.contains("wordprocessingml")) {
                        resolver.openInputStream(picked.uri)?.use { docxText(it) }
                    } else {
                        resolver.openInputStream(picked.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } ?: return null
                if (text.isBlank()) throw IllegalStateException("„${picked.name}“ enthält keinen lesbaren Text.")
                store.textFile(id).writeText(text)
                val pages = max(1, (text.length + TEXT_PAGE - 1) / TEXT_PAGE)
                CourseDoc(id, title, kind, halbjahr, System.currentTimeMillis(), picked.name, "text", pages, text.length.toLong()).also { store.add(it) }
            }
            else -> throw IllegalStateException("„${picked.name}“: Dieses Format kann die App nicht lesen (PDF, Fotos, Word oder Text gehen).")
        }
    }

    /** Text of a .docx file: the paragraphs of word/document.xml without the markup. */
    private fun docxText(input: java.io.InputStream): String {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return xml
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<w:tab/>"), "\t")
                        .replace(Regex("<w:br/>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                        .lines().joinToString("\n") { it.trim() }
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                }
            }
        }
        return ""
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

    companion object {
        /** Longest side of a rendered PDF page: enough for small print and handwriting. */
        const val PAGE_PIXELS = 1600

        /** Longest side of a stored photo. */
        const val PHOTO_PIXELS = 2000

        /** Characters of a text file that count as one "page". */
        const val TEXT_PAGE = 3000
    }
}
