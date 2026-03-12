package com.tools.toolapp_v2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Авторизация через Supabase Auth.
 * Отправка email и пароля на сервер, получение сессии и статуса аккаунта (активен ли).
 */
object SupabaseAuth {
    const val SUPABASE_URL = "https://dlyauyrjpupgunteynpp.supabase.co"
    internal const val SUPABASE_ANON_KEY = "sb_publishable_5cl63PMNgyhmcgGgIMCDVg_qnTp6yWD"

    /** Ответ успешного входа: токен и данные пользователя. */
    data class SignInResult(
        val accessToken: String,
        val refreshToken: String?,
        val userEmail: String?,
        val userId: String?,
        /** Аккаунт активен (email подтверждён). */
        val isActive: Boolean,
        /** Сырой JSON ответа от Supabase (для лога на закладке Настройка). */
        val rawJson: String
    )

    /** Выполняет вход по email и паролю. Соединение по HTTPS. */
    suspend fun signIn(email: String, password: String): Result<SignInResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password
        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email и пароль не должны быть пустыми"))
        }
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$SUPABASE_URL/auth/v1/token?grant_type=password&apikey=${URLEncoder.encode(SUPABASE_ANON_KEY, Charsets.UTF_8.name())}")
            connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val body = JSONObject().apply {
                put("grant_type", "password")
                put("email", trimmedEmail)
                put("password", trimmedPassword)
            }
            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            }
            if (code !in 200..299) {
                val errMsg = try {
                    JSONObject(responseBody).optString("error_description", responseBody).ifBlank { "Ошибка $code" }
                } catch (_: Exception) {
                    responseBody.ifBlank { "Ошибка $code" }
                }
                return@withContext Result.failure(SupabaseAuthException(errMsg, code))
            }
            val json = JSONObject(responseBody)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
            val userObj = json.optJSONObject("user")
            val userEmail = userObj?.optString("email")?.takeIf { it.isNotBlank() }
            val userId = userObj?.optString("id")?.takeIf { it.isNotBlank() }
            val emailConfirmedAt = userObj?.optString("email_confirmed_at")?.takeIf { it.isNotBlank() }
            val isActive = !emailConfirmedAt.isNullOrBlank()
            if (accessToken.isBlank()) {
                return@withContext Result.failure(SupabaseAuthException("Сервер не вернул токен", code))
            }
            Result.success(
                SignInResult(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userEmail = userEmail,
                    userId = userId,
                    isActive = isActive,
                    rawJson = responseBody
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}

class SupabaseAuthException(message: String, val httpCode: Int, val responseBody: String? = null) : Exception(message)
