package com.tools.toolapp_v2

/**
 * Управление правами доступа пользователей к данным приложения.
 * Весь код, относящийся к проверке и фильтрации доступа, размещается здесь.
 */

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
    permittedProjects: List<PermittedProjects>
): List<Issues> {
    if (user.role == UserRole.ADMIN) return issues
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return issues.filter { issue ->
        issue.project == null || issue.project!!.id in permittedProjectIds ||
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

/**
 * Фильтр при загрузке заявок из файла/таблицы: в память попадают только заявки,
 * у которых status не DONE и не CLOSED, и текущий пользователь — исполнитель (user) или постановщик (applicant).
 * Таким образом в приложении не оказываются «чужие» задачи и уже закрытые.
 */
fun filterLoadedIssuesForCurrentUser(issues: List<Issues>, currentUser: Users): List<Issues> =
    issues.filter { issue ->
        issue.status != IssueStatuses.DONE && issue.status != IssueStatuses.CLOSED &&
            (issue.user?.id == currentUser.id || issue.applicant?.id == currentUser.id)
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
fun filterLoadedNotesForCurrentUser(notes: List<Notes>, currentUser: Users): List<Notes> =
    notes.filter { it.user?.id == currentUser.id || it.applicant?.id == currentUser.id }

/** Стикеры видимы по разрешённым проектам; загруженные из Google — если user или applicant = текущий пользователь (как у заявок). */
fun filterNotesByUserAccess(
    user: Users,
    notes: List<Notes>,
    permittedProjects: List<PermittedProjects>
): List<Notes> {
    if (user.role == UserRole.ADMIN) return notes
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return notes.filter { note ->
        note.project == null || note.project!!.id in permittedProjectIds ||
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

// --- Журнал (ProjectLogs): видимость по PermittedProjects и фильтры ---

/** При загрузке из файла/таблицы в список попадают только записи, где текущий пользователь — user или applicant (как у Notes). */
fun filterLoadedProjectLogsForCurrentUser(logs: List<ProjectLogs>, currentUser: Users): List<ProjectLogs> =
    logs.filter { it.user?.id == currentUser.id || it.applicant?.id == currentUser.id }

/** Журнал виден по разрешённым проектам; загруженные из Google — если user или applicant = текущий пользователь (как у заявок и Notes). */
fun filterProjectLogsByUserAccess(
    user: Users,
    logs: List<ProjectLogs>,
    permittedProjects: List<PermittedProjects>
): List<ProjectLogs> {
    if (user.role == UserRole.ADMIN) return logs
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return logs.filter { log ->
        log.project == null || log.project!!.id in permittedProjectIds ||
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
    permittedProjects: List<PermittedProjects>
): List<RoadMap> {
    if (user.role == UserRole.ADMIN) return roadMaps
    val permittedProjectIds = permittedProjects
        .filter { it.user.id == user.id }
        .map { it.project.id }
        .toSet()
    return roadMaps.filter { rm ->
        rm.project == null || rm.project!!.id in permittedProjectIds ||
            (rm.source_type == "GoogleTab" && rm.user?.id == user.id)
    }
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

fun filterProjectLogsByDate(logs: List<ProjectLogs>, selectedDateMillis: Long): List<ProjectLogs> {
    val dayStart = dayStartMillis(selectedDateMillis)
    return logs.filter { dayStartMillis(it.date) == dayStart }
}
