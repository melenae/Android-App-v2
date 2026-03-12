package com.tools.toolapp_v2

/**
 * Данные приложения (users, projects, accounts, companies, permitted_projects) загружаются
 * после авторизации через RPC app_init (см. AppInit.kt). Тестовые списки exampleUsers,
 * exampleProjects, exampleAccounts, examplePermittedProjects, exampleCompanies удалены.
 */

/** Заявки по умолчанию пустые — данные только через импорт. */
fun exampleIssues(users: List<Users>): List<Issues> = emptyList()

/** Заметки по умолчанию пустые — данные только через импорт. */
val exampleNotes: MutableList<Notes> = mutableListOf()

/** Записи журнала по умолчанию пустые — данные только через импорт с закладки projectLogs. */
val exampleProjectLogs: MutableList<ProjectLogs> = mutableListOf()

/** Элементы дорожной карты по умолчанию пустые — данные только через импорт с листа roadMaps. */
val exampleRoadMaps: MutableList<RoadMap> = mutableListOf()

/** Трудозатраты (таблица TimeEntries). id — строка до 10 символов. */
val exampleTimeEntries: MutableList<TimeEntries> = mutableListOf()

/** Комментарии к заявкам по умолчанию пустые — подгружаются при импорте. */
val exampleIssueComments: MutableList<IssueComments> = mutableListOf()
