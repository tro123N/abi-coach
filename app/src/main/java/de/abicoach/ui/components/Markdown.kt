/*
AbiCoach – Markdown-Anzeige für KI-Antworten, übernommen aus Mental Math Plus (tro123N),
basierend auf Mental Math, Copyright (C) 2025-2026 HeldDerTierwelt. Lizenz: GNU GPL v3 oder neuer.
*/

package de.abicoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent

/** Ein Abschnitt einer formatierten Antwort. */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock

    data class Bullet(val level: Int, val spans: List<MdSpan>) : MdBlock

    data class Numbered(val number: String, val level: Int, val spans: List<MdSpan>) : MdBlock

    data class Code(val text: String) : MdBlock

    data class Quote(val spans: List<MdSpan>) : MdBlock

    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock

    data object Rule : MdBlock
}

/** Ein Textstück mit Auszeichnung. */
data class MdSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

/** Kleiner Markdown-Leser (Absätze, Listen, Tabellen, fett, kursiv, Code). */
object Markdown {
    private val bullet = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val heading = Regex("^(#{1,6})\\s+(.*?)\\s*#*$")
    private val rule = Regex("^\\s*([-*_])\\s*(\\1\\s*){2,}$")
    private val tableSeparator = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun parse(text: String): List<MdBlock> {
        val lines = text.replace("\r", "").split('\n')
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(inline(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code += lines[i]
                        i++
                    }
                    blocks += MdBlock.Code(code.joinToString("\n").trimEnd())
                }
                trimmed.isEmpty() -> flush()
                trimmed.startsWith("|") && i + 1 < lines.size && tableSeparator.matches(lines[i + 1]) -> {
                    flush()
                    val header = cells(trimmed)
                    i += 2
                    val rows = mutableListOf<List<List<MdSpan>>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        rows += cells(lines[i].trim())
                        i++
                    }
                    blocks += MdBlock.Table(header, rows)
                    continue
                }
                rule.matches(trimmed) && paragraph.isEmpty() -> {
                    flush()
                    blocks += MdBlock.Rule
                }
                heading.matches(trimmed) -> {
                    flush()
                    val m = heading.find(trimmed)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, inline(m.groupValues[2]))
                }
                bullet.matches(line) -> {
                    flush()
                    val m = bullet.find(line)!!
                    blocks += MdBlock.Bullet(level(m.groupValues[1]), inline(m.groupValues[2]))
                }
                numbered.matches(line) -> {
                    flush()
                    val m = numbered.find(line)!!
                    blocks += MdBlock.Numbered(m.groupValues[2] + ".", level(m.groupValues[1]), inline(m.groupValues[3]))
                }
                trimmed.startsWith(">") -> {
                    flush()
                    blocks += MdBlock.Quote(inline(trimmed.removePrefix(">").trim()))
                }
                else -> paragraph += line.trim()
            }
            i++
        }
        flush()
        return blocks
    }

    private fun level(indent: String): Int = (indent.replace("\t", "    ").length / 2).coerceAtMost(3)

    private fun cells(row: String): List<List<MdSpan>> = row.trim().removePrefix("|").removeSuffix("|").split('|').map { inline(it.trim()) }

    /** Fett (**…**), kursiv (*…*) und Code (`…`). Nicht geschlossene Zeichen werden weggelassen. */
    fun inline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val sb = StringBuilder()
        var bold = false
        var italic = false
        var i = 0

        fun emit(code: Boolean = false) {
            if (sb.isNotEmpty()) {
                spans += MdSpan(sb.toString(), bold = bold, italic = italic, code = code)
                sb.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        emit()
                        sb.append(text, i + 1, end)
                        emit(code = true)
                        i = end + 1
                        continue
                    }
                    i++
                    continue
                }
                (c == '*' || c == '_') && i + 1 < text.length && text[i + 1] == c -> {
                    val marker = "$c$c"
                    if (bold || text.indexOf(marker, i + 2) > i + 2) {
                        emit()
                        bold = !bold
                    }
                    i += 2
                    continue
                }
                c == '*' -> {
                    val prevSpace = i == 0 || text[i - 1].isWhitespace()
                    val nextSpace = i + 1 >= text.length || text[i + 1].isWhitespace()
                    if (italic && !prevSpace) {
                        emit()
                        italic = false
                        i++
                        continue
                    }
                    if (!italic && !nextSpace && text.indexOf('*', i + 1).let { e -> e > i + 1 && !text[e - 1].isWhitespace() }) {
                        emit()
                        italic = true
                        i++
                        continue
                    }
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        emit()
        return spans.ifEmpty { listOf(MdSpan("")) }
    }

    /** Text ohne Auszeichnung, z. B. zum Vorlesen. */
    fun plain(text: String): String =
        parse(text).joinToString("\n") { block ->
            when (block) {
                is MdBlock.Paragraph -> block.spans.text()
                is MdBlock.Heading -> block.spans.text()
                is MdBlock.Bullet -> "• " + block.spans.text()
                is MdBlock.Numbered -> block.number + " " + block.spans.text()
                is MdBlock.Code -> block.text
                is MdBlock.Quote -> block.spans.text()
                is MdBlock.Table -> (listOf(block.header) + block.rows).joinToString("\n") { row -> row.joinToString(" | ") { it.text() } }
                MdBlock.Rule -> "—"
            }
        }

    private fun List<MdSpan>.text(): String = joinToString("") { it.text }
}

