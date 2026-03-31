package com.tools.toolapp_v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

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

@Composable
fun CalendarScreen(
    logs: List<ProjectLogs>,
    allLogsForHighlight: List<ProjectLogs>,
    currentUser: Users,
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    selectedType: String?,
    permittedProjects: List<Projects>,
    taskCounts: Map<TaskFilter, Int>,
    projectCounts: Map<String, Int>,
    roadMapCounts: Map<String, Int>,
    typeCounts: Map<String, Int>,
    roadMapOptions: List<RoadMap>,
    typeOptions: List<String>,
    displayedMonthMillis: Long,
    onDisplayedMonthChange: (Long) -> Unit,
    wholeMonthSelected: Boolean,
    onWholeMonthChange: (Boolean) -> Unit,
    selectedDateMillis: Long,
    onDateSelect: (Long) -> Unit,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    onRoadMapChange: (RoadMap?) -> Unit,
    onTypeChange: (String?) -> Unit,
    filtersSectionExpanded: Boolean = true,
    onFiltersSectionExpandedChange: (Boolean) -> Unit = {},
    calendarExpanded: Boolean,
    onCalendarExpandedChange: (Boolean) -> Unit,
    onLogClick: (ProjectLogs) -> Unit,
    modifier: Modifier = Modifier
) {
    val daysWithEvents = remember(allLogsForHighlight, displayedMonthMillis) {
        val first = firstDayOfMonthMillis(displayedMonthMillis)
        val c = Calendar.getInstance()
        val set = mutableSetOf<Long>()
        for (i in 1..daysInMonth(displayedMonthMillis)) {
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
        CollapsibleFiltersSection(
            expanded = filtersSectionExpanded,
            onExpandedChange = onFiltersSectionExpandedChange,
            showHeader = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            CalendarFiltersBar(
                taskFilter = taskFilter,
                selectedProject = selectedProject,
                selectedRoadMap = selectedRoadMap,
                selectedType = selectedType,
                permittedProjects = permittedProjects,
                taskCounts = taskCounts,
                projectCounts = projectCounts,
                roadMapCounts = roadMapCounts,
                typeCounts = typeCounts,
                roadMapOptions = roadMapOptions,
                typeOptions = typeOptions,
                onTaskFilterChange = onTaskFilterChange,
                onProjectChange = onProjectChange,
                onRoadMapChange = onRoadMapChange,
                onTypeChange = onTypeChange,
                modifier = Modifier.fillMaxWidth()
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val c = Calendar.getInstance()
                            c.timeInMillis = displayedMonthMillis
                            c.add(Calendar.MONTH, -1)
                            onDisplayedMonthChange(firstDayOfMonthMillis(c.timeInMillis))
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Пред. месяц")
                        }
                        Text(
                            text = Calendar.getInstance().apply { timeInMillis = displayedMonthMillis }
                                .getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())!!.replaceFirstChar { it.uppercase() } +
                                " ${Calendar.getInstance().apply { timeInMillis = displayedMonthMillis }.get(Calendar.YEAR)}",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )
                        IconButton(onClick = {
                            val c = Calendar.getInstance()
                            c.timeInMillis = displayedMonthMillis
                            c.add(Calendar.MONTH, 1)
                            onDisplayedMonthChange(firstDayOfMonthMillis(c.timeInMillis))
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "След. месяц")
                        }
                        FilterChip(
                            selected = wholeMonthSelected,
                            onClick = { onWholeMonthChange(!wholeMonthSelected) },
                            label = { Text("Весь месяц", style = MaterialTheme.typography.labelMedium) }
                        )
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
                    val firstWeekday = dayOfWeekFirst(displayedMonthMillis)
                    val totalDays = daysInMonth(displayedMonthMillis)
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
                                    c.timeInMillis = firstDayOfMonthMillis(displayedMonthMillis)
                                    c.set(Calendar.DAY_OF_MONTH, dayNum.coerceIn(1, totalDays))
                                    val cellDayStart = if (dayNum in 1..totalDays) dayStartMillis(c.timeInMillis) else null
                                    val hasEvents = cellDayStart != null && daysWithEvents.contains(cellDayStart)
                                    val inWholeMonth = wholeMonthSelected && dayNum in 1..totalDays
                                    val isSingleDaySelected =
                                        !wholeMonthSelected && cellDayStart != null && cellDayStart == selectedDayStart
                                    val isToday = cellDayStart != null && cellDayStart == todayStart
                                    val primary = MaterialTheme.colorScheme.primary
                                    val cellBackground = when {
                                        isSingleDaySelected -> primary
                                        hasEvents && inWholeMonth -> Color(0xFFB2DFDB)
                                        hasEvents -> Color(0xFFC8E6C9)
                                        inWholeMonth -> primary.copy(alpha = 0.22f)
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (cellBackground != Color.Transparent)
                                                    Modifier.background(cellBackground)
                                                else Modifier
                                            )
                                            .then(
                                                if (isToday && !isSingleDaySelected)
                                                    Modifier.border(1.dp, primary, CircleShape)
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
                                                    isSingleDaySelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday && !isSingleDaySelected -> primary
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

        // Список журнала
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { log ->
                    ProjectLogCard(
                        log = log,
                        currentUser = currentUser,
                        onClick = { onLogClick(log) }
                    )
                }
            }
        }
    }
}

private fun userKey(u: Users): String =
    userDisplayId(u.displayName, u.email.takeIf { it.isNotBlank() } ?: u.login)

@Composable
private fun ProjectLogCard(
    log: ProjectLogs,
    currentUser: Users,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentUserForLog = log.user?.let { userKey(it) == userKey(currentUser) } == true
    val projectBoldStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

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
                    style = projectBoldStyle,
                    color = MaterialTheme.colorScheme.onSurface,
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
                color = if (isCurrentUserForLog) Color.Blue else MaterialTheme.colorScheme.onSurface,
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
                    text = "отв:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = log.user?.displayName ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentUserForLog) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
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
