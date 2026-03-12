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

private val roadMapDateTitleFormat = SimpleDateFormat("yy-MM-dd", Locale.getDefault())
private val roadMapDateFieldFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

private const val NAME_MAX_LENGTH = 500
private const val CONTENT_MAX_LENGTH = 2000

val defaultRoadMapSteps = listOf("Завершено", "в работе", "ожидает начало")

private fun formatRoadMapDate(millis: Long): String {
    if (millis <= 0L) return ""
    return roadMapDateFieldFormat.format(Date(millis))
}

private fun parseRoadMapDateFromField(s: String?): Long {
    val v = s?.trim() ?: return 0L
    if (v.isEmpty()) return 0L
    val formats = listOf("dd.MM.yyyy", "d.M.yyyy", "yyyy-MM-dd", "yy-MM-dd")
    for (pattern in formats) {
        try {
            SimpleDateFormat(pattern, Locale.getDefault()).parse(v)?.time?.let { return it }
        } catch (_: Exception) { }
    }
    return 0L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadMapFormScreen(
    roadMap: RoadMap,
    onDismiss: () -> Unit,
    onSaveRoadMap: (RoadMap) -> Unit,
    currentUser: Users,
    permittedProjects: List<Projects>,
    suggestedSteps: List<String>,
    allUsers: List<Users>,
    allCompanies: List<Companies>,
    modifier: Modifier = Modifier
) {
    var nameState by remember(roadMap.id) { mutableStateOf(roadMap.name.take(NAME_MAX_LENGTH)) }
    var contentState by remember(roadMap.id) { mutableStateOf(roadMap.content.take(CONTENT_MAX_LENGTH)) }
    var stepState by remember(roadMap.id) { mutableStateOf(roadMap.step) }
    var startState by remember(roadMap.id) { mutableStateOf(roadMap.start) }
    var endState by remember(roadMap.id) { mutableStateOf(roadMap.end) }
    var startStrState by remember(roadMap.id) { mutableStateOf(formatRoadMapDate(roadMap.start)) }
    var endStrState by remember(roadMap.id) { mutableStateOf(formatRoadMapDate(roadMap.end)) }
    var projectState by remember(roadMap.id) { mutableStateOf(roadMap.project) }
    var userState by remember(roadMap.id) { mutableStateOf(roadMap.user ?: currentUser) }
    var onTimingState by remember(roadMap.id) { mutableStateOf(roadMap.onTiming) }

    var projectDialogOpen by remember { mutableStateOf(false) }
    var stepDialogOpen by remember { mutableStateOf(false) }
    var userDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (roadMap.id.isEmpty()) "График (новая запись)"
                        else "График №${roadMap.id} (${roadMapDateTitleFormat.format(Date(roadMap.dateUpdate.coerceAtLeast(roadMap.start)))})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Выйти без сохранения", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = {
                        val start = parseRoadMapDateFromField(startStrState).takeIf { it > 0L } ?: startState
                        val end = parseRoadMapDateFromField(endStrState).takeIf { it > 0L } ?: endState
                        onSaveRoadMap(
                            roadMap.copy(
                                name = nameState.take(NAME_MAX_LENGTH),
                                content = contentState.take(CONTENT_MAX_LENGTH),
                                step = stepState,
                                start = start,
                                end = end,
                                project = projectState,
                                user = userState,
                                dateUpdate = System.currentTimeMillis(),
                                onTiming = onTimingState
                            )
                        )
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Сохранить и выйти")
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
                OutlinedTextField(
                    value = allCompanies.firstOrNull { it.project?.id == projectState?.id }?.name ?: "—",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Компания") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            if (projectDialogOpen) {
                AlertDialog(
                    onDismissRequest = { projectDialogOpen = false },
                    title = { Text("Проект") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Не выбрано", style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { projectState = null; projectDialogOpen = false }.padding(vertical = 12.dp))
                            permittedProjects.forEach { project ->
                                Text(project.name, style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().clickable { projectState = project; projectDialogOpen = false }.padding(vertical = 12.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { projectDialogOpen = false }) { Text("Отмена") } }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = nameState,
                onValueChange = { nameState = it.take(NAME_MAX_LENGTH) },
                label = { Text("Название (до $NAME_MAX_LENGTH)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it.take(CONTENT_MAX_LENGTH) },
                label = { Text("Описание (до $CONTENT_MAX_LENGTH)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { stepDialogOpen = true }
            ) {
                OutlinedTextField(
                    value = stepState.ifBlank { "Не выбрано" },
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Этап (step)") },
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
            if (stepDialogOpen) {
                AlertDialog(
                    onDismissRequest = { stepDialogOpen = false },
                    title = { Text("Этап") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            suggestedSteps.forEach { step ->
                                Text(step, style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().clickable { stepState = step; stepDialogOpen = false }.padding(vertical = 12.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { stepDialogOpen = false }) { Text("Отмена") } }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startStrState,
                    onValueChange = { startStrState = it },
                    label = { Text("Начало (дд.мм.гггг)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = endStrState,
                    onValueChange = { endStrState = it },
                    label = { Text("Окончание (дд.мм.гггг)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
            if (userDialogOpen) {
                AlertDialog(
                    onDismissRequest = { userDialogOpen = false },
                    title = { Text("Ответственный") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allUsers.forEach { user ->
                                Text(user.displayName, style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().clickable { userState = user; userDialogOpen = false }.padding(vertical = 12.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { userDialogOpen = false }) { Text("Отмена") } }
                )
            }
        }
    }
}
