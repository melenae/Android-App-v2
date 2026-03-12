package com.tools.toolapp_v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val commentDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
private val dateUpdateTitleFormat = SimpleDateFormat("yy-MM-dd HH-mm", Locale.getDefault())

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
    /** Элементы графика для выбора; фильтруются по project заявки на стороне вызывающего. */
    roadMapsForProject: List<RoadMap> = emptyList(),
    modifier: Modifier = Modifier
) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Строка 1: Проект, Компания, Тайминг (иконка часов)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                OutlinedTextField(
                    value = allCompanies
                        .firstOrNull { it.project?.id == issue.project?.id }
                        ?.name ?: "",
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
            Spacer(modifier = Modifier.height(12.dp))

            // График (roadMap) — выбор из списка по проекту заявки
            var roadMapDialogOpen by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { roadMapDialogOpen = true }
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
                    onDismissRequest = { roadMapDialogOpen = false },
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
                                        roadMapDialogOpen = false
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
                                            roadMapDialogOpen = false
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { roadMapDialogOpen = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Строка 2: Статус (ручной выбор) + иконка повышения на 1 пункт
            var statusDialogOpen by remember { mutableStateOf(false) }
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
                        ) { statusDialogOpen = true }
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
                        onClick = {
                            statusState = nextStatus
                            issue.status = nextStatus
                        }
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
                    onDismissRequest = { statusDialogOpen = false },
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
                                            statusState = status
                                            issue.status = status
                                            statusDialogOpen = false
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { statusDialogOpen = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = nameState,
                onValueChange = { nameState = it; issue.name = it },
                label = { Text("Наименование") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = contentState,
                onValueChange = { contentState = it; issue.content = it },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Ответственный и Постановщик — в одной строке, ширина по экрану
            var userDialogOpen by remember { mutableStateOf(false) }
            var applicantDialogOpen by remember { mutableStateOf(false) }
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
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { applicantDialogOpen = true }
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
                    onDismissRequest = { userDialogOpen = false },
                    title = { Text("Ответственный") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Не выбрано",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { issue.user = null; userDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
                            allUsers.forEach { user ->
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { issue.user = user; userDialogOpen = false }
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
                                    .clickable { issue.applicant = null; applicantDialogOpen = false }
                                    .padding(vertical = 12.dp)
                            )
                            allUsers.forEach { user ->
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { issue.applicant = user; applicantDialogOpen = false }
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
            Spacer(modifier = Modifier.height(24.dp))

            // Комментарии к заявке (от новых к старым)
            Text(
                text = "Комментарии",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
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

            // Новый комментарий + возможность изменить статус при отправке
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
                        ) { statusCommentDialogOpen = true }
                ) {
                    OutlinedTextField(
                        value = "Новый Статус: ${statusWhenComment.displayName}",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            commentText = ""
                            commentsVersion++
                            if (statusWhenComment != statusState) {
                                statusState = statusWhenComment
                                issue.status = statusWhenComment
                            }
                            // Поднять флаги непрочитанного для ответственного и постановщика (кроме автора)
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
                    onDismissRequest = { statusCommentDialogOpen = false },
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
                                            statusWhenComment = status
                                            statusCommentDialogOpen = false
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { statusCommentDialogOpen = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    }
}
