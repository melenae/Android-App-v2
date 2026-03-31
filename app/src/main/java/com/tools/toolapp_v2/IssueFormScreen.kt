package com.tools.toolapp_v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val commentDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
private val dateUpdateTitleFormat = SimpleDateFormat("yy-MM-dd HH-mm", Locale.getDefault())

/** Невидимый символ: при пустом тексте иначе подпись многострочного OutlinedTextField остаётся внутри рамки. */
private const val OUTLINED_MULTILINE_EMPTY_SENTINEL = "\u200B"

private fun outlinedMultilineDisplayedValue(actual: String): String =
    if (actual.isEmpty()) OUTLINED_MULTILINE_EMPTY_SENTINEL else actual

private fun outlinedMultilineToActual(typed: String): String =
    typed.replace(OUTLINED_MULTILINE_EMPTY_SENTINEL, "")

/** Вертикальный зазор между строками полей формы заявки (было 12.dp). */
private val IssueFormFieldGap = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueFormScreen(
    issue: Issues,
    onDismiss: () -> Unit,
    onSaveIssue: (Issues) -> Unit,
    onUpdateIssue: (Issues) -> Unit = {},
    currentUser: Users,
    allIssues: List<Issues>,
    permittedProjects: List<Projects>,
    allUsers: List<Users>,
    allCompanies: List<Companies>,
    /** Все графики; для поля «График» фильтруются по выбранному проекту. */
    allRoadMaps: List<RoadMap> = emptyList(),
    modifier: Modifier = Modifier
) {
    var projectIdForRoadMaps by remember(issue.id) { mutableStateOf(issue.project?.id) }
    LaunchedEffect(issue.id) { projectIdForRoadMaps = issue.project?.id }
    val roadMapsForForm = remember(projectIdForRoadMaps, allRoadMaps, currentUser.id) {
        allRoadMaps
            .filter { it.project?.id == projectIdForRoadMaps }
            .filter { it.user?.id == currentUser.id }
    }
    var projectDialogOpen by remember { mutableStateOf(false) }
    var nameState by remember(issue.id) { mutableStateOf(issue.name) }
    var contentState by remember(issue.id) { mutableStateOf(issue.content) }
    var statusState by remember(issue.id) { mutableStateOf(issue.status) }
    var onTimingState by remember(issue.id) { mutableStateOf(issue.onTiming) }
    var newCommentForUserState by remember(issue.id) { mutableStateOf(issue.newCommentForUser) }
    var newCommentForApplicantState by remember(issue.id) { mutableStateOf(issue.newCommentForApplicant) }
    var commentText by remember { mutableStateOf("") }
    var statusWhenComment by remember { mutableStateOf(issue.status) }
    var statusCommentDialogOpen by remember { mutableStateOf(false) }
    var commentsVersion by remember { mutableStateOf(0) }
    var roadMapDialogOpen by remember { mutableStateOf(false) }
    var companyDialogOpen by remember { mutableStateOf(false) }
    var statusDialogOpen by remember { mutableStateOf(false) }
    var userDialogOpen by remember { mutableStateOf(false) }
    var applicantDialogOpen by remember { mutableStateOf(false) }
    val initialUserId = remember(issue.id) { issue.user?.id }
    val initialApplicantId = remember(issue.id) { issue.applicant?.id }
    val isNewForm = issue.id.isEmpty()
    val isDirty = isNewForm ||
        nameState != issue.name ||
        contentState != issue.content ||
        statusState != issue.status ||
        onTimingState != issue.onTiming ||
        newCommentForUserState != issue.newCommentForUser ||
        newCommentForApplicantState != issue.newCommentForApplicant ||
        issue.user?.id != initialUserId ||
        issue.applicant?.id != initialApplicantId
    LaunchedEffect(statusState) { statusWhenComment = statusState }
    // При открытии заявки снимаем флаги непрочитанного для текущего пользователя
    LaunchedEffect(Unit) {
        var changed = false
        if (currentUser.id == issue.user?.id && issue.newCommentForUser) {
            issue.newCommentForUser = false
            changed = true
        }
        if (currentUser.id == issue.applicant?.id && issue.newCommentForApplicant) {
            issue.newCommentForApplicant = false
            changed = true
        }
        if (changed) {
            newCommentForUserState = issue.newCommentForUser
            newCommentForApplicantState = issue.newCommentForApplicant
            onUpdateIssue(issue)
        }
    }
    LaunchedEffect(issue.newCommentForUser, issue.newCommentForApplicant) {
        newCommentForUserState = issue.newCommentForUser
        newCommentForApplicantState = issue.newCommentForApplicant
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = when {
                                issue.id.isEmpty() -> "Задача (новая)"
                                else -> {
                                    val idPart = when {
                                        issue.id.startsWith("legacy-") -> issue.id.removePrefix("legacy-").trim().toIntOrNull()?.let { "Задача №$it" } ?: "Задача ID: ${issue.id.take(8)}"
                                        else -> issueNumberInProject(issue, allIssues)?.let { "Задача №$it" } ?: "Задача ID: ${issue.id.take(8)}"
                                    }
                                    val datePart = if (issue.dateUpdate > 0L) " (${dateUpdateTitleFormat.format(Date(issue.dateUpdate))})" else ""
                                    idPart + datePart
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Выйти без сохранения",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    val ctx = LocalContext.current.applicationContext
                    IconButton(onClick = {
                        val updated = issue.copy(
                            name = nameState,
                            content = contentState,
                            status = statusState,
                            dateUpdate = System.currentTimeMillis(),
                            onTiming = onTimingState,
                            newCommentForUser = newCommentForUserState,
                            newCommentForApplicant = newCommentForApplicantState,
                            roadMap = issue.roadMap
                        )
                        Toast.makeText(ctx, "Начинаю синхронизацию", Toast.LENGTH_LONG).show()
                        onSaveIssue(updated)
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

        val leftFields: @Composable () -> Unit = {
            IssueFormLeftFieldsContent(
                issue = issue,
                allCompanies = allCompanies,
                permittedProjects = permittedProjects,
                isNewIssue = issue.id.isEmpty(),
                roadMapsForProject = roadMapsForForm,
                projectDialogOpen = projectDialogOpen,
                onProjectDialogOpenChange = { projectDialogOpen = it },
                onProjectChanged = { p ->
                    issue.project = p
                    issue.company = null
                    issue.roadMap = null
                    projectIdForRoadMaps = p?.id
                },
                includeProjectCompanyRoadMap = !landscapeTwoPanel,
                nameState = nameState,
                onNameChange = { v -> nameState = v; issue.name = v },
                contentState = contentState,
                onContentChange = { v -> contentState = v; issue.content = v },
                statusState = statusState,
                onStatusChange = { s -> statusState = s; issue.status = s },
                allUsers = allUsers,
                roadMapDialogOpen = roadMapDialogOpen,
                onRoadMapDialogOpenChange = { roadMapDialogOpen = it },
                companyDialogOpen = companyDialogOpen,
                onCompanyDialogOpenChange = { companyDialogOpen = it },
                statusDialogOpen = statusDialogOpen,
                onStatusDialogOpenChange = { statusDialogOpen = it },
                userDialogOpen = userDialogOpen,
                onUserDialogOpenChange = { userDialogOpen = it },
                applicantDialogOpen = applicantDialogOpen,
                onApplicantDialogOpenChange = { applicantDialogOpen = it }
            )
        }
        val rightComments: @Composable () -> Unit = {
            IssueFormRightCommentsContent(
                issue = issue,
                currentUser = currentUser,
                commentText = commentText,
                onCommentTextChange = { commentText = it },
                statusWhenComment = statusWhenComment,
                onStatusWhenCommentChange = { statusWhenComment = it },
                statusCommentDialogOpen = statusCommentDialogOpen,
                onStatusCommentDialogOpenChange = { statusCommentDialogOpen = it },
                commentsVersion = commentsVersion,
                onBumpCommentsVersion = { commentsVersion++ },
                statusState = statusState,
                onStatusStateChange = { s -> statusState = s; issue.status = s },
                onUpdateIssue = onUpdateIssue
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
                        .padding(6.dp)
                ) {
                    leftFields()
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
                        .padding(6.dp)
                ) {
                    IssueFormProjectCompanyRoadMapSection(
                        issue = issue,
                        allCompanies = allCompanies,
                        permittedProjects = permittedProjects,
                        isNewIssue = issue.id.isEmpty(),
                        roadMapsForProject = roadMapsForForm,
                        projectDialogOpen = projectDialogOpen,
                        onProjectDialogOpenChange = { projectDialogOpen = it },
                        onProjectChanged = { p ->
                            issue.project = p
                            issue.company = null
                            issue.roadMap = null
                            projectIdForRoadMaps = p?.id
                        },
                        roadMapDialogOpen = roadMapDialogOpen,
                        onRoadMapDialogOpenChange = { roadMapDialogOpen = it },
                        companyDialogOpen = companyDialogOpen,
                        onCompanyDialogOpenChange = { companyDialogOpen = it }
                    )
                    rightComments()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollPortrait)
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp)
            ) {
                leftFields()
                Spacer(modifier = Modifier.height(12.dp))
                rightComments()
            }
        }
    }
}

/** Верхняя часть формы: проект, компания, график (в ландшафте — правая колонка). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueFormProjectCompanyRoadMapSection(
    issue: Issues,
    allCompanies: List<Companies>,
    permittedProjects: List<Projects>,
    isNewIssue: Boolean,
    roadMapsForProject: List<RoadMap>,
    projectDialogOpen: Boolean,
    onProjectDialogOpenChange: (Boolean) -> Unit,
    onProjectChanged: (Projects?) -> Unit,
    roadMapDialogOpen: Boolean,
    onRoadMapDialogOpenChange: (Boolean) -> Unit,
    companyDialogOpen: Boolean,
    onCompanyDialogOpenChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isNewIssue) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onProjectDialogOpenChange(true) }
            ) {
                OutlinedTextField(
                    value = issue.project?.name?.takeIf { it.isNotBlank() } ?: "Не выбрано",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Проект") },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать проект")
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            OutlinedTextField(
                value = issue.project?.name ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Проект") },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCompanyDialogOpenChange(true) }
        ) {
            OutlinedTextField(
                value = issue.company?.name?.takeIf { it.isNotBlank() } ?: "Не выбрано",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Компания") },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать компанию")
                },
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
                            .clickable {
                                onProjectChanged(null)
                                onProjectDialogOpenChange(false)
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
                                    onProjectChanged(project)
                                    onProjectDialogOpenChange(false)
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onProjectDialogOpenChange(false) }) {
                    Text("Отмена")
                }
            }
        )
    }
    if (companyDialogOpen) {
        val companiesForProject = allCompanies
            .asSequence()
            .filter { it.project?.id == issue.project?.id }
            .distinctBy { it.name.trim().lowercase() }
            .sortedBy { it.name.lowercase() }
            .toList()
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
                            .clickable {
                                issue.company = null
                                onCompanyDialogOpenChange(false)
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
                                    issue.company = company
                                    onCompanyDialogOpenChange(false)
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onCompanyDialogOpenChange(false) }) {
                    Text("Отмена")
                }
            }
        )
    }
    Spacer(modifier = Modifier.height(IssueFormFieldGap))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onRoadMapDialogOpenChange(true) }
    ) {
        OutlinedTextField(
            value = issue.roadMap?.name?.take(40)?.plus(if ((issue.roadMap?.name?.length ?: 0) > 40) "…" else "") ?: "Не выбрано",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("График") },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать график")
            },
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
                            .clickable {
                                issue.roadMap = null
                                onRoadMapDialogOpenChange(false)
                            }
                            .padding(vertical = 12.dp)
                    )
                    roadMapsForProject.forEach { rm ->
                        Text(
                            text = rm.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    issue.roadMap = rm
                                    onRoadMapDialogOpenChange(false)
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRoadMapDialogOpenChange(false) }) {
                    Text("Отмена")
                }
            }
        )
    }
    Spacer(modifier = Modifier.height(IssueFormFieldGap))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueFormLeftFieldsContent(
    issue: Issues,
    allCompanies: List<Companies>,
    permittedProjects: List<Projects>,
    isNewIssue: Boolean,
    roadMapsForProject: List<RoadMap>,
    projectDialogOpen: Boolean,
    onProjectDialogOpenChange: (Boolean) -> Unit,
    onProjectChanged: (Projects?) -> Unit,
    /** В ландшафте блок «Проект / Компания / График» переносится в правую колонку — здесь не рисуем. */
    includeProjectCompanyRoadMap: Boolean = true,
    nameState: String,
    onNameChange: (String) -> Unit,
    contentState: String,
    onContentChange: (String) -> Unit,
    statusState: IssueStatuses,
    onStatusChange: (IssueStatuses) -> Unit,
    allUsers: List<Users>,
    roadMapDialogOpen: Boolean,
    onRoadMapDialogOpenChange: (Boolean) -> Unit,
    companyDialogOpen: Boolean,
    onCompanyDialogOpenChange: (Boolean) -> Unit,
    statusDialogOpen: Boolean,
    onStatusDialogOpenChange: (Boolean) -> Unit,
    userDialogOpen: Boolean,
    onUserDialogOpenChange: (Boolean) -> Unit,
    applicantDialogOpen: Boolean,
    onApplicantDialogOpenChange: (Boolean) -> Unit
) {
    if (includeProjectCompanyRoadMap) {
        IssueFormProjectCompanyRoadMapSection(
            issue = issue,
            allCompanies = allCompanies,
            permittedProjects = permittedProjects,
            isNewIssue = isNewIssue,
            roadMapsForProject = roadMapsForProject,
            projectDialogOpen = projectDialogOpen,
            onProjectDialogOpenChange = onProjectDialogOpenChange,
            onProjectChanged = onProjectChanged,
            roadMapDialogOpen = roadMapDialogOpen,
            onRoadMapDialogOpenChange = onRoadMapDialogOpenChange,
            companyDialogOpen = companyDialogOpen,
            onCompanyDialogOpenChange = onCompanyDialogOpenChange
        )
    }

    val nextStatus = IssueStatuses.nextStatus(statusState)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onStatusDialogOpenChange(true) }
        ) {
            OutlinedTextField(
                value = statusState.displayName,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Статус") },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать статус")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (nextStatus != null) {
            IconButton(
                onClick = { onStatusChange(nextStatus) }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Повысить: ${nextStatus.displayName}"
                )
            }
            Text(
                text = nextStatus.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (statusDialogOpen) {
        AlertDialog(
            onDismissRequest = { onStatusDialogOpenChange(false) },
            title = { Text("Выберите статус") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IssueStatuses.entries.forEach { status ->
                        Text(
                            text = status.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStatusChange(status)
                                    onStatusDialogOpenChange(false)
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onStatusDialogOpenChange(false) }) {
                    Text("Отмена")
                }
            }
        )
    }
    Spacer(modifier = Modifier.height(IssueFormFieldGap))

    OutlinedTextField(
        value = nameState,
        onValueChange = { onNameChange(it) },
        label = { Text("Наименование") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
    Spacer(modifier = Modifier.height(IssueFormFieldGap))

    OutlinedTextField(
        value = outlinedMultilineDisplayedValue(contentState),
        onValueChange = { onContentChange(outlinedMultilineToActual(it)) },
        label = { Text("Описание") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
    Spacer(modifier = Modifier.height(IssueFormFieldGap))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onUserDialogOpenChange(true) }
        ) {
            OutlinedTextField(
                value = issue.user?.displayName ?: "Не выбрано",
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
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onApplicantDialogOpenChange(true) }
        ) {
            OutlinedTextField(
                value = issue.applicant?.displayName ?: "Не выбрано",
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
                            .clickable { issue.user = null; onUserDialogOpenChange(false) }
                            .padding(vertical = 12.dp)
                    )
                    allUsers.forEach { user ->
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { issue.user = user; onUserDialogOpenChange(false) }
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
                            .clickable { issue.applicant = null; onApplicantDialogOpenChange(false) }
                            .padding(vertical = 12.dp)
                    )
                    allUsers.forEach { user ->
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { issue.applicant = user; onApplicantDialogOpenChange(false) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueFormRightCommentsContent(
    issue: Issues,
    currentUser: Users,
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    statusWhenComment: IssueStatuses,
    onStatusWhenCommentChange: (IssueStatuses) -> Unit,
    statusCommentDialogOpen: Boolean,
    onStatusCommentDialogOpenChange: (Boolean) -> Unit,
    commentsVersion: Int,
    onBumpCommentsVersion: () -> Unit,
    statusState: IssueStatuses,
    onStatusStateChange: (IssueStatuses) -> Unit,
    onUpdateIssue: (Issues) -> Unit
) {
    val commentsForIssue = remember(commentsVersion) {
        exampleIssueComments
            .filter { it.issue.id == issue.id }
            .sortedByDescending { it.dateCreate }
    }
    commentsForIssue.forEach { comment ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = comment.author?.displayName ?: comment.user?.displayName ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = commentDateFormat.format(Date(comment.dateCreate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = comment.comment,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            comment.statusSet?.let { status ->
                Text(
                    text = "Статус: ${status.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
        value = outlinedMultilineDisplayedValue(commentText),
        onValueChange = { onCommentTextChange(outlinedMultilineToActual(it)) },
        label = { Text("Комментарий") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onStatusCommentDialogOpenChange(true) }
        ) {
            OutlinedTextField(
                value = statusWhenComment.displayName,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Новый статус") },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать статус комментария")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = {
                val text = commentText.trim()
                if (text.isNotEmpty()) {
                    exampleIssueComments.add(
                        IssueComments(
                            generateCommentId(),
                            issue,
                            currentUser,
                            currentUser,
                            text,
                            System.currentTimeMillis(),
                            statusSet = statusWhenComment
                        )
                    )
                    onCommentTextChange("")
                    onBumpCommentsVersion()
                    if (statusWhenComment != statusState) {
                        onStatusStateChange(statusWhenComment)
                    }
                    if (currentUser.id != issue.user?.id) issue.newCommentForUser = true
                    if (currentUser.id != issue.applicant?.id) issue.newCommentForApplicant = true
                    onUpdateIssue(issue)
                }
            }
        ) {
            Text("Добавить комментарий")
        }
    }
    if (statusCommentDialogOpen) {
        AlertDialog(
            onDismissRequest = { onStatusCommentDialogOpenChange(false) },
            title = { Text("Статус при отправке комментария") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IssueStatuses.entries.forEach { status ->
                        Text(
                            text = status.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStatusWhenCommentChange(status)
                                    onStatusCommentDialogOpenChange(false)
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onStatusCommentDialogOpenChange(false) }) {
                    Text("Отмена")
                }
            }
        )
    }
}

