package de.abicoach.memory

/** Turns the inside of office files, web pages and RTF files into plain text (no Android needed, so it is tested). */
object TextExtract {
    /** Markup to text: paragraph ends become line breaks, tags go, the usual entities are resolved. */
    fun plainText(xml: String, paragraphEnd: Regex): String =
        xml
            .replace(paragraphEnd, "\n")
            .replace(Regex("<w:tab/>|<text:tab/>"), "\t")
            .replace(Regex("<w:br/>|<a:br/>|<text:line-break/>|<br ?/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    /** The readable text of a web page. */
    fun htmlText(html: String): String =
        plainText(
            html.replace(Regex("<(script|style|head)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), ""),
            Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE),
        )

    /** The readable text of an RTF file (enough for exported notes and worksheets). */
    fun rtfText(rtf: String): String {
        var t = rtf
        // Tables of fonts, colours, styles and embedded pictures carry no text.
        t = t.replace(Regex("\\{\\\\(fonttbl|colortbl|stylesheet|info|pict|\\*)[^{}]*(\\{[^{}]*\\}[^{}]*)*\\}"), "")
        t = t.replace(Regex("\\\\par\\b ?|\\\\line\\b ?"), "\n").replace(Regex("\\\\tab\\b ?"), "\t")
        t = Regex("\\\\u(-?\\d+)\\??").replace(t) { m -> (m.groupValues[1].toInt().let { if (it < 0) it + 65536 else it }).toChar().toString() }
        t = Regex("\\\\'([0-9a-fA-F]{2})").replace(t) { m -> String(byteArrayOf(m.groupValues[1].toInt(16).toByte()), charset("windows-1252")) }
        t = t.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "").replace(Regex("\\\\([{}\\\\])"), "$1").replace(Regex("(?<!\\\\)[{}]"), "")
        return t.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** True if the bytes read like text and not like a picture, an archive or a program. */
    fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = bytes.take(4000)
        if (sample.any { it.toInt() == 0 }) return false
        val odd = sample.count { val c = it.toInt() and 0xFF; c < 9 || c in 14..31 }
        return odd * 50 < sample.size
    }
}
