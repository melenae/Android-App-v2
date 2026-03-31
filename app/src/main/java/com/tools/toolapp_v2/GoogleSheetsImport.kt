package com.tools.toolapp_v2

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Загрузка данных из Google Таблиц (Google Sheets).
 * Таблица должна быть доступна по ссылке («Настройки доступа» → «Все, у кого есть ссылка»).
 * Читается лист Issues: при доступе по API — сначала лист с названием «Issues» (как notes/roadMaps),
 * иначе gid из ссылки или первый лист (gid=0). Ссылка с #gid=… на другой лист (например roadMaps) больше не подменяет заявки.
 */

/** Извлекает ID таблицы из ссылки вида https://docs.google.com/spreadsheets/d/ID/edit... */
fun extractGoogleSpreadsheetId(url: String): String? {
    val u = url.trim()
    val prefix = "docs.google.com/spreadsheets/d/"
    val i = u.lowercase().indexOf(prefix)
    if (i < 0) return null
    val start = i + prefix.length
    val rest = u.substring(start)
    val end = rest.indexOfAny(charArrayOf('/', '?', '#'))
    return if (end < 0) rest.takeIf { it.isNotBlank() } else rest.substring(0, end).takeIf { it.isNotBlank() }
}

/**
 * Извлекает gid листа из ссылки (edit#gid=12345).
 * Если не найден — возвращается null (используется gid=0 по умолчанию).
 */
fun extractGoogleSheetGid(url: String): Int? {
    val gidMatch = Regex("[#&]gid=(\\d+)").find(url) ?: return null
    return gidMatch.groupValues.getOrNull(1)?.toIntOrNull()
}

/**
 * Скачивает лист Google Таблицы как CSV (без авторизации).
 * Работает только при включённом доступе «Все, у кого есть ссылка».
 */
fun fetchGoogleSheetAsCsv(
    spreadsheetId: String,
    gid: Int = 0
): String? {
    val exportUrl = "https://docs.google.com/spreadsheets/d/${URLEncoder.encode(spreadsheetId, "UTF-8")}/export?format=csv&gid=$gid"
    return try {
        val conn = URL(exportUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "ToolApp/1.0 (Android)")
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "text/csv, */*")
        conn.connect()
        if (conn.responseCode != 200) return null
        conn.getInputStream().bufferedReader().use { it.readText() }.trim()
    } catch (_: Exception) {
        null
    }
}

/**
 * Загружает данные листа через Sheets API v4 (с авторизацией).
 * Используется, когда доступ по ссылке отключён, но пользователь вошёл в Google.
 * @return CSV-подобная строка (первая строка — заголовки) или null при ошибке
 */
private fun fetchGoogleSheetValuesViaApi(
    spreadsheetId: String,
    gid: Int,
    accessToken: String
): String? {
    val sheetName = getGoogleSheetNameByGid(spreadsheetId, gid, accessToken) ?: return null
    val range = "'${sheetName.replace("'", "''")}'!A1:Z1000"
    val json = sheetsGet(spreadsheetId, accessToken, "/values/${URLEncoder.encode(range, "UTF-8")}?valueRenderOption=FORMATTED_VALUE") ?: return null
    return try {
        val obj = JSONObject(json)
        val values = obj.optJSONArray("values") ?: return null
        if (values.length() == 0) return null
        val maxCols = (0 until values.length()).maxOfOrNull { (values.optJSONArray(it)?.length() ?: 0) } ?: 0
        if (maxCols == 0) return null
        fun cellStr(arr: JSONArray, col: Int): String {
            if (col >= arr.length()) return ""
            val v = arr.opt(col) ?: return ""
            return when (v) {
                is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                else -> v.toString().trim()
            }
        }
        fun rowToCsv(row: JSONArray): String {
            val cells = (0 until maxCols).map { cellStr(row, it) }
            return cells.joinToString(",") { cell ->
                val escaped = cell.replace("\"", "\"\"")
                if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) "\"$escaped\"" else escaped
            }
        }
        (0 until values.length()).mapNotNull { i -> values.optJSONArray(i)?.let { rowToCsv(it) } }.joinToString("\n")
    } catch (_: Exception) {
        null
    }
}

/** Заявка + id графика из колонки (для разрешения roadMap после загрузки). */
data class IssueLoadItem(val issue: Issues, val roadMapId: String)
/** Заметка + id графика из колонки (для разрешения roadMap после загрузки). */
data class NoteLoadItem(val note: Notes, val roadMapId: String)
/** Запись журнала + id графика из колонки (для разрешения roadMap после загрузки). */
data class ProjectLogLoadItem(val log: ProjectLogs, val roadMapId: String)

/**
 * Результат загрузки из Google Таблицы: список заявок, примечания из колонки C (Описание, комментарии), заметки с листа notes, записи журнала с листа projectLogs, элементы roadMaps и сообщение об ошибке.
 * Issues/Notes/ProjectLogs приходят с roadMap=null; roadMapId в *LoadItem используется для последующего разрешения в roadMap.
 */
