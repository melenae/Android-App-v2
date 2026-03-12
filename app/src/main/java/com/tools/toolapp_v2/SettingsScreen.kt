package com.tools.toolapp_v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private sealed class AccountTreeItem {
    data class Account(val account: Accounts) : AccountTreeItem()
    data class Project(val project: Projects) : AccountTreeItem()
}

private enum class SettingsDestination {
    MENU,
    USERS,
    PROJECTS,
    ACCOUNTS,
    COMPANIES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: Users,
    permittedProjects: List<Projects>,
    googleAccessToken: String,
    onGoogleAccessTokenChange: (String) -> Unit,
    onSignInGoogleForSheets: () -> Unit,
    /** Интервал регламентной загрузки (секунды). */
    loadIntervalSeconds: Int = 30,
    onLoadIntervalChange: (Int) -> Unit = {},
    /** Текст отладочного лога (добавляется из загрузки и др.); показывается в большом поле, т.к. алерты обрезаются. */
    debugLogText: String = "",
    onClearDebugLog: () -> Unit = {},
    /** Список пользователей для раздела «Пользователи» (ФИО и email для идентификации при загрузке). */
    allUsers: List<Users>,
    /** Счета (для раздела «Счета» в настройках). */
    allAccounts: List<Accounts>,
    /** Компании (для раздела «Компании» в настройках). */
    allCompanies: List<Companies>,
    /** Разлогирование: сброс сессии и переход на экран входа. */
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Фильтрация доступа из CmAccessManagement: проекты/счета по manager, компании по permitted_projects
    val visibleProjects = remember(currentUser, permittedProjects) {
        projectsVisibleInSettings(currentUser, permittedProjects)
    }
    val visibleAccounts = remember(currentUser, allAccounts) {
        accountsVisibleInSettings(currentUser, allAccounts)
    }
    val permittedProjectIds: Set<String> = remember(permittedProjects) {
        permittedProjects.map { it.id }.toSet()
    }
    val visibleCompanies = remember(permittedProjectIds, allCompanies) {
        companiesVisibleInSettings(permittedProjectIds, allCompanies)
    }

    var destination by remember { mutableStateOf(SettingsDestination.MENU) }
    var selectedProject by remember { mutableStateOf<Projects?>(null) }
    var selectedAccount by remember { mutableStateOf<Accounts?>(null) }
    var selectedCompany by remember { mutableStateOf<Companies?>(null) }

