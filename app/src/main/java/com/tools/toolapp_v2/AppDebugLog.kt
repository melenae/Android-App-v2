package com.tools.toolapp_v2

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Общий отладочный лог для экрана Настройка.
 * Доступен до показа MainScreen (например, с LoginScreen при входе через Supabase),
 * чтобы ответ Supabase можно было записать и увидеть на закладке Настройка.
 */
object AppDebugLog {
    private const val MAX_CHARS = 80_000
    private val buffer = StringBuilder()

    fun append(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        synchronized(buffer) {
            if (buffer.isNotEmpty()) buffer.append('\n')
            buffer.append("$ts $line")
            if (buffer.length > MAX_CHARS) {
                buffer.delete(0, buffer.length - MAX_CHARS)
            }
        }
    }

    fun getText(): String = synchronized(buffer) { buffer.toString() }

    fun clear() {
        synchronized(buffer) { buffer.setLength(0) }
    }
}
