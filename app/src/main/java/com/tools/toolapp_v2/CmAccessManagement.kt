package com.tools.toolapp_v2

import java.util.Locale

/**
 * Управление правами доступа пользователей к данным приложения.
 * Весь код, относящийся к проверке и фильтрации доступа, размещается здесь.
 */

/** Нормализация URL Google Таблицы для сравнения с [Projects.source] и полем source у загруженных сущностей. */
private fun normalizeGoogleSheetUrl(url: String): String =
    url.trim().lowercase(Locale.ROOT).removeSuffix("/").replace("http://", "https://")

/**
 * Id проектов, к которым относится строка из таблицы: явно из поля [project] или все проекты с тем же source (URL), что у сущности после загрузки.
 * Нужно, чтобы РП видел строки с пустой/битой колонкой «Проект», но загруженные из своей таблицы.
 */
fun projectIdsForLoadedSheetRow(project: Projects?, source: String, allProjects: List<Projects>): Set<String> {
    val explicit = project?.id?.trim()?.takeIf { it.isNotEmpty() }
    if (explicit != null) return setOf(explicit)
    val norm = normalizeGoogleSheetUrl(source)
    if (norm.isEmpty()) return emptySet()
    return allProjects
        .mapNotNull { p -> if (normalizeGoogleSheetUrl(p.source) == norm) p.id else null }
        .toSet()
}

/** Id проектов, где у пользователя в app_init роль руководителя (PROJECT_MEMBER_ROLE_PM) — полные данные из Google по проекту. */
fun projectIdsWhereUserIsPm(projects: List<Projects>): Set<String> =
    projects.asSequence()
        .filter { it.isMemberRolePm() }
        .map { it.id }
        .toSet()

/**
 * Проверяет, разрешён ли пользователю доступ к проекту.
 * admin — доступ ко всем проектам.
 */
fun isProjectPermittedForUser(
    user: Users,
    project: Projects,
    permittedProjects: List<PermittedProjects>
): Boolean {
    if (user.role == UserRole.ADMIN) return true
    return permittedProjects.any { it.user.id == user.id && it.project.id == project.id }
}

/**
 * Возвращает список проектов, к которым у пользователя есть доступ.
 * admin видит все проекты, regular_user — только из permitted_projects.
 */
fun permittedProjectsForUser(
    user: Users,
    allProjects: List<Projects>,
    permittedProjects: List<PermittedProjects>
): List<Projects> {
    if (user.role == UserRole.ADMIN) return allProjects
    val permittedIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return allProjects.filter { it.id in permittedIds }
}

/**
 * Фильтрует заявки: оставляет только те, по проектам которых у пользователя есть доступ.
 * admin видит все заявки, regular_user — только по своим проектам и без проекта.
 */
fun filterIssuesByUserAccess(
    user: Users,
    issues: List<Issues>,
    permittedProjects: List<PermittedProjects>,
    pmProjectIds: Set<String> = emptySet()
): List<Issues> {
    if (user.role == UserRole.ADMIN) return issues
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return issues.filter { issue ->
        val pid = issue.project?.id
        pid != null && pid in pmProjectIds ||
            issue.project == null || pid in permittedProjectIds ||
            (issue.source_type == "GoogleTab" && (issue.user?.id == user.id || issue.applicant?.id == user.id))
    }
}

/**
 * Проверяет, может ли пользователь видеть/редактировать данную заявку.
 * admin — доступ ко всем; regular_user — по проекту из permitted_projects.
 */
fun canUserAccessIssue(
    user: Users,
    issue: Issues,
    permittedProjects: List<PermittedProjects>
): Boolean {
    if (user.role == UserRole.ADMIN) return true
    if (issue.project == null) return true
    return isProjectPermittedForUser(user, issue.project!!, permittedProjects)
}

// --- Доступ в разделе «Настройка» (списки проектов, счетов, компаний) ---

/**
 * Проекты, доступные пользователю в настройках: все разрешённые проекты (permitted_projects).
 * Раньше показывались только те, где user = manager; при пустом manager у проектов из app_init список был пуст.
 */
fun projectsVisibleInSettings(
    user: Users,
    permittedProjects: List<Projects>
): List<Projects> =
    permittedProjects

/**
 * Счета, доступные пользователю в настройках: только те, где текущий пользователь = manager.
 */