data class GoogleSheetsLoadResult(
    val issueLoadItems: List<IssueLoadItem>,
    val error: String? = null,
    /** Комментарии, распознанные из примечаний ячеек колонки C (Описание) для слияния с exampleIssueComments. */
    val newComments: List<IssueComments> = emptyList(),
    /** Заметки с листа «notes» (roadMap разрешается по noteLoadItems[].roadMapId). */
    val noteLoadItems: List<NoteLoadItem> = emptyList(),
    /** Записи журнала с листа «projectLogs» (roadMap разрешается по projectLogLoadItems[].roadMapId). */
    val projectLogLoadItems: List<ProjectLogLoadItem> = emptyList(),
    /** Сообщение о том, была ли при загрузке прочитана таблица projectLogs (для Календаря). */
    val projectLogsStatusMessage: String? = null,
    /** Элементы дорожной карты с листа «roadMaps» (если он есть). */
    val newRoadMaps: List<RoadMap> = emptyList(),
    /** Сообщение о загрузке листа roadMaps. */
    val roadMapsStatusMessage: String? = null
)

/**
 * Загружает заявки из Google Таблицы.
 * Если передан accessToken (пользователь вошёл в Google), используется Sheets API — загрузка работает
 * и при отключённом доступе по ссылке. Иначе используется экспорт в CSV (нужен доступ по ссылке).
 * @param url полная ссылка на таблицу
 * @param users список пользователей для подстановки user/applicant
 * @param projects список проектов для подстановки project
 * @param accessToken токен Google (если есть) — для доступа к таблице без «доступа по ссылке»
 */
fun loadIssuesFromGoogleSheets(
    url: String,
    users: List<Users>,
    projects: List<Projects>,
    companies: List<Companies>,
    accessToken: String? = null,
    onDebugLog: (String) -> Unit = {},
    /** Текущий пользователь сеанса — выводится в лог при разборе заявок. */
    currentUser: Users? = null
): GoogleSheetsLoadResult {
    val spreadsheetId = extractGoogleSpreadsheetId(url)
        ?: return GoogleSheetsLoadResult(emptyList(), "Неверная ссылка на Google Таблицу")
    val token = accessToken?.trim()?.takeIf { it.isNotEmpty() }
    val gidFromUrl = extractGoogleSheetGid(url) ?: 0
    val gid = if (token != null) {
        getGoogleSheetGidByTitle(spreadsheetId, "Issues", token) ?: gidFromUrl
    } else {
        gidFromUrl
    }
    val csv = if (token != null) {
        fetchGoogleSheetValuesViaApi(spreadsheetId, gid, token)
    } else {
        null
    } ?: fetchGoogleSheetAsCsv(spreadsheetId, gid)
    if (csv == null)
        return GoogleSheetsLoadResult(
            emptyList(),
            if (token != null) "Не удалось прочитать таблицу по API. Проверьте, что таблица доступна авторизованному аккаунту."
            else "Не удалось скачать таблицу. Войдите в Google в настройках или включите доступ «Все, у кого есть ссылка»."
        )
    if (csv.isBlank()) return GoogleSheetsLoadResult(emptyList(), "Лист пуст или недоступен")
    val issueLoadItems = loadIssuesFromCsv(csv, users, projects, companies, onDebugLog, currentUser)
    val issues = issueLoadItems.map { it.issue }
    if (token != null) {
        val sheetName = getGoogleSheetNameByGid(spreadsheetId, gid, token)
        if (sheetName != null) {
            val rangePrefix = "'${sheetName.replace("'", "''")}'!"
            for (issue in issues) {
                if (!issue.id.startsWith("legacy-")) continue
                val n = issue.id.removePrefix("legacy-").trim().toIntOrNull() ?: continue
                val sheetRow = n + 1
                val idValue = issueIdToSheetId(issue.id)
                sheetsPut(spreadsheetId, token, "${rangePrefix}A$sheetRow", listOf(idValue))
            }
        }
    }
    val newComments = if (token != null) {
        fetchGoogleSheetNotesForColumnC(spreadsheetId, gid, token)?.let { notes ->
            issues.flatMapIndexed { idx, issue ->
                parseCommentsFromNote(notes.getOrNull(idx), issue, users)
            }
        } ?: emptyList()
    } else emptyList()
    val noteLoadItems = if (token != null) {
        getGoogleSheetGidByTitle(spreadsheetId, "notes", token)?.let { notesGid ->
            fetchGoogleSheetValuesViaApi(spreadsheetId, notesGid, token)?.let { notesCsv ->
                if (notesCsv.isNotBlank()) loadNotesFromCsv(notesCsv, users, projects, companies, onDebugLog, currentUser) else emptyList()
            } ?: emptyList()
        } ?: emptyList()
    } else emptyList()
    val (projectLogLoadItems, projectLogsStatusMessage) = if (token != null) {
        val logsGid = getGoogleSheetGidByTitle(spreadsheetId, "projectLogs", token)
        when {
            logsGid == null -> emptyList<ProjectLogLoadItem>() to "Лист «projectLogs» не найден в книге."
            else -> {
                val logsCsv = fetchGoogleSheetValuesViaApi(spreadsheetId, logsGid, token)
                when {
                    logsCsv == null -> emptyList<ProjectLogLoadItem>() to "Лист «projectLogs» найден, но не удалось прочитать данные."
                    logsCsv.isBlank() -> emptyList<ProjectLogLoadItem>() to "Таблица projectLogs прочитана: 0 записей (лист пуст)."
                    else -> {
                        val list = loadProjectLogsFromCsv(logsCsv, users, projects, companies, onDebugLog, currentUser)
                        list to "Таблица projectLogs прочитана: ${list.size} записей."
                    }
                }
            }
        }
    } else {
        emptyList<ProjectLogLoadItem>() to "Таблица projectLogs не загружалась (нужен вход в Google)."
    }
    val (newRoadMaps, roadMapsStatusMessage) = if (token != null) {
        val roadMapsGid = getGoogleSheetGidByTitle(spreadsheetId, "roadMaps", token)
        when {
            roadMapsGid == null -> emptyList<RoadMap>() to "Лист «roadMaps» не найден в книге."
            else -> {
                val roadMapsCsv = fetchGoogleSheetValuesViaApi(spreadsheetId, roadMapsGid, token)
                when {
                    roadMapsCsv == null -> emptyList<RoadMap>() to "Лист «roadMaps» найден, но не удалось прочитать данные."
                    roadMapsCsv.isBlank() -> emptyList<RoadMap>() to "Таблица roadMaps прочитана: 0 записей (лист пуст)."
                    else -> {
                        val list = loadRoadMapsFromCsv(roadMapsCsv, users, projects, onDebugLog, currentUser)
                        list to "Таблица roadMaps прочитана: ${list.size} записей."
                    }
                }
            }
        }
    } else {
        emptyList<RoadMap>() to "Таблица roadMaps не загружалась (нужен вход в Google)."
    }
    return GoogleSheetsLoadResult(issueLoadItems, null, newComments, noteLoadItems, projectLogLoadItems, projectLogsStatusMessage, newRoadMaps, roadMapsStatusMessage)
}

