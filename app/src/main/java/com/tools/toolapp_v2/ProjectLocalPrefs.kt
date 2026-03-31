package com.tools.toolapp_v2

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/** Файл SharedPreferences для локальных адресов таблиц, токенов и т.д. */
internal const val APP_PREFS_NAME = "app_prefs"

private const val TOOLAPP_PREFS_NAME = "toolapp"

/**
 * Стабильный ключ: email пользователя (нижний регистр), а не [Users.id]:
 * после входа id в приложении может совпадать с app_init («ФИО (email)»),
 * а в toolapp prefs оставаться UUID Supabase — старый ключ `project_source_${id}_${projectId}` не находил запись.
 */
internal fun projectGoogleSourcePrefsKey(user: Users, projectId: String): String {
    val segment = user.email.trim().lowercase(Locale.getDefault()).ifBlank { user.id }
    return "project_source_${segment}_${projectId}"
}

internal fun projectGoogleSourcePrefsKeyLegacy(userId: String, projectId: String): String =
    "project_source_${userId}_${projectId}"

/**
 * Читает URL таблицы: сначала ключ по email, затем legacy по [Users.id] и по saved_user_id из toolapp.
 * При нахождении по legacy — копирует в основной ключ и удаляет legacy.
 */
internal fun readProjectGoogleTableUrl(
    appContext: Context,
    prefs: SharedPreferences,
    user: Users,
    projectId: String
): String {
    val primary = projectGoogleSourcePrefsKey(user, projectId)
    prefs.getString(primary, null)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val legacyUserIds = buildList {
        add(user.id)
        appContext.getSharedPreferences(TOOLAPP_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("saved_user_id", null)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }.distinct()

    for (legacyId in legacyUserIds) {
        val k = projectGoogleSourcePrefsKeyLegacy(legacyId, projectId)
        if (k == primary) continue
        val v = prefs.getString(k, null)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        prefs.edit().putString(primary, v).remove(k).commit()
        return v
    }
    return ""
}

internal fun writeProjectGoogleTableUrl(
    appContext: Context,
    prefs: SharedPreferences,
    user: Users,
    projectId: String,
    url: String
): Boolean {
    val primary = projectGoogleSourcePrefsKey(user, projectId)
    val editor = prefs.edit().putString(primary, url.trim())
    val legacyIds = buildList {
        add(user.id)
        appContext.getSharedPreferences(TOOLAPP_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("saved_user_id", null)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }.distinct()
    for (legacyId in legacyIds) {
        val k = projectGoogleSourcePrefsKeyLegacy(legacyId, projectId)
        if (k != primary) editor.remove(k)
    }
    return editor.commit()
}

/**
 * Текст для отладочного лога: какие ключи prefs и что в них после чтения (в т.ч. после миграции legacy → основной).
 */
internal fun projectGoogleSourceReadDiagnostics(
    appContext: Context,
    prefs: SharedPreferences,
    user: Users,
    projectId: String,
    projectName: String,
    resolvedUrl: String
): String {
    val primary = projectGoogleSourcePrefsKey(user, projectId)
    val primaryVal = prefs.getString(primary, null)?.trim().orEmpty()
    val savedAuthId = appContext.getSharedPreferences(TOOLAPP_PREFS_NAME, Context.MODE_PRIVATE)
        .getString("saved_user_id", null)?.trim().orEmpty()
    return buildString {
        appendLine("— Карточка проекта: чтение адреса таблицы —")
        appendLine("Файл SharedPreferences: $APP_PREFS_NAME")
        appendLine("Проект: «$projectName» (id=$projectId)")
        appendLine("Пользователь: email=${user.email}, id=${user.id}")
        appendLine("toolapp.saved_user_id: ${savedAuthId.ifEmpty { "〈нет〉" }}")
        appendLine("Основной ключ: $primary")
        appendLine("Значение по основному ключу: ${if (primaryVal.isEmpty()) "〈пусто〉" else primaryVal}")
        val legacyIds = buildList {
            add(user.id)
            if (savedAuthId.isNotEmpty()) add(savedAuthId)
        }.distinct()
        for (legacyId in legacyIds) {
            val k = projectGoogleSourcePrefsKeyLegacy(legacyId, projectId)
            if (k == primary) {
                appendLine("(legacy совпадает с основным при id=$legacyId)")
                continue
            }
            val v = prefs.getString(k, null)?.trim().orEmpty()
            appendLine("Legacy: $k → ${if (v.isEmpty()) "〈пусто〉" else v}")
        }
        appendLine("Итог readProjectGoogleTableUrl: ${if (resolvedUrl.isEmpty()) "〈пусто〉" else resolvedUrl}")
    }.trimEnd()
}