private fun annotated(spans: List<MdSpan>, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        for (s in spans) {
            withStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    fontFamily = if (s.code) FontFamily.Monospace else null,
                    background = if (s.code) codeBackground else Color.Unspecified,
                ),
            ) { append(s.text) }
        }
    }

/** Antwort der KI mit Absätzen, Listen, Tabellen, fett und kursiv. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    fontSize: TextUnit = AbiTheme.dimens.fonts.body,
) {
    val dimens = AbiTheme.dimens
    val blocks = remember(text) { Markdown.parse(text) }
    val codeBg = MaterialTheme.colorScheme.background
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Paragraph -> Text(annotated(b.spans, codeBg), fontSize = fontSize, lineHeight = fontSize * 1.4f, color = color)
                is MdBlock.Heading ->
                    Text(
                        annotated(b.spans, codeBg),
                        fontSize = if (b.level <= 2) dimens.fonts.label else fontSize,
                        lineHeight = dimens.fonts.label * 1.25f,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                is MdBlock.Bullet ->
                    Row(Modifier.padding(start = (14 * b.level).dp)) {
                        Text("•", fontSize = fontSize, lineHeight = fontSize * 1.4f, color = accent)
                        Spacer(Modifier.width(8.dp))
                        Text(annotated(b.spans, codeBg), fontSize = fontSize, lineHeight = fontSize * 1.4f, color = color, modifier = Modifier.weight(1f))
                    }
                is MdBlock.Numbered ->
                    Row(Modifier.padding(start = (14 * b.level).dp)) {
                        Text(b.number, fontSize = fontSize, lineHeight = fontSize * 1.4f, color = accent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(annotated(b.spans, codeBg), fontSize = fontSize, lineHeight = fontSize * 1.4f, color = color, modifier = Modifier.weight(1f))
                    }
                is MdBlock.Code ->
                    Text(
                        b.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = dimens.fonts.caption,
                        color = color,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(codeBg).padding(10.dp),
                    )
                is MdBlock.Quote ->
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                        Spacer(Modifier.width(10.dp))
                        Text(annotated(b.spans, codeBg), fontSize = fontSize, lineHeight = fontSize * 1.4f, color = color, fontStyle = FontStyle.Italic)
                    }
                is MdBlock.Table -> MdTable(b, color, codeBg)
                MdBlock.Rule -> HorizontalDivider(color = color.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, color: Color, codeBg: Color) {
    val dimens = AbiTheme.dimens
    val columns = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
    ) {
        (listOf(t.header) + t.rows).forEachIndexed { i, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (i == 0) codeBg else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                for (c in 0 until columns) {
                    Text(
                        annotated(row.getOrElse(c) { listOf(MdSpan("")) }, codeBg),
                        fontSize = dimens.fonts.caption,
                        lineHeight = dimens.fonts.caption * 1.3f,
                        fontWeight = if (i == 0) FontWeight.Bold else null,
                        color = color,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                    )
                }
            }
        }
    }
}