fun accountsVisibleInSettings(
    user: Users,
    accounts: List<Accounts>
): List<Accounts> =
    accounts.filter { it.manager?.id == user.id }

/**
 * Компании, доступные пользователю в настройках: только те, у которых проект
 * входит в список разрешённых проектов пользователя (permitted_projects).
 */
fun companiesVisibleInSettings(
    permittedProjectIds: Set<String>,
    companies: List<Companies>
): List<Companies> =
    companies.filter { it.project != null && it.project!!.id in permittedProjectIds }

// --- Фильтр задач на вкладке «Задачи» (поверх фильтра по project) ---

/** Варианты фильтра: Мои (user = я), От меня (applicant = я), Все (user или applicant = я). */
enum class TaskFilter {
    /** Ответственный (user) = текущий пользователь */
    MY,
    /** Постановщик (applicant) = текущий пользователь */
    FROM_ME,
    /** user = текущий пользователь ИЛИ applicant = текущий пользователь */
    ALL;

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<TaskFilter, String>(
            save = { it.name },
            restore = { value -> entries.find { it.name == value } ?: ALL }
        )
    }
}

/** Отбор по полю User у строк графика на вкладке «Графики» (перед фильтром по проекту). */
enum class RoadMapUserScopeFilter {
    /** User = текущий пользователь */
    FOR_ME,
    /** Все строки, уже отфильтрованные по правам доступа */
    ALL;

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<RoadMapUserScopeFilter, String>(
            save = { it.name },
            restore = { value -> entries.find { it.name == value } ?: ALL }
        )
    }
}

fun filterRoadMapsByUserScope(
    roadMaps: List<RoadMap>,
    currentUser: Users,
    scope: RoadMapUserScopeFilter
): List<RoadMap> = when (scope) {
    RoadMapUserScopeFilter.ALL -> roadMaps
    RoadMapUserScopeFilter.FOR_ME -> roadMaps.filter { it.user?.id == currentUser.id }
}

/**
 * Фильтр при загрузке заявок из файла/таблицы: в память попадают только заявки,
 * у которых status не DONE и не CLOSED, и текущий пользователь — исполнитель (user) или постановщик (applicant).
 * Таким образом в приложении не оказываются «чужие» задачи и уже закрытые.
 */
fun filterLoadedIssuesForCurrentUser(
    issues: List<Issues>,
    currentUser: Users,
    pmProjectIds: Set<String> = emptySet(),
    allProjects: List<Projects> = emptyList()
): List<Issues> =
    issues.filter { issue ->
        issue.status != IssueStatuses.DONE && issue.status != IssueStatuses.CLOSED &&
            (issue.user?.id == currentUser.id || issue.applicant?.id == currentUser.id ||
                projectIdsForLoadedSheetRow(issue.project, issue.source, allProjects).any { it in pmProjectIds })
    }

/**
 * Фильтрует заявки по выбранному фильтру задач.
 * Применяется после фильтра по project (permitted_projects).
 * «Все» — все заявки, видимые по проекту (в т.ч. загруженные из файлов).
 */
fun filterIssuesByTaskFilter(
    issues: List<Issues>,
    currentUser: Users,
    filter: TaskFilter
): List<Issues> = when (filter) {
    TaskFilter.MY -> issues.filter { it.user?.id == currentUser.id }
    TaskFilter.FROM_ME -> issues.filter { it.applicant?.id == currentUser.id }
    TaskFilter.ALL -> issues
}

// --- Стикеры (Notes): видимость по PermittedProjects и фильтры ---

/**
 * Фильтр при загрузке заметок из файла/таблицы: в память попадают только те,
 * где текущий пользователь — ответственный (user) или постановщик (applicant).
 */
fun filterLoadedNotesForCurrentUser(
    notes: List<Notes>,
    currentUser: Users,
    pmProjectIds: Set<String> = emptySet(),
    allProjects: List<Projects> = emptyList()
): List<Notes> =
    notes.filter { note ->
        note.user?.id == currentUser.id || note.applicant?.id == currentUser.id ||
            projectIdsForLoadedSheetRow(note.project, note.source, allProjects).any { it in pmProjectIds }
    }