/** Возвращает примечания ячеек колонки C (Описание) по строкам (без заголовка): notes[0] = для 1-й строки данных и т.д. */
private fun fetchGoogleSheetNotesForColumnC(
    spreadsheetId: String,
    gid: Int,
    accessToken: String
): List<String?>? {
    val fields = "sheets(properties(sheetId,title),data(rowData(values(note))))"
    val json = sheetsGet(spreadsheetId, accessToken, "?includeGridData=true&fields=${URLEncoder.encode(fields, "UTF-8")}")
        ?: return null
    return try {
        val sheets = JSONObject(json).optJSONArray("sheets") ?: return null
        for (s in 0 until sheets.length()) {
            val sheet = sheets.getJSONObject(s)
            if (sheet.optJSONObject("properties")?.optInt("sheetId", -1) != gid) continue
            val dataArr = sheet.optJSONArray("data") ?: continue
            val rowData = dataArr.optJSONObject(0)?.optJSONArray("rowData") ?: continue
            val list = mutableListOf<String?>()
            for (r in 1 until rowData.length()) {
                val row = rowData.optJSONObject(r) ?: continue
                val values = row.optJSONArray("values") ?: continue
                val cellC = values.optJSONObject(2)
                val note = cellC?.optString("note")?.takeIf { it.isNotBlank() }
                list.add(note)
            }
            return list
        }
        null
    } catch (_: Exception) {
        null
    }
}

// --- Запись изменений в Google Таблицу (Sheets API v4) ---

private const val SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets"

