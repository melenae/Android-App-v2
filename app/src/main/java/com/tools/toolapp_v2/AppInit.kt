package com.tools.toolapp_v2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Данные приложения после вызова app_init (участники проектов, счета, проекты и т.д.).
 * Загружаются сразу после успешной авторизации через Supabase.
 */
data class AppData(
    val users: List<Users>,
    val projects: List<Projects>,
    val accounts: List<Accounts>,
    val companies: List<Companies>,
    val permittedProjects: List<PermittedProjects>
)

/**
 * Вызов RPC app_init после логина.
 * POST .../rest/v1/rpc/app_init, заголовки apikey и Authorization: Bearer ACCESS_TOKEN, body {}.
 * Ответ: { "user": {...}, "users": [...], "accounts": [...], "projects": [...], "companies": [...], "permitted_projects": [...] }
 */
suspend fun loadAppInit(accessToken: String): Result<AppData> = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val url = URL("${SupabaseAuth.SUPABASE_URL}/rest/v1/rpc/app_init?apikey=${URLEncoder.encode(SupabaseAuth.SUPABASE_ANON_KEY, Charsets.UTF_8.name())}")
        connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept-Profile", "public")
        connection.setRequestProperty("Content-Profile", "public")
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val body = if (code in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } else {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
        }
        if (code !in 200..299) {
            return@withContext Result.failure(Exception("app_init: $code $body"))
        }
        val data = parseAppInitResponse(body)
        Result.success(data)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        connection?.disconnect()
    }
}

