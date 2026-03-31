package com.tools.toolapp_v2

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
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
    val roadMapsForProject = remember(projectState, currentUser.id) {
        allRoadMaps
            .filter { it.project?.id == projectState?.id }
            .filter { it.user?.id == currentUser.id }
    }
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
        val scrollPortrait = rememberScrollState()
        val scrollLeft = rememberScrollState()
        val scrollRight = rememberScrollState()
        val landscapeTwoPanel =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        val leftBlock: @Composable () -> Unit = {
            NoteFormStickerLeftColumn(
                projectState = projectState,
                companyState = companyState,
                roadMapState = roadMapState,
                doneState = doneState,
                onDoneChange = { doneState = it },
                roadMapsForProject = roadMapsForProject,
                permittedProjects = permittedProjects,
                companiesForProject = companiesForProject,
                projectDialogOpen = projectDialogOpen,
                onProjectDialogOpenChange = { projectDialogOpen = it },
                companyDialogOpen = companyDialogOpen,
                onCompanyDialogOpenChange = { companyDialogOpen = it },
                roadMapDialogOpen = roadMapDialogOpen,
                onRoadMapDialogOpenChange = { roadMapDialogOpen = it },
                onProjectClear = {
                    projectState = null
                    companyState = null
                    projectDialogOpen = false
                },
                onProjectPick = { project ->
                    projectState = project
                    if (companyState?.project?.id != project.id) companyState = null
                    projectDialogOpen = false
                },
                onCompanyClear = {
                    companyState = null
                    companyDialogOpen = false
                },
                onCompanyPick = { company ->
                    companyState = company
                    companyDialogOpen = false
                },
                onRoadMapClear = {
                    roadMapState = null
                    roadMapDialogOpen = false
                },
                onRoadMapPick = { rm ->
                    roadMapState = rm
                    roadMapDialogOpen = false
                }
            )
        }
        val rightBlock: @Composable () -> Unit = {
            NoteFormStickerRightColumn(
                contentState = contentState,
                onContentChange = { contentState = it.take(CONTENT_MAX_LENGTH) },
                userState = userState,
                applicantState = applicantState,
                userDialogOpen = userDialogOpen,
                onUserDialogOpenChange = { userDialogOpen = it },
                applicantDialogOpen = applicantDialogOpen,
                onApplicantDialogOpenChange = { applicantDialogOpen = it },
                allUsers = allUsers,
                onUserClear = {
                    userState = null
                    userDialogOpen = false
                },
                onUserPick = { user ->
                    userState = user
                    userDialogOpen = false
                },
                onApplicantClear = {
                    applicantState = null
                    applicantDialogOpen = false
                },
                onApplicantPick = { user ->
                    applicantState = user
                    applicantDialogOpen = false
                }
            )
        }

        if (landscapeTwoPanel) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollLeft)
                        .padding(8.dp)
                ) {
                    leftBlock()
                }
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollRight)
                        .padding(8.dp)
                ) {
                    rightBlock()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollPortrait)
                    .padding(16.dp)
            ) {
                leftBlock()
                Spacer(modifier = Modifier.height(24.dp))
                rightBlock()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteFormStickerLeftColumn(
    projectState: Projects?,
    companyState: Companies?,
    roadMapState: RoadMap?,
    doneState: Boolean,
    onDoneChange: (Boolean) -> Unit,
    roadMapsForProject: List<RoadMap>,
    permittedProjects: List<Projects>,
    companiesForProject: List<Companies>,
    projectDialogOpen: Boolean,
    onProjectDialogOpenChange: (Boolean) -> Unit,
    companyDialogOpen: Boolean,
    onCompanyDialogOpenChange: (Boolean) -> Unit,
    roadMapDialogOpen: Boolean,
    onRoadMapDialogOpenChange: (Boolean) -> Unit,
    onProjectClear: () -> Unit,
    onProjectPick: (Projects) -> Unit,
    onCompanyClear: () -> Unit,
    onCompanyPick: (Companies) -> Unit,
    onRoadMapClear: () -> Unit,
    onRoadMapPick: (RoadMap) -> Unit
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
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onProjectDialogOpenChange(true)
                }
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
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onCompanyDialogOpenChange(true)
                }
        ) {
            OutlinedTextField(
                value = companyState?.name?.takeIf { it.isNotBlank() } ?: "Не выбрано",
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onRoadMapDialogOpenChange(true)
            }
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
            onDismissRequest = { onRoadMapDialogOpenChange(false) },
            title = { Text("График") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Не выбрано",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRoadMapClear() }
                            .padding(vertical = 12.dp)
                    )
                    roadMapsForProject.forEach { rm ->
                        Text(
                            text = rm.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRoadMapPick(rm) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRoadMapDialogOpenChange(false) }) { Text("Отмена") }
            }
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = doneState, onCheckedChange = onDoneChange)
        Text("Выполнено", style = MaterialTheme.typography.bodyLarge)
    }

    if (projectDialogOpen) {
        AlertDialog(
            onDismissRequest = { onProjectDialogOpenChange(false) },
            title = { Text("Проект") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Не выбрано",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProjectClear() }
                            .padding(vertical = 12.dp)
                    )
                    permittedProjects.forEach { project ->
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProjectPick(project) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onProjectDialogOpenChange(false) }) { Text("Отмена") }
            }
        )
    }
    if (companyDialogOpen) {
        AlertDialog(
            onDismissRequest = { onCompanyDialogOpenChange(false) },
            title = { Text("Компания") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Не выбрано",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCompanyClear() }
                            .padding(vertical = 12.dp)
                    )
                    companiesForProject.forEach { company ->
                        Text(
                            text = company.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCompanyPick(company) }
                                .padding(vertical = 12.dp)
                        )
                    }
                    if (companiesForProject.isEmpty()) {
                        Text(
                            "— Нет компаний по проекту",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onCompanyDialogOpenChange(false) }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteFormStickerRightColumn(
    contentState: String,
    onContentChange: (String) -> Unit,
    userState: Users?,
    applicantState: Users?,
    userDialogOpen: Boolean,
    onUserDialogOpenChange: (Boolean) -> Unit,
    applicantDialogOpen: Boolean,
    onApplicantDialogOpenChange: (Boolean) -> Unit,
    allUsers: List<Users>,
    onUserClear: () -> Unit,
    onUserPick: (Users) -> Unit,
    onApplicantClear: () -> Unit,
    onApplicantPick: (Users) -> Unit
) {
    OutlinedTextField(
        value = contentState,
        onValueChange = onContentChange,
        label = { Text("Примечание (до $CONTENT_MAX_LENGTH символов)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onUserDialogOpenChange(true)
                }
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
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onApplicantDialogOpenChange(true)
                }
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
            onDismissRequest = { onUserDialogOpenChange(false) },
            title = { Text("Ответственный") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Не выбрано",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserClear() }
                            .padding(vertical = 12.dp)
                    )
                    allUsers.forEach { user ->
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUserPick(user) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onUserDialogOpenChange(false) }) { Text("Отмена") }
            }
        )
    }
    if (applicantDialogOpen) {
        AlertDialog(
            onDismissRequest = { onApplicantDialogOpenChange(false) },
            title = { Text("Постановщик") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Не выбрано",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplicantClear() }
                            .padding(vertical = 12.dp)
                    )
                    allUsers.forEach { user ->
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onApplicantPick(user) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onApplicantDialogOpenChange(false) }) { Text("Отмена") }
            }
        )
    }
}
