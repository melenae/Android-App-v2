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

private const val CONTENT_MAX_LENGTH = 1000
private val noteDateTitleFormat = SimpleDateFormat("yy-MM-dd HH-mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteFormScreen(
    note: Notes,
    onDismiss: () -> Unit,
    onSaveNote: (Notes) -> Unit,
    currentUser: Users,
    permittedProjects: List<Projects>,
    allUsers: List<Users>,
    allCompanies: List<Companies>,
    allRoadMaps: List<RoadMap> = emptyList(),
    modifier: Modifier = Modifier
) {
    var contentState by remember(note.id) { mutableStateOf((note.content ?: "").take(CONTENT_MAX_LENGTH)) }
    var doneState by remember(note.id) { mutableStateOf(note.done) }
    var onTimingState by remember(note.id) { mutableStateOf(note.onTiming) }
    var projectState by remember(note.id) { mutableStateOf(note.project) }
    var userState by remember(note.id) { mutableStateOf<Users?>(note.user ?: currentUser) }
    var applicantState by remember(note.id) { mutableStateOf<Users?>(note.applicant ?: currentUser) }
    var companyState by remember(note.id) { mutableStateOf(note.company) }
    var roadMapState by remember(note.id) { mutableStateOf(note.roadMap) }
    val roadMapsForProject = remember(projectState) { allRoadMaps.filter { it.project?.id == projectState?.id } }
    val companiesForProject = remember(projectState, allCompanies) {
        if (projectState == null) emptyList()
        else allCompanies.filter { it.project?.id == projectState?.id }
    }
    var projectDialogOpen by remember { mutableStateOf(false) }
    var companyDialogOpen by remember { mutableStateOf(false) }
    var roadMapDialogOpen by remember { mutableStateOf(false) }
    var userDialogOpen by remember { mutableStateOf(false) }
    var applicantDialogOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            note.id.isEmpty() -> "Новая заметка"
                            else -> {
                                val datePart = if (note.dateUpdate > 0L) noteDateTitleFormat.format(Date(note.dateUpdate)) else ""
                                if (datePart.isNotEmpty()) "Заметка №${note.id} ($datePart)" else "Заметка №${note.id}"
                            }
                        },
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
                        onSaveNote(
                            note.copy(
                                content = contentState.take(CONTENT_MAX_LENGTH),
                                done = doneState,
                                dateUpdate = System.currentTimeMillis(),
                                onTiming = onTimingState,
                                project = projectState,
                                user = userState,
                                applicant = applicantState,
                                company = companyState,
                                roadMap = roadMapState
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Верхняя строка: Проект и Компания — как в форме Заявки
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
                        value = projectState?.name ?: "",
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
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { companyDialogOpen = true }
                ) {
                    OutlinedTextField(
                        value = companyState?.name ?: "",
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

            // График (roadMap) — выбор из списка по проекту заметки
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

            // Выполнено
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = doneState, onCheckedChange = { doneState = it })
                Text("Выполнено", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Примечание (текст) — стиль как Описание в Задаче
            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it.take(CONTENT_MAX_LENGTH) },
                label = { Text("Примечание (до $CONTENT_MAX_LENGTH символов)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Ответственный и Постановщик — в одной строке, стиль как в Задаче
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

            // Диалоги выбора
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
                                    .clickable {
                                        projectState = null
                                        companyState = null
                                        projectDialogOpen = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                            permittedProjects.forEach { project ->
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            projectState = project
                                            if (companyState?.project?.id != project.id)
                                                companyState = null
                                            projectDialogOpen = false
                                        }
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
                                    .clickable { companyState = null; companyDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
                            companiesForProject.forEach { company ->
                                Text(
                                    text = company.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { companyState = company; companyDialogOpen = false }
                                        .padding(vertical = 12.dp)
                                )
                            }
                            if (companiesForProject.isEmpty()) {
                                Text("— Нет компаний по проекту", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { companyDialogOpen = false }) { Text("Отмена") }
                    }
                )
            }
            if (userDialogOpen) {
                AlertDialog(
                    onDismissRequest = { userDialogOpen = false },
                    title = { Text("Ответственный") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Не выбрано",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { userState = null; userDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
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
                            Text(
                                text = "Не выбрано",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applicantState = null; applicantDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
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