/** Стикеры видимы по разрешённым проектам; загруженные из Google — если user или applicant = текущий пользователь (как у заявок). */
fun filterNotesByUserAccess(
    user: Users,
    notes: List<Notes>,
    permittedProjects: List<PermittedProjects>,
    pmProjectIds: Set<String> = emptySet()
): List<Notes> {
    if (user.role == UserRole.ADMIN) return notes
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return notes.filter { note ->
        val pid = note.project?.id
        pid != null && pid in pmProjectIds ||
            note.project == null || pid in permittedProjectIds ||
            (note.source_type == "GoogleTab" && (note.user?.id == user.id || note.applicant?.id == user.id))
    }
}

fun filterNotesByTaskFilter(
    notes: List<Notes>,
    currentUser: Users,
    filter: TaskFilter
): List<Notes> = when (filter) {
    TaskFilter.MY -> notes.filter { it.user?.id == currentUser.id }
    TaskFilter.FROM_ME -> notes.filter { it.applicant?.id == currentUser.id }
    TaskFilter.ALL -> notes
}

/** Фильтр по выполнению: Все / Выполнено / Не выполнено */
enum class DoneFilter { ALL, DONE, NOT_DONE }

fun filterNotesByDone(notes: List<Notes>, filter: DoneFilter): List<Notes> = when (filter) {
    DoneFilter.ALL -> notes
    DoneFilter.DONE -> notes.filter { it.done }
    DoneFilter.NOT_DONE -> notes.filter { !it.done }
}

// --- Вкладка «Графики»: активные задачи и стикеры текущего пользователя по элементу roadMap ---

/**
 * Задачи по строке графика для текущего пользователя: привязка к [roadMapId], ответственный или постановщик = [currentUser],
 * статус не [IssueStatuses.DONE] и не [IssueStatuses.CLOSED].
 */
fun activeIssuesForCurrentUserOnRoadMap(
    roadMapId: String,
    issues: List<Issues>,
    currentUser: Users,
    pmProjectIds: Set<String> = emptySet(),
    allProjects: List<Projects> = emptyList()
): List<Issues> =
    issues
        .filter { issue ->
            issue.roadMap?.id == roadMapId &&
                issue.status != IssueStatuses.DONE &&
                issue.status != IssueStatuses.CLOSED &&
                (issue.user?.id == currentUser.id || issue.applicant?.id == currentUser.id ||
                    projectIdsForLoadedSheetRow(issue.project, issue.source, allProjects).any { it in pmProjectIds })
        }
        .sortedBy { it.name.lowercase() }

/**
 * Стикеры по строке графика для текущего пользователя: привязка к [roadMapId], user/applicant = [currentUser], не выполнены.
 */
fun activeNotesForCurrentUserOnRoadMap(
    roadMapId: String,
    notes: List<Notes>,
    currentUser: Users,
    pmProjectIds: Set<String> = emptySet(),
    allProjects: List<Projects> = emptyList()
): List<Notes> =
    notes
        .filter { note ->
            note.roadMap?.id == roadMapId &&
                !note.done &&
                (note.user?.id == currentUser.id || note.applicant?.id == currentUser.id ||
                    projectIdsForLoadedSheetRow(note.project, note.source, allProjects).any { it in pmProjectIds })
        }
        .sortedBy { (it.content ?: "").lowercase() }

// --- Журнал (ProjectLogs): видимость по PermittedProjects и фильтры ---

/** При загрузке из файла/таблицы в список попадают только записи, где текущий пользователь — user или applicant (как у Notes). */
fun filterLoadedProjectLogsForCurrentUser(
    logs: List<ProjectLogs>,
    currentUser: Users,
    pmProjectIds: Set<String> = emptySet()
): List<ProjectLogs> =
    logs.filter { log ->
        log.user?.id == currentUser.id || log.applicant?.id == currentUser.id ||
            (log.project?.id != null && log.project!!.id in pmProjectIds)
    }

/** Журнал виден по разрешённым проектам; загруженные из Google — если user или applicant = текущий пользователь (как у заявок и Notes). */
fun filterProjectLogsByUserAccess(
    user: Users,
    logs: List<ProjectLogs>,
    permittedProjects: List<PermittedProjects>,
    pmProjectIds: Set<String> = emptySet()
): List<ProjectLogs> {
    if (user.role == UserRole.ADMIN) return logs
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return logs.filter { log ->
        val pid = log.project?.id
        pid != null && pid in pmProjectIds ||
            log.project == null || pid in permittedProjectIds ||
            (log.source_type == "GoogleTab" && (log.user?.id == user.id || log.applicant?.id == user.id))
    }
}

