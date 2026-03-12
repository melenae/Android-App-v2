package com.tools.toolapp_v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ROW_HEIGHT_DP = 36f
private const val LABEL_WIDTH_DP = 140f
private const val AXIS_HEIGHT_DP = 28f

/** Цвет полосы по этапу (step). */
private fun barColorForStep(step: String, isDark: Boolean, scheme: androidx.compose.material3.ColorScheme): Color = when {
    step.contains("Завершено", ignoreCase = true) ->
        if (isDark) scheme.primary.copy(alpha = 0.5f) else scheme.primaryContainer
    step.contains("в работе", ignoreCase = true) -> scheme.primary
    step.contains("ожидает", ignoreCase = true) -> scheme.outline.copy(alpha = 0.7f)
    else -> scheme.surfaceVariant
}

@Composable
internal fun GanttTimeAxis(
    rangeStartMs: Long,
    rangeEndMs: Long,
    totalDays: Int,
    heightPx: Float,
    chartWidthPx: Float
) {
    val density = LocalDensity.current
    val totalMs = (rangeEndMs - rangeStartMs).toFloat().coerceAtLeast(1f)
    val monthFormat = remember { SimpleDateFormat("MMM yyyy", Locale.US) }
    val monthLabels = remember(rangeStartMs, rangeEndMs) {
        val cal = Calendar.getInstance(Locale.US)
        val list = mutableListOf<Pair<Long, String>>()
        cal.timeInMillis = rangeStartMs
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val endCal = Calendar.getInstance(Locale.US).apply { timeInMillis = rangeEndMs }
        while (cal.before(endCal) || (cal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH) && cal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR))) {
            val monthStart = cal.timeInMillis
            val label = monthFormat.format(Date(monthStart))
            list.add(monthStart to label)
            cal.add(Calendar.MONTH, 1)
        }
        list
    }
    Box(
        modifier = Modifier
            .height(AXIS_HEIGHT_DP.dp)
            .width(with(density) { chartWidthPx.toDp() })
    ) {
        monthLabels.forEach { (monthStartMs, label) ->
            val xPx = ((monthStartMs - rangeStartMs) / totalMs * chartWidthPx).toFloat().coerceIn(0f, chartWidthPx - 1f)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(x = with(density) { xPx.toDp() }, y = 2.dp)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
internal fun GanttBar(
    startMs: Long,
    endMs: Long,
    rangeStartMs: Long,
    rangeEndMs: Long,
    chartWidthPx: Float,
    rowHeightPx: Float,
    barColor: Color
) {
    if (startMs <= 0L && endMs <= 0L) return
    val effectiveStart = if (startMs > 0L) startMs else endMs
    val effectiveEnd = if (endMs > 0L) endMs else startMs
    val totalMs = (rangeEndMs - rangeStartMs).toFloat().coerceAtLeast(1f)
    val leftPx = ((effectiveStart - rangeStartMs) / totalMs * chartWidthPx).toFloat().coerceIn(0f, chartWidthPx)
    val widthPx = ((effectiveEnd - effectiveStart) / totalMs * chartWidthPx).toFloat().coerceAtLeast(4f).coerceAtMost(chartWidthPx - leftPx)
    val topPx = (rowHeightPx * 0.15f).toInt().toFloat()
    val barHeightPx = (rowHeightPx * 0.7f).toInt().toFloat()
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRoundRect(
            color = barColor,
            topLeft = Offset(leftPx, topPx),
            size = Size(widthPx, barHeightPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
    }
}

@Composable
fun RoadMapScreen(
    roadMaps: List<RoadMap>,
    loadStatusMessage: String?,
    onItemClick: (RoadMap) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }
    val axisHeightPx = with(density) { AXIS_HEIGHT_DP.dp.toPx() }

    val (rangeStartMs, rangeEndMs, totalDays) = remember(roadMaps) {
        val withDates = roadMaps.filter { it.start > 0L || it.end > 0L }
        val rawStart = withDates.minOfOrNull { if (it.start > 0L) it.start else it.end } ?: run {
            val c = Calendar.getInstance(Locale.US)
            c.set(Calendar.MONTH, Calendar.JANUARY)
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }
        val rawEnd = withDates.maxOfOrNull { if (it.end > 0L) it.end else it.start } ?: run {
            val c = Calendar.getInstance(Locale.US)
            c.set(Calendar.MONTH, Calendar.DECEMBER)
            c.set(Calendar.DAY_OF_MONTH, 31)
            c.set(Calendar.HOUR_OF_DAY, 23)
            c.set(Calendar.MINUTE, 59)
            c.set(Calendar.SECOND, 59)
            c.set(Calendar.MILLISECOND, 999)
            c.timeInMillis
        }
        val start = rawStart.coerceAtMost(rawEnd - 24L * 60 * 60 * 1000)
        val end = rawEnd.coerceAtLeast(start + 24L * 60 * 60 * 1000)
        val days = ((end - start) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
        Triple(start, end, days)
    }

    val surface = MaterialTheme.colorScheme.surface
    val isDark = (0.299f * surface.red + 0.587f * surface.green + 0.114f * surface.blue) < 0.5f

    Column(modifier = modifier.fillMaxSize()) {
        loadStatusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableChartWidthDp = maxWidth - LABEL_WIDTH_DP.dp
            val chartWidthPx = with(density) { availableChartWidthDp.toPx() }.coerceAtLeast(0f)

            Row(modifier = Modifier.fillMaxSize()) {
            // Левая колонка: подпись оси времени + названия строк
            Column(modifier = Modifier.width(LABEL_WIDTH_DP.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AXIS_HEIGHT_DP.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Этап / период",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                roadMaps.forEach { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT_DP.dp)
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = item.name.ifBlank { "Без названия" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Правая часть: ось времени + полосы Ганта (ширина = оставшееся место на экране)
            Column(modifier = Modifier.fillMaxWidth()) {
                GanttTimeAxis(
                    rangeStartMs = rangeStartMs,
                    rangeEndMs = rangeEndMs,
                    totalDays = totalDays,
                    heightPx = axisHeightPx,
                    chartWidthPx = chartWidthPx
                )
                // Строки с полосами
                roadMaps.forEach { item ->
                    Box(
                        modifier = Modifier
                            .height(ROW_HEIGHT_DP.dp)
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                    ) {
                        GanttBar(
                            startMs = item.start,
                            endMs = item.end,
                            rangeStartMs = rangeStartMs,
                            rangeEndMs = rangeEndMs,
                            chartWidthPx = chartWidthPx,
                            rowHeightPx = rowHeightPx,
                            barColor = barColorForStep(item.step, isDark, MaterialTheme.colorScheme)
                        )
                    }
                }
            }
        }
    }
    }
}