    when (destination) {
        SettingsDestination.MENU -> {
            val menuScrollState = rememberScrollState()
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(menuScrollState)
            ) {
                Text(
                    text = "Настройка",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Текущий пользователь",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "ФИО (name): ${currentUser.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Email (логин): ${currentUser.login}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "id (для загрузки из Excel): ${userDisplayId(currentUser.displayName, currentUser.email.takeIf { it.isNotBlank() } ?: currentUser.login)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text("Выйти из аккаунта")
                }

                Text(
                    text = "Токен доступа Google (для записи в Таблицу)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                OutlinedTextField(
                    value = googleAccessToken,
                    onValueChange = onGoogleAccessTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text(
                            "Необязательно. Можно ввести вручную или нажать «Войти в Google» — токен сохранится автоматически.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
                Button(
                    onClick = onSignInGoogleForSheets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Войти в Google для записи в Таблицу")
                }
                Text(
                    text = "Интервал регламентной загрузки (сек)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                OutlinedTextField(
                    value = loadIntervalSeconds.toString(),
                    onValueChange = { s ->
                        val v = s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 3600) ?: 30
                        onLoadIntervalChange(v)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            "Периодическая загрузка заявок с интервалом (5–3600 сек). После сохранения заявки загрузка выполняется сразу, следующий запуск — через указанный интервал.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Отладочный лог",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClearDebugLog) {
                        Text("Очистить")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = debugLogText.ifBlank { "Тут лог" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Навигация: Пользователи (ФИО и email для загрузки из Excel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { destination = SettingsDestination.USERS }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "users",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Пользователи (${allUsers.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Навигация: Проекты (только где currentUser = manager)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { destination = SettingsDestination.PROJECTS }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "projects",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Проекты (${visibleProjects.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Навигация: Счета (только где currentUser = manager)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { destination = SettingsDestination.ACCOUNTS }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "accounts",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Счета (${visibleAccounts.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Навигация: Компании (только у которых project в permitted_projects)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { destination = SettingsDestination.COMPANIES }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "company",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Компании (${visibleCompanies.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }
            }
        }
        SettingsDestination.USERS -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Пользователи") },
                        navigationIcon = {
                            IconButton(onClick = { destination = SettingsDestination.MENU }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allUsers) { user ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "ФИО (name): ${user.displayName}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Email (логин): ${user.login}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "id (для загрузки из Excel): ${userDisplayId(user.displayName, user.email.takeIf { it.isNotBlank() } ?: user.login)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        SettingsDestination.PROJECTS -> {
            if (selectedProject != null) {
                ProjectCardScreen(
                    project = selectedProject!!,
                    currentUser = currentUser,
                    permittedProjects = permittedProjects,
                    allUsers = allUsers,
                    allAccounts = allAccounts,
                    onDismiss = { selectedProject = null },
                    modifier = modifier.fillMaxSize()
                )
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Проекты") },
                            navigationIcon = {
                                IconButton(onClick = { destination = SettingsDestination.MENU }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleProjects) { project ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProject = project }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "GUID: ${project.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        SettingsDestination.ACCOUNTS -> {
            if (selectedAccount != null) {
                AccountCardScreen(
                    allUsers = allUsers,
                    account = selectedAccount!!,
                    onDismiss = { selectedAccount = null },
                    modifier = modifier.fillMaxSize()
                )
            } else if (selectedProject != null) {
                ProjectCardScreen(
                    project = selectedProject!!,
                    currentUser = currentUser,
                    permittedProjects = permittedProjects,
                    allUsers = allUsers,
                    allAccounts = allAccounts,
                    onDismiss = { selectedProject = null },
                    modifier = modifier.fillMaxSize()
                )
            } else {
                var expandedAccountIds by remember { mutableStateOf(setOf<String>()) }
                val treeItems = remember(visibleAccounts, permittedProjects, expandedAccountIds) {
                    visibleAccounts.flatMap { account ->
                        val projects = permittedProjects.filter { it.account?.id == account.id }
                        val isExpanded = account.id in expandedAccountIds
                        listOf(AccountTreeItem.Account(account)) + if (isExpanded) projects.map { AccountTreeItem.Project(it) } else emptyList()
                    }
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Счета") },
                            navigationIcon = {
                                IconButton(onClick = { destination = SettingsDestination.MENU }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(treeItems.size) { index ->
                            when (val row = treeItems[index]) {
                                is AccountTreeItem.Account -> {
                                    val account = row.account
                                    val isExpanded = account.id in expandedAccountIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAccount = account },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                expandedAccountIds = if (isExpanded) {
                                                    expandedAccountIds - account.id
                                                } else {
                                                    expandedAccountIds + account.id
                                                }
                                            },
                                            modifier = Modifier.padding(start = 0.dp, end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Свернуть" else "Развернуть"
                                            )
                                        }
                                        Text(
                                            text = account.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                is AccountTreeItem.Project -> {
                                    val project = row.project
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedProject = project }
                                            .padding(start = 48.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SettingsDestination.COMPANIES -> {
            if (selectedCompany != null) {
                CompanyCardScreen(
                    company = selectedCompany!!,
                    permittedProjects = permittedProjects,
                    allUsers = allUsers,
                    onDismiss = { selectedCompany = null },
                    modifier = modifier.fillMaxSize()
                )
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Компании") },
                            navigationIcon = {
                                IconButton(onClick = { destination = SettingsDestination.MENU }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleCompanies) { company ->
                            Text(
                                text = company.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCompany = company }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