fun filterProjectLogsByTaskFilter(
    logs: List<ProjectLogs>,
    currentUser: Users,
    filter: TaskFilter
): List<ProjectLogs> = when (filter) {
    TaskFilter.MY -> logs.filter { it.user?.id == currentUser.id }
    TaskFilter.FROM_ME -> logs.filter { it.applicant?.id == currentUser.id }
    TaskFilter.ALL -> logs
}

/** Графики видимы по разрешённым проектам; загруженные из Google — если user = текущий пользователь (как у заявок и Notes). */
fun filterRoadMapsByUserAccess(
    user: Users,
    roadMaps: List<RoadMap>,
    permittedProjects: List<PermittedProjects>,
    pmProjectIds: Set<String> = emptySet()
): List<RoadMap> {
    if (user.role == UserRole.ADMIN) return roadMaps
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return roadMaps.filter { rm ->
        val pid = rm.project?.id
        pid != null && pid in pmProjectIds ||
            rm.project == null || pid in permittedProjectIds ||
            (rm.source_type == "GoogleTab" && rm.user?.id == user.id)
    }
}

/** Id пункта фильтра «график не указан» (не совпадает с id записи roadMaps). */
const val NO_ROADMAP_FILTER_ID = "__NO_ROADMAP__"

private fun syntheticNoRoadMapFilterOption(): RoadMap = RoadMap(
    id = NO_ROADMAP_FILTER_ID,
    name = "Не указан",
    content = "",
    step = "",
    start = 0L,
    end = 0L,
    user = null,
    project = null
)

/**
 * Результат расчёта фильтров вкладок «Задачи» / «Стикеры»: счётчики и итоговый список.
 * Общая логика для [Issues] и [Notes] (одинаковые поля user/applicant/project/roadMap).
 */
data class TabFilterMetrics<T>(
    val taskCounts: Map<TaskFilter, Int>,
    val projectCounts: Map<String, Int>,
    val roadMapCounts: Map<String, Int>,
    val availableRoadMapsForFilter: List<RoadMap>,
    val filteredItems: List<T>
)

/**
 * Единый расчёт фильтров по типу задачи, проекту и графику для списка сущностей с полями project/roadMap.
 */
fun <T> computeTabFilterMetrics(
    visibleItems: List<T>,
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    permittedProjects: List<Projects>,
    applyTaskFilter: (List<T>, TaskFilter) -> List<T>,
    projectId: (T) -> String?,
    roadMapOf: (T) -> RoadMap?
): TabFilterMetrics<T> {
    fun applyRoadMapSelection(source: List<T>): List<T> {
        val sel = selectedRoadMap ?: return source
        return when (sel.id) {
            NO_ROADMAP_FILTER_ID -> source.filter { roadMapOf(it) == null }
            else -> source.filter { roadMapOf(it)?.id == sel.id }
        }
    }
    val byProject: (List<T>) -> List<T> = { list ->
        if (selectedProject == null) list
        else list.filter { projectId(it) == selectedProject.id }
    }
    val itemsForTaskCounts = applyRoadMapSelection(byProject(visibleItems))
    val taskCounts = mapOf(
        TaskFilter.MY to applyTaskFilter(itemsForTaskCounts, TaskFilter.MY).size,
        TaskFilter.FROM_ME to applyTaskFilter(itemsForTaskCounts, TaskFilter.FROM_ME).size,
        TaskFilter.ALL to itemsForTaskCounts.size
    )
    val afterTaskFilter = applyTaskFilter(visibleItems, taskFilter)
    val itemsForProjectCounts = applyRoadMapSelection(afterTaskFilter)
    val projectCounts = buildMap {
        put("", itemsForProjectCounts.size)
        permittedProjects.forEach { project ->
            put(project.id, itemsForProjectCounts.count { projectId(it) == project.id })
        }
    }
    val itemsForRoadMapCounts = byProject(afterTaskFilter)
    val byRoadMapId = itemsForRoadMapCounts.groupingBy { roadMapOf(it)?.id ?: "" }.eachCount()
    val roadMapCounts = buildMap {
        put("", itemsForRoadMapCounts.size)
        put(NO_ROADMAP_FILTER_ID, byRoadMapId[""] ?: 0)
        byRoadMapId.forEach { (roadMapId, count) ->
            if (roadMapId.isNotBlank()) put(roadMapId, count)
        }
    }
    val filteredByTask = applyTaskFilter(visibleItems, taskFilter)
    val filteredByProject = byProject(filteredByTask)
    val availableRoadMapsForFilter = run {
        val list = filteredByProject.mapNotNull { roadMapOf(it) }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        if (filteredByProject.any { roadMapOf(it) == null }) {
            listOf(syntheticNoRoadMapFilterOption()) + list
        } else list
    }
    val filteredItems = applyRoadMapSelection(filteredByProject)
    return TabFilterMetrics(
        taskCounts = taskCounts,
        projectCounts = projectCounts,
        roadMapCounts = roadMapCounts,
        availableRoadMapsForFilter = availableRoadMapsForFilter,
        filteredItems = filteredItems
    )
}