/** Текстовое поле роли из JSON: не путать с JSON null и строкой "null". */
private fun JSONObject.optNonBlankText(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

/**
 * Роль текущего пользователя в проекте в элементе массива projects (app_init).
 * Ключ в RPC обычно member_role; редко memberRole. Поле role у строки проекта не используем — это может быть другая семантика.
 */
private fun parseMemberRoleFromProjectElement(p: JSONObject): String? =
    p.optNonBlankText("member_role") ?: p.optNonBlankText("memberRole")

/**
 * Роль участника в строке permitted_projects (если бэкенд отдаёт join с project_members).
 */
private fun parseMemberRoleFromPermittedRow(pp: JSONObject): String? =
    pp.optNonBlankText("member_role")
        ?: pp.optNonBlankText("memberRole")
        ?: pp.optNonBlankText("role")

private fun parseAppInitResponse(json: String): AppData {
    val root = JSONObject(json)
    val sessionUserId = root.optJSONObject("user")?.optNonBlankText("id")
    val users = mutableListOf<Users>()
    val userById = mutableMapOf<String, Users>()

    // Текущий пользователь из ответа: login = email, displayName = full_name (для совместимости с гугл-таблицей: идентификация по «ФИО (email)»)
    root.optJSONObject("user")?.let { u ->
        val id = u.optString("id").takeIf { it.isNotBlank() } ?: return@let
        val fullName = u.optString("full_name").takeIf { it.isNotBlank() } ?: id
        val email = u.optString("email").takeIf { it.isNotBlank() }
            ?: u.optString("login").takeIf { it.isNotBlank() }
            ?: id
        val user = Users(
            id = id,
            login = email,
            password = "",
            displayName = fullName,
            email = email,
            UserRole.REGULAR_USER,
            null
        )
        users.add(user)
        userById[id] = user
    }

    // Массив users (если есть): login = email, displayName = full_name
    root.optJSONArray("users")?.let { arr ->
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val id = u.optString("id").takeIf { it.isNotBlank() } ?: continue
            if (id in userById) continue
            val fullName = u.optString("full_name").takeIf { it.isNotBlank() } ?: id
            val email = u.optString("email").takeIf { it.isNotBlank() }
                ?: u.optString("login").takeIf { it.isNotBlank() }
                ?: id
            val role = if (u.optString("role") == "admin") UserRole.ADMIN else UserRole.REGULAR_USER
            val user = Users(id, email, "", fullName, email, role, null)
            users.add(user)
            userById[id] = user
        }
    }

    val projects = mutableListOf<Projects>()
    val projectById = mutableMapOf<String, Projects>()
    (root.optJSONArray("projects") ?: root.optJSONArray("Projects"))?.let { arr ->
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val id = p.optString("id").takeIf { it.isNotBlank() }
                ?: p.optString("project_id").takeIf { it.isNotBlank() }
                ?: continue
            val name = p.optString("name", "").ifBlank { p.optString("title", id).ifBlank { id } }
            val content = p.optString("content", "").ifBlank { p.optString("description", "") }
            val slug = p.optString("slug", "").ifBlank { id }
            val managerId = p.optString("manager_id").takeIf { it.isNotBlank() }
            val manager = managerId?.let { userById[it] }
            val memberRole = parseMemberRoleFromProjectElement(p)
            val proj = Projects(id, name, content, manager, null, slug, "", memberRole)
            projects.add(proj)
            projectById[id] = proj
        }
    }

    val accounts = mutableListOf<Accounts>()
    val accountById = mutableMapOf<String, Accounts>()
    (root.optJSONArray("accounts") ?: root.optJSONArray("Accounts"))?.let { arr ->
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val id = a.optString("id").takeIf { it.isNotBlank() }
                ?: a.opt("id")?.toString()?.takeIf { it.isNotBlank() }
                ?: "account-${i + 1}"
            val name = a.optString("name", "").ifBlank { a.optString("title", "").ifBlank { "Счёт $id" } }
            val content = a.optString("content", "").ifBlank { a.optString("description", "") }
                .ifBlank { a.optString("subscription_plan", "") }
            val managerId = a.optString("owner_id").takeIf { it.isNotBlank() }
                ?: a.optString("manager_id").takeIf { it.isNotBlank() }
                ?: a.optJSONObject("manager")?.optString("id")?.takeIf { it.isNotBlank() }
            val manager = managerId?.let { userById[it] }
            val acc = Accounts(id, name, content, manager)
            accounts.add(acc)
            accountById[id] = acc
        }
    }

    // Связи account у проектов (если в ответе есть account_id у проекта — UUID строка)
    root.optJSONArray("projects")?.let { arr ->
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val projectId = p.optString("id").takeIf { it.isNotBlank() } ?: continue
            val accountId = p.optString("account_id").takeIf { it.isNotBlank() }
            val proj = projectById[projectId] ?: continue
            accountId?.let { aid -> accountById[aid]?.let { proj.account = it } }
        }
    }

    val companies = mutableListOf<Companies>()
    root.optJSONArray("companies")?.let { arr ->
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val id = c.optLong("id", 0L).takeIf { it != 0L } ?: (i + 1).toLong()
            val name = c.optString("name", "").ifBlank { "Компания $id" }
            val content = c.optString("content", "")
            val projectId = c.optString("project_id").takeIf { it.isNotBlank() }
            val project = projectId?.let { projectById[it] }
            val userId = c.optString("user_id").takeIf { it.isNotBlank() }
            val user = userId?.let { userById[it] }
            companies.add(Companies(id, name, content, project, user, null))
        }
    }

    val permittedProjects = mutableListOf<PermittedProjects>()
    root.optJSONArray("permitted_projects")?.let { arr ->
        for (i in 0 until arr.length()) {
            val pp = arr.optJSONObject(i) ?: continue
            val id = pp.optLong("id", 0L).takeIf { it != 0L } ?: (i + 1).toLong()
            val userId = pp.optString("user_id").takeIf { it.isNotBlank() } ?: continue
            val projectId = pp.optString("project_id").takeIf { it.isNotBlank() } ?: continue
            val user = userById[userId] ?: continue
            val project = projectById[projectId] ?: continue
            if (sessionUserId != null && userId == sessionUserId) {
                parseMemberRoleFromPermittedRow(pp)?.let { role -> project.memberRole = role }
            }
            permittedProjects.add(PermittedProjects(id, user, project))
        }
    }

    return AppData(
        users = users,
        projects = projects,
        accounts = accounts,
        companies = companies,
        permittedProjects = permittedProjects
    )
}
