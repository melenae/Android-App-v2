package com.tools.toolapp_v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Тёмно-зелёный цвет для текущего счётчика таймера (контраст). */
private val TimerRunningColor = Color(0xFF1B5E20)

/** Форматирует часы (дробные) в виде "X ч Y мин Z сек" для отображения таймера в реальном времени. */
private fun formatHoursMinutesSeconds(hours: Double): String {
    val totalSeconds = (hours * 3600).toInt().coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "$h ч $m мин $s сек"
}

/** Формат даты списания на форме Тайминга. */
private val timingDateDisplayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

/** Строка на закладке Timing: одна сущность (Issue, Note или ProjectLog) с onTiming = true. */
data class TimingRow(
    val key: String,
    val title: String,
    val project: Projects?,
    val issue: Issues?,
    val note: Notes?,
    val log: ProjectLogs?
) {
    init {
        require((issue != null).xor(note != null).xor(log != null)) { "Ровно одна из issue, note, log должна быть задана" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimingScreen(
    timingRows: List<TimingRow>,
    todayAccumulatedHours: Map<String, Double>,
    currentRunningKey: String?,
    currentTimerStartMillis: Long?,
    onStartTimer: (String) -> Unit,
    onPauseTimer: () -> Unit,
    timeEntries: List<TimeEntries>,
    currentUser: Users,
    totalTodayFromEntries: Double,
    selectedWorkDateMillis: Long,
    onWorkDateChange: (Long) -> Unit,
    onFinishDay: (workDateDayStartMillis: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    LaunchedEffect(currentRunningKey) {
        if (currentRunningKey != null) {
            while (true) {
                delay(1000)
                nowMs = System.currentTimeMillis()
            }
        }
    }
    val runningElapsedHours = if (currentRunningKey != null && currentTimerStartMillis != null)
        (nowMs - currentTimerStartMillis) / 3600_000.0 else 0.0
    val totalTodayHours = totalTodayFromEntries + todayAccumulatedHours.values.sum() + runningElapsedHours

    Column(modifier = modifier.fillMaxSize()) {
        // Дата списания
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Дата списания:",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(timingDateDisplayFormat.format(Date(selectedWorkDateMillis)))
            }
        }
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedWorkDateMillis,
                initialDisplayedMonthMillis = selectedWorkDateMillis
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { ms ->
                                onWorkDateChange(dayStartMillis(ms))
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("ОК")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Отмена")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        // Верх: строки таймеров
        Text(
            text = "Тайминг",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(timingRows) { row ->
                val accumulated = todayAccumulatedHours[row.key] ?: 0.0
                val isRunning = currentRunningKey == row.key
                val runningElapsed = if (isRunning && currentTimerStartMillis != null)
                    (nowMs - currentTimerStartMillis) / 3600_000.0 else 0.0
                val totalHours = accumulated + runningElapsed
                TimingRowCard(
                    row = row,
                    hoursToday = totalHours,
                    isRunning = isRunning,
                    onStart = { onStartTimer(row.key) },
                    onPause = onPauseTimer
                )
            }
        }

        // Низ: записи TimeEntries (только свои)
        Text(
            text = "Записи трудозатрат",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(timeEntries) { entry ->
                TimeEntryCard(entry = entry)
            }
        }

        // Итого за сегодня + кнопка «Завершить день»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Итого сегодня: ${formatHoursMinutesSeconds(totalTodayHours)}",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = { onFinishDay(selectedWorkDateMillis) }) {
                Text("Завершить день")
            }
        }
    }
}

@Composable
private fun TimingRowCard(
    row: TimingRow,
    hoursToday: Double,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.project?.name ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = formatHoursMinutesSeconds(hoursToday),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * 2).toInt().sp,
                    fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isRunning) TimerRunningColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (isRunning) {
                IconButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = "Пауза")
                }
            } else {
                IconButton(onClick = onStart) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Старт")
                }
            }
        }
    }
}

@Composable
private fun TimeEntryCard(
    entry: TimeEntries,
    modifier: Modifier = Modifier
) {
    val title = when {
        entry.issue != null -> entry.issue!!.name
        entry.note != null -> entry.note!!.content.take(50)
        entry.projectLog != null -> entry.projectLog!!.content.take(50)
        else -> "—"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.project?.name ?: "—",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = "%.2f ч".format(roundTimeEntryHours(entry.hours)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Собирает строки для закладки Timing из сущностей с onTiming = true (с фильтром по разрешённым проектам). */
fun buildTimingRows(
    issues: List<Issues>,
    notes: List<Notes>,
    logs: List<ProjectLogs>,
    permittedProjectIds: Set<String>
): List<TimingRow> {
    val list = mutableListOf<TimingRow>()
    issues.filter { it.onTiming && (it.project == null || it.project!!.id in permittedProjectIds) }.forEach { issue ->
        list.add(TimingRow("issue:${issue.id}", issue.name, issue.project, issue, null, null))
    }
    notes.filter { it.onTiming && (it.project == null || it.project!!.id in permittedProjectIds) }.forEach { note ->
        list.add(TimingRow("note:${note.id}", note.content.take(80).ifEmpty { "—" }, note.project, null, note, null))
    }
    logs.filter { it.onTiming && (it.project == null || it.project!!.id in permittedProjectIds) }.forEach { log ->
        list.add(TimingRow("log:${log.id}", log.content.take(80).ifEmpty { "—" }, log.project, null, null, log))
    }
    return list
}