/**
 * Фильтры вкладки «Календарь»: как у Задач (задача/проект/график) плюс тип события; счётчики в скобках.
 * [logsMatchingFilters] — после отборов по задаче, проекту, графику и типу (без фильтра по дню).
 */
data class CalendarFilterMetrics(
    val taskCounts: Map<TaskFilter, Int>,
    val projectCounts: Map<String, Int>,
    val roadMapCounts: Map<String, Int>,
    /** [""] — «Все», остальные ключи — значения [ProjectLogs.type]. */
    val typeCounts: Map<String, Int>,
    val availableRoadMapsForFilter: List<RoadMap>,
    val availableTypesOrdered: List<String>,
    val logsMatchingFilters: List<ProjectLogs>
)

fun computeCalendarFilterMetrics(
    visibleLogs: List<ProjectLogs>,
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    selectedType: String?,
    permittedProjects: List<Projects>,
    currentUser: Users
): CalendarFilterMetrics {
    val tab = computeTabFilterMetrics(
        visibleItems = visibleLogs,
        taskFilter = taskFilter,
        selectedProject = selectedProject,
        selectedRoadMap = selectedRoadMap,
        permittedProjects = permittedProjects,
        applyTaskFilter = { list, f -> filterProjectLogsByTaskFilter(list, currentUser, f) },
        projectId = { it.project?.id },
        roadMapOf = { it.roadMap }
    )
    val afterRoad = tab.filteredItems
    val typeCounts = buildMap {
        put("", afterRoad.size)
        afterRoad.map { it.type }.filter { it.isNotBlank() }.distinct().forEach { t ->
            put(t, afterRoad.count { it.type == t })
        }
    }
    val logsMatchingFilters =
        if (selectedType == null) afterRoad
        else afterRoad.filter { it.type == selectedType }
    val availableTypesOrdered =
        afterRoad.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
    return CalendarFilterMetrics(
        taskCounts = tab.taskCounts,
        projectCounts = tab.projectCounts,
        roadMapCounts = tab.roadMapCounts,
        typeCounts = typeCounts,
        availableRoadMapsForFilter = tab.availableRoadMapsForFilter,
        availableTypesOrdered = availableTypesOrdered,
        logsMatchingFilters = logsMatchingFilters
    )
}

/** Начало дня (00:00:00) в локальной временной зоне. */
fun dayStartMillis(millis: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = millis
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Первый день календарного месяца для переданной даты/времени (00:00:00 локально). */
fun firstDayOfMonthMillis(millis: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = millis
    c.set(java.util.Calendar.DAY_OF_MONTH, 1)
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun filterProjectLogsByDate(logs: List<ProjectLogs>, selectedDateMillis: Long): List<ProjectLogs> {
    val dayStart = dayStartMillis(selectedDateMillis)
    return logs.filter { dayStartMillis(it.date) == dayStart }
}

/** Все записи журнала, у которых дата попадает в тот же календарный месяц, что и [monthStartMillis]. */
fun filterProjectLogsByMonth(logs: List<ProjectLogs>, monthStartMillis: Long): List<ProjectLogs> {
    val ref = java.util.Calendar.getInstance()
    ref.timeInMillis = firstDayOfMonthMillis(monthStartMillis)
    val year = ref.get(java.util.Calendar.YEAR)
    val month = ref.get(java.util.Calendar.MONTH)
    val logCal = java.util.Calendar.getInstance()
    return logs.filter { log ->
        logCal.timeInMillis = log.date
        logCal.get(java.util.Calendar.YEAR) == year &&
            logCal.get(java.util.Calendar.MONTH) == month
    }
}
