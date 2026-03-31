package com.tools.toolapp_v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val logDateTitleFormat = SimpleDateFormat("yy-MM-dd HH-mm", Locale.getDefault())

private const val CONTENT_MAX_LENGTH = 1000
private const val TYPE_MAX_LENGTH = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectLogFormScreen(
    log: ProjectLogs,
    onDismiss: () -> Unit,
    onSaveLog: (ProjectLogs) -> Unit,
    currentUser: Users,
    permittedProjects: List<Projects>,
    suggestedTypes: List<String>,
    allUsers: List<Users>,
    allCompanies: List<Companies>,
    allRoadMaps: List<RoadMap> = emptyList(),
    modifier: Modifier = Modifier
) {
    var contentState by remember(log.id) { mutableStateOf(log.content.take(CONTENT_MAX_LENGTH)) }
    var agendaState by remember(log.id) { mutableStateOf(log.agenda.take(CONTENT_MAX_LENGTH)) }
    var resolutionState by remember(log.id) { mutableStateOf(log.resolution.take(CONTENT_MAX_LENGTH)) }
    var typeState by remember(log.id) { mutableStateOf(log.type.take(TYPE_MAX_LENGTH)) }
    var dateState by remember(log.id) { mutableStateOf(log.date) }
    var dateTimeStrState by remember(log.id) { mutableStateOf(formatLogDate(log.date)) }
    var projectState by remember(log.id) { mutableStateOf(log.project) }
    var companyState by remember(log.id) { mutableStateOf(log.company) }
    var userState by remember(log.id) { mutableStateOf(log.user ?: currentUser) }
    var applicantState by remember(log.id) { mutableStateOf(log.applicant ?: currentUser) }
    var roadMapState by remember(log.id) { mutableStateOf(log.roadMap) }
    var onTimingState by remember(log.id) { mutableStateOf(log.onTiming) }
    val roadMapsForProject = remember(projectState, currentUser.id) {
        allRoadMaps
            .filter { it.project?.id == projectState?.id }
            .filter { it.user?.id == currentUser.id }
    }
    val companiesForProject = remember(projectState, allCompanies) {
        if (projectState == null) emptyList()
        else allCompanies.filter { it.project?.id == projectState?.id }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (log.id.isEmpty()) "Событие (новая запись)"
                        else "Событие №${log.id.take(12)}${if (log.id.length > 12) "…" else ""} (${logDateTitleFormat.format(Date(log.date))})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Выйти без сохранения",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = {
                        val parsedDate = parseLogDateFromField(dateTimeStrState)
                        onSaveLog(
                            log.copy(
                                content = contentState.take(CONTENT_MAX_LENGTH),
                                agenda = agendaState.take(CONTENT_MAX_LENGTH),
                                resolution = resolutionState.take(CONTENT_MAX_LENGTH),
                                type = typeState.take(TYPE_MAX_LENGTH),
                                date = parsedDate ?: dateState,
                                project = projectState,
                                user = userState,
                                applicant = applicantState,
                                roadMap = roadMapState,
                                company = companyState,
                                onTiming = onTimingState
                            )
                        )
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Сохранить и выйти (экспорт в таблицу)"
                        )
                    }
                    Row(
                        modifier = Modifier.clickable { onTimingState = !onTimingState },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Тайминг",
                            tint = if (onTimingState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Checkbox(
                            checked = onTimingState,
                            onCheckedChange = { onTimingState = it }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        var projectDialogOpen by remember { mutableStateOf(false) }
        var typeDialogOpen by remember { mutableStateOf(false) }
        var roadMapDialogOpen by remember { mutableStateOf(false) }
        var companyDialogOpen by remember { mutableStateOf(false) }
        var userDialogOpen by remember { mutableStateOf(false) }
        var applicantDialogOpen by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Строка 1: Проект, Компания (как в Issue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { projectDialogOpen = true }
                ) {
                    OutlinedTextField(
                        value = projectState?.name ?: "Не выбрано",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Проект") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать проект") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            companyDialogOpen = true
                        }
                ) {
                    OutlinedTextField(
                        value = companyState?.name ?: "Не выбрано",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Компания") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать компанию") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // График (roadMap) — выбор из списка по проекту
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { roadMapDialogOpen = true }
            ) {
                OutlinedTextField(
                    value = roadMapState?.name?.take(40)?.plus(if ((roadMapState?.name?.length ?: 0) > 40) "…" else "") ?: "Не выбрано",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("График") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать график") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (roadMapDialogOpen) {
                AlertDialog(
                    onDismissRequest = { roadMapDialogOpen = false },
                    title = { Text("График") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Не выбрано",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { roadMapState = null; roadMapDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
                            roadMapsForProject.forEach { rm ->
                                Text(
                                    text = rm.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { roadMapState = rm; roadMapDialogOpen = false }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { roadMapDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (companyDialogOpen) {
                AlertDialog(
                    onDismissRequest = { companyDialogOpen = false },
                    title = { Text("Компания") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Не выбрано",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        companyState = null
                                        companyDialogOpen = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                            companiesForProject.forEach { company ->
                                Text(
                                    text = company.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            companyState = company
                                            companyDialogOpen = false
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { companyDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
            if (projectDialogOpen) {
                AlertDialog(
                    onDismissRequest = { projectDialogOpen = false },
                    title = { Text("Проект") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Не выбрано",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { projectState = null; projectDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
                            permittedProjects.forEach { project ->
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { projectState = project; projectDialogOpen = false }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { projectDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Строка 2: Тип и Дата/время
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { typeDialogOpen = true }
                ) {
                    OutlinedTextField(
                        value = typeState.ifBlank { "Не выбрано" },
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Тип (до $TYPE_MAX_LENGTH)") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать тип") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = dateTimeStrState,
                    onValueChange = { dateTimeStrState = it },
                    label = { Text("дд.мм.гггг чч:мм") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
            if (typeDialogOpen) {
                AlertDialog(
                    onDismissRequest = { typeDialogOpen = false },
                    title = { Text("Тип") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "(оставить пустым)",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { typeState = ""; typeDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
                            suggestedTypes.forEach { type ->
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { typeState = type.take(TYPE_MAX_LENGTH); typeDialogOpen = false }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { typeDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Строка 3: Содержание — 2 строки с возможностью скролла
            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it.take(CONTENT_MAX_LENGTH) },
                label = { Text("Содержание (до $CONTENT_MAX_LENGTH)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Повестка — 4 строки с возможностью скролла
            OutlinedTextField(
                value = agendaState,
                onValueChange = { agendaState = it.take(CONTENT_MAX_LENGTH) },
                label = { Text("Повестка (до $CONTENT_MAX_LENGTH)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Резолюция — 4 строки с возможностью скролла
            OutlinedTextField(
                value = resolutionState,
                onValueChange = { resolutionState = it.take(CONTENT_MAX_LENGTH) },
                label = { Text("Резолюция (до $CONTENT_MAX_LENGTH)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Ответственный и Постановщик — в одной строке, выбор из списка (как в Issue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { userDialogOpen = true }
                ) {
                    OutlinedTextField(
                        value = userState?.displayName ?: "Не выбрано",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Ответственный") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { applicantDialogOpen = true }
                ) {
                    OutlinedTextField(
                        value = applicantState?.displayName ?: "Не выбрано",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Постановщик") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (userDialogOpen) {
                AlertDialog(
                    onDismissRequest = { userDialogOpen = false },
                    title = { Text("Ответственный") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allUsers.forEach { user ->
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { userState = user; userDialogOpen = false }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { userDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
            if (applicantDialogOpen) {
                AlertDialog(
                    onDismissRequest = { applicantDialogOpen = false },
                    title = { Text("Постановщик") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allUsers.forEach { user ->
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applicantState = user; applicantDialogOpen = false }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { applicantDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
        }
    }
}

private fun parseLogDateFromField(s: String?): Long? {
    val v = s?.trim() ?: return null
    if (v.isEmpty()) return null
    val formats = listOf("dd.MM.yyyy HH:mm", "d.M.yyyy H:mm", "d.M.yyyy HH:mm", "dd.MM.yyyy H:mm")
    for (pattern in formats) {
        try {
            SimpleDateFormat(pattern, Locale.getDefault()).parse(v)?.time?.let { return it }
        } catch (_: Exception) { }
    }
    return null
}

private fun formatLogDate(millis: Long): String {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = millis
    val d = c.get(java.util.Calendar.DAY_OF_MONTH)
    val m = c.get(java.util.Calendar.MONTH) + 1
    val y = c.get(java.util.Calendar.YEAR)
    val h = c.get(java.util.Calendar.HOUR_OF_DAY)
    val min = c.get(java.util.Calendar.MINUTE)
    return "%02d.%02d.%d %02d:%02d".format(d, m, y, h, min)
}
