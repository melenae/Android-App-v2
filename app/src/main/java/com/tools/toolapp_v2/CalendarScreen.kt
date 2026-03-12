package com.tools.toolapp_v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

private fun firstDayOfMonthMillis(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun daysInMonth(millis: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    return c.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun dayOfWeekFirst(millis: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    return c.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    logs: List<ProjectLogs>,
    allLogsForHighlight: List<ProjectLogs>,
    types: List<String>,
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedType: String?,
    permittedProjects: List<Projects>,
    currentUser: Users,
    selectedDateMillis: Long,
    onDateSelect: (Long) -> Unit,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    onTypeChange: (String?) -> Unit,
    calendarExpanded: Boolean,
    onCalendarExpandedChange: (Boolean) -> Unit,
    onLogClick: (ProjectLogs) -> Unit,
    /** Сообщение о том, была ли при последней загрузке из Google прочитана таблица projectLogs (null = не загружали из Google). */
    projectLogsLoadStatusMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var displayedMonth by remember { mutableStateOf(firstDayOfMonthMillis(selectedDateMillis)) }
    val daysWithEvents = remember(allLogsForHighlight, displayedMonth) {
        val first = firstDayOfMonthMillis(displayedMonth)
        val c = Calendar.getInstance()
        val set = mutableSetOf<Long>()
        for (i in 1..daysInMonth(displayedMonth)) {
            c.timeInMillis = first
            c.set(Calendar.DAY_OF_MONTH, i)
            val dayStart = dayStartMillis(c.timeInMillis)
            if (allLogsForHighlight.any { dayStartMillis(it.date) == dayStart })
                set.add(dayStart)
        }
        set
    }
    val todayStart = remember { dayStartMillis(System.currentTimeMillis()) }
    val selectedDayStart = dayStartMillis(selectedDateMillis)

    Column(modifier = modifier.fillMaxSize()) {
        projectLogsLoadStatusMessage?.let { statusMsg ->
            Text(
                text = "При загрузке: $statusMsg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        // Календарь (сворачиваемый) — своя сетка с подсветкой дней, где есть события
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = calendarExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val c = Calendar.getInstance()
                            c.timeInMillis = displayedMonth
                            c.add(Calendar.MONTH, -1)
                            displayedMonth = c.timeInMillis
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Пред. месяц")
                        }
                        Text(
                            text = Calendar.getInstance().apply { timeInMillis = displayedMonth }
                                .getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())!!.replaceFirstChar { it.uppercase() } +
                                    " ${Calendar.getInstance().apply { timeInMillis = displayedMonth }.get(Calendar.YEAR)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = {
                            val c = Calendar.getInstance()
                            c.timeInMillis = displayedMonth
                            c.add(Calendar.MONTH, 1)
                            displayedMonth = c.timeInMillis
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "След. месяц")
                        }
                    }
                    // Заголовки дней недели
                    val weekDays = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        weekDays.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // Сетка дней
                    val firstWeekday = dayOfWeekFirst(displayedMonth)
                    val totalDays = daysInMonth(displayedMonth)
                    val rows = (firstWeekday + totalDays + 6) / 7
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (col in 0..6) {
                                    val cellIndex = row * 7 + col
                                    val dayNum = cellIndex - firstWeekday + 1
                                    val c = Calendar.getInstance()
                                    c.timeInMillis = firstDayOfMonthMillis(displayedMonth)
                                    c.set(Calendar.DAY_OF_MONTH, dayNum.coerceIn(1, totalDays))
                                    val cellDayStart = if (dayNum in 1..totalDays) dayStartMillis(c.timeInMillis) else null
                                    val hasEvents = cellDayStart != null && daysWithEvents.contains(cellDayStart)
                                    val isSelected = cellDayStart != null && cellDayStart == selectedDayStart
                                    val isToday = cellDayStart != null && cellDayStart == todayStart
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (hasEvents && !isSelected)
                                                    Modifier.background(Color(0xFFC8E6C9))
                                                else Modifier
                                            )
                                            .then(
                                                if (isSelected)
                                                    Modifier.background(MaterialTheme.colorScheme.primary)
                                                else Modifier
                                            )
                                            .then(
                                                if (isToday && !isSelected)
                                                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier
                                            )
                                            .then(
                                                if (dayNum in 1..totalDays)
                                                    Modifier.clickable { onDateSelect(c.timeInMillis) }
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (dayNum in 1..totalDays) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday && !isSelected -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onCalendarExpandedChange(false) }) {
                            Icon(Icons.Default.ExpandLess, contentDescription = "Свернуть календарь")
                        }
                    }
                }
            }
            if (!calendarExpanded) {
                IconButton(onClick = { onCalendarExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Развернуть календарь"
                    )
                }
            }
        }

        // Фильтры: одна строка — Задачи + Проект (выпадающий), затем Тип
        TaskAndProjectFilterRow(
            taskFilter = taskFilter,
            selectedProject = selectedProject,
            permittedProjects = permittedProjects,
            onTaskFilterChange = onTaskFilterChange,
            onProjectChange = onProjectChange
        )
        CalendarTypeFilterRow(
            types = types,
            selectedType = selectedType,
            onTypeChange = onTypeChange
        )

        // Список журнала
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { log ->
                    ProjectLogCard(log = log, onClick = { onLogClick(log) })
                }
            }
        }
    }
}

@Composable
private fun CalendarTypeFilterRow(
    types: List<String>,
    selectedType: String?,
    onTypeChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Тип:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier.clickable { onTypeChange(null) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedType == null,
                onClick = { onTypeChange(null) }
            )
            Text(
                text = "Все",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        types.forEach { type ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable { onTypeChange(type) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedType == type,
                    onClick = { onTypeChange(type) }
                )
                Text(
                    text = type,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProjectLogCard(
    log: ProjectLogs,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.project?.name ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = log.type.ifEmpty() { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = log.content.ifEmpty() { "—" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (log.agenda.isNotBlank()) {
                Text(
                    text = "Повестка: ${log.agenda}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "отв: ${log.user?.displayName ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatLogDate(log.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatLogDate(millis: Long): String {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    val d = c.get(Calendar.DAY_OF_MONTH)
    val m = c.get(Calendar.MONTH) + 1
    val y = c.get(Calendar.YEAR)
    val h = c.get(Calendar.HOUR_OF_DAY)
    val min = c.get(Calendar.MINUTE)
    return "%02d.%02d.%d %02d:%02d".format(d, m, y, h, min)
}
