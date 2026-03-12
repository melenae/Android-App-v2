package com.tools.toolapp_v2

import android.content.Context
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.CreationHelper
import org.apache.poi.ss.usermodel.Drawing
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.util.IOUtils
import org.apache.poi.xssf.usermodel.XSSFRichTextString
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Имя подкаталога и префикс файлов заявок пользователя — email как есть (@ и . допустимы в именах).
 */
fun userFolderName(user: Users): String = user.email

/** Символы для GUID: цифры и заглавные латинские буквы. */
private const val GUID_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Генерирует GUID 32 символа для комментария (цифры и заглавные A–Z). */
fun generateCommentId(): String = (1..32).map { GUID_CHARS.random() }.joinToString("")

/** Генерирует id для TimeEntries (строка 10 символов: TE + 8 символов). */
fun generateTimeEntryId(): String = "TE" + (1..8).map { GUID_CHARS.random() }.joinToString("")

/** Генерирует уникальный slug (GUID в формате UUID) для нового проекта. */
fun generateProjectSlug(): String = UUID.randomUUID().toString()

/** Генерирует глобальный уникальный идентификатор заявки (GUID). */
fun generateIssueId(): String = UUID.randomUUID().toString()

/**
 * Импорт заявок из JSON.
 * Рабочий каталог: у каждого пользователя подкаталог с именем = user.email.
 * Файлы заявок: имя = {email}_{номерЗаявки}_{датаВремя}.json, одна заявка — один файл.
 */

/**
 * Парсит JSON-строку с массивом заявок и возвращает список [Issues].
 * Ссылки user, applicant, project разрешаются по id из переданных списков пользователей и проектов.
 *
 * Формат элемента в JSON:
 * - id (Long) — идентификатор заявки
 * - name (String) — наименование
 * - content (String) — описание
 * - status (String) — статус: NEW, IN_PROGRESS, AWAITING, TESTING, DONE, CLOSED
 * - userId (Long?, optional) — id пользователя (ответственный)
 * - applicantId (Long?, optional) — id пользователя (постановщик)
 * - projectId (Long?, optional) — id проекта
 *
 * @param jsonString строка JSON (массив объектов заявок)
 * @param users список пользователей для разрешения userId / applicantId
 * @param projects список проектов для разрешения projectId
 * @return список заявок
 */
/** Строит карту пользователей по id, legacy-N, email, login, displayName и по представлению «ФИО (адрес почты)». Поиск по адресу из скобок работает и по email, и по login. */
private fun usersByIdAndLegacy(users: List<Users>): Map<String, Users> {
    fun displayKey(u: Users) = userDisplayId(u.displayName, u.email.takeIf { it.isNotBlank() } ?: u.login)
    return users.associateBy { it.id } +
        users.mapIndexed { i, u -> "legacy-${i + 1}" to u }.toMap() +
        users.associateBy { it.email } +
        users.filter { it.login.isNotBlank() }.associateBy { it.login } +
        users.associateBy { it.displayName } +
        users.associateBy { displayKey(it) }
}

/**
 * Разрешает пользователя по представлению «ФИО (адрес почты)»: точное совпадение строки, затем поиск по адресу почты из скобок.
 * Та же логика, что в таблице и в userDisplayId: ФИО плюс адрес почты в скобках.
 */
private fun resolveUserRef(userRef: String?, usersByRef: Map<String, Users>): Users? {
    if (userRef.isNullOrBlank()) return null
    usersByRef[userRef]?.let { return it }
    val emailInParens = userRef.indexOf('(').takeIf { it >= 0 }?.let { start ->
        val end = userRef.indexOf(')', start)
        if (end > start) userRef.substring(start + 1, end).trim() else null
    } ?: return null
    return usersByRef[emailInParens]
}

/**
 * Единая логика определения user и applicant для Issue, Notes, ProjectLog, RoadMap.
 * Поддерживает: «ФИО (адрес почты)», совпадение с текущим пользователем, а также id/legacy-N в ячейке.
 */
private fun resolveUserAndApplicant(
    userRef: String?,
    applicantRef: String?,
    usersByRef: Map<String, Users>,
    currentUser: Users? = null
): Pair<Users?, Users?> {
    val currentUserDisplay = currentUser?.let { userDisplayId(it.displayName, it.email.takeIf { it.isNotBlank() } ?: it.login) }
    fun resolveOne(ref: String?): Users? {
        if (ref.isNullOrBlank()) return null
        if (currentUser != null && currentUserDisplay != null && ref == currentUserDisplay) return currentUser
        return resolveUserRef(parseIssueIdFromCell(ref) ?: ref, usersByRef)
    }
    return Pair(resolveOne(userRef), resolveOne(applicantRef))
}

/** Единая логика определения проекта по ячейке: число → legacy-N, иначе как есть (UUID и т.д.); поиск в projectsByRef. */
private fun resolveProjectFromCell(cellValue: String?, projectsByRef: Map<String, Projects>): Projects? =
    parseIssueIdFromCell(cellValue)?.let { projectsByRef[it] }

/** Строит карту проектов по id и по legacy-N. */
private fun projectsByIdAndLegacy(projects: List<Projects>): Map<String, Projects> =
    projects.associateBy { it.id } + projects.mapIndexed { i, p -> "legacy-${i + 1}" to p }.toMap()

/** Парсит ссылку на пользователя/проект из JSON: число -> "legacy-N", строка -> как есть. */
private fun parseRefId(obj: JSONObject, key: String): String? {
    if (!obj.has(key)) return null
    return when (val v = obj.get(key)) {
        is Number -> v.toLong().takeIf { it > 0 }?.let { "legacy-$it" }
        is String -> v.takeIf { it.isNotBlank() }
        else -> null
    }
}

fun loadIssuesFromJson(
    jsonString: String,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies> = emptyList()
): List<Issues> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val companiesById = companies.associateBy { it.id.toString() }
    val list = mutableListOf<Issues>()
    val jsonArray = JSONArray(jsonString)
    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val id = parseIssueIdFromJson(obj)
        val name = obj.getString("name")
        val content = obj.optString("content", "")
        val statusStr = obj.optString("status", "NEW")
        val status = parseIssueStatus(statusStr)
        val userId = parseRefId(obj, "userId")
        val applicantId = parseRefId(obj, "applicantId")
        val projectId = parseRefId(obj, "projectId")
        val companyId = parseRefId(obj, "companyId") ?: obj.optString("company", null)?.takeIf { it.isNotBlank() }
        val user = userId?.let { usersByRef[it] }
        val applicant = applicantId?.let { usersByRef[it] }
        val project = projectId?.let { projectsByRef[it] }
            ?: obj.optString("projectSlug", null).takeIf { it.isNotBlank() }?.let { slug -> projects.firstOrNull { it.slug == slug } }
        val company = companyId?.let { companiesById[it] } ?: companyId?.toLongOrNull()?.let { companiesById[it.toString()] }
        val dateUpdate = obj.optLong("dateUpdate", 0L)
        list.add(
            Issues(
                id = id,
                name = name,
                content = content,
                status = status,
                user = user,
                applicant = applicant,
                project = project,
                company = company,
                dateUpdate = dateUpdate
            )
        )
    }
    return list
}

/** Парсит id заявки из JSON: "guid" или "id" (строка/GUID или число -> "legacy-N"). Пусто — генерирует новый. */
private fun parseIssueIdFromJson(obj: JSONObject): String {
    when {
        obj.has("guid") -> obj.optString("guid", "").takeIf { it.isNotBlank() }?.let { return it }
        obj.has("id") -> {
            val v = obj.get("id")
            return when (v) {
                is Number -> "legacy-${v.toLong()}"
                else -> v.toString().takeIf { it.isNotBlank() } ?: generateIssueId()
            }
        }
    }
    return generateIssueId()
}

/**
 * Преобразует внутренний id заявки в значение для колонки A листа Issues (без GUID).
 * "legacy-1" -> "1"; иначе возвращает id как есть.
 */
fun issueIdToSheetId(issueId: String): String =
    issueId.removePrefix("legacy-").trim().ifBlank { issueId }

/** Парсит id заявки из ячейки/поля: число -> "legacy-N", иначе строка как есть. Пусто -> null (строка пропускается). */
private fun parseIssueIdFromCell(s: String?): String? {
    val v = s?.trim() ?: return null
    if (v.isEmpty()) return null
    val num = v.trimEnd('L', 'l').toLongOrNull()
    return if (num != null && num > 0) "legacy-$num" else v
}

/** Парсит число из ячейки: "1", "1L", "2L" -> Long. */
private fun parseCsvLong(s: String?): Long? {
    val v = s?.trim() ?: return null
    val num = v.trimEnd('L', 'l')
    return num.toLongOrNull()?.takeIf { it > 0 }
}

/** Парсит булево: ИСТИНА/TRUE/true/1/да/yes -> true, иначе false. */
private fun parseCsvBoolean(s: String?): Boolean {
    val v = s?.trim()?.uppercase() ?: return false
    return v == "ИСТИНА" || v == "TRUE" || v == "1" || v == "ДА" || v == "YES"
}

/**
 * Парсит CSV с заявками. Первая строка — заголовки.
 * Поддерживаются колонки: id, name, content, status, user/userId, applicant/applicantId, project/projectId,
 * dateUpdate, onTiming, newCommentForUser, newCommentForApplicant.
 * Значения id/user/applicant/project могут быть "1L", "2L". status — "NEW" или "IssueStatuses.NEW".
 * Разделитель — запятая или точка с запятой; значения в кавычках допускают запятые внутри.
 */
