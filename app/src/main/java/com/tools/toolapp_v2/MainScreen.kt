package com.tools.toolapp_v2

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

enum class MainTab(val title: String) {
    TASKS("Задачи"),
    STICKERS("Стикеры"),
    CALENDAR("Календарь"),
    ROADMAP("Графики"),
    TIMING("Тайминг"),
    SETTINGS("Настройка")
}

/** Считает сообщение от экспорта ошибкой (не «Данные записаны» / «Данные выгружены»). */
private fun isExportError(msg: String): Boolean =
    !msg.startsWith("Данные записаны") && !msg.startsWith("Данные выгружены")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentUser: Users,
    appData: AppData,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var googleAccessToken by remember {
        mutableStateOf(prefs.getString("google_access_token", null) ?: "")
    }
    var loadIntervalSeconds by remember {
        mutableStateOf(prefs.getInt("load_interval_sec", 30).coerceIn(5, 3600))
    }
    LaunchedEffect(appData.projects, currentUser.id) {
        appData.projects.forEach { p -> p.source = prefs.getString("project_source_${currentUser.id}_${p.id}", "") ?: "" }
    }
    /** URL таблицы для записи: сначала source сущности, затем адрес таблицы проекта из prefs (по пользователю и проекту). */
    fun resolveTableUrlForExport(entitySource: String, project: Projects?, prefs: SharedPreferences): String {
        if (entitySource.isNotBlank()) return entitySource
        val projectSource = project?.let { prefs.getString("project_source_${currentUser.id}_${it.id}", "")?.trim()?.takeIf { s -> s.isNotBlank() } }
        if (!projectSource.isNullOrBlank()) return projectSource
        return prefs.getString("table_address", "")?.trim()?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: ""
    }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.SETTINGS) }
    /** Время следующей регламентной загрузки (мс). После входа в Google выставляем в now — регламентный LaunchedEffect сразу выполнит загрузку. */
    var nextScheduledLoadAt by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var pendingGoogleAccountEmail by remember { mutableStateOf<String?>(null) }
    // Для кнопки «Войти в Google» нужен OAuth 2.0 Client ID (Android) в Google Cloud Console:
    // API & Services → Credentials → Create OAuth client ID → Android (указать package name и SHA-1), включить Google Sheets API.
    val googleSignInClient = remember(activity) {
        activity?.let {
            val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"))
                .build()
            GoogleSignIn.getClient(it, opts)
        }
    }
    /** Сохраняет токен после входа в Google (обычный аккаунт); заменяет любой старый токен (например сервисного аккаунта) в настройках и в prefs. */
    fun saveGoogleToken(token: String) {
        googleAccessToken = token
        prefs.edit().putString("google_access_token", token).apply()
        Toast.makeText(context, "Токен обновлён в настройках (аккаунт Google). Запись и загрузка из Таблицы доступны.", Toast.LENGTH_SHORT).show()
    }
    /** После обновления токена: запускаем загрузку (регламентный LaunchedEffect сработает при nextScheduledLoadAt = now). Остаёмся на Настройке. */
    val onTokenSaved: () -> Unit = { nextScheduledLoadAt = System.currentTimeMillis() }
    val recoverableLauncherRef = remember { mutableStateOf<ActivityResultLauncher<Intent>?>(null) }
    val sheetsScope = "oauth2:https://www.googleapis.com/auth/spreadsheets"
    val logTag = "GoogleSignIn"
    /** Отладочный лог на закладке Настройка — только ответ app_init. */
    var debugLogText by remember { mutableStateOf(AppDebugLog.getText()) }
    fun fetchAndSaveGoogleToken(
        email: String,
        recoverableLauncher: ActivityResultLauncher<Intent>,
        onTokenSaved: () -> Unit = {}
    ) {
        // onTokenSaved вызывается после saveGoogleToken; снаружи передаём переключение на Задачи + сброс таймера загрузки
        val act = activity ?: run {
            Log.e(logTag, "fetchAndSaveGoogleToken: активность недоступна")
            Toast.makeText(context, "Ошибка: активность недоступна", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            val account = try {
                AccountManager.get(context).getAccountsByType("com.google")
                    .firstOrNull { it.name.equals(email, ignoreCase = true) }
                    ?: Account(email, "com.google")
            } catch (_: Exception) {
                Account(email, "com.google")
            }
            val tokenLogLine = "Запрос токена: scope=$sheetsScope, account=${account.name}. Эндпоинт: https://oauth2.googleapis.com/token"
            Log.d(logTag, tokenLogLine)
            try {
                val token = GoogleAuthUtil.getToken(act, account, sheetsScope)
                if (token.isNullOrBlank()) {
                    Log.e(logTag, "Токен пустой от GoogleAuthUtil.getToken")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Токен пустой. Повторите вход в Google.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    saveGoogleToken(token)
                    onTokenSaved()
                }
            } catch (e: UserRecoverableAuthException) {
                Log.w(logTag, "UserRecoverableAuthException: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    pendingGoogleAccountEmail = email
                    e.intent?.let { recoverableLauncher.launch(it) }
                }
            } catch (e: Exception) {
                val errLine = "Ошибка получения токена: ${e.javaClass.simpleName} message=${e.message} | ${e.stackTraceToString().take(400)}"
                Log.e(logTag, "Ошибка получения токена: ${e.javaClass.simpleName} message=${e.message}", e)
                withContext(Dispatchers.Main) {
                    val msg = e.message ?: e.javaClass.simpleName
                    Toast.makeText(context, "Не удалось получить токен: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val recoverableAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingGoogleAccountEmail?.let { email ->
            pendingGoogleAccountEmail = null
            recoverableLauncherRef.value?.let { launcher -> fetchAndSaveGoogleToken(email, launcher, onTokenSaved) }
        }
    }
    LaunchedEffect(recoverableAuthLauncher) {
        recoverableLauncherRef.value = recoverableAuthLauncher
        // При старте приложения: обновляем токен (если есть вход в Google), затем onTokenSaved запустит загрузку
        val act = activity ?: return@LaunchedEffect
        val account = GoogleSignIn.getLastSignedInAccount(act)
        account?.email?.let { email ->
            fetchAndSaveGoogleToken(email, recoverableAuthLauncher, onTokenSaved)
        }
    }
    fun signInErrorToast(e: Exception?, resultCode: Int) {
        val logLine = when (e) {
            is ApiException -> {
                val msg = "Ошибка входа в Google. statusCode=${e.statusCode} (7=NETWORK_ERROR). Адрес: accounts.google.com (экран входа). message=${e.message}. stack: ${e.stackTraceToString().take(500)}"
                Log.e(logTag, msg, e)
                msg
            }
            else -> {
                val msg = "Ошибка входа: resultCode=$resultCode, exception=${e?.javaClass?.simpleName}, message=${e?.message}"
                Log.e(logTag, msg, e ?: Throwable("no exception"))
                msg
            }
        }
        val base = when (e) {
            is ApiException -> when (e.statusCode) {
                7 -> "Ошибка сети при входе в Google. Проверьте интернет (Wi‑Fi или мобильные данные) и попробуйте снова."
                10 -> "Ошибка настройки приложения (код 10). Добавьте SHA-1 и имя пакета в Google Cloud Console → Credentials → OAuth 2.0 Client ID (Android)."
                12501 -> "Вход отменён."
                else -> "Ошибка входа в Google: код ${e.statusCode}. ${e.message?.take(100) ?: ""}"
            }
            else -> e?.message ?: if (resultCode != Activity.RESULT_OK) "Вход отменён или не выполнен." else "Вход не выполнен."
        }
        Toast.makeText(context, base, Toast.LENGTH_LONG).show()
    }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val resLine = "Результат входа: resultCode=${result.resultCode}, data=${result.data != null}"
        Log.d(logTag, resLine)
        if (result.data == null && result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Вход отменён.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { account ->
                val email = account?.email
                if (email.isNullOrBlank()) {
                    signInErrorToast(null, result.resultCode)
                    return@addOnSuccessListener
                }
                Toast.makeText(context, "Получаю токен…", Toast.LENGTH_SHORT).show()
                fetchAndSaveGoogleToken(email, recoverableAuthLauncher, onTokenSaved)
            }
            .addOnFailureListener { e -> signInErrorToast(e, result.resultCode) }
    }
    fun onSignInGoogleForSheets() {
        if (googleSignInClient == null) {
            Log.e(logTag, "onSignInGoogleForSheets: googleSignInClient == null")
            Toast.makeText(context, "Вход в Google недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = googleSignInClient!!.signInIntent
        val launchLine = "Запуск входа в Google. Intent: action=${intent.action}, package=${intent.`package`}. Обращение к accounts.google.com (Play Services)."
        Log.d(logTag, launchLine)
        signInLauncher.launch(intent)
    }
    var loadResultDialogMessage by remember { mutableStateOf<String?>(null) }
    /** Сообщение о чтении таблицы projectLogs при последней загрузке из Google (для Календаря). */
    var lastProjectLogsLoadMessage by remember { mutableStateOf<String?>(null) }
    // Данные только через регламентную загрузку из Google Таблицы.
    var allIssues by remember { mutableStateOf(emptyList<Issues>()) }
    var allNotes by remember { mutableStateOf(exampleNotes.toList()) }
    var allLogs by remember { mutableStateOf(exampleProjectLogs.toList()) }
    var allRoadMaps by remember { mutableStateOf(exampleRoadMaps.toList()) }
    var lastRoadMapsLoadMessage by remember { mutableStateOf<String?>(null) }
    var selectedIssueId by rememberSaveable { mutableStateOf<String?>(null) }
    fun doLoadFromFile(permittedProjectsForLoad: List<Projects>) {
        val fromProjects = permittedProjectsForLoad.mapNotNull { p ->
            p.source.trim().takeIf { s -> s.isNotBlank() && s.contains("docs.google.com/spreadsheets") }
        }.distinct()
        val urls = if (fromProjects.isNotEmpty()) fromProjects else {
            prefs.getString("table_address", "")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?.filter { it.contains("docs.google.com/spreadsheets") } ?: emptyList()
        }
        if (urls.isEmpty()) {
            Toast.makeText(context, "Укажите ссылку на Google Таблицу в настройках проекта (Настройки → Проекты)", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            var errorMsg: String? = null
            var loadedIssueItems: List<IssueLoadItem>? = null
            var loadedComments: List<IssueComments> = emptyList()
            var loadedNoteItems: List<NoteLoadItem>? = null
            var loadedLogItems: List<ProjectLogLoadItem>? = null
            var loadedRoadMaps: List<RoadMap> = emptyList()
            var receivedFormat: String? = null
            var lastProjectLogsStatusMessage: String? = null
            var lastRoadMapsStatusMessage: String? = null
            withContext(Dispatchers.IO) {
                val allLoadedIssueItems = mutableListOf<IssueLoadItem>()
                val allComments = mutableListOf<IssueComments>()
                val allLoadedNoteItems = mutableListOf<NoteLoadItem>()
                val allLoadedLogItems = mutableListOf<ProjectLogLoadItem>()
                val allLoadedRoadMaps = mutableListOf<RoadMap>()
                for (urlStr in urls) {
                    try {
                        receivedFormat = "Google Таблица (лист Issues)"
                        val gResult = loadIssuesFromGoogleSheets(urlStr, appData.users, appData.projects, appData.companies, googleAccessToken, {}, currentUser)
                        if (gResult.error != null) {
                            if (errorMsg == null) errorMsg = gResult.error
                        } else {
                            allLoadedIssueItems.addAll(gResult.issueLoadItems.map { IssueLoadItem(it.issue.copy(source_type = "GoogleTab", source = urlStr), it.roadMapId) })
                            allComments.addAll(gResult.newComments)
                            allLoadedNoteItems.addAll(gResult.noteLoadItems.map { NoteLoadItem(it.note.copy(source_type = "GoogleTab", source = urlStr), it.roadMapId) })
                            allLoadedLogItems.addAll(gResult.projectLogLoadItems.map { ProjectLogLoadItem(it.log.copy(source_type = "GoogleTab", source = urlStr), it.roadMapId) })
                            allLoadedRoadMaps.addAll(gResult.newRoadMaps.map { it.copy(source_type = "GoogleTab", source = urlStr) })
                            gResult.projectLogsStatusMessage?.let { lastProjectLogsStatusMessage = it }
                            gResult.roadMapsStatusMessage?.let { lastRoadMapsStatusMessage = it }
                        }
                    } catch (e: Throwable) {
                        val msg = e.message ?: e.javaClass.simpleName
                        if (errorMsg == null) errorMsg = "Ошибка загрузки: $msg"
                    }
                }
                if (allLoadedIssueItems.isNotEmpty()) {
                    val allIssuesFromLoad = allLoadedIssueItems.map { it.issue }
                    val afterUserFilter = filterLoadedIssuesForCurrentUser(allIssuesFromLoad, currentUser)
                    val keptIssueIds = afterUserFilter.map { it.id }.toSet()
                    loadedIssueItems = allLoadedIssueItems.filter { it.issue.id in keptIssueIds }
                    val afterProjectFilter = filterIssuesByUserAccess(currentUser, loadedIssueItems.map { it.issue }, appData.permittedProjects)
                    loadedComments = allComments.filter { it.issue.id in keptIssueIds }
                    val notesFromCsv = allLoadedNoteItems.map { it.note }
                    val notesAfterUserFilter = filterLoadedNotesForCurrentUser(notesFromCsv, currentUser)
                    val keptNoteIds = notesAfterUserFilter.map { it.id }.toSet()
                    loadedNoteItems = allLoadedNoteItems.filter { it.note.id in keptNoteIds }
                    val logsAfterUserFilter = filterLoadedProjectLogsForCurrentUser(allLoadedLogItems.map { it.log }, currentUser)
                    val keptLogIds = logsAfterUserFilter.map { it.id }.toSet()
                    loadedLogItems = allLoadedLogItems.filter { it.log.id in keptLogIds }
                    loadedRoadMaps = allLoadedRoadMaps
                }
            }
            when {
                errorMsg != null -> {
                    val err = errorMsg!!
                    val isTableOrTokenError = err.contains("не найден", ignoreCase = true) ||
                        err.contains("не удалось", ignoreCase = true) ||
                        err.contains("таблиц", ignoreCase = true) ||
                        err.contains("404") ||
                        err.contains("доступ", ignoreCase = true) ||
                        err.contains("прочитать таблицу", ignoreCase = true)
                    if (isTableOrTokenError) {
                        val act = activity
                        val account = act?.let { GoogleSignIn.getLastSignedInAccount(it) }
                        val email = account?.email
                        val launcher = recoverableLauncherRef.value
                        if (!email.isNullOrBlank() && launcher != null) {
                            Toast.makeText(context, "Обновляю токен и повторяю загрузку…", Toast.LENGTH_SHORT).show()
                            fetchAndSaveGoogleToken(email, launcher, onTokenSaved)
                            return@launch
                        }
                    }
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
                loadedIssueItems != null -> {
                    if (loadedRoadMaps.isNotEmpty()) allRoadMaps = loadedRoadMaps
                    allIssues = loadedIssueItems.map { it.issue.copy(roadMap = allRoadMaps.find { r -> r.id == it.roadMapId }) }
                    val loadedIds = loadedIssueItems.map { it.issue.id }.toSet()
                    exampleIssueComments.removeAll { it.issue.id in loadedIds }
                    exampleIssueComments.addAll(loadedComments)
                    if (loadedNoteItems != null && loadedNoteItems.isNotEmpty()) allNotes = loadedNoteItems.map { it.note.copy(roadMap = allRoadMaps.find { r -> r.id == it.roadMapId }) }
                    if (loadedLogItems != null && loadedLogItems.isNotEmpty()) allLogs = loadedLogItems.map { it.log.copy(roadMap = allRoadMaps.find { r -> r.id == it.roadMapId }) }
                    val msg = buildString {
                        receivedFormat?.let { append("$it.\n") }
                        append("ЗАГРУЖЕНО ЗАЯВОК: ${loadedIssueItems.size}")
                        if (loadedNoteItems != null && loadedNoteItems.isNotEmpty()) append(". Заметок: ${loadedNoteItems.size}")
                        if (loadedLogItems != null && loadedLogItems.isNotEmpty()) append(". Записей журнала: ${loadedLogItems.size}")
                        if (loadedRoadMaps.isNotEmpty()) append(". Графиков: ${allRoadMaps.size}")
                        lastProjectLogsStatusMessage?.let { append("\n").append(it) }
                        lastRoadMapsStatusMessage?.let { append("\n").append(it) }
                    }
                    if (lastProjectLogsStatusMessage != null) lastProjectLogsLoadMessage = lastProjectLogsStatusMessage
                    if (lastRoadMapsStatusMessage != null) lastRoadMapsLoadMessage = lastRoadMapsStatusMessage
                    val showInDialog = loadedIssueItems.isEmpty() && errorMsg == null
                    if (showInDialog) loadResultDialogMessage = msg else Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    var selectedNote by mutableStateOf<Notes?>(null)
    var selectedLog by mutableStateOf<ProjectLogs?>(null)
    var selectedRoadMap by mutableStateOf<RoadMap?>(null)
    var stickerTaskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var stickerProjectFilter by remember { mutableStateOf<Projects?>(null) }
    // Начальная дата календаря — сегодня
    var selectedCalendarDateMillis by remember { mutableStateOf(dayStartMillis(System.currentTimeMillis())) }
    var calendarExpanded by remember { mutableStateOf(true) }
    var calendarTaskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var calendarProjectFilter by remember { mutableStateOf<Projects?>(null) }
    var calendarTypeFilter by remember { mutableStateOf<String?>(null) }
    var allTimeEntries by remember { mutableStateOf(exampleTimeEntries.toList()) }
    val todayAccumulatedHours: MutableMap<String, Double> = remember { mutableStateMapOf<String, Double>() }
    var currentRunningKey by remember { mutableStateOf<String?>(null) }
    var currentTimerStartMillis by remember { mutableStateOf<Long?>(null) }
    var selectedTimingWorkDateMillis by remember { mutableStateOf(dayStartMillis(System.currentTimeMillis())) }
    var showTimerConfirmDialog by remember { mutableStateOf(false) }
    var pendingResumeKey by remember { mutableStateOf<String?>(null) }
    val permittedProjects = remember(currentUser, appData.projects, appData.permittedProjects) {
        if (appData.permittedProjects.isEmpty() && appData.projects.isNotEmpty()) {
            // Сервер вернул проекты пользователя без отдельной таблицы permitted_projects — считаем все возвращённые проекты разрешёнными
            appData.projects
        } else {
            permittedProjectsForUser(currentUser, appData.projects, appData.permittedProjects)
        }
    }
    val selectedIssue: Issues? = remember(selectedIssueId, allIssues, currentUser, permittedProjects) {
        when (selectedIssueId) {
            null -> null
            "" -> Issues("", "", "", IssueStatuses.NEW, currentUser, currentUser, permittedProjects.firstOrNull())
            else -> allIssues.find { it.id == selectedIssueId }
        }
    }
    val visibleIssues = remember(currentUser, allIssues) {
        filterIssuesByUserAccess(currentUser, allIssues, appData.permittedProjects)
    }
    var taskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var selectedProjectFilter by remember { mutableStateOf<Projects?>(null) }
    val filteredByTask = remember(visibleIssues, currentUser, taskFilter) {
        filterIssuesByTaskFilter(visibleIssues, currentUser, taskFilter)
    }
    val filteredIssues = remember(filteredByTask, selectedProjectFilter) {
        if (selectedProjectFilter == null) filteredByTask
        else filteredByTask.filter { it.project?.id == selectedProjectFilter?.id }
    }
    // Регламентная загрузка: при nextScheduledLoadAt (после входа/старта) — сразу; на вкладке Задачи — раз в loadIntervalSeconds сек
    LaunchedEffect(selectedTab, loadIntervalSeconds, nextScheduledLoadAt) {
        val intervalMs = loadIntervalSeconds * 1000L
        val target = nextScheduledLoadAt ?: if (selectedTab == MainTab.TASKS) System.currentTimeMillis() + intervalMs else null
        if (target == null) return@LaunchedEffect
        nextScheduledLoadAt = target
        val delayMs = (target - System.currentTimeMillis()).coerceAtLeast(0L)
        kotlinx.coroutines.delay(delayMs)
        doLoadFromFile(permittedProjects)
        nextScheduledLoadAt = System.currentTimeMillis() + intervalMs
    }
    // Счётчик непрочитанных: при смене visibleIssues/currentUser и по тику раз в 10 с на вкладке Задачи
    var unreadRefreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(selectedTab) {
        if (selectedTab != MainTab.TASKS) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            unreadRefreshTick += 1
        }
    }
    val unreadTasksCount = remember(visibleIssues, currentUser, unreadRefreshTick) {
        visibleIssues.count { issue ->
            (currentUser.id == issue.user?.id && issue.newCommentForUser) ||
                (currentUser.id == issue.applicant?.id && issue.newCommentForApplicant)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (selectedIssue == null && selectedNote == null && selectedLog == null) {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Text(
                            text = if (selectedTab == MainTab.TASKS && unreadTasksCount > 0)
                                "Задачи ($unreadTasksCount)"
                            else
                                selectedTab.title
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* меню / drawer позже */ }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Меню"
                            )
                        }
                    },
                    actions = {
                        if (selectedTab == MainTab.TASKS) {
                            IconButton(onClick = { selectedIssueId = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Новая заявка"
                                )
                            }
                        }
                        if (selectedTab == MainTab.STICKERS) {
                            IconButton(onClick = {
                                selectedNote = Notes(
                                    id = "",
                                    project = permittedProjects.firstOrNull(),
                                    user = currentUser,
                                    applicant = currentUser,
                                    company = null,
                                    done = false,
                                    content = ""
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Новая заметка"
                                )
                            }
                        }
                        if (selectedTab == MainTab.CALENDAR) {
                            IconButton(onClick = {
                                selectedLog = ProjectLogs(
                                    id = "",
                                    project = permittedProjects.firstOrNull(),
                                    user = currentUser,
                                    applicant = currentUser,
                                    content = "",
                                    agenda = "",
                                    resolution = "",
                                    type = "",
                                    date = selectedCalendarDateMillis
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Новая запись журнала"
                                )
                            }
                        }
                        if (selectedTab == MainTab.ROADMAP) {
                            IconButton(onClick = {
                                selectedRoadMap = RoadMap(
                                    id = "",
                                    name = "",
                                    content = "",
                                    step = "",
                                    start = 0L,
                                    end = 0L,
                                    user = currentUser,
                                    project = permittedProjects.firstOrNull()
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Новый элемент графика"
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            val icon = when (tab) {
                                MainTab.TASKS -> Icons.Default.Assignment
                                MainTab.STICKERS -> Icons.Default.StickyNote2
                                MainTab.CALENDAR -> Icons.Default.CalendarMonth
                                MainTab.ROADMAP -> Icons.Default.Map
                                MainTab.TIMING -> Icons.Default.Timer
                                MainTab.SETTINGS -> Icons.Default.Settings
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = if (tab == MainTab.TASKS && unreadTasksCount > 0)
                                    "Задачи ($unreadTasksCount)"
                                else
                                    tab.title
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.TASKS -> {
                    if (selectedIssue != null) {
                        IssueFormScreen(
                            issue = selectedIssue!!,
                            onDismiss = { selectedIssueId = null },
                            onSaveIssue = { updated ->
                                val isNew = updated.id.isEmpty()
                                val tempId = if (isNew) generateIssueId() else updated.id
                                val newList = if (isNew) {
                                    allIssues + updated.copy(id = tempId)
                                } else {
                                    allIssues.map { if (it.id == updated.id) updated else it }
                                }
                                val issueToExport = updated.copy(id = tempId)
                                allIssues = newList
                                val tableUrl = resolveTableUrlForExport(issueToExport.source, issueToExport.project, prefs)
                                val notesForTable = if (issueToExport.project != null)
                                    allNotes.filter { it.project?.id == issueToExport.project?.id }
                                else allNotes
                                val ctx = context.applicationContext
                                val tokenToUse = googleAccessToken
                                scope.launch(Dispatchers.IO) {
                                    val result = exportBackToTableOnSave(
                                        issueToExport, newList, tableUrl, tokenToUse,
                                        getCommentsForIssue = { issue -> exampleIssueComments.filter { it.issue.id == issue.id } },
                                        allNotes = notesForTable
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (result.newIssueId != null) {
                                            allIssues = allIssues.map { if (it.id == tempId) it.copy(id = result.newIssueId!!) else it }
                                        }
                                        if (isExportError(result.message)) {
                                            val issueToMarkError = if (result.newIssueId != null) allIssues.find { it.id == result.newIssueId } else allIssues.find { it.id == tempId } ?: issueToExport
                                            if (issueToMarkError != null) {
                                                val withError = issueToMarkError.copy(
                                                    content = (issueToMarkError.content ?: "").trimEnd() + "\n\n--- Ошибка записи в таблицу ---\n" + result.message
                                                )
                                                allIssues = allIssues.map { if (it.id == issueToMarkError.id) withError else it }
                                            }
                                        }
                                        selectedIssueId = null
                                        doLoadFromFile(permittedProjects)
                                        nextScheduledLoadAt = System.currentTimeMillis() + loadIntervalSeconds * 1000L
                                        Toast.makeText(ctx, "Закончил обновление", Toast.LENGTH_LONG).show()
                                        Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onUpdateIssue = { updated ->
                                allIssues = allIssues.map { if (it.id == updated.id) updated else it }
                            },
                            currentUser = currentUser,
                            allIssues = allIssues,
                            permittedProjects = permittedProjects,
                            allUsers = appData.users,
                            allCompanies = appData.companies,
                            roadMapsForProject = allRoadMaps.filter { it.project?.id == selectedIssue?.project?.id },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TaskAndProjectFilterRow(
                                taskFilter = taskFilter,
                                selectedProject = selectedProjectFilter,
                                permittedProjects = permittedProjects,
                                onTaskFilterChange = { taskFilter = it },
                                onProjectChange = { selectedProjectFilter = it }
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                TasksScreen(
                                    issues = filteredIssues,
                                    currentUser = currentUser,
                                    companies = appData.companies,
                                    onIssueClick = { issue -> selectedIssueId = issue.id },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                MainTab.STICKERS -> {
                    if (selectedNote != null) {
                        NoteFormScreen(
                            note = selectedNote!!,
                            onDismiss = { selectedNote = null },
                            onSaveNote = { updated ->
                                val newList = if (updated.id.isEmpty()) {
                                    allNotes + updated.copy(id = (allNotes.size + 1).toString())
                                } else {
                                    allNotes.map { if (it.id == updated.id) updated else it }
                                }
                                allNotes = newList
                                val tableUrl = resolveTableUrlForExport(updated.source, updated.project, prefs)
                                val notesForTable = if (updated.project != null)
                                    newList.filter { it.project?.id == updated.project?.id }
                                else newList
                                val issuesForTable = if (updated.project != null)
                                    allIssues.filter { it.project?.id == updated.project?.id }
                                else allIssues
                                val ctx = context.applicationContext
                                scope.launch(Dispatchers.IO) {
                                    val msg = exportNotesToTableOnSave(
                                        notesForTable, issuesForTable, tableUrl, googleAccessToken,
                                        getCommentsForIssue = { issue -> exampleIssueComments.filter { it.issue.id == issue.id } },
                                        updatedNote = updated
                                    )
                                    withContext(Dispatchers.Main) {
                                        selectedNote = null
                                        doLoadFromFile(permittedProjects)
                                        nextScheduledLoadAt = System.currentTimeMillis() + loadIntervalSeconds * 1000L
                                        Toast.makeText(ctx, "Закончил обновление", Toast.LENGTH_SHORT).show()
                                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            currentUser = currentUser,
                            permittedProjects = permittedProjects,
                            allUsers = appData.users,
                            allCompanies = appData.companies,
                            allRoadMaps = allRoadMaps,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val visibleNotes = remember(currentUser, allNotes) {
                            filterNotesByUserAccess(currentUser, allNotes, appData.permittedProjects)
                        }
                        val notesByTask = remember(visibleNotes, currentUser, stickerTaskFilter) {
                            filterNotesByTaskFilter(visibleNotes, currentUser, stickerTaskFilter)
                        }
                        val filteredNotes = remember(notesByTask, stickerProjectFilter) {
                            if (stickerProjectFilter == null) notesByTask
                            else notesByTask.filter { it.project?.id == stickerProjectFilter?.id }
                        }
                        StickersScreen(
                            notes = filteredNotes,
                            taskFilter = stickerTaskFilter,
                            selectedProject = stickerProjectFilter,
                            permittedProjects = permittedProjects,
                            currentUser = currentUser,
                            onTaskFilterChange = { stickerTaskFilter = it },
                            onProjectChange = { stickerProjectFilter = it },
                            onNoteClick = { selectedNote = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                MainTab.CALENDAR -> {
                    if (selectedLog != null) {
                        val visibleLogsForTypes = remember(currentUser, allLogs) {
                            filterProjectLogsByUserAccess(currentUser, allLogs, appData.permittedProjects)
                        }
                        val suggestedTypes = remember(visibleLogsForTypes) {
                            visibleLogsForTypes.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
                        }
                        ProjectLogFormScreen(
                            log = selectedLog!!,
                            onDismiss = { selectedLog = null },
                            onSaveLog = { updated ->
                                val newList = if (updated.id.isEmpty()) {
                                    allLogs + updated.copy(id = generateCommentId())
                                } else {
                                    allLogs.map { if (it.id == updated.id) updated else it }
                                }
                                allLogs = newList
                                val logToSave = if (updated.id.isEmpty()) newList.last() else updated
                                val tableUrl = resolveTableUrlForExport(logToSave.source, logToSave.project, prefs)
                                val ctx = context.applicationContext
                                scope.launch(Dispatchers.IO) {
                                    val spreadsheetId = extractGoogleSpreadsheetId(tableUrl.trim())
                                    val ok = spreadsheetId != null && googleAccessToken.isNotBlank() &&
                                        writeSingleProjectLogToGoogleSheet(spreadsheetId!!, googleAccessToken, logToSave)
                                    withContext(Dispatchers.Main) {
                                        selectedLog = null
                                        Toast.makeText(ctx, if (ok) "Запись журнала сохранена в Google Таблицу." else "Не удалось записать в таблицу (проверьте токен и ссылку проекта).", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            currentUser = currentUser,
                            permittedProjects = permittedProjects,
                            suggestedTypes = suggestedTypes,
                            allUsers = appData.users,
                            allCompanies = appData.companies,
                            allRoadMaps = allRoadMaps,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val visibleLogs = remember(currentUser, allLogs) {
                            filterProjectLogsByUserAccess(currentUser, allLogs, appData.permittedProjects)
                        }
                        val calendarTypes = remember(visibleLogs) {
                            visibleLogs.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
                        }
                        val logsByTask = remember(visibleLogs, currentUser, calendarTaskFilter) {
                            filterProjectLogsByTaskFilter(visibleLogs, currentUser, calendarTaskFilter)
                        }
                        val logsByProject = remember(logsByTask, calendarProjectFilter) {
                            if (calendarProjectFilter == null) logsByTask
                            else logsByTask.filter { it.project?.id == calendarProjectFilter?.id }
                        }
                        val logsByType = remember(logsByProject, calendarTypeFilter) {
                            if (calendarTypeFilter == null) logsByProject
                            else logsByProject.filter { it.type == calendarTypeFilter }
                        }
                        val filteredCalendarLogs = remember(logsByType, selectedCalendarDateMillis) {
                            filterProjectLogsByDate(logsByType, selectedCalendarDateMillis)
                        }
                        CalendarScreen(
                            logs = filteredCalendarLogs,
                            allLogsForHighlight = logsByType,
                            types = calendarTypes,
                            taskFilter = calendarTaskFilter,
                            selectedProject = calendarProjectFilter,
                            selectedType = calendarTypeFilter,
                            permittedProjects = permittedProjects,
                            currentUser = currentUser,
                            selectedDateMillis = selectedCalendarDateMillis,
                            onDateSelect = { selectedCalendarDateMillis = it },
                            onTaskFilterChange = { calendarTaskFilter = it },
                            onProjectChange = { calendarProjectFilter = it },
                            onTypeChange = { calendarTypeFilter = it },
                            calendarExpanded = calendarExpanded,
                            onCalendarExpandedChange = { calendarExpanded = it },
                            onLogClick = { selectedLog = it },
                            projectLogsLoadStatusMessage = lastProjectLogsLoadMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                MainTab.ROADMAP -> {
                    if (selectedRoadMap != null) {
                        val visibleRoadMapsForSteps = remember(currentUser, allRoadMaps) {
                            filterRoadMapsByUserAccess(currentUser, allRoadMaps, appData.permittedProjects)
                        }
                        val suggestedSteps = remember(visibleRoadMapsForSteps) {
                            (defaultRoadMapSteps + visibleRoadMapsForSteps.map { it.step }.filter { it.isNotBlank() }).distinct().sorted()
                        }
                        RoadMapFormScreen(
                            roadMap = selectedRoadMap!!,
                            onDismiss = { selectedRoadMap = null },
                            onSaveRoadMap = { updated ->
                                val isNew = updated.id.isEmpty()
                                val newId = if (isNew) (allRoadMaps.map { it.id.toIntOrNull() ?: 0 }.maxOrNull() ?: 0) + 1 else null
                                val idToUse = if (isNew && newId != null) newId.toString() else updated.id
                                val toSave = if (isNew && newId != null) updated.copy(id = idToUse) else updated
                                val newList = if (isNew) allRoadMaps + toSave else allRoadMaps.map { if (it.id == updated.id) toSave else it }
                                allRoadMaps = newList
                                val tableUrl = resolveTableUrlForExport(toSave.source, toSave.project, prefs)
                                val ctx = context.applicationContext
                                scope.launch(Dispatchers.IO) {
                                    val spreadsheetId = extractGoogleSpreadsheetId(tableUrl.trim())
                                    val ok = spreadsheetId != null && googleAccessToken.isNotBlank() &&
                                        writeSingleRoadMapToGoogleSheet(spreadsheetId!!, googleAccessToken, toSave)
                                    withContext(Dispatchers.Main) {
                                        selectedRoadMap = null
                                        Toast.makeText(ctx, if (ok) "Элемент графика сохранён в Google Таблицу." else "Не удалось записать в таблицу.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            currentUser = currentUser,
                            permittedProjects = permittedProjects,
                            suggestedSteps = suggestedSteps,
                            allUsers = appData.users,
                            allCompanies = appData.companies,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val visibleRoadMaps = remember(currentUser, allRoadMaps) {
                            filterRoadMapsByUserAccess(currentUser, allRoadMaps, appData.permittedProjects)
                        }
                        RoadMapScreen(
                            roadMaps = visibleRoadMaps,
                            loadStatusMessage = lastRoadMapsLoadMessage,
                            onItemClick = { selectedRoadMap = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                MainTab.TIMING -> {
                    // Каждые timerNotification минут останавливаем таймер и показываем блокирующее подтверждение
                    LaunchedEffect(selectedTab, currentRunningKey, currentUser.timerNotification) {
                        if (selectedTab != MainTab.TIMING) return@LaunchedEffect
                        val key = currentRunningKey ?: return@LaunchedEffect
                        val intervalMinutes = currentUser.timerNotification
                        if (intervalMinutes <= 0) return@LaunchedEffect
                        delay(intervalMinutes * 60 * 1000L)
                        if (currentRunningKey != key || selectedTab != MainTab.TIMING) return@LaunchedEffect
                        val startMillis = currentTimerStartMillis
                        if (startMillis != null) {
                            val elapsed = (System.currentTimeMillis() - startMillis) / 3600_000.0
                            todayAccumulatedHours[key] = (todayAccumulatedHours[key] ?: 0.0) + elapsed
                        }
                        currentRunningKey = null
                        currentTimerStartMillis = null
                        pendingResumeKey = key
                        showTimerConfirmDialog = true
                    }
                    val visibleIssuesForTiming = remember(currentUser, allIssues) {
                        filterIssuesByUserAccess(currentUser, allIssues, appData.permittedProjects)
                    }
                    val visibleNotesForTiming = remember(currentUser, allNotes) {
                        filterNotesByUserAccess(currentUser, allNotes, appData.permittedProjects)
                    }
                    val visibleLogsForTiming = remember(currentUser, allLogs) {
                        filterProjectLogsByUserAccess(currentUser, allLogs, appData.permittedProjects)
                    }
                    val permittedProjectIds: Set<String> = remember(permittedProjects) {
                        permittedProjects.map { it.id }.toSet()
                    }
                    val timingRowsList = remember(visibleIssuesForTiming, visibleNotesForTiming, visibleLogsForTiming, permittedProjectIds) {
                        buildTimingRows(visibleIssuesForTiming, visibleNotesForTiming, visibleLogsForTiming, permittedProjectIds)
                    }
                    val myTimeEntries = remember(allTimeEntries, currentUser) {
                        allTimeEntries.filter { it.user?.id == currentUser.id }
                    }
                    val todayStart = dayStartMillis(System.currentTimeMillis())
                    val totalTodayFromEntries = remember(allTimeEntries, currentUser, todayStart) {
                        allTimeEntries
                            .filter { it.user?.id == currentUser.id && it.createdOn >= todayStart }
                            .sumOf { it.hours }
                    }
                    TimingScreen(
                        timingRows = timingRowsList,
                        todayAccumulatedHours = todayAccumulatedHours,
                        currentRunningKey = currentRunningKey,
                        currentTimerStartMillis = currentTimerStartMillis,
                        onStartTimer = { key ->
                            val runningKey = currentRunningKey
                            val startMillis = currentTimerStartMillis
                            if (runningKey != null && startMillis != null) {
                                val elapsed = (System.currentTimeMillis() - startMillis) / 3600_000.0
                                todayAccumulatedHours[runningKey] = (todayAccumulatedHours[runningKey] ?: 0.0) + elapsed
                            }
                            currentRunningKey = key
                            currentTimerStartMillis = System.currentTimeMillis()
                        },
                        onPauseTimer = {
                            val runningKey = currentRunningKey
                            val startMillis = currentTimerStartMillis
                            if (runningKey != null && startMillis != null) {
                                val elapsed = (System.currentTimeMillis() - startMillis) / 3600_000.0
                                todayAccumulatedHours[runningKey] = (todayAccumulatedHours[runningKey] ?: 0.0) + elapsed
                            }
                            currentRunningKey = null
                            currentTimerStartMillis = null
                        },
                        timeEntries = myTimeEntries,
                        currentUser = currentUser,
                        totalTodayFromEntries = totalTodayFromEntries,
                        selectedWorkDateMillis = selectedTimingWorkDateMillis,
                        onWorkDateChange = { selectedTimingWorkDateMillis = it },
                        onFinishDay = { workDateDayStartMillis ->
                            val runningKey = currentRunningKey
                            val startMillis = currentTimerStartMillis
                            if (runningKey != null && startMillis != null) {
                                val elapsed = (System.currentTimeMillis() - startMillis) / 3600_000.0
                                todayAccumulatedHours[runningKey] = (todayAccumulatedHours[runningKey] ?: 0.0) + elapsed
                            }
                            currentRunningKey = null
                            currentTimerStartMillis = null
                            val now = System.currentTimeMillis()
                            var entriesList = allTimeEntries
                            timingRowsList.forEach { row ->
                                val hours = todayAccumulatedHours[row.key] ?: 0.0
                                if (hours > 0) {
                                    val entry = TimeEntries(
                                        id = generateTimeEntryId(),
                                        issue = row.issue,
                                        note = row.note,
                                        projectLog = row.log,
                                        roadMap = row.issue?.roadMap ?: row.note?.roadMap ?: row.log?.roadMap,
                                        user = currentUser,
                                        hours = roundTimeEntryHours(hours),
                                        createdOn = workDateDayStartMillis,
                                        updatedOn = now,
                                        project = row.project,
                                        comment = ""
                                    )
                                    entriesList = entriesList + entry
                                    exampleTimeEntries.add(entry)
                                }
                            }
                            allTimeEntries = entriesList
                            todayAccumulatedHours.clear()
                            // Выгрузка тайминга по проектам: таблица для каждого проекта из настроек (project_source_${userId}_${project.id} или адрес по умолчанию)
                            val entriesByTableUrl = entriesList.groupBy { resolveTableUrlForExport("", it.project, prefs) }
                            val ctx = context.applicationContext
                            val token = googleAccessToken
                            scope.launch(Dispatchers.IO) {
                                val results = mutableListOf<Pair<String, Int>>()
                                var hasError = false
                                for ((tableUrl, entries) in entriesByTableUrl) {
                                    if (entries.isEmpty()) continue
                                    val spreadsheetId = extractGoogleSpreadsheetId(tableUrl)
                                    if (spreadsheetId != null && token.isNotBlank()) {
                                        val ok = writeTimeEntriesToGoogleSheet(spreadsheetId, token, entries)
                                        if (ok) {
                                            val projectNames = entries.mapNotNull { it.project?.name }.distinct().joinToString(", ").ifEmpty { "—" }
                                            results.add(projectNames to entries.size)
                                        } else hasError = true
                                    } else hasError = true
                                }
                                withContext(Dispatchers.Main) {
                                    val msg = when {
                                        hasError && results.isEmpty() -> "Не удалось записать тайминг. Проверьте ссылку на таблицу и токен Google."
                                        hasError -> "Тайминг частично записан: ${results.joinToString("; ") { "${it.first} (${it.second})" }}. Часть таблиц недоступна."
                                        results.isEmpty() -> "Нет записей для выгрузки."
                                        else -> "Тайминг записан на лист «timeEntries»: " + results.joinToString("; ") { "${it.first}: ${it.second} записей" }
                                    }
                                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MainTab.SETTINGS -> SettingsScreen(
                        currentUser = currentUser,
                        permittedProjects = permittedProjects,
                        googleAccessToken = googleAccessToken,
                        onGoogleAccessTokenChange = { t ->
                            googleAccessToken = t
                            prefs.edit().putString("google_access_token", t).apply()
                        },
                        onSignInGoogleForSheets = { onSignInGoogleForSheets() },
                        loadIntervalSeconds = loadIntervalSeconds,
                        onLoadIntervalChange = { sec ->
                            loadIntervalSeconds = sec
                            prefs.edit().putInt("load_interval_sec", sec).apply()
                        },
                        debugLogText = debugLogText,
                        onClearDebugLog = { AppDebugLog.clear(); debugLogText = "" },
                        allUsers = appData.users,
                        allAccounts = appData.accounts,
                        allCompanies = appData.companies,
                        onLogout = onLogout,
                        modifier = Modifier.fillMaxSize()
                    )
            }
            loadResultDialogMessage?.let { msg ->
                AlertDialog(
                    onDismissRequest = { loadResultDialogMessage = null },
                    title = { Text("Результат загрузки") },
                    text = {
                        Column(
                            Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(msg)
                        }
                    },
                    confirmButton = {
                        Button(onClick = { loadResultDialogMessage = null }) { Text("ОК") }
                    }
                )
            }
            if (showTimerConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Подтверждение") },
                    text = { Text("Подтвердите, что вы на месте. Таймер был приостановлен и возобновится после нажатия ОК.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showTimerConfirmDialog = false
                                val key = pendingResumeKey
                                pendingResumeKey = null
                                if (key != null) {
                                    currentRunningKey = key
                                    currentTimerStartMillis = System.currentTimeMillis()
                                }
                            }
                        ) {
                            Text("ОК")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
