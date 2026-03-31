package com.tools.toolapp_v2

import kotlin.math.round

/**
 * Объединенные классы
 */
sealed class ApplicantRef {
    data class Team(val value: ClientTeams) : ApplicantRef()
    data class User(val value: Users) : ApplicantRef()
}

/**
 * Справочники
 */

data class Accounts(
    /** UUID счёта из API (app_init). */
    val id: String,
    var name: String,
    var content: String,
    /** Владелец счёта (owner_id в API); от него показываются счета в настройках. */
    var manager: Users?
)


data class ClientTeams(
    val id: Long,
    var name: String,
    var content: String,
    var project: Projects?,
    var user: Users?,
    var email: String,
    var phone: String,
    var role: String,
    var company: Companies?
)

/**
 * Постановщик заявки/компании: либо команда клиента (ClientTeams), либо пользователь (Users).
 * Использование: when (applicant) { is ApplicantRef.Team -> ... is ApplicantRef.User -> ... null -> ... }
 */


data class Companies(
    val id: Long,
    var name: String,
    var content: String,
    var project: Projects?,
    var user: Users?,
    var applicant: ApplicantRef?
)


data class Issues(
    /**
     * Заявка / задача (таблица Issues).
     * Редактируемые реквизиты объявлены как var — их можно менять.
     * dateUpdate — дата/время последнего изменения (мс); при загрузке сравнивается с датой в имени файла.
     *
     * @param id глобальный уникальный идентификатор заявки (GUID, строка). Печатный номер в рамках проекта вычисляется через issueNumberInProject().
     * @param name наименование
     * @param content описание
     * @param status статус
     * @param user ответственный по заявке (ссылка на USERS)
     * @param applicant постановщик (ссылка на USERS)
     */
    val id: String,
    var name: String,
    var content: String,
    var status: IssueStatuses,
    var user: Users?,
    var applicant: Users?,
    var project: Projects?,
    var company: Companies? = null,
    /** Элемент графика (roadMaps). При загрузке из таблицы разрешается по id из колонки roadMap. */
    var roadMap: RoadMap? = null,
    var dateUpdate: Long = 0L,
    /** Участвует в учёте времени на закладке Timing. */
    var onTiming: Boolean = false,
    /** Есть непрочитанный комментарий для ответственного (user). */
    var newCommentForUser: Boolean = false,
    /** Есть непрочитанный комментарий для постановщика (applicant). */
    var newCommentForApplicant: Boolean = false,
    /** Тип источника при загрузке: GoogleTab, Xlsx, Json, Csv. При экспорте — куда писать. */
    var source_type: String = "",
    /** Адрес таблицы/файла-источника. При импорте записывается, при экспорте используется как адрес назначения. */
    var source: String = ""
)

/**
 * Печатный номер заявки в рамках проекта: 1-based порядковый номер среди заявок того же проекта.
 * Сортировка по id (GUID) для стабильного порядка. Если у заявки нет проекта — null.
 */
fun issueNumberInProject(issue: Issues, allIssues: List<Issues>): Int? {
    val project = issue.project ?: return null
    val inProject = allIssues.filter { it.project == project }.sortedBy { it.id }
    val idx = inProject.indexOf(issue)
    return if (idx < 0) null else idx + 1
}

/**
 * Комментарии к заявке (таблица IssueComments).
 * id — GUID 32 символа. author — автор комментария (кто написал).
 * Реквизиты: issue (Задача), user (Пользователь), comment (Комментарий), date_create (Дата создания).
 * statusSet — статус заявки, установленный при отправке комментария (если был изменён).
 */
data class IssueComments(
    val id: String,
    var issue: Issues,
    var user: Users?,
    var author: Users?,
    var comment: String,
    var dateCreate: Long,
    var statusSet: IssueStatuses? = null
)

/**
 * Заметки / стикеры (таблица Notes).
 * id — GUID 32 символа. content — текст до 1000 символов.
 */
data class Notes(
    val id: String,
    var project: Projects?,
    var user: Users?,
    var applicant: Users?,
    var company: Companies?,
    var done: Boolean,
    var content: String,
    var roadMap: RoadMap? = null,
    /** Дата/время последнего изменения (мс); при записи в Google Таблицу передаётся для последующего чтения. */
    var dateUpdate: Long = 0L,
    /** Участвует в учёте времени на закладке Timing. */
    var onTiming: Boolean = false,
    /** Тип источника при загрузке: GoogleTab, Xlsx, Json, Csv. При экспорте — куда писать. */
    var source_type: String = "",
    /** Адрес таблицы/файла-источника. При импорте записывается, при экспорте используется как адрес назначения. */
    var source: String = ""
)

/**
 * Журнал по проекту (таблица ProjectLogs).
 * id — GUID 32 символа. date — дата/время (мс). content, agenda, resolution — до 1000 символов, type — до 100.
 */
data class ProjectLogs(
    val id: String,
    var project: Projects?,
    var user: Users?,
    var applicant: Users?,
    var content: String,
    var agenda: String,
    var resolution: String,
    var type: String,
    var date: Long,
    var roadMap: RoadMap? = null,
    var company: Companies? = null,
    /** Участвует в учёте времени на закладке Timing. */
    var onTiming: Boolean = false,
    /** Тип источника при загрузке: GoogleTab, Xlsx, Json, Csv. При экспорте — куда писать. */
    var source_type: String = "",
    /** Адрес таблицы/файла-источника. При импорте записывается, при экспорте используется как адрес назначения. */
    var source: String = ""
)