fun loadIssuesFromCsv(
    csvContent: String,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies> = emptyList(),
    onDebugLog: (String) -> Unit = {},
    /** Текущий пользователь сеанса — для лога (в «Мои» попадают заявки, где User или Applicant = он). */
    currentUser: Users? = null
): List<IssueLoadItem> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val companiesById = companies.associateBy { it.id.toString() }
    /** Представление текущего пользователя для сравнения с колонками User/Applicant: «ФИО (адрес почты)» — та же логика, что в таблице. */
    val currentUserDisplay = currentUser?.let { userDisplayId(it.displayName, it.email.takeIf { it.isNotBlank() } ?: it.login) }
    if (currentUser != null)
        onDebugLog("Текущий пользователь в сеансе: id=${currentUser.id}, displayName=\"${currentUser.displayName}\", login=\"${currentUser.login}\". Представление для таблицы: \"$currentUserDisplay\". В «Мои» попадают заявки, где User или Applicant совпадает с ним.")
    val lines = csvContent.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val sep = if (lines[0].contains(';')) ';' else ','
    fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            when (val c = line[i]) {
                '"' -> inQuotes = !inQuotes
                sep -> if (!inQuotes) { result.add(current.toString().trim()); current = StringBuilder() } else current.append(c)
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
    val header = splitCsvLine(lines[0]).map { it.removeSurrounding("\"") }
    // Для листа Issues: идентификатор заявки берём из колонки id (1-я колонка), без отдельной колонки GUID
    val idIdx = header.indexOfFirst { it.equals("id", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("guid", ignoreCase = true) }.takeIf { it >= 0 } ?: return emptyList()
    val nameIdx = header.indexOfFirst { it.equals("name", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("Название", ignoreCase = true) }.takeIf { it >= 0 } ?: return emptyList()
    val contentIdx = maxOf(
        header.indexOfFirst { it.equals("content", ignoreCase = true) },
        header.indexOfFirst { it.equals("Описание", ignoreCase = true) }
    ).coerceAtLeast(-1)
    val statusIdx = header.indexOfFirst { it.equals("status", ignoreCase = true) }.coerceAtLeast(-1)
    val userIdx = header.indexOfFirst { it.equals("user", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("userId", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("Пользователь", ignoreCase = true) }.takeIf { it >= 0 } ?: -1
    val applicantIdx = header.indexOfFirst { it.equals("applicant", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("applicantId", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("Постановщик", ignoreCase = true) }.takeIf { it >= 0 } ?: -1
    val projectIdx = header.indexOfFirst { it.equals("project", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("projectId", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("Проект", ignoreCase = true) }.takeIf { it >= 0 } ?: -1
    val companyIdx = header.indexOfFirst { it.equals("company", ignoreCase = true) }.coerceAtLeast(-1)
    val dateUpdateIdx = header.indexOfFirst { it.equals("dateUpdate", ignoreCase = true) }.coerceAtLeast(-1)
    val onTimingIdx = header.indexOfFirst { it.equals("onTiming", ignoreCase = true) }.coerceAtLeast(-1)
    val newCommentForUserIdx = header.indexOfFirst { it.equals("newCommentForUser", ignoreCase = true) }.coerceAtLeast(-1)
    val newCommentForApplicantIdx = header.indexOfFirst { it.equals("newCommentForApplicant", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceTypeIdx = header.indexOfFirst { it.equals("source_type", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceIdx = header.indexOfFirst { it.equals("source", ignoreCase = true) }.coerceAtLeast(-1)
    val roadMapIdx = listOf(
        header.indexOfFirst { it.equals("roadMap", ignoreCase = true) },
        header.indexOfFirst { it.equals("roadMapId", ignoreCase = true) },
        header.indexOfFirst { it.equals("График", ignoreCase = true) }
    ).firstOrNull { it >= 0 } ?: -1
    val list = mutableListOf<IssueLoadItem>()
    for (i in 1 until lines.size) {
        val cells = splitCsvLine(lines[i])
        if (cells.size <= maxOf(idIdx, nameIdx)) continue
        val idRaw = cells.getOrNull(idIdx)?.trim()
        val name = cells.getOrNull(nameIdx)?.trim() ?: ""
        val userFilled = userIdx >= 0 && !cells.getOrNull(userIdx)?.trim().isNullOrBlank()
        val applicantFilled = applicantIdx >= 0 && !cells.getOrNull(applicantIdx)?.trim().isNullOrBlank()
        val id = when {
            !idRaw.isNullOrBlank() -> parseIssueIdFromCell(idRaw)
            name.isNotBlank() && userFilled && applicantFilled -> "legacy-$i"
            else -> null
        } ?: continue
        val content = cells.getOrNull(contentIdx) ?: ""
        val statusStr = if (statusIdx >= 0) cells.getOrNull(statusIdx)?.trim() ?: "NEW" else "NEW"
        val status = parseIssueStatus(if (statusStr.contains(".")) statusStr.substringAfterLast(".") else statusStr)
        val userRef = if (userIdx >= 0) cells.getOrNull(userIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val applicantRef = if (applicantIdx >= 0) cells.getOrNull(applicantIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val projectRef = if (projectIdx >= 0) cells.getOrNull(projectIdx) else null
        val companyRef = if (companyIdx >= 0) cells.getOrNull(companyIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val (user, applicant) = resolveUserAndApplicant(userRef, applicantRef, usersByRef, currentUser)
        val project = resolveProjectFromCell(projectRef, projectsByRef)
        val company = companyRef?.let { companiesById[it] } ?: companyRef?.toLongOrNull()?.let { companiesById[it.toString()] }
        val dateUpdate = if (dateUpdateIdx >= 0) parseCsvLong(cells.getOrNull(dateUpdateIdx)) ?: 0L else 0L
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cells.getOrNull(onTimingIdx)) else false
        val newCommentForUser = if (newCommentForUserIdx >= 0) parseCsvBoolean(cells.getOrNull(newCommentForUserIdx)) else false
        val newCommentForApplicant = if (newCommentForApplicantIdx >= 0) parseCsvBoolean(cells.getOrNull(newCommentForApplicantIdx)) else false
        val sourceType = if (sourceTypeIdx >= 0) cells.getOrNull(sourceTypeIdx)?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cells.getOrNull(sourceIdx)?.trim() ?: "" else ""
        val roadMapId = if (roadMapIdx >= 0) cells.getOrNull(roadMapIdx)?.trim() ?: "" else ""
        if (userRef != null && user == null)
            onDebugLog("Строка ${i + 1} (id=$id): User \"$userRef\" ${if (currentUserDisplay != null) "не совпадает с текущим пользователем (\"" + currentUserDisplay + "\")" else "не найден в списке пользователей"} — заявка не попадёт в «Мои»")
        if (i == 1) {
            val userStatus = if (user != null) "найден (id=${user.id})" else "не найден"
            val applicantStatus = if (applicant != null) "найден (id=${applicant.id})" else "не найден"
            val projectStatus = if (project != null) "найден (${project.name})" else "не найден"
            onDebugLog("Строка 2 (1-я с данными): id=$id, name=\"$name\", User=\"$userRef\" → $userStatus, Applicant=\"$applicantRef\" → $applicantStatus, Project=\"$projectRef\" → $projectStatus. В «Мои» попадёт только если User или Applicant = текущий пользователь.")
        }
        list.add(
            IssueLoadItem(
                issue = Issues(
                    id = id,
                    name = name,
                    content = content,
                    status = status,
                    user = user,
                    applicant = applicant,
                    project = project,
                    company = company,
                    dateUpdate = dateUpdate,
                    onTiming = onTiming,
                    newCommentForUser = newCommentForUser,
                    newCommentForApplicant = newCommentForApplicant,
                    source_type = sourceType,
                    source = source
                ),
                roadMapId = roadMapId
            )
        )
    }
    return list
}

/** Парсит булево: ИСТИНА/TRUE/true/1/да/yes -> true (уже есть parseCsvBoolean выше, но для Notes нужна публичная аналогия). */
private fun parseNoteDone(s: String?): Boolean = parseCsvBoolean(s)

/**
 * Парсит CSV с заметками (лист notes). Заголовки: id/guid (опционально), content/Описание, project, User, applicant, company, done, onTiming.
 * User и applicant ищутся по представлению «ФИО (адрес почты)». Если колонки id/guid нет, id задаётся как "note-&lt;номер строки&gt;".
 * Загружаются только строки с done = False (TRUE/FALSE или ИСТИНА/ЛОЖЬ).
 */
fun loadNotesFromCsv(
    csvContent: String,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies>,
    onDebugLog: (String) -> Unit = {},
    /** Текущий пользователь сеанса — та же логика, что для Issue (совпадение по «ФИО (адрес почты)»). */
    currentUser: Users? = null
): List<NoteLoadItem> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val companiesById = companies.associateBy { it.id.toString() }
    val lines = csvContent.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val sep = if (lines[0].contains(';')) ';' else ','
    fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            when (val c = line[i]) {
                '"' -> inQuotes = !inQuotes
                sep -> if (!inQuotes) { result.add(current.toString().trim()); current = StringBuilder() } else current.append(c)
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
    val header = splitCsvLine(lines[0]).map { it.removeSurrounding("\"").trim() }
    val idIdx = header.indexOfFirst { it.equals("guid", ignoreCase = true) }.takeIf { it >= 0 }
        ?: header.indexOfFirst { it.equals("id", ignoreCase = true) }.takeIf { it >= 0 } ?: -1
    val contentIdx = maxOf(
        header.indexOfFirst { it.equals("content", ignoreCase = true) },
        header.indexOfFirst { it.equals("Описание", ignoreCase = true) }
    ).coerceAtLeast(-1)
    val projectIdx = header.indexOfFirst { it.equals("project", ignoreCase = true) }.coerceAtLeast(-1)
    val userIdx = listOf(
        header.indexOfFirst { it.equals("user", ignoreCase = true) },
        header.indexOfFirst { it.equals("Пользователь", ignoreCase = true) }
    ).firstOrNull { it >= 0 } ?: -1
    val applicantIdx = listOf(
        header.indexOfFirst { it.equals("applicant", ignoreCase = true) },
        header.indexOfFirst { it.equals("Постановщик", ignoreCase = true) }
    ).firstOrNull { it >= 0 } ?: -1
    val companyIdx = header.indexOfFirst { it.equals("company", ignoreCase = true) }.coerceAtLeast(-1)
    val doneIdx = header.indexOfFirst { it.equals("done", ignoreCase = true) }.coerceAtLeast(-1)
    val dateUpdateIdx = header.indexOfFirst { it.equals("dateUpdate", ignoreCase = true) }.coerceAtLeast(-1)
    val onTimingIdx = header.indexOfFirst { it.equals("onTiming", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceTypeIdx = header.indexOfFirst { it.equals("source_type", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceIdx = header.indexOfFirst { it.equals("source", ignoreCase = true) }.coerceAtLeast(-1)
    val roadMapIdx = listOf(
        header.indexOfFirst { it.equals("roadMap", ignoreCase = true) },
        header.indexOfFirst { it.equals("roadMapId", ignoreCase = true) },
        header.indexOfFirst { it.equals("График", ignoreCase = true) }
    ).firstOrNull { it >= 0 } ?: -1
    val list = mutableListOf<NoteLoadItem>()
    for (i in 1 until lines.size) {
        val rawCells = splitCsvLine(lines[i])
        val cells = if (rawCells.size < header.size) List(header.size - rawCells.size) { "" } + rawCells else rawCells
        if (idIdx >= 0 && cells.size <= idIdx) continue
        val idRaw = if (idIdx >= 0) cells.getOrNull(idIdx)?.trim() else null
        val content = if (contentIdx >= 0) cells.getOrNull(contentIdx)?.trim() ?: "" else ""
        val userFilled = userIdx >= 0 && !cells.getOrNull(userIdx)?.trim().isNullOrBlank()
        val applicantFilled = applicantIdx >= 0 && !cells.getOrNull(applicantIdx)?.trim().isNullOrBlank()
        val id = when {
            !idRaw.isNullOrBlank() -> idRaw
            content.isNotBlank() && userFilled && applicantFilled -> if (idIdx < 0) "note-$i" else "$i"
            else -> null
        }
        val projectCell = if (projectIdx >= 0) cells.getOrNull(projectIdx) else null
        val userRef = if (userIdx >= 0) cells.getOrNull(userIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val applicantRef = if (applicantIdx >= 0) cells.getOrNull(applicantIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val companyRef = if (companyIdx >= 0) cells.getOrNull(companyIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val project = resolveProjectFromCell(projectCell, projectsByRef)
        val (user, applicant) = resolveUserAndApplicant(userRef, applicantRef, usersByRef, currentUser)
        val done = if (doneIdx >= 0) parseNoteDone(cells.getOrNull(doneIdx)) else false
        if (i == 1) {
            val outcome = when {
                id == null -> "пропуск: нет id (idRaw=\"$idRaw\", content=\"${content.take(30)}\", userFilled=$userFilled, applicantFilled=$applicantFilled)"
                done -> "пропуск: done=TRUE"
                else -> "загружена: id=$id, User=\"$userRef\"→${user?.id ?: "не найден"}, Applicant=\"$applicantRef\"→${applicant?.id ?: "не найден"}, project=${project?.name ?: "не найден"}"
            }
            onDebugLog("Notes строка 2 (1-я с данными): $outcome")
        }
        if (id == null) continue
        val company = companyRef?.let { companiesById[it] } ?: companyRef?.toLongOrNull()?.let { companiesById[it.toString()] }
        if (done) continue
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cells.getOrNull(onTimingIdx)) else false
        val dateUpdate = if (dateUpdateIdx >= 0) {
            val v = cells.getOrNull(dateUpdateIdx)?.trim()
            when {
                v.isNullOrBlank() -> 0L
                else -> (v.toDoubleOrNull()?.toLong() ?: v.toLongOrNull()) ?: 0L
            }
        } else 0L
        val sourceType = if (sourceTypeIdx >= 0) cells.getOrNull(sourceTypeIdx)?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cells.getOrNull(sourceIdx)?.trim() ?: "" else ""
        val roadMapId = if (roadMapIdx >= 0) cells.getOrNull(roadMapIdx)?.trim() ?: "" else ""
        list.add(
            NoteLoadItem(
                note = Notes(
                    id = id,
                    project = project,
                    user = user,
                    applicant = applicant,
                    company = company,
                    done = done,
                    content = content,
                    dateUpdate = dateUpdate,
                    onTiming = onTiming,
                    source_type = sourceType,
                    source = source
                ),
                roadMapId = roadMapId
            )
        )
    }
    return list
}

/**
 * Парсит CSV с записями журнала (лист projectLogs). Заголовки: id, type, date, content, agenda, resolution, user, applicant, project, onTiming, source_type, source.
 * User и applicant ищутся по представлению «ФИО (адрес почты)» (та же логика, что для Issues и Notes).
 * Строки с пустым id пропускаются.
 */
fun loadProjectLogsFromCsv(
    csvContent: String,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies>,
    onDebugLog: (String) -> Unit = {},
    currentUser: Users? = null
): List<ProjectLogLoadItem> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val lines = csvContent.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val sep = if (lines[0].contains(';')) ';' else ','
    fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            when (val c = line[i]) {
                '"' -> inQuotes = !inQuotes
                sep -> if (!inQuotes) { result.add(current.toString().trim()); current = StringBuilder() } else current.append(c)
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
    val header = splitCsvLine(lines[0]).map { it.removeSurrounding("\"") }
    val idIdx = header.indexOfFirst { it.equals("id", ignoreCase = true) }.takeIf { it >= 0 } ?: return emptyList()
    val typeIdx = header.indexOfFirst { it.equals("type", ignoreCase = true) }.coerceAtLeast(-1)
    val dateIdx = header.indexOfFirst { it.equals("date", ignoreCase = true) }.coerceAtLeast(-1)
    val contentIdx = header.indexOfFirst { it.equals("content", ignoreCase = true) }.coerceAtLeast(-1)
    val agendaIdx = header.indexOfFirst { it.equals("agenda", ignoreCase = true) }.coerceAtLeast(-1)
    val resolutionIdx = header.indexOfFirst { it.equals("resolution", ignoreCase = true) }.coerceAtLeast(-1)
    val userIdx = header.indexOfFirst { it.equals("user", ignoreCase = true) }.coerceAtLeast(-1)
    val applicantIdx = header.indexOfFirst { it.equals("applicant", ignoreCase = true) }.coerceAtLeast(-1)
    val projectIdx = header.indexOfFirst { it.equals("project", ignoreCase = true) }.coerceAtLeast(-1)
    val onTimingIdx = header.indexOfFirst { it.equals("onTiming", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceTypeIdx = header.indexOfFirst { it.equals("source_type", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceIdx = header.indexOfFirst { it.equals("source", ignoreCase = true) }.coerceAtLeast(-1)
    val roadMapIdx = listOf(
        header.indexOfFirst { it.equals("roadMap", ignoreCase = true) },
        header.indexOfFirst { it.equals("roadMapId", ignoreCase = true) },
        header.indexOfFirst { it.equals("График", ignoreCase = true) }
    ).firstOrNull { it >= 0 } ?: -1
    val list = mutableListOf<ProjectLogLoadItem>()
    for (i in 1 until lines.size) {
        val rawCells = splitCsvLine(lines[i])
        val cells = if (rawCells.size < header.size) List(header.size - rawCells.size) { "" } + rawCells else rawCells
        if (cells.size <= idIdx) continue
        val idRaw = cells.getOrNull(idIdx)?.trim()
        if (idRaw.isNullOrBlank()) continue
        val id = idRaw
        val type = if (typeIdx >= 0) cells.getOrNull(typeIdx)?.trim() ?: "" else ""
        val dateStr = if (dateIdx >= 0) cells.getOrNull(dateIdx) else null
        val date = parseDateFromCell(dateStr)
        val content = if (contentIdx >= 0) cells.getOrNull(contentIdx)?.trim() ?: "" else ""
        val agenda = if (agendaIdx >= 0) cells.getOrNull(agendaIdx)?.trim() ?: "" else ""
        val resolution = if (resolutionIdx >= 0) cells.getOrNull(resolutionIdx)?.trim() ?: "" else ""
        val projectCell = if (projectIdx >= 0) cells.getOrNull(projectIdx) else null
        val userRef = if (userIdx >= 0) cells.getOrNull(userIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val applicantRef = if (applicantIdx >= 0) cells.getOrNull(applicantIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val project = resolveProjectFromCell(projectCell, projectsByRef)
        val (user, applicant) = resolveUserAndApplicant(userRef, applicantRef, usersByRef, currentUser)
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cells.getOrNull(onTimingIdx)) else false
        val sourceType = if (sourceTypeIdx >= 0) cells.getOrNull(sourceTypeIdx)?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cells.getOrNull(sourceIdx)?.trim() ?: "" else ""
        val roadMapId = if (roadMapIdx >= 0) cells.getOrNull(roadMapIdx)?.trim() ?: "" else ""
        if (i == 1) {
            val userMatch = user?.id == currentUser?.id
            val applicantMatch = applicant?.id == currentUser?.id
            val outcome = if (userMatch || applicantMatch) "загружена: id=$id, User=\"$userRef\"→${user?.id ?: "не найден"}, Applicant=\"$applicantRef\"→${applicant?.id ?: "не найден"}"
                else "пропуск (не user/applicant): id=$id, User=\"$userRef\", Applicant=\"$applicantRef\""
            onDebugLog("ProjectLogs строка 2 (1-я с данными): $outcome")
        }
        list.add(
            ProjectLogLoadItem(
                log = ProjectLogs(
                    id = id,
                    project = project,
                    user = user,
                    applicant = applicant,
                    content = content,
                    agenda = agenda,
                    resolution = resolution,
                    type = type,
                    date = date,
                    onTiming = onTiming,
                    source_type = sourceType,
                    source = source
                ),
                roadMapId = roadMapId
            )
        )
    }
    return list
}

/**
 * Парсит CSV с элементами дорожной карты (лист roadMaps). Заголовки: id, Название, Описание, step, start, end, User, Проект, project, dateUpdate, onTiming, source_type, source.
 * User ищется по представлению «ФИО (адрес почты)» (та же логика, что для Issues, Notes, projectLogs).
 * Строки с пустым id пропускаются.
 */
fun loadRoadMapsFromCsv(
    csvContent: String,
    users: List<Users>,
    projects: List<Projects>,
    currentUser: Users? = null
): List<RoadMap> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val lines = csvContent.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val sep = if (lines[0].contains(';')) ';' else ','
    fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            when (val c = line[i]) {
                '"' -> inQuotes = !inQuotes
                sep -> if (!inQuotes) { result.add(current.toString().trim()); current = StringBuilder() } else current.append(c)
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
    val header = splitCsvLine(lines[0]).map { it.removeSurrounding("\"") }
    val idIdx = header.indexOfFirst { it.equals("id", ignoreCase = true) }.takeIf { it >= 0 } ?: return emptyList()
    val nameIdx = header.indexOfFirst { it.equals("Название", ignoreCase = true) }.coerceAtLeast(-1)
    val contentIdx = header.indexOfFirst { it.equals("Описание", ignoreCase = true) }.coerceAtLeast(-1)
    val stepIdx = header.indexOfFirst { it.equals("step", ignoreCase = true) }.coerceAtLeast(-1)
    val startIdx = header.indexOfFirst { it.equals("start", ignoreCase = true) }.coerceAtLeast(-1)
    val endIdx = header.indexOfFirst { it.equals("end", ignoreCase = true) }.coerceAtLeast(-1)
    val userIdx = header.indexOfFirst { it.equals("User", ignoreCase = true) }.coerceAtLeast(-1)
    val projectIdx = header.indexOfFirst { it.equals("project", ignoreCase = true) }.coerceAtLeast(-1)
    val dateUpdateIdx = header.indexOfFirst { it.equals("dateUpdate", ignoreCase = true) }.coerceAtLeast(-1)
    val onTimingIdx = header.indexOfFirst { it.equals("onTiming", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceTypeIdx = header.indexOfFirst { it.equals("source_type", ignoreCase = true) }.coerceAtLeast(-1)
    val sourceIdx = header.indexOfFirst { it.equals("source", ignoreCase = true) }.coerceAtLeast(-1)
    val list = mutableListOf<RoadMap>()
    for (i in 1 until lines.size) {
        val rawCells = splitCsvLine(lines[i])
        val cells = if (rawCells.size < header.size) List(header.size - rawCells.size) { "" } + rawCells else rawCells
        if (cells.size <= idIdx) continue
        val idRaw = cells.getOrNull(idIdx)?.trim()
        if (idRaw.isNullOrBlank()) continue
        val id = idRaw
        val name = if (nameIdx >= 0) cells.getOrNull(nameIdx)?.trim() ?: "" else ""
        val content = if (contentIdx >= 0) cells.getOrNull(contentIdx)?.trim() ?: "" else ""
        val step = if (stepIdx >= 0) cells.getOrNull(stepIdx)?.trim() ?: "" else ""
        val startStr = if (startIdx >= 0) cells.getOrNull(startIdx) else null
        val endStr = if (endIdx >= 0) cells.getOrNull(endIdx) else null
        val start = parseDateFromCell(startStr)
        val end = parseDateFromCell(endStr)
        val projectCell = if (projectIdx >= 0) cells.getOrNull(projectIdx) else null
        val userRef = if (userIdx >= 0) cells.getOrNull(userIdx)?.trim()?.takeIf { it.isNotBlank() } else null
        val project = resolveProjectFromCell(projectCell, projectsByRef)
        val user = resolveUserAndApplicant(userRef, null, usersByRef, currentUser).first
        val dateUpdate = if (dateUpdateIdx >= 0) parseCsvLong(cells.getOrNull(dateUpdateIdx)) ?: 0L else 0L
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cells.getOrNull(onTimingIdx)) else false
        val sourceType = if (sourceTypeIdx >= 0) cells.getOrNull(sourceTypeIdx)?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cells.getOrNull(sourceIdx)?.trim() ?: "" else ""
        list.add(
            RoadMap(
                id = id,
                name = name,
                content = content,
                step = step,
                start = start,
                end = end,
                user = user,
                project = project,
                dateUpdate = dateUpdate,
                onTiming = onTiming,
                source_type = sourceType,
                source = source
            )
        )
    }
    return list
}

/** Читает значение ячейки как строку без использования DataFormatter (стабильно на Android). */
private fun cellToString(cell: Cell?): String? {
    if (cell == null) return null
    val type = cell.cellType
    val result = when (type) {
        CellType.STRING -> cell.stringCellValue.takeIf { it.isNotBlank() }
        CellType.NUMERIC -> {
            val n = cell.numericCellValue
            if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
        }
        CellType.BOOLEAN -> if (cell.booleanCellValue) "TRUE" else "FALSE"
        CellType.FORMULA -> {
            try {
                when (cell.cachedFormulaResultType) {
                    CellType.STRING -> cell.stringCellValue.takeIf { it.isNotBlank() }
                    CellType.NUMERIC -> {
                        val n = cell.numericCellValue
                        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
                    }
                    CellType.BOOLEAN -> if (cell.booleanCellValue) "TRUE" else "FALSE"
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
        CellType.BLANK, CellType.ERROR, CellType._NONE -> null
        else -> null
    }
    return result?.trim()?.takeIf { it.isNotBlank() }
}

/** Максимум строк диагностики в сообщении (чтобы не переполнять toast). */
private const val XLSX_DIAG_MAX_LINES = 12

/**
 * Результат загрузки XLSX: список заявок, размеры таблицы и диагностика (проект/user/applicant по GUID, причины пропусков).
 */
data class XlsxLoadResult(
    val issues: List<Issues>,
    val rowCount: Int,
    val colCount: Int,
    val diagnostics: List<String> = emptyList(),
    /** Имя листа, с которого читали данные. */
    val sheetName: String? = null,
    /** Имя книги из метаданных файла (свойство «Название» документа). */
    val workbookTitle: String? = null,
    /** Комментарии из примечаний ячеек колонки D (для слияния с exampleIssueComments). */
    val newComments: List<IssueComments> = emptyList(),
    /** Заметки с листа «notes» (если есть). */
    val newNotes: List<Notes> = emptyList(),
    /** Записи журнала с листа «projectLogs» (если есть). */
    val newProjectLogs: List<ProjectLogs> = emptyList()
)

// Фиксированные колонки для загрузки листа Issues (0-based). Одна колонка id (A), без GUID.
// A=id, B=Название, C=Описание, D=status, E=User, F=applicant, G=Проект, H=project, I=company, J=dateUpdate
private const val XLSX_COL_ID = 0
private const val XLSX_COL_STATUS = 3
private const val XLSX_COL_USER = 4
private const val XLSX_COL_APPLICANT = 5
private const val XLSX_COL_PROJECT = 7

/** Читает имя книги (название документа) из метаданных XLSX. Для xls или при ошибке — null. */
private fun getWorkbookTitle(workbook: Workbook): String? = try {
    (workbook as? XSSFWorkbook)?.getProperties()?.coreProperties?.title?.trim()?.takeIf { it.isNotBlank() }
} catch (_: Throwable) { null }

/** Ищет лист по имени (без учёта регистра). Если не найден — null. */
private fun findSheetByName(workbook: Workbook, name: String): Sheet? {
    for (i in 0 until workbook.numberOfSheets) {
        val s = workbook.getSheetAt(i) ?: continue
        if (s.sheetName.equals(name, ignoreCase = true)) return s
    }
    return null
}

/** Парсит лист «notes» в XLSX: первая строка — заголовки, далее данные. Загружаются только строки с done = False. */
private fun loadNotesFromSheet(
    sheet: Sheet,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies>
): List<Notes> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val companiesById = companies.associateBy { it.id.toString() }
    val headerRow = sheet.getRow(0) ?: return emptyList()
    val colCount = headerRow.lastCellNum.toInt()
    fun colIndex(name: String, vararg alt: String): Int {
        for (i in 0 until colCount) {
            val v = cellToString(headerRow.getCell(i)) ?: continue
            if (alt.any { v.equals(it, ignoreCase = true) } || v.equals(name, ignoreCase = true)) return i
        }
        return -1
    }
    val idIdx = colIndex("id", "guid").takeIf { it >= 0 } ?: return emptyList()
    val contentIdx = colIndex("content", "Описание").coerceAtLeast(0)
    val projectIdx = colIndex("project")
    val userIdx = colIndex("user", "User", "Пользователь")
    val applicantIdx = colIndex("applicant", "Applicant", "Постановщик")
    val companyIdx = colIndex("company")
    val doneIdx = colIndex("done")
    val dateUpdateIdx = colIndex("dateUpdate")
    val onTimingIdx = colIndex("onTiming")
    val sourceTypeIdx = colIndex("source_type")
    val sourceIdx = colIndex("source")
    val list = mutableListOf<Notes>()
    for (rowNum in 1..sheet.lastRowNum) {
        val row = sheet.getRow(rowNum) ?: continue
        val idRaw = cellToString(row.getCell(idIdx))?.trim()
        val content = if (contentIdx >= 0) cellToString(row.getCell(contentIdx))?.trim() ?: "" else ""
        val userRaw = if (userIdx >= 0) cellToString(row.getCell(userIdx))?.trim() else null
        val applicantRaw = if (applicantIdx >= 0) cellToString(row.getCell(applicantIdx))?.trim() else null
        val id = when {
            !idRaw.isNullOrBlank() -> idRaw
            content.isNotBlank() && !userRaw.isNullOrBlank() && !applicantRaw.isNullOrBlank() -> "$rowNum"
            else -> null
        } ?: continue
        val projectCell = if (projectIdx >= 0) cellToString(row.getCell(projectIdx)) else null
        val userRef = if (userIdx >= 0) userRaw?.takeIf { it.isNotBlank() } else null
        val applicantRef = if (applicantIdx >= 0) applicantRaw?.takeIf { it.isNotBlank() } else null
        val companyRef = if (companyIdx >= 0) cellToString(row.getCell(companyIdx))?.trim()?.takeIf { it.isNotBlank() } else null
        val project = resolveProjectFromCell(projectCell, projectsByRef)
        val (user, applicant) = resolveUserAndApplicant(userRef, applicantRef, usersByRef, null)
        val company = companyRef?.let { companiesById[it] } ?: companyRef?.toLongOrNull()?.let { companiesById[it.toString()] }
        val done = if (doneIdx >= 0) parseCsvBoolean(cellToString(row.getCell(doneIdx))) else false
        if (done) continue
        val dateUpdate = if (dateUpdateIdx >= 0) {
            val v = cellToString(row.getCell(dateUpdateIdx))?.trim()
            when {
                v.isNullOrBlank() -> 0L
                else -> (v.toDoubleOrNull()?.toLong() ?: v.toLongOrNull()) ?: 0L
            }
        } else 0L
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cellToString(row.getCell(onTimingIdx))) else false
        val sourceType = if (sourceTypeIdx >= 0) cellToString(row.getCell(sourceTypeIdx))?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cellToString(row.getCell(sourceIdx))?.trim() ?: "" else ""
        list.add(
            Notes(
                id = id,
                project = project,
                user = user,
                applicant = applicant,
                company = company,
                done = done,
                content = content,
                dateUpdate = dateUpdate,
                onTiming = onTiming,
                source_type = sourceType,
                source = source
            )
        )
    }
    return list
}

/** Серийный номер даты Excel для 1970-01-01 (дней с 1899-12-30). */
private const val EXCEL_EPOCH_1970 = 25569

/**
 * Парсит дату из ячейки (колонка date листа projectLogs).
 * Поддерживаются: Unix мс (число ≥ 1e12), Unix сек (1e9..1e10), серийный номер Excel (дни), строки дат.
 * [onDebug] — при необходимости пишет в лог прочитанное значение и выбранную ветку парсинга.
 */
private fun parseDateFromCell(s: String?, onDebug: (String) -> Unit = {}): Long {
    val v = s?.trim()
    onDebug("дата: прочитано значение = '${v ?: "(пусто)"}'")
    if (v.isNullOrBlank()) {
        onDebug("дата: пусто -> 0")
        return 0L
    }
    parseCsvLong(v)?.let { num ->
        if (num >= 1e12) {
            onDebug("дата: как число (Unix мс) -> $num")
            return num
        }
        if (num in 1_000_000_000L..10_000_000_000L) {
            val ms = num * 1000L
            onDebug("дата: как Unix сек -> $num * 1000 = $ms")
            return ms
        }
        if (num in 1000..1000000) {
            val excelMs = (num - EXCEL_EPOCH_1970) * 86400L * 1000L
            if (excelMs >= 0) {
                onDebug("дата: как серийный Excel (дни) -> $num -> ${excelMs} мс")
                return excelMs
            }
            onDebug("дата: серийный Excel $num дал отрицательную дату, отбрасываем -> 0")
            return 0L
        }
        onDebug("дата: как число без преобразования -> $num")
        return num
    }
    v.toDoubleOrNull()?.let { num ->
        if (num >= 1e12) {
            onDebug("дата: как double (Unix мс) -> ${num.toLong()}")
            return num.toLong()
        }
        if (num in 1e9..1e10) {
            val ms = (num * 1000).toLong()
            onDebug("дата: как double Unix сек -> $ms мс")
            return ms
        }
        if (num in 1000.0..1000000.0) {
            val excelMs = ((num - EXCEL_EPOCH_1970) * 86400 * 1000).toLong()
            if (excelMs >= 0) {
                onDebug("дата: как double Excel (дни) -> $num -> $excelMs мс")
                return excelMs
            }
        }
    }
    val formats = listOf(
        "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
        "yyyy/MM/dd",
        "dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy", "d.M.yy",
        "dd/MM/yyyy", "d/M/yyyy"
    )
    for (pattern in formats) {
        try {
            java.text.SimpleDateFormat(pattern, Locale.US).parse(v)?.time?.let { ms ->
                onDebug("дата: как строка формат '$pattern' -> $ms мс")
                return ms
            }
        } catch (_: Exception) { }
    }
    onDebug("дата: ни один формат не подошёл -> 0")
    return 0L
}

/** Парсит лист «projectLogs» в XLSX: первая строка — заголовки (id, type, date, content, agenda, resolution, user, applicant, project, ...), без колонки GUID. Строки с пустым id пропускаются. */
private fun loadProjectLogsFromSheet(
    sheet: Sheet,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies>
): List<ProjectLogs> {
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val headerRow = sheet.getRow(0) ?: return emptyList()
    val colCount = headerRow.lastCellNum.toInt()
    fun colIndex(name: String, vararg alt: String): Int {
        for (i in 0 until colCount) {
            val v = cellToString(headerRow.getCell(i)) ?: continue
            if (alt.any { v.equals(it, ignoreCase = true) } || v.equals(name, ignoreCase = true)) return i
        }
        return -1
    }
    val idIdx = colIndex("id").takeIf { it >= 0 } ?: return emptyList()
    val typeIdx = colIndex("type")
    val dateIdx = colIndex("date")
    val contentIdx = colIndex("content", "Описание")
    val agendaIdx = colIndex("agenda")
    val resolutionIdx = colIndex("resolution")
    val userIdx = colIndex("user")
    val applicantIdx = colIndex("applicant")
    val projectIdx = colIndex("project")
    val onTimingIdx = colIndex("onTiming")
    val sourceTypeIdx = colIndex("source_type")
    val sourceIdx = colIndex("source")
    val list = mutableListOf<ProjectLogs>()
    for (rowNum in 1..sheet.lastRowNum) {
        val row = sheet.getRow(rowNum) ?: continue
        val idRaw = cellToString(row.getCell(idIdx))?.trim()
        if (idRaw.isNullOrBlank()) continue
        val id = idRaw
        val type = if (typeIdx >= 0) cellToString(row.getCell(typeIdx))?.trim() ?: "" else ""
        val dateStr = if (dateIdx >= 0) cellToString(row.getCell(dateIdx)) else null
        val date = parseDateFromCell(dateStr)
        val content = if (contentIdx >= 0) cellToString(row.getCell(contentIdx))?.trim() ?: "" else ""
        val agenda = if (agendaIdx >= 0) cellToString(row.getCell(agendaIdx))?.trim() ?: "" else ""
        val resolution = if (resolutionIdx >= 0) cellToString(row.getCell(resolutionIdx))?.trim() ?: "" else ""
        val projectCell = if (projectIdx >= 0) cellToString(row.getCell(projectIdx)) else null
        val userRef = if (userIdx >= 0) cellToString(row.getCell(userIdx))?.trim()?.takeIf { it.isNotBlank() } else null
        val applicantRef = if (applicantIdx >= 0) cellToString(row.getCell(applicantIdx))?.trim()?.takeIf { it.isNotBlank() } else null
        val project = resolveProjectFromCell(projectCell, projectsByRef)
        val (user, applicant) = resolveUserAndApplicant(userRef, applicantRef, usersByRef, null)
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cellToString(row.getCell(onTimingIdx))) else false
        val sourceType = if (sourceTypeIdx >= 0) cellToString(row.getCell(sourceTypeIdx))?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cellToString(row.getCell(sourceIdx))?.trim() ?: "" else ""
        list.add(
            ProjectLogs(
                id = id,
                project = project,
                user = user,
                applicant = applicant,
                content = content,
                agenda = agenda,
                resolution = resolution,
                type = type,
                date = date,
                onTiming = onTiming,
                source_type = sourceType,
                source = source
            )
        )
    }
    return list
}

/**
 * Парсит XLSX: сначала ищется лист с именем "issues", иначе берётся первый лист.
 * Первая строка — заголовки, далее данные. Колонка A — id заявки; далее Название, Описание, status, User, applicant, project.
 */
private fun ensurePoiStaxFactories() {
    try {
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    } catch (_: Throwable) { }
}

fun loadIssuesFromXlsx(
    inputStream: InputStream,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies>
): XlsxLoadResult {
    ensurePoiStaxFactories()
    // На Android/при скачивании по ссылке POI может выбросить из-за лимита размера записи — временно повышаем лимит
    try {
        IOUtils.setByteArrayMaxOverride(Integer.MAX_VALUE)
    } catch (_: Throwable) { }
    val usersByRef = usersByIdAndLegacy(users)
    val projectsByRef = projectsByIdAndLegacy(projects)
    val companiesById = companies.associateBy { it.id.toString() }
    val workbook = WorkbookFactory.create(inputStream)
    val workbookTitle = getWorkbookTitle(workbook)
    val sheet = findSheetByName(workbook, "issues") ?: workbook.getSheetAt(0) ?: run {
        workbook.close()
        return XlsxLoadResult(emptyList(), 0, 0, listOf("В файле нет листов или первый лист пуст."), null, workbookTitle)
    }
    val sheetName = sheet.sheetName
    val headerRow = sheet.getRow(0) ?: run {
        workbook.close()
        return XlsxLoadResult(emptyList(), 0, 0, listOf("Первая строка (заголовки) отсутствует."), sheetName, workbookTitle)
    }
    val colCount = headerRow.lastCellNum.toInt()
    fun colIndex(name: String, vararg alt: String): Int {
        val lastCol = headerRow.lastCellNum.toInt()
        for (i in 0 until lastCol) {
            val v = cellToString(headerRow.getCell(i)) ?: continue
            if (alt.any { v.equals(it, ignoreCase = true) } || v.equals(name, ignoreCase = true)) return i
        }
        return -1
    }
    // Колонка A (0) = id заявки; при выборе по заголовкам — "id" или "guid"
    val idIdx = if (XLSX_COL_ID < colCount) XLSX_COL_ID else (colIndex("id").takeIf { it >= 0 } ?: colIndex("guid").takeIf { it >= 0 } ?: run {
        workbook.close()
        return XlsxLoadResult(emptyList(), 1, colCount, listOf("Не найдена колонка id заявки (ожидается колонка A или заголовок id/guid)."), sheetName, workbookTitle)
    })
    val nameIdx = colIndex("name", "Название").takeIf { it >= 0 } ?: 1
    val contentIdx = colIndex("content", "Описание").takeIf { it >= 0 } ?: 2
    val statusIdx = if (XLSX_COL_STATUS < colCount) XLSX_COL_STATUS else colIndex("status")
    val userIdx = if (XLSX_COL_USER < colCount) XLSX_COL_USER else colIndex("userId", "user")
    val applicantIdx = if (XLSX_COL_APPLICANT < colCount) XLSX_COL_APPLICANT else colIndex("applicantId", "applicant")
    val projectIdx = if (XLSX_COL_PROJECT < colCount) XLSX_COL_PROJECT else colIndex("projectId", "project", "Проект")
    val companyIdx = colIndex("company")
    val dateUpdateIdx = colIndex("dateUpdate")
    val onTimingIdx = colIndex("onTiming")
    val newCommentForUserIdx = colIndex("newCommentForUser")
    val newCommentForApplicantIdx = colIndex("newCommentForApplicant")
    val sourceTypeIdx = colIndex("source_type", "sourceType")
    val sourceIdx = colIndex("source")
    val list = mutableListOf<Issues>()
    val newCommentsList = mutableListOf<IssueComments>()
    val diagnostics = mutableListOf<String>()
    val totalRows = (sheet.lastRowNum + 1).coerceAtLeast(1)
    if (colCount < 1) {
        workbook.close()
        return XlsxLoadResult(
            emptyList(),
            totalRows,
            colCount,
            listOf("Недостаточно колонок: $colCount (нужна минимум колонка A для id заявки)."),
            sheetName,
            workbookTitle
        )
    }
    fun diagLine(rowNum: Int, created: Boolean, projectRef: String?, project: Projects?, userRef: String?, user: Users?, applicantRef: String?, applicant: Users?, reason: String? = null): String {
        val proj = projectRef?.let { "проект $it — ${project?.name ?: "не найден"}" } ?: "проект не указан"
        val us = userRef?.let { "user $it — ${user?.displayName ?: "не найден"}" } ?: "user не указан"
        val app = applicantRef?.let { "applicant $it — ${applicant?.displayName ?: "не найден"}" } ?: "applicant не указан"
        val action = if (created) "создана" else "не создана — $reason"
        return "Строка ${rowNum + 1}: $action. $proj, $us, $app."
    }
    for (rowNum in 1..sheet.lastRowNum) {
        val row = sheet.getRow(rowNum) ?: continue
        val idRaw = cellToString(row.getCell(idIdx))?.trim()
        val name = cellToString(row.getCell(nameIdx))?.trim() ?: ""
        val userRaw = if (userIdx >= 0) cellToString(row.getCell(userIdx))?.trim() else null
        val applicantRaw = if (applicantIdx >= 0) cellToString(row.getCell(applicantIdx))?.trim() else null
        val id = when {
            !idRaw.isNullOrBlank() -> parseIssueIdFromCell(idRaw)
            name.isNotBlank() && !userRaw.isNullOrBlank() && !applicantRaw.isNullOrBlank() -> "legacy-$rowNum"
            else -> null
        }
        val content = if (contentIdx >= 0) cellToString(row.getCell(contentIdx)) ?: "" else ""
        val statusStr = if (statusIdx >= 0) cellToString(row.getCell(statusIdx)) ?: "NEW" else "NEW"
        val status = parseIssueStatus(if (statusStr.contains(".")) statusStr.substringAfterLast(".") else statusStr)
        val userRef = if (userIdx >= 0) userRaw?.takeIf { it.isNotBlank() } else null
        val applicantRef = if (applicantIdx >= 0) applicantRaw?.takeIf { it.isNotBlank() } else null
        val projectCell = if (projectIdx >= 0) cellToString(row.getCell(projectIdx)) else null
        val companyRef = if (companyIdx >= 0) cellToString(row.getCell(companyIdx))?.trim()?.takeIf { it.isNotBlank() } else null
        val (user, applicant) = resolveUserAndApplicant(userRef, applicantRef, usersByRef, null)
        val project = resolveProjectFromCell(projectCell, projectsByRef)
        val projectRef = parseIssueIdFromCell(projectCell)
        val company = companyRef?.let { companiesById[it] } ?: companyRef?.toLongOrNull()?.let { companiesById[it.toString()] }
        if (id == null) {
            val reason = when {
                idRaw.isNullOrBlank() -> "пустой id (для автоподстановки нужны Название, User и Applicant)"
                else -> "неверный id: \"$idRaw\""
            }
            diagnostics.add(diagLine(rowNum, false, projectRef, project, userRef, user, applicantRef, applicant, reason))
            continue
        }
        val dateUpdate = if (dateUpdateIdx >= 0) parseCsvLong(cellToString(row.getCell(dateUpdateIdx))) ?: 0L else 0L
        val onTiming = if (onTimingIdx >= 0) parseCsvBoolean(cellToString(row.getCell(onTimingIdx))) else false
        val newCommentForUser = if (newCommentForUserIdx >= 0) parseCsvBoolean(cellToString(row.getCell(newCommentForUserIdx))) else false
        val newCommentForApplicant = if (newCommentForApplicantIdx >= 0) parseCsvBoolean(cellToString(row.getCell(newCommentForApplicantIdx))) else false
        val sourceType = if (sourceTypeIdx >= 0) cellToString(row.getCell(sourceTypeIdx))?.trim() ?: "" else ""
        val source = if (sourceIdx >= 0) cellToString(row.getCell(sourceIdx))?.trim() ?: "" else ""
        diagnostics.add(diagLine(rowNum, true, projectRef, project, userRef, user, applicantRef, applicant))
        val issue = Issues(
            id = id,
            name = name,
            content = content,
            status = status,
            user = user,
            applicant = applicant,
            project = project,
            company = company,
            dateUpdate = dateUpdate,
            onTiming = onTiming,
            newCommentForUser = newCommentForUser,
            newCommentForApplicant = newCommentForApplicant,
            source_type = sourceType,
            source = source
        )
        list.add(issue)
        val cellD = row.getCell(contentIdx)
        val noteStr = cellD?.cellComment?.string?.string?.takeIf { it.isNotBlank() }
        newCommentsList.addAll(parseCommentsFromNote(noteStr, issue, users))
    }
    val newNotesList = findSheetByName(workbook, "notes")?.let { loadNotesFromSheet(it, users, projects, companies) } ?: emptyList()
    val newProjectLogsList = findSheetByName(workbook, "projectLogs")?.let { loadProjectLogsFromSheet(it, users, projects, companies) } ?: emptyList()
    workbook.close()
    val diagList = if (diagnostics.size > XLSX_DIAG_MAX_LINES) {
        diagnostics.take(XLSX_DIAG_MAX_LINES) + "… ещё ${diagnostics.size - XLSX_DIAG_MAX_LINES} строк."
    } else diagnostics
    return XlsxLoadResult(list, totalRows, colCount, diagList, sheetName, workbookTitle, newCommentsList, newNotesList, newProjectLogsList)
}

private fun parseIssueStatus(value: String): IssueStatuses =
    IssueStatuses.entries.find { it.name.equals(value, ignoreCase = true) } ?: IssueStatuses.NEW

/**
 * Разрешает пользователя из JSON: по userId/guid (число -> legacy-N, строка -> id или email).
 */
private fun resolveUser(obj: JSONObject, key: String, usersByRef: Map<String, Users>, usersByEmail: Map<String, Users>): Users? {
    val ref = parseRefId(obj, key) ?: return null
    return usersByRef[ref] ?: usersByEmail[ref]
}

/**
 * Разрешает проект из JSON: по projectId (число/строка) или по projectSlug (строка).
 */
private fun resolveProject(obj: JSONObject, projectsByRef: Map<String, Projects>, projectsBySlug: Map<String, Projects>): Projects? {
    parseRefId(obj, "projectId")?.let { return projectsByRef[it] }
    obj.optString("projectSlug", null).takeIf { it.isNotBlank() }?.let { return projectsBySlug[it] }
    return null
}

/**
 * Парсит один JSON-объект заявки (содержимое одного файла).
 * Пользователи: userId (число) или userId (строка = email); applicantId — аналогично.
 * Проект: projectId (число) или projectSlug (строка = slug проекта).
 * Комментарии: массив "comments" в JSON загружается и возвращается в ParseIssueResult.
 */
fun parseSingleIssueFromJson(
    jsonString: String,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies> = emptyList()
): ParseIssueResult {
    val usersById = users.associateBy { it.id }
    val usersByEmail = users.associateBy { it.email }
    val projectsById = projects.associateBy { it.id }
    val projectsBySlug = projects.associateBy { it.slug }
    val companiesById = companies.associateBy { it.id.toString() }
    val obj = JSONObject(jsonString)
    val id = parseIssueIdFromJson(obj)
    val name = obj.getString("name")
    val content = obj.optString("content", "")
    val statusStr = obj.optString("status", "NEW")
    val status = parseIssueStatus(statusStr)
    val user = resolveUser(obj, "userId", usersById, usersByEmail)
    val applicant = resolveUser(obj, "applicantId", usersById, usersByEmail)
    val project = resolveProject(obj, projectsById, projectsBySlug)
    val companyId = parseRefId(obj, "companyId") ?: obj.optString("company", null)?.takeIf { it.isNotBlank() }
    val company = companyId?.let { companiesById[it] } ?: companyId?.toLongOrNull()?.let { companiesById[it.toString()] }
    val dateUpdate = obj.optLong("dateUpdate", 0L)
    val issue = Issues(
        id = id,
        name = name,
        content = content,
        status = status,
        user = user,
        applicant = applicant,
        project = project,
        company = company,
        dateUpdate = dateUpdate
    )
    val comments = parseCommentsFromJson(obj.optJSONArray("comments"), issue, usersById, usersByEmail)
    return ParseIssueResult(issue, comments)
}

/**
 * Результат парсинга файла заявки: заявка и список комментариев.
 */
data class ParseIssueResult(val issue: Issues, val comments: List<IssueComments>)

private fun parseCommentsFromJson(
    arr: org.json.JSONArray?,
    issue: Issues,
    usersByRef: Map<String, Users>,
    usersByEmail: Map<String, Users>
): List<IssueComments> {
    if (arr == null || arr.length() == 0) return emptyList()
    val list = mutableListOf<IssueComments>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val idStr = obj.optString("id", "")
        val id = idStr.takeIf { it.length == 32 } ?: generateCommentId()
        val user = resolveUser(obj, "userId", usersByRef, usersByEmail)
        val author = resolveUser(obj, "authorId", usersByRef, usersByEmail) ?: user
        val comment = obj.optString("comment", "")
        val dateCreate = obj.optLong("dateCreate", 0L)
        val statusSet = obj.optString("statusSet", null)?.takeIf { it.isNotBlank() }
            ?.let { parseIssueStatus(it) }
        list.add(IssueComments(id, issue, user, author, comment, dateCreate, statusSet))
    }
    return list
}

/**
 * Парсит из имени файла дату обновления (блок yyyyMMdd_HHmmss) и возвращает timestamp в мс, или null.
 */
fun parseFileDateFromFileName(fileName: String): Long? {
    if (!fileName.endsWith(".json")) return null
    val parts = fileName.dropLast(5).split("_")
    if (parts.size < 2) return null
    val datePart = parts.takeLast(2).joinToString("_")
    return runCatching {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(datePart)?.time
    }.getOrNull()
}

/** Из имени файла извлекает id заявки (GUID или legacy-N; блок перед датой). */
fun parseIssueIdFromFileName(fileName: String): String? {
    if (!fileName.endsWith(".json")) return null
    val parts = fileName.dropLast(5).split("_")
    if (parts.size < 4) return null
    return parts[parts.size - 3].takeIf { it.isNotBlank() }
}

/**
 * Загружает заявки пользователя из рабочего каталога.
 * Если у уже загруженной заявки (по id) dateUpdate >= дате из имени файла, файл не читается.
 *
 * @param existingIssues уже имеющиеся заявки — если dateUpdate в заявке >= дате в имени файла, файл не грузится
 * @param commentsOut сюда добавляются загруженные комментарии; перед добавлением удаляются комментарии по этой заявке (по issue.id)
 * @return список заявок пользователя (из файлов с новой информацией)
 */
fun loadIssuesForUser(
    workDir: File,
    user: Users,
    users: List<Users>,
    projects: List<Projects>,
    existingIssues: List<Issues> = emptyList(),
    commentsOut: MutableList<IssueComments>? = null
): List<Issues> {
    val folderName = userFolderName(user)
    val userDir = File(workDir, folderName)
    if (!userDir.isDirectory) return emptyList()
    val prefix = "${folderName}_"
    val existingById = existingIssues.associateBy { it.id }
    val list = mutableListOf<Issues>()
    userDir.listFiles()?.filter { file ->
        file.isFile && file.name.endsWith(".json") && file.name.startsWith(prefix)
    }?.forEach { file ->
        val fileDate = parseFileDateFromFileName(file.name) ?: 0L
        val issueId = parseIssueIdFromFileName(file.name)
        val existing = issueId?.let { existingById[it] }
        if (existing != null && existing.dateUpdate >= fileDate) return@forEach
        runCatching {
            val json = file.readText()
            val result = parseSingleIssueFromJson(json, users, projects)
            result.issue.dateUpdate = fileDate
            list.add(result.issue)
            commentsOut?.let { out ->
                out.removeAll { it.issue.id == result.issue.id }
                out.addAll(result.comments)
            }
        }
    }
    return list
}

private val issueFileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

/**
 * Формирует имя файла заявки: {email}_{projectSlug}_{номерЗаявки}_{датаВремя}.json
 * Без проекта — {email}_noproject_{номерЗаявки}_{датаВремя}.json
 */
fun issueFileName(
    user: Users,
    issue: Issues,
    dateTime: Long = System.currentTimeMillis()
): String {
    val datePart = issueFileNameDateFormat.format(Date(dateTime))
    val projectSlug = issue.project?.slug ?: "noproject"
    return "${userFolderName(user)}_${projectSlug}_${issue.id}_$datePart.json"
}

/**
 * Каталог заявок пользователя: workDir / user.email
 */
fun userIssuesDir(workDir: File, user: Users): File = File(workDir, userFolderName(user))

/** Разделитель полей в примечании/ноте ячейки: "Автор | дата | текст". */
private const val NOTE_COMMENT_DELIM = " | "
private val noteCommentDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

/** Форматирует комментарии заявки в одну строку для примечания ячейки (колонка D). */
fun formatCommentsAsNote(comments: List<IssueComments>): String =
    comments
        .filter { it.issue.id.isNotBlank() }
        .sortedBy { it.dateCreate }
        .joinToString("\n") { c ->
            val author = c.author?.displayName ?: c.user?.displayName ?: "—"
            val dateStr = noteCommentDateFormat.format(Date(c.dateCreate))
            val text = (c.comment ?: "").replace("\n", " ")
            "$author$NOTE_COMMENT_DELIM$dateStr$NOTE_COMMENT_DELIM$text"
        }

/**
 * Парсит примечание ячейки (колонка D) в список комментариев.
 * Формат строки: "Автор | dd.MM.yyyy HH:mm | текст" по одной строке на комментарий.
 */
fun parseCommentsFromNote(
    noteText: String?,
    issue: Issues,
    users: List<Users>
): List<IssueComments> {
    if (noteText.isNullOrBlank()) return emptyList()
    val usersByDisplayName = users.associateBy { it.displayName }
    val list = mutableListOf<IssueComments>()
    noteText.trim().split('\n').forEach { line ->
        val parts = line.split(NOTE_COMMENT_DELIM, limit = 3)
        if (parts.size < 3) return@forEach
        val authorName = parts[0].trim()
        val dateStr = parts[1].trim()
        val text = parts[2].trim()
        val author = usersByDisplayName[authorName] ?: users.firstOrNull()
        val dateMs = try {
            noteCommentDateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        list.add(
            IssueComments(
                id = generateCommentId(),
                issue = issue,
                user = author,
                author = author,
                comment = text,
                dateCreate = dateMs
            )
        )
    }
    return list
}

/**
 * Сериализует заявку в JSON-строку (формат, совместимый с parseSingleIssueFromJson).
 * Включает массив comments для комментариев по этой заявке.
 */
fun issueToJson(issue: Issues, commentsForIssue: List<IssueComments> = emptyList()): String {
    val commentsArray = JSONArray()
    commentsForIssue.filter { it.issue.id == issue.id }.forEach { c ->
        commentsArray.put(JSONObject().apply {
            put("id", c.id)
            put("userId", c.user?.id ?: JSONObject.NULL)
            put("authorId", c.author?.id ?: c.user?.id ?: JSONObject.NULL)
            put("comment", c.comment)
            put("dateCreate", c.dateCreate)
            put("statusSet", c.statusSet?.name ?: JSONObject.NULL)
        })
    }
    val obj = JSONObject().apply {
        put("id", issue.id)
        put("guid", issue.id)
        put("name", issue.name)
        put("content", issue.content)
        put("status", issue.status.name)
        put("userId", issue.user?.id ?: JSONObject.NULL)
        put("applicantId", issue.applicant?.id ?: JSONObject.NULL)
        put("projectId", issue.project?.id ?: JSONObject.NULL)
        put("dateUpdate", issue.dateUpdate)
        put("comments", commentsArray)
    }
    return obj.toString(2)
}

/**
 * Сохраняет одну заявку в рабочий каталог (с массивом комментариев в JSON).
 * Устанавливает issue.dateUpdate = текущее время и использует его в имени файла.
 * В качестве каталога пользователя берётся ответственный (issue.user); если null — applicant.
 */
fun saveIssueToWorkDir(workDir: File, issue: Issues, commentsForIssue: List<IssueComments> = emptyList()): Boolean {
    val user = issue.user ?: issue.applicant ?: return false
    val dir = userIssuesDir(workDir, user)
    if (!dir.exists() && !dir.mkdirs()) return false
    val dateTime = System.currentTimeMillis()
    issue.dateUpdate = dateTime
    val file = File(dir, issueFileName(user, issue, dateTime))
    return runCatching { file.writeText(issueToJson(issue, commentsForIssue)) }.isSuccess
}

/**
 * Сохраняет тестовые заявки (exampleIssues) в рабочий каталог — по файлу на заявку в папке ответственного.
 * Создаёт workDir, если его нет. Возвращает число сохранённых файлов и сообщение об ошибке (если есть).
 */
fun saveExampleIssuesToWorkDir(workDir: File): Pair<Int, String?> {
    if (!workDir.exists() && !workDir.mkdirs()) {
        return 0 to "Не удалось создать каталог: ${workDir.absolutePath}"
    }
    if (!workDir.isDirectory) return 0 to "Путь не является каталогом: ${workDir.absolutePath}"
    val issues = emptyList<Issues>()
    var saved = 0
    for (issue in issues) {
        val commentsForIssue = exampleIssueComments.filter { it.issue.id == issue.id }
        if (saveIssueToWorkDir(workDir, issue, commentsForIssue)) saved++
    }
    return if (saved == 0 && issues.isNotEmpty()) {
        0 to "Не удалось записать файлы (проверьте путь и доступ)"
    } else {
        saved to null
    }
}

/** Имя файла для выгрузки заявок в Excel. */
const val ISSUES_EXPORT_XLSX = "issues_export.xlsx"

/**
 * Выгружает список заявок в XLSX (структура как при импорте: колонка A=id, далее Название, Описание, status, User, applicant, project).
 * Лист «Issues», первая строка — заголовки, далее по строке на заявку.
 * @return путь к файлу при успехе, null при ошибке
 */
fun exportIssuesToXlsx(
    issues: List<Issues>,
    outputFile: File,
    getCommentsForIssue: (Issues) -> List<IssueComments> = { emptyList() },
    notes: List<Notes> = emptyList()
): String? {
    return try {
        ensurePoiStaxFactories()
        try { IOUtils.setByteArrayMaxOverride(Integer.MAX_VALUE) } catch (_: Throwable) { }
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Issues")
        val header = sheet.createRow(0)
        listOf("id", "Название", "Описание", "status", "User", "applicant", "project", "company", "dateUpdate", "onTiming", "newCommentForUser", "newCommentForApplicant", "source_type", "source").forEachIndexed { i, v ->
            header.createCell(i).setCellValue(v)
        }
        val drawing = sheet.createDrawingPatriarch()
        val factory: CreationHelper = workbook.creationHelper
        issues.forEachIndexed { idx, issue ->
            val row = sheet.createRow(idx + 1)
            val excelRow1Based = idx + 2
            row.createCell(0).setCellValue(issueIdToSheetId(issue.id))
            row.createCell(1).setCellValue(issue.name)
            val cellD = row.createCell(2)
            cellD.setCellValue(issue.content)
            val noteText = formatCommentsAsNote(getCommentsForIssue(issue))
            if (noteText.isNotBlank()) {
                val anchor = factory.createClientAnchor().apply {
                    setCol1(2); setRow1(idx + 1); setCol2(4); setRow2(idx + 3)
                }
                val comment = drawing.createCellComment(anchor)
                comment.string = XSSFRichTextString(noteText)
                cellD.cellComment = comment
            }
            row.createCell(3).setCellValue(issue.status.name)
            row.createCell(4).setCellValue(issue.user?.id ?: "")
            row.createCell(5).setCellValue(issue.applicant?.id ?: "")
            row.createCell(6).setCellValue(issue.project?.id ?: "")
            row.createCell(7).setCellValue(issue.company?.id?.toString() ?: "")
            row.createCell(8).setCellValue(issue.dateUpdate.toDouble())
            row.createCell(9).setCellValue(if (issue.onTiming) "ИСТИНА" else "ЛОЖЬ")
            row.createCell(10).setCellValue(if (issue.newCommentForUser) "ИСТИНА" else "ЛОЖЬ")
            row.createCell(11).setCellValue(if (issue.newCommentForApplicant) "ИСТИНА" else "ЛОЖЬ")
            row.createCell(12).setCellValue(issue.source_type)
            row.createCell(13).setCellValue(issue.source)
        }
        if (notes.isNotEmpty()) {
            val notesSheet = workbook.createSheet("notes")
            val notesHeader = notesSheet.createRow(0)
            listOf("id", "done", "Описание", "User", "applicant", "Проект", "project", "company", "dateUpdate", "onTiming", "source_type", "source").forEachIndexed { i, v ->
                notesHeader.createCell(i).setCellValue(v)
            }
            notes.forEachIndexed { idx, note ->
                val row = notesSheet.createRow(idx + 1)
                val excelRow1Based = idx + 2
                val idForSheet = "note-${excelRow1Based - 1}"
                row.createCell(0).setCellValue(idForSheet)
                row.createCell(1).setCellValue(if (note.done) "TRUE" else "FALSE")
                row.createCell(2).setCellValue(note.content)
                row.createCell(3).setCellValue(note.user?.id ?: "")
                row.createCell(4).setCellValue(note.applicant?.id ?: "")
                row.createCell(5).setCellValue(note.project?.name ?: "")
                row.createCell(6).setCellValue(note.project?.id ?: "")
                row.createCell(7).setCellValue(note.company?.id?.toString() ?: "")
                row.createCell(8).setCellValue("")
                row.createCell(9).setCellValue(if (note.onTiming) "TRUE" else "FALSE")
                row.createCell(10).setCellValue(note.source_type)
                row.createCell(11).setCellValue(note.source)
            }
        }
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { workbook.write(it) }
        workbook.close()
        outputFile.absolutePath
    } catch (e: Throwable) {
        null
    }
}

/**
 * Результат экспорта при сохранении заявки: сообщение и (при добавлении новой строки в Google) новый id по шаблону "issue-" + (строка−1).
 */
data class ExportBackResult(val message: String, val newIssueId: String? = null)

/**
 * При сохранении заявки — записывает изменения в Google Таблицу (лист Issues и при необходимости лист notes).
 * @param getCommentsForIssue функция, возвращающая комментарии по заявке (для примечания колонки D)
 * @param allNotes список заметок для выгрузки на лист «notes»
 * @return результат с сообщением и опционально newIssueId при добавлении новой заявки в Google
 */
fun exportBackToTableOnSave(
    updatedIssue: Issues,
    allIssues: List<Issues>,
    tableUrl: String,
    googleAccessToken: String? = null,
    getCommentsForIssue: (Issues) -> List<IssueComments> = { emptyList() },
    allNotes: List<Notes> = emptyList()
): ExportBackResult {
    val token = googleAccessToken?.trim()?.takeIf { it.isNotBlank() }
    val spreadsheetId = extractGoogleSpreadsheetId(tableUrl.trim())
    var googleRow: Int? = null
    var googleError: String? = null
    var newIssueId: String? = null
    if (spreadsheetId != null && token != null) {
        val comments = getCommentsForIssue(updatedIssue)
        val res = updateIssueInGoogleSheet(updatedIssue, allIssues, tableUrl, token, comments)
        if (res.error == null) {
            googleRow = res.row
            newIssueId = res.newIssueId
        } else googleError = res.error
    }
    val rowInfo = if (googleRow != null) " Лист Issues, строка $googleRow." else ""
    val message = when {
        googleRow != null -> "Данные записаны в Google Таблицу.$rowInfo"
        googleError != null -> "Не удалось обновить Google Таблицу: $googleError"
        token.isNullOrBlank() -> "Укажите токен Google в настройках для записи в таблицу."
        spreadsheetId == null -> "Неверная ссылка на Google Таблицу."
        else -> "Не удалось записать в Google Таблицу."
    }
    return ExportBackResult(message, newIssueId)
}

/**
 * При сохранении заметки — записывает на лист «notes» в Google Таблицу.
 * Если передан updatedNote с непустым id — обновляется только строка этой заметки; иначе выгружается весь список (для новой заметки).
 */
fun exportNotesToTableOnSave(
    allNotes: List<Notes>,
    allIssues: List<Issues>,
    tableUrl: String,
    googleAccessToken: String? = null,
    getCommentsForIssue: (Issues) -> List<IssueComments> = { emptyList() },
    updatedNote: Notes? = null
): String {
    val token = googleAccessToken?.trim()?.takeIf { it.isNotBlank() }
    val spreadsheetId = extractGoogleSpreadsheetId(tableUrl.trim())
    var notesWrittenToGoogle = false
    var googleError: String? = null
    if (spreadsheetId != null && token != null) {
        notesWrittenToGoogle = when {
            updatedNote != null && updatedNote.id.isNotEmpty() -> writeSingleNoteToGoogleSheet(spreadsheetId, token, updatedNote)
            allNotes.isNotEmpty() -> writeNotesToGoogleSheet(spreadsheetId, token, allNotes)
            else -> false
        }
        if (!notesWrittenToGoogle) {
            googleError = if (updatedNote != null && updatedNote.id.isNotEmpty()) "Не удалось обновить заметку в Google Таблицу"
            else "Не удалось записать заметки в Google Таблицу"
        }
    }
    return when {
        notesWrittenToGoogle -> if (updatedNote != null && updatedNote.id.isNotEmpty()) "Заметка обновлена в Google Таблицу." else "Заметки записаны в Google Таблицу."
        googleError != null -> googleError
        token.isNullOrBlank() -> "Укажите токен Google в настройках для записи в таблицу."
        spreadsheetId == null -> "Неверная ссылка на Google Таблицу."
        else -> "Не удалось записать заметки в Google Таблицу."
    }
}

/**
 * Загружает заявки из JSON-файла в папке assets.
 *
 * @param context контекст приложения (для доступа к assets)
 * @param assetFileName имя файла в assets (например, "issues_test.json")
 * @param users список пользователей для разрешения ссылок
 * @param projects список проектов для разрешения ссылок
 * @return список заявок
 */
fun loadIssuesFromAssets(
    context: Context,
    assetFileName: String,
    users: List<Users>,
    projects: List<Projects>
): List<Issues> {
    val json = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
    return loadIssuesFromJson(json, users, projects)
}
