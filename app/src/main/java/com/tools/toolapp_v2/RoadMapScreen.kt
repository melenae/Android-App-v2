package com.tools.toolapp_v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
/** Высота строки этапа графика: название + User в скобках, несколько строк. */
private const val MAIN_STAGE_ROW_HEIGHT_DP = 56f
private const val DETAIL_ROW_MIN_HEIGHT_DP = 36f
private const val LABEL_WIDTH_DP = 196f
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
    val minLabelSpacingPx = with(density) { 56.dp.toPx() }
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
    val visibleMonthTicks = remember(monthLabels, chartWidthPx, minLabelSpacingPx, rangeStartMs, totalMs) {
        var lastX = -Float.MAX_VALUE
        buildList {
            monthLabels.forEach { (monthStartMs, label) ->
                val xPx = ((monthStartMs - rangeStartMs) / totalMs * chartWidthPx).toFloat().coerceIn(0f, chartWidthPx - 1f)
                if (isEmpty() || xPx - lastX >= minLabelSpacingPx) {
                    add(xPx to label)
                    lastX = xPx
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .height(AXIS_HEIGHT_DP.dp)
            .width(with(density) { chartWidthPx.toDp() })
    ) {
        visibleMonthTicks.forEach { (xPx, label) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
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

private fun noteDescriptionText(note: Notes): String {
    val t = (note.content ?: "").trim().replace("\n", " ").trim()
    return t.ifEmpty { "—" }
}

/** Подпись строки этапа: название и ответственный (как в таблицах). */
private fun roadMapStageLabelWithUser(rm: RoadMap): String {
    val title = rm.name.ifBlank { "Без названия" }
    val u = rm.user
    val who = if (u == null) {
        "—"
    } else {
        val email = u.email.takeIf { it.isNotBlank() } ?: u.login
        userDisplayId(u.displayName, email)
    }
    return "$title ($who)"
}

private fun userKey(u: Users): String =
    userDisplayId(
        u.displayName,
        u.email.takeIf { it.isNotBlank() } ?: u.login
    )

/** Список графиков; в ландшафте больше места для шкалы. Ориентация активности для вкладки не фиксируется — см. [MainScreen]. */
@Composable
fun RoadMapScreen(
    roadMaps: List<RoadMap>,
    currentUser: Users,
    issues: List<Issues>,
    notes: List<Notes>,
    pmProjectIds: Set<String> = emptySet(),
    onItemClick: (RoadMap) -> Unit,
    onIssueClick: (Issues) -> Unit = {},
    onNoteClick: (Notes) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val mainStageRowHeightPx = with(density) { MAIN_STAGE_ROW_HEIGHT_DP.dp.toPx() }
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

    val detailsByRoadMapId = remember(roadMaps, issues, notes, currentUser, pmProjectIds) {
        roadMaps.associate { rm ->
            rm.id to Pair(
                activeIssuesForCurrentUserOnRoadMap(rm.id, issues, currentUser, pmProjectIds),
                activeNotesForCurrentUserOnRoadMap(rm.id, notes, currentUser, pmProjectIds)
            )
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val isDark = (0.299f * surface.red + 0.587f * surface.green + 0.114f * surface.blue) < 0.5f
    val detailStripColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableChartWidthDp = maxWidth - LABEL_WIDTH_DP.dp
            val chartWidthPx = with(density) { availableChartWidthDp.toPx() }.coerceAtLeast(0f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(LABEL_WIDTH_DP.dp)
                            .height(AXIS_HEIGHT_DP.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Этап / период",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        GanttTimeAxis(
                            rangeStartMs = rangeStartMs,
                            rangeEndMs = rangeEndMs,
                            totalDays = totalDays,
                            heightPx = axisHeightPx,
                            chartWidthPx = chartWidthPx
                        )
                    }
                }

                roadMaps.forEach { item ->
                    val (issueRows, noteRows) = detailsByRoadMapId[item.id] ?: (emptyList<Issues>() to emptyList())

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MAIN_STAGE_ROW_HEIGHT_DP.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(LABEL_WIDTH_DP.dp)
                                .fillMaxHeight()
                                .clickable { onItemClick(item) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = roadMapStageLabelWithUser(item),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onItemClick(item) }
                        ) {
                            GanttBar(
                                startMs = item.start,
                                endMs = item.end,
                                rangeStartMs = rangeStartMs,
                                rangeEndMs = rangeEndMs,
                                chartWidthPx = chartWidthPx,
                                rowHeightPx = mainStageRowHeightPx,
                                barColor = barColorForStep(item.step, isDark, MaterialTheme.colorScheme)
                            )
                        }
                    }

                    issueRows.forEach { issue ->
                        val isIssueForCurrentUser =
                            issue.user?.let { userKey(it) == userKey(currentUser) } == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DETAIL_ROW_MIN_HEIGHT_DP.dp)
                                .clickable { onIssueClick(issue) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(LABEL_WIDTH_DP.dp)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Задача",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(detailStripColor)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = issue.name.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIssueForCurrentUser) Color.Blue else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    noteRows.forEach { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = DETAIL_ROW_MIN_HEIGHT_DP.dp)
                                .clickable { onNoteClick(note) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(LABEL_WIDTH_DP.dp)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Стикер",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(detailStripColor)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = noteDescriptionText(note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (note.user?.let { userKey(it) == userKey(currentUser) } == true) Color.Blue else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