/**
 * Элемент дорожной карты (таблица roadMaps). id — строка (число в листе). step — этап: «Завершено», «в работе», «ожидает начало».
 * start/end — даты в мс (начало/конец периода). На листе — YYYY-MM-DD.
 */
data class RoadMap(
    val id: String,
    var name: String,
    var content: String,
    var step: String,
    var start: Long,
    var end: Long,
    var user: Users?,
    var project: Projects?,
    var dateUpdate: Long = 0L,
    var onTiming: Boolean = false,
    var source_type: String = "",
    var source: String = ""
)

/** Округляет часы до 0.01 (точность реквизита hours в TimeEntries). */
fun roundTimeEntryHours(hours: Double): Double = round(hours * 100) / 100.0

/**
 * Трудозатраты (таблица TimeEntries).
 * id — строка до 10 символов. Одна из ссылок issue, note, projectLog задана.
 * hours — точность до 0.01 (одна сотая часа).
 */
data class TimeEntries(
    val id: String,
    var issue: Issues? = null,
    var note: Notes? = null,
    var projectLog: ProjectLogs? = null,
    var roadMap: RoadMap? = null,
    var user: Users?,
    var hours: Double,
    var createdOn: Long,
    var updatedOn: Long,
    var project: Projects?,
    var comment: String = ""
)

data class PermittedProjects(
    val id: Long,
    var user: Users,
    var project: Projects
)

data class Projects(
    /** Глобальный уникальный идентификатор проекта (GUID, строка). */
    val id: String,
    var name: String,
    var content: String,
    var manager: Users?,
    var account: Accounts? = null,
    /** Уникальный идентификатор (GUID в стиле 1C) для обмена и имён файлов. */
    val slug: String,
    /** Ссылка на Google Таблицу с данными проекта (заявки, заметки, журнал). Загрузка и запись — по этой таблице. */
    var source: String = "",
    /**
     * Роль текущего пользователя в проекте из project_members.role (app_init, поле member_role).
     * Значение PROJECT_MEMBER_ROLE_PM — руководитель: из Google загружаются все строки листов проекта.
     */
    var memberRole: String? = null
)

/** Роль руководителя проекта в project_members; при совпадении — полная загрузка строк таблицы Google по проекту. */
const val PROJECT_MEMBER_ROLE_PM = "PM"

/** В app_init для текущего пользователя в этом проекте указана роль руководителя (см. PROJECT_MEMBER_ROLE_PM). */
fun Projects.isMemberRolePm(): Boolean {
    val r = memberRole?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    if (r.equals(PROJECT_MEMBER_ROLE_PM, ignoreCase = true)) return true
    // Частые обозначения в БД (латиница/кириллица)
    if (r.equals("РП", ignoreCase = true)) return true
    if (r.equals("RP", ignoreCase = true)) return true
    return false
}


/**
 * Представление пользователя для таблицы и поиска: «ФИО (адрес почты)».
 * Одна логика везде: при загрузке из Excel/Google Таблицы, в настройках и при выгрузке.
 */
fun userDisplayId(displayName: String, email: String): String = "$displayName ($email)"

/** Первая строка карточки: название проекта; при указанном графике — «Проект (график)». */
fun projectLineWithOptionalRoadMap(project: Projects?, roadMap: RoadMap?): String {
    val p = project?.name?.trim().orEmpty()
    val r = roadMap?.name?.trim().orEmpty()
    return when {
        p.isNotEmpty() && r.isNotEmpty() -> "$p ($r)"
        p.isNotEmpty() -> p
        r.isNotEmpty() -> "($r)"
        else -> ""
    }
}

data class Users(
    /**
     * Пользователь приложения (таблица USERS).
     * id — идентификатор в формате «ФИО (email)» для удобной подстановки при загрузке из Excel.
     * displayName — ФИО (имя для отображения). login — логин для входа; в настройках отображается как email.
     * email — для рабочего каталога заявок (подкаталог и префикс имён файлов).
     * timerNotification — интервал (минуты) для блокирующего запроса подтверждения работы; 0 = отключено.
     */
    val id: String,
    val login: String,
    val password: String,
    val displayName: String,
    val email: String,
    val role: UserRole = UserRole.REGULAR_USER,
    var account: Accounts? = null,
    /** Интервал напоминания в минутах (число 4 знака, точность 0). По умолчанию 5. 0 = не останавливать таймер. */
    val timerNotification: Int = 5
)


/**
 * Тут будут перечисления.
 */

enum class IssueStatuses(val displayName: String) {
    NEW("Новая"),
    IN_PROGRESS("В работе"),
    AWAITING("Ожидает"),
    TESTING("Приемка"),
    DONE("Выполнена"),
    CLOSED("Закрыта");

    companion object {
        /** Статусы для отображения на доске задач (4 колонки). */
        val boardStatuses: List<IssueStatuses> = listOf(NEW, IN_PROGRESS, AWAITING, TESTING)

        /** Следующий статус по порядку (для кнопки повышения). */
        fun nextStatus(current: IssueStatuses): IssueStatuses? {
            val idx = boardStatuses.indexOf(current)
            if (idx >= 0 && idx + 1 < boardStatuses.size) return boardStatuses[idx + 1]
            if (current == TESTING) return DONE
            return null
        }
    }
}

/** Роль пользователя: admin видит все проекты, regular_user — только из permitted_projects. */
enum class UserRole {
    ADMIN,
    REGULAR_USER
}
