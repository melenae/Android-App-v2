package com.tools.toolapp_v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    /** Вызывается при успешном входе через Supabase. accessToken можно сохранить для последующих запросов к API. */
    onLoginSuccess: (Users, accessToken: String?) -> Unit
) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var serverResponse by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Вход в приложение",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = login,
                onValueChange = { login = it; error = null; serverResponse = "" },
                label = { Text("Email (логин)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null; serverResponse = "" },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (loading) return@Button
                    error = null
                    serverResponse = ""
                    loading = true
                    scope.launch {
                        val result = SupabaseAuth.signIn(login, password)
                        loading = false
                        result.fold(
                            onSuccess = { signInResult ->
                                serverResponse = signInResult.rawJson
                                // Сервер вернул access_token — переходим на основной экран (закладка Настройка по умолчанию)
                                val email = signInResult.userEmail ?: login.trim()
                                val userToUse = Users(
                                    id = signInResult.userId ?: userDisplayId(email, email),
                                    login = email,
                                    password = "",
                                    displayName = email,
                                    email = email
                                )
                                onLoginSuccess(userToUse, signInResult.accessToken)
                            },
                            onFailure = { e ->
                                val callLabel = "Ошибка после вызова: вход (auth/v1/token)"
                                val body = (e as? SupabaseAuthException)?.responseBody
                                val rawMessage = body?.takeIf { it.isNotBlank() } ?: (e.message ?: "Ошибка входа")
                                serverResponse = "$callLabel\n\n$rawMessage"
                                error = "$callLabel: ${when (e) {
                                    is SupabaseAuthException -> e.message ?: "Ошибка входа (код ${e.httpCode})"
                                    else -> e.message ?: "Ошибка входа"
                                }}"
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.padding(8.dp))
                } else {
                    Text("Войти")
                }
            }
            if (serverResponse.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = serverResponse,
                    onValueChange = { },
                    label = { Text("Ответ сервера") },
                    readOnly = true,
                    minLines = 8,
                    maxLines = 16,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Авторизация через Supabase. Введите email и пароль от аккаунта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
