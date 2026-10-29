/*
AbiCoach – Diagramme im Stil von Mental Math Plus (tro123N), basierend auf Mental Math,
Copyright (C) 2025-2026 HeldDerTierwelt. Lizenz: GNU GPL v3 oder neuer.
*/

package de.abicoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/** Waagrechte Hilfslinie in einem Liniendiagramm, z. B. das Ziel von 13 Punkten. */
data class ChartGuide(val value: Float, val label: String, val color: Color)

/** Liniendiagramm mit Punkten, z. B. die Punkte der letzten Sitzungen. */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    guides: List<ChartGuide> = emptyList(),
    labels: List<String> = emptyList(),
    height: Dp = 150.dp,
    minY: Float? = 0f,
    maxY: Float? = null,
    valueText: (Float) -> String = { it.roundToInt().toString() },
) {
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
    val dotFill = MaterialTheme.colorScheme.primary
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val top = 14.dp.toPx()
        val bottom = if (labels.any { it.isNotEmpty() }) 18.dp.toPx() else 6.dp.toPx()
        val left = 6.dp.toPx()
        val right = 44.dp.toPx()
        val chartHeight = size.height - top - bottom
        val chartWidth = size.width - left - right
        val all = values + guides.map { it.value }
        var lo = minY ?: (all.minOrNull() ?: 0f)
        var hi = maxY ?: (all.maxOrNull() ?: 1f)
        if (hi - lo < 1e-3f) {
            hi += 1f
            lo -= if (minY == null) 1f else 0f
        }
        if (maxY == null) hi += (hi - lo) * 0.1f

        fun y(v: Float) = top + chartHeight * (1f - (v - lo) / (hi - lo))

        fun x(i: Int) = if (values.size == 1) left + chartWidth / 2 else left + chartWidth * i / (values.size - 1)

        drawLine(gridColor, Offset(left, top + chartHeight), Offset(left + chartWidth, top + chartHeight), strokeWidth = 1.dp.toPx())
        guides.forEach { g ->
            val gy = y(g.value)
            drawLine(
                g.color.copy(alpha = 0.8f),
                Offset(left, gy),
                Offset(left + chartWidth, gy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val layout = measurer.measure(g.label, labelStyle.copy(color = g.color))
            drawText(layout, topLeft = Offset(left + chartWidth + 4.dp.toPx(), gy - layout.size.height / 2f))
        }
        if (values.size > 1) {
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        values.forEachIndexed { i, v ->
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
            drawCircle(dotFill, radius = 2.dp.toPx(), center = Offset(x(i), y(v)))
        }
        val last = values.last()
        val lastLayout = measurer.measure(valueText(last), labelStyle.copy(color = color))
        val lx = (x(values.lastIndex) - lastLayout.size.width - 6.dp.toPx()).coerceIn(0f, size.width - lastLayout.size.width)
        val ly = (y(last) - lastLayout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
        drawText(lastLayout, topLeft = Offset(lx, ly))
        labels.forEachIndexed { i, label ->
            if (label.isNotEmpty() && i < values.size) {
                val layout = measurer.measure(label, labelStyle)
                val tx = (x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(tx, size.height - layout.size.height))
            }
        }
    }
}

/** Säulendiagramm mit eigener Farbe je Säule, z. B. Übungsminuten pro Tag. */
@Composable
fun ColorBarChart(
    values: List<Float>,
    colors: List<Color>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    guide: ChartGuide? = null,
    height: Dp = 140.dp,
) {
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val top = 8.dp.toPx()
        val bottom = 16.dp.toPx()
        val right = if (guide != null) 40.dp.toPx() else 0f
        val chartHeight = size.height - top - bottom
        val chartWidth = size.width - right
        val maxValue = max(max(values.maxOrNull() ?: 0f, guide?.value ?: 0f) * 1.08f, 1f)
        val slot = chartWidth / values.size
        val barWidth = slot * 0.66f
        drawLine(gridColor, Offset(0f, top + chartHeight), Offset(chartWidth, top + chartHeight), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - barWidth) / 2
            val c = colors.getOrElse(i) { textColor }
            if (v > 0f) {
                val h = (v / maxValue) * chartHeight
                drawRoundRect(
                    color = c,
                    topLeft = Offset(x, top + chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            } else {
                drawLine(gridColor, Offset(x, top + chartHeight - 1.dp.toPx()), Offset(x + barWidth, top + chartHeight - 1.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
            val label = labels.getOrNull(i).orEmpty()
            if (label.isNotEmpty()) {
                val layout = measurer.measure(label, labelStyle)
                val lx = (slot * i + slot / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, size.height - layout.size.height))
            }
        }
        guide?.let { g ->
            val gy = top + chartHeight * (1f - g.value / maxValue)
            drawLine(
                g.color.copy(alpha = 0.85f),
                Offset(0f, gy),
                Offset(chartWidth, gy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val layout = measurer.measure(g.label, labelStyle.copy(color = g.color))
            drawText(layout, topLeft = Offset(chartWidth + 4.dp.toPx(), (gy - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
        }
    }
}

/** Kleiner farbiger Punkt mit Beschriftung (Legende). */
@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        CaptionText(text)
    }
}

/** Waagrechter Balken mit Anteil 0 bis 1. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (fraction > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(height / 2))
                        .background(color),
            )
        }
    }
}

/** Beschriftung, darunter Balken und Wert. */
@Composable
fun BarRow(label: String, fraction: Float, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        CaptionText(label)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShareBar(fraction, color, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            CaptionText(value)
        }
    }
}