private fun sheetsGet(spreadsheetId: String, accessToken: String, subPath: String): String? {
    return try {
        val url = URL("$SHEETS_API_BASE/$spreadsheetId$subPath")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        if (conn.responseCode != 200) return null
        conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}

private fun sheetsPut(spreadsheetId: String, accessToken: String, range: String, values: List<Any?>): Boolean {
    return sheetsPutError(spreadsheetId, accessToken, range, values) == null
}

/**
 * Пишет одну строку в диапазон и возвращает текст ошибки (null при успехе).
 * Ошибка включает HTTP-код и тело ответа Google API для диагностики.
 */
private fun sheetsPutError(spreadsheetId: String, accessToken: String, range: String, values: List<Any?>): String? {
    return try {
        val encodedRange = URLEncoder.encode(range, "UTF-8")
        val url = URL("$SHEETS_API_BASE/$spreadsheetId/values/$encodedRange?valueInputOption=USER_ENTERED")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        val body = JSONObject().apply {
            put("values", JSONArray().put(JSONArray().apply { values.forEach { put(it) } }))
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        if (conn.responseCode in 200..299) {
            null
        } else {
            val errBody = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            val suffix = if (errBody.isNotBlank()) " | body=$errBody" else ""
            "HTTP ${conn.responseCode}${suffix}"
        }
    } catch (e: Exception) {
        e.message ?: "Неизвестная ошибка запроса"
    }
}

/**
 * Добавляет одну строку в конец листа (append). range — например 'Лист1'!A:R.
 * @return номер добавленной строки (1-based) или null при ошибке
 */
private fun sheetsAppendRow(spreadsheetId: String, accessToken: String, range: String, values: List<Any?>): Int? {
    return try {
        val encodedRange = URLEncoder.encode(range, "UTF-8")
        val url = URL("$SHEETS_API_BASE/$spreadsheetId/values/$encodedRange:append?valueInputOption=USER_ENTERED")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        val body = JSONObject().apply {
            put("values", JSONArray().put(JSONArray().apply { values.forEach { put(it) } }))
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        if (conn.responseCode !in 200..299) return null
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(response)
        val updates = obj.optJSONObject("updates") ?: return null
        val updatedRange = updates.optString("updatedRange", "")
        // updatedRange вида "Лист1!A5:R5" — извлекаем номер строки
        val rowMatch = Regex("!A(\\d+):").find(updatedRange) ?: Regex("!(\\d+):").find(updatedRange)
        rowMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    } catch (_: Exception) {
        null
    }
}

/** Записывает несколько строк в диапазон. values[i] — i-я строка (список ячеек). */
private fun sheetsPutRange(spreadsheetId: String, accessToken: String, range: String, values: List<List<Any?>>): Boolean {
    if (values.isEmpty()) return true
    return try {
        val encodedRange = URLEncoder.encode(range, "UTF-8")
        val url = URL("$SHEETS_API_BASE/$spreadsheetId/values/$encodedRange?valueInputOption=USER_ENTERED")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        val arr = JSONArray()
        for (row in values) {
            arr.put(JSONArray().apply { row.forEach { put(it) } })
        }
        val body = JSONObject().put("values", arr)
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}

/** Устанавливает примечание (note) ячейки через spreadsheets.batchUpdate. row — 1-based. Для Issues: колонка C (Описание) = индекс 2. */
private fun sheetsSetCellNote(
    spreadsheetId: String,
    accessToken: String,
    sheetId: Int,
    row: Int,
    noteText: String?,
    columnIndex: Int = 2
): Boolean {
    if (noteText.isNullOrBlank()) return true
    return try {
        val url = URL("$SHEETS_API_BASE/$spreadsheetId:batchUpdate")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        val range = JSONObject().apply {
            put("sheetId", sheetId)
            put("startRowIndex", row - 1)
            put("endRowIndex", row)
            put("startColumnIndex", columnIndex)
            put("endColumnIndex", columnIndex + 1)
        }
        val cell = JSONObject().put("note", noteText)
        val repeatCell = JSONObject().apply {
            put("range", range)
            put("cell", cell)
            put("fields", "note")
        }
        val body = JSONObject().apply {
            put("requests", JSONArray().put(JSONObject().put("repeatCell", repeatCell)))
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}

/**
 * Возвращает название листа по gid (sheetId в API = gid в URL).
 */
private fun getGoogleSheetNameByGid(spreadsheetId: String, gid: Int, accessToken: String): String? {
    val json = sheetsGet(spreadsheetId, accessToken, "?fields=sheets(properties(sheetId,title))") ?: return null
    return try {
        val obj = JSONObject(json)
        val sheets = obj.optJSONArray("sheets") ?: return null
        for (i in 0 until sheets.length()) {
            val sheet = sheets.getJSONObject(i)
            val props = sheet.optJSONObject("properties") ?: continue
            if (props.optInt("sheetId", -1) == gid) return props.optString("title", "Sheet1")
        }
        null
    } catch (_: Exception) {
        null
    }
}

/** Возвращает sheetId (gid) листа по названию (без учёта регистра). Если не найден — null. */
private fun getGoogleSheetGidByTitle(spreadsheetId: String, title: String, accessToken: String): Int? {
    val json = sheetsGet(spreadsheetId, accessToken, "?fields=sheets(properties(sheetId,title))") ?: return null
    return try {
        val obj = JSONObject(json)
        val sheets = obj.optJSONArray("sheets") ?: return null
        val want = title.trim().lowercase()
        for (i in 0 until sheets.length()) {
            val sheet = sheets.getJSONObject(i)
            val props = sheet.optJSONObject("properties") ?: continue
            if (props.optString("title", "").trim().lowercase() == want)
                return props.optInt("sheetId", -1).takeIf { it >= 0 }
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Возвращает следующий id для новой заявки и номер последней строки с заполненным id (1-based).
 * Ищет последнюю строку с непустым значением в колонке A; nextRow = lastFilledRow + 1.
 * Запрашивает A2:A500. Если колонка пуста — (1, 1), т.е. новую запись писать в строку 2.
 */
private fun getNextIssueIdAndLastFilledRow(
    spreadsheetId: String,
    sheetName: String,
    accessToken: String
): Pair<Int, Int> {
    fun quotedRange(a1: String) = "'${sheetName.replace("'", "''")}'!$a1"
    val json = sheetsGet(spreadsheetId, accessToken, "/values/${URLEncoder.encode(quotedRange("A2:A500"), "UTF-8")}")
        ?: return 1 to 1
    return try {
        val rows = JSONObject(json).optJSONArray("values") ?: return 1 to 1
        var maxId = 0
        var lastFilledRow1Based = 1
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val cell = row.optString(0, "").trim()
            if (cell.isEmpty()) continue
            lastFilledRow1Based = 2 + i
            val n = cell.toIntOrNull()
            if (n != null && n > maxId) maxId = n
        }
        (maxId + 1) to lastFilledRow1Based
    } catch (_: Exception) {
        1 to 1
    }
}

/**
 * Ищет номер строки (1-based) по id заявки в колонке A и project_id в колонке H.
 * (Колонка I — company; ранее ошибочно читали project из I, из-за чего строка не находилась и создавалась новая.)
 * Запрашивает A2:I1000; при неудаче — A2:A500 (по id).
 */
private fun findGoogleSheetRowById(
    spreadsheetId: String,
    sheetName: String,
    issueId: String,
    projectId: String?,
    accessToken: String
): Int? {
    fun quotedRange(a1: String) = "'${sheetName.replace("'", "''")}'!$a1"
    fun fetchRange(a1: String): JSONArray? {
        val range = quotedRange(a1)
        val json = sheetsGet(spreadsheetId, accessToken, "/values/${URLEncoder.encode(range, "UTF-8")}") ?: return null
        return try {
            JSONObject(json).optJSONArray("values")
        } catch (_: Exception) { null }
    }
    val sheetId = issueIdToSheetId(issueId)
    val rows = fetchRange("A2:I1000") ?: fetchRange("A2:A500")
    if (rows == null || rows.length() == 0) return null
    return try {
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val colA = row.optString(0, "").trim()
            if (colA != sheetId) continue
            // H = project id (0-based index 7); I = company
            val colProject = if (row.length() > 7) row.optString(7, "").trim() else ""
            val projectOk = projectId.isNullOrBlank() || colProject == projectId
            if (projectOk) return 2 + i
        }
        null
    } catch (_: Exception) {
        null
    }
}

/** Создаёт лист с указанным названием, если такого ещё нет. Возвращает true, если лист есть или создан. */
private fun ensureGoogleSheetExists(spreadsheetId: String, accessToken: String, sheetTitle: String): Boolean {
    if (getGoogleSheetGidByTitle(spreadsheetId, sheetTitle, accessToken) != null) return true
    return try {
        val url = URL("$SHEETS_API_BASE/$spreadsheetId:batchUpdate")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        val body = JSONObject().apply {
            put("requests", JSONArray().put(JSONObject().apply {
                put("addSheet", JSONObject().apply {
                    put("properties", JSONObject().apply { put("title", sheetTitle) })
                })
            }))
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}

/** Строка листа notes без колонки id (B–K): done, content, User, applicant, Проект, project, company, dateUpdate, onTiming, roadMap. Колонка A (id) при записи не меняется. */
private val dateUpdateSheetFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
private fun formatDateUpdateForSheet(ms: Long): Any? =
    if (ms > 0L) dateUpdateSheetFormat.format(Date(ms)) else ""

private fun noteRowValuesWithoutId(note: Notes): List<Any?> = listOf(
    note.done,
    note.content,
    userDisplayForSheet(note.user),
    userDisplayForSheet(note.applicant),
    note.project?.name ?: "",
    note.project?.id ?: "",
    note.company?.name ?: "",
    formatDateUpdateForSheet(note.dateUpdate),
    if (note.onTiming) "True" else "False",
    note.roadMap?.id ?: ""
)

private val projectLogDateSheetFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** Строка листа projectLogs: A=id, B=type, C=date, D=content, ..., roadMap. */
private fun projectLogRowValues(log: ProjectLogs, companies: List<Companies>): List<Any?> {
    // company (колонка 11) — наименование компании из самой записи журнала
    val companyName = log.company?.name ?: ""
    return listOf(
    log.id,
    log.type,
    if (log.date > 0L) projectLogDateSheetFormat.format(Date(log.date)) else "",
    log.content,
    log.agenda,
    log.resolution,
    userDisplayForSheet(log.user),
    userDisplayForSheet(log.applicant),
    log.project?.name ?: "",
    log.project?.id ?: "",
    companyName, // company (колонка 11)
    // dateUpdate (12-я колонка): фиксируем момент выгрузки (текущее время)
    formatDateUpdateForSheet(System.currentTimeMillis()),
    if (log.onTiming) "True" else "False",
    log.roadMap?.id ?: ""
)
}

/** Ищет номер строки (1-based) на листе projectLogs по id (A) и project_id (J). */
private fun findProjectLogRowByIdAndProject(
    spreadsheetId: String,
    accessToken: String,
    logId: String,
    projectId: String?
): Int? {
    val sheetName = "projectLogs"
    val range = "'${sheetName.replace("'", "''")}'!A2:J5000"
    val json = sheetsGet(spreadsheetId, accessToken, "/values/${URLEncoder.encode(range, "UTF-8")}") ?: return null
    return try {
        val rows = JSONObject(json).optJSONArray("values") ?: return null
        val projectIdStr = projectId?.trim().orEmpty()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val colA = row.optString(0, "").trim()
            // A=id, J=project (id проекта)
            val colJ = row.optString(9, "").trim()
            if (colA == logId.trim() && colJ == projectIdStr) return 2 + i
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Обновляет или добавляет одну запись журнала на лист «projectLogs».
 * Если запись с таким id есть — обновляется только её строка; иначе добавляется новая строка в конец.
 * @return true при успешной записи
 */
fun writeSingleProjectLogToGoogleSheet(
    spreadsheetId: String,
    accessToken: String,
    log: ProjectLogs,
    companies: List<Companies> = emptyList()
): Boolean {
    if (!ensureGoogleSheetExists(spreadsheetId, accessToken, "projectLogs")) return false
    val token = accessToken.trim()
    val row1Based = findProjectLogRowByIdAndProject(spreadsheetId, token, log.id, log.project?.id)
    val values = projectLogRowValues(log, companies)
    return if (row1Based != null) {
        val range = "'projectLogs'!A$row1Based:N$row1Based"
        sheetsPutRange(spreadsheetId, token, range, listOf(values))
    } else {
        sheetsAppendRange(spreadsheetId, token, "'projectLogs'!A:N", listOf(values))
    }
}

private fun sheetsAppendRange(spreadsheetId: String, accessToken: String, range: String, values: List<List<Any?>>): Boolean {
    if (values.isEmpty()) return true
    return try {
        val encodedRange = URLEncoder.encode(range, "UTF-8")
        val url = URL("$SHEETS_API_BASE/$spreadsheetId/values/$encodedRange:append?valueInputOption=USER_ENTERED")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        val arr = JSONArray()
        for (row in values) {
            arr.put(JSONArray().apply { row.forEach { put(it) } })
        }
        val body = JSONObject().put("values", arr)
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}

/**
 * Записывает все записи журнала на лист «projectLogs» (полная перезапись). Используется при первой выгрузке.
 */
fun writeProjectLogsToGoogleSheet(
    spreadsheetId: String,
    accessToken: String,
    logs: List<ProjectLogs>,
    companies: List<Companies> = emptyList()
): Boolean {
    if (!ensureGoogleSheetExists(spreadsheetId, accessToken, "projectLogs")) return false
    val header = listOf<Any?>("id", "type", "date", "content", "agenda", "resolution", "user", "applicant", "Проект", "project", "company", "dateUpdate", "onTiming", "roadMap")
    val rows = listOf(header) + logs.map { projectLogRowValues(it, companies) }
    val range = "'projectLogs'!A1:N${rows.size}"
    return sheetsPutRange(spreadsheetId, accessToken.trim(), range, rows)
}

private val timeEntryDateSheetFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** ID для выгрузки: без префикса "legacy-" (сразу числовой или фактический ID). */
private fun timeEntryExportId(id: String?): String =
    id?.removePrefix("legacy-")?.trim()?.ifBlank { id } ?: ""

/** User в формате «ФИО (адрес почты)» для выгрузки в колонки User/applicant — та же логика, что при поиске в таблице. */
private fun userDisplayForSheet(user: Users?): String =
    user?.let { userDisplayId(it.displayName?.trim()?.takeIf { n -> n.isNotBlank() } ?: it.id, it.email?.trim()?.takeIf { n -> n.isNotBlank() } ?: it.login?.trim()?.takeIf { n -> n.isNotBlank() } ?: it.id) } ?: ""

/** User в формате «ФИО (email)» для колонки user в timeEntries. */
private fun formatUserForTimeEntry(user: Users?): String = userDisplayForSheet(user)

private val timeEntryDateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** Строка листа timeEntries: id, issueID, issueName, noteID, noteName, projectLogID, projectLogName, roadMapID, roadMapName, user, hours, date, createdOn, updatedOn, project, comment. noteName = content (у Note нет реквизита name). date = дата списания (одна колонка). */
private fun timeEntryRowValues(entry: TimeEntries): List<Any?> = listOf(
    entry.id,
    timeEntryExportId(entry.issue?.id),
    entry.issue?.name ?: "",
    timeEntryExportId(entry.note?.id),
    entry.note?.content?.replace("\n", " ") ?: "",
    timeEntryExportId(entry.projectLog?.id),
    entry.projectLog?.content?.take(80)?.replace("\n", " ") ?: entry.projectLog?.type ?: "",
    entry.roadMap?.id ?: "",
    entry.roadMap?.name ?: "",
    formatUserForTimeEntry(entry.user),
    roundTimeEntryHours(entry.hours),
    if (entry.createdOn > 0L) timeEntryDateOnlyFormat.format(Date(entry.createdOn)) else "",
    if (entry.createdOn > 0L) timeEntryDateSheetFormat.format(Date(entry.createdOn)) else "",
    if (entry.updatedOn > 0L) timeEntryDateSheetFormat.format(Date(entry.updatedOn)) else "",
    entry.project?.id ?: "",
    entry.comment
)

/**
 * Добавляет записи тайминга на лист «timeEntries» (append, без полной перезаписи).
 * Вызывается при нажатии «Завершить день» на закладке Тайминг.
 * Колонка date — дата списания (к которой относятся трудозатраты). noteName = content.
 */
fun writeTimeEntriesToGoogleSheet(spreadsheetId: String, accessToken: String, entries: List<TimeEntries>): Boolean {
    if (!ensureGoogleSheetExists(spreadsheetId, accessToken, "timeEntries")) return false
    if (entries.isEmpty()) return true
    val token = accessToken.trim()
    val header = listOf<Any?>(
        "id", "issueID", "issueName", "noteID", "noteName", "projectLogID", "projectLogName",
        "roadMapID", "roadMapName", "user", "hours", "date", "createdOn", "updatedOn", "project", "comment"
    )
    // Заголовок поддерживаем всегда, но данные добавляем только append (важно для конкурентных устройств).
    if (!sheetsPutRange(spreadsheetId, token, "'timeEntries'!A1:P1", listOf(header))) return false
    val rows = entries.map { timeEntryRowValues(it) }
    return sheetsAppendRange(spreadsheetId, token, "'timeEntries'!A:P", rows)
}

private val roadMapDateSheetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** Строка листа roadMaps: A=id, B=Название, C=Описание, D=step, E=start, F=end, G=User, H=Проект, I=project, J=company, K=dateUpdate, L=onTiming. */
private fun roadMapRowValues(roadMap: RoadMap, companies: List<Companies>): List<Any?> {
    val companyId = companies.firstOrNull { it.project?.id == roadMap.project?.id }?.id?.toString() ?: ""
    return listOf(
    roadMap.id,
    roadMap.name,
    roadMap.content,
    roadMap.step,
    if (roadMap.start > 0L) roadMapDateSheetFormat.format(Date(roadMap.start)) else "",
    if (roadMap.end > 0L) roadMapDateSheetFormat.format(Date(roadMap.end)) else "",
    userDisplayForSheet(roadMap.user),
    roadMap.project?.name ?: "",
    roadMap.project?.id ?: "",
    companyId, // company (новая колонка 10)
    formatDateUpdateForSheet(roadMap.dateUpdate),
    if (roadMap.onTiming) "True" else "False"
)
}

/** Ищет номер строки (1-based) на листе roadMaps по id (A) и project_id (I). */
private fun findRoadMapRowByIdAndProject(
    spreadsheetId: String,
    accessToken: String,
    roadMapId: String,
    projectId: String?
): Int? {
    val sheetName = "roadMaps"
    val range = "'${sheetName.replace("'", "''")}'!A2:I5000"
    val json = sheetsGet(spreadsheetId, accessToken, "/values/${URLEncoder.encode(range, "UTF-8")}") ?: return null
    return try {
        val rows = JSONObject(json).optJSONArray("values") ?: return null
        val idStr = roadMapId.trim()
        val projectIdStr = projectId?.trim().orEmpty()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val colA = row.optString(0, "").trim()
            // A=id, I=project (id проекта)
            val colI = row.optString(8, "").trim()
            if (colA == idStr && colI == projectIdStr) return 2 + i
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Обновляет или добавляет один элемент дорожной карты на лист «roadMaps».
 * Если запись с таким id есть — обновляется только её строка; иначе добавляется новая строка в конец.
 */
fun writeSingleRoadMapToGoogleSheet(
    spreadsheetId: String,
    accessToken: String,
    roadMap: RoadMap,
    companies: List<Companies> = emptyList()
): Boolean {
    if (!ensureGoogleSheetExists(spreadsheetId, accessToken, "roadMaps")) return false
    val token = accessToken.trim()
    val roadMapsHeader = listOf<Any?>(
        "id", "name", "content", "step", "start", "end", "User",
        "Проект", "project", "company", "dateUpdate", "onTiming"
    )
    if (!sheetsPutRange(spreadsheetId, token, "'roadMaps'!A1:L1", listOf(roadMapsHeader))) return false
    val row1Based = findRoadMapRowByIdAndProject(spreadsheetId, token, roadMap.id, roadMap.project?.id)
    val values = roadMapRowValues(roadMap, companies)
    return if (row1Based != null) {
        val range = "'roadMaps'!A$row1Based:L$row1Based"
        sheetsPutRange(spreadsheetId, token, range, listOf(values))
    } else {
        sheetsAppendRange(spreadsheetId, token, "'roadMaps'!A:L", listOf(values))
    }
}

private fun companyKey(company: Companies): String {
    val projectId = company.project?.id?.trim().orEmpty()
    val name = company.name.trim().lowercase()
    return "$projectId|$name"
}

fun collectUnifiedCompanies(
    baseCompanies: List<Companies>,
    issues: List<Issues>,
    notes: List<Notes>,
    projectLogs: List<ProjectLogs> = emptyList(),
    roadMaps: List<RoadMap> = emptyList()
): List<Companies> {
    val map = LinkedHashMap<String, Companies>()
    baseCompanies.forEach { c -> map.putIfAbsent(companyKey(c), c) }
    issues.mapNotNull { it.company }.forEach { c -> map.putIfAbsent(companyKey(c), c) }
    notes.mapNotNull { it.company }.forEach { c -> map.putIfAbsent(companyKey(c), c) }
    // Для журнала и графиков компания не хранится как отдельный реквизит в модели:
    // добавляем компанию проекта (если она есть в справочнике).
    projectLogs
        .mapNotNull { log -> baseCompanies.firstOrNull { it.project?.id == log.project?.id } }
        .forEach { c -> map.putIfAbsent(companyKey(c), c) }
    roadMaps
        .mapNotNull { rm -> baseCompanies.firstOrNull { it.project?.id == rm.project?.id } }
        .forEach { c -> map.putIfAbsent(companyKey(c), c) }
    return map.values.toList()
}

fun writeCompaniesToGoogleSheet(
    spreadsheetId: String,
    accessToken: String,
    companies: List<Companies>
): Boolean {
    // Синхронизацию листа Companies с гугл-таблицей больше не выполняем.
    // Функция оставлена для совместимости, но ничего не делает и всегда возвращает true.
    return true
}

/** Заголовок листа notes (B–M), чтобы при загрузке читалась в т.ч. колонка roadMap. */
private val notesHeaderWithoutId = listOf<Any?>("done", "content", "User", "applicant", "Проект", "project", "company", "dateUpdate", "onTiming", "roadMap")
private val notesHeaderWithId = listOf<Any?>("id") + notesHeaderWithoutId

private fun noteRowValuesWithId(note: Notes): List<Any?> = listOf(note.id) + noteRowValuesWithoutId(note)

/** Ищет номер строки (1-based) на листе notes по id (A) и projectId (G). */
private fun findNoteRowByIdAndProject(
    spreadsheetId: String,
    accessToken: String,
    noteId: String,
    projectId: String?
): Int? {
    val sheetName = "notes"
    val range = "'${sheetName.replace("'", "''")}'!A2:G5000"
    val json = sheetsGet(spreadsheetId, accessToken, "/values/${URLEncoder.encode(range, "UTF-8")}") ?: return null
    return try {
        val rows = JSONObject(json).optJSONArray("values") ?: return null
        val idStr = noteId.trim()
        val projectIdStr = projectId?.trim().orEmpty()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val colA = row.optString(0, "").trim()
            // A=id, G=project (id проекта) для листа notes
            val colG = row.optString(6, "").trim()
            if (colA == idStr && colG == projectIdStr) return 2 + i
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Обновляет одну строку заметки на листе «notes». Колонка A (id) не трогается.
 * Строка: id=1 → строка 2, id=2 → строка 3 и т.д. (sheetRow1Based = id.toInt() + 1).
 * @return true при успешной записи
 */
fun writeSingleNoteToGoogleSheet(spreadsheetId: String, accessToken: String, note: Notes): Boolean {
    if (note.id.isBlank()) return false
    if (!ensureGoogleSheetExists(spreadsheetId, accessToken, "notes")) return false
    val token = accessToken.trim()
    val row1Based = findNoteRowByIdAndProject(spreadsheetId, token, note.id, note.project?.id)
    return if (row1Based != null) {
        val rowValues = listOf(noteRowValuesWithoutId(note))
        val range = "'notes'!B$row1Based:K$row1Based"
        sheetsPutRange(spreadsheetId, token, range, rowValues)
    } else {
        sheetsAppendRange(spreadsheetId, token, "'notes'!A:K", listOf(noteRowValuesWithId(note)))
    }
}

/**
 * Записывает все заметки на лист «notes» (полная перезапись данных B–K, начиная со 2-й строки; заголовок в строке 1 не трогаем).
 * Используется при добавлении новой заметки или при первой выгрузке.
 */
fun writeNotesToGoogleSheet(spreadsheetId: String, accessToken: String, notes: List<Notes>): Boolean {
    if (!ensureGoogleSheetExists(spreadsheetId, accessToken, "notes")) return false
    val sortedNotes = notes.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
    // Пишем только данные, начиная со 2-й строки (B2), чтобы не изменять заголовки
    val rows = sortedNotes.map { noteRowValuesWithoutId(it) }
    val range = "'notes'!B2:K${rows.size + 1}"
    return sheetsPutRange(spreadsheetId, accessToken.trim(), range, rows)
}

/** Порядок колонок листа Issues: id (A), Название, Описание, status, User, applicant, Проект, project, company, dateUpdate, ..., roadMap. */
private fun issueRowValues(issue: Issues, allIssues: List<Issues>): List<Any?> {
    return listOf(
        issueIdToSheetId(issue.id),
        issue.name,
        issue.content,
        issue.status.name,
        userDisplayForSheet(issue.user),
        userDisplayForSheet(issue.applicant),
        issue.project?.name ?: "",
        issue.project?.id ?: "",
        issue.company?.name ?: "",
        formatDateUpdateForSheet(issue.dateUpdate),
        if (issue.onTiming) "True" else "False",
        if (issue.newCommentForUser) "True" else "False",
        if (issue.newCommentForApplicant) "True" else "False",
        issue.roadMap?.id ?: ""
    )
}

/**
 * Результат обновления строки в Google Таблице: номер строки при успехе или сообщение об ошибке.
 * newIssueId — при добавлении новой заявки: id по шаблону "issue-" + (номер строки - 1).
 */
data class GoogleSheetUpdateResult(val row: Int?, val error: String?, val newIssueId: String? = null)

/**
 * Обновляет одну строку в Google Таблице по id заявки (колонка A). В колонку C (Описание) записывается значение;
 * комментарии к заявке — в примечание (note) ячейки C.
 * @param commentsForIssue комментарии этой заявки для примечания ячейки D
 * @return GoogleSheetUpdateResult(row = N при успехе, иначе error = сообщение)
 */
fun updateIssueInGoogleSheet(
    updatedIssue: Issues,
    allIssues: List<Issues>,
    tableUrl: String,
    accessToken: String,
    commentsForIssue: List<IssueComments> = emptyList()
): GoogleSheetUpdateResult {
    val spreadsheetId = extractGoogleSpreadsheetId(tableUrl)
        ?: return GoogleSheetUpdateResult(null, "Неверная ссылка на Google Таблицу")
    val gidFromUrl = extractGoogleSheetGid(tableUrl) ?: 0
    val token = accessToken.trim()
    if (token.isBlank()) return GoogleSheetUpdateResult(null, "Не указан токен доступа Google")
    val issuesGid = getGoogleSheetGidByTitle(spreadsheetId, "Issues", token) ?: gidFromUrl
    var gid = issuesGid
    var sheetName = getGoogleSheetNameByGid(spreadsheetId, issuesGid, token)
    if (sheetName == null) return GoogleSheetUpdateResult(null, "Не удалось получить имя листа (проверьте ссылку и лист Issues)")
    var row = findGoogleSheetRowById(
        spreadsheetId, sheetName,
        updatedIssue.id,
        updatedIssue.project?.id,
        token
    )
    val issueToWrite: Issues
    if (row == null) {
        // Новая заявка: ищем последнюю строку с заполненным id и пишем в следующую строку
        val (nextId, lastFilledRow) = getNextIssueIdAndLastFilledRow(spreadsheetId, sheetName, token)
        val nextRow = lastFilledRow + 1
        issueToWrite = updatedIssue.copy(id = "legacy-$nextId")
        val values = issueRowValues(issueToWrite, allIssues)
        val rangeWithSheet = "'${sheetName.replace("'", "''")}'!A$nextRow:N$nextRow"
        val putError = sheetsPutError(spreadsheetId, token, rangeWithSheet, values)
        if (putError != null)
            return GoogleSheetUpdateResult(
                null,
                "Ошибка записи строки в таблицу ($rangeWithSheet): $putError",
                null
            )
        row = nextRow
        val noteText = formatCommentsAsNote(commentsForIssue)
        if (noteText.isNotBlank()) sheetsSetCellNote(spreadsheetId, token, gid, row, noteText)
        return GoogleSheetUpdateResult(row, null, issueToWrite.id)
    }
    issueToWrite = updatedIssue
    val values = issueRowValues(issueToWrite, allIssues)
    val rangeWithSheet = "'${sheetName.replace("'", "''")}'!A$row:N$row"
    val putError = sheetsPutError(spreadsheetId, token, rangeWithSheet, values)
    if (putError != null)
        return GoogleSheetUpdateResult(
            null,
            "Ошибка записи в Google Таблицу ($rangeWithSheet): $putError"
        )
    val noteText = formatCommentsAsNote(commentsForIssue)
    if (noteText.isNotBlank()) sheetsSetCellNote(spreadsheetId, token, gid, row, noteText)
    return GoogleSheetUpdateResult(row, null, null)
}

