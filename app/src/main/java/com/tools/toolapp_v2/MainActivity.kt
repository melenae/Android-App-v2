package com.tools.toolapp_v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tools.toolapp_v2.ui.theme.ToolApp_v2Theme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToolApp_v2Theme {
                ToolAppContent()
            }
        }
    }
}

@Composable
fun ToolAppContent() {
    val prefs = LocalContext.current.getSharedPreferences("toolapp", android.content.Context.MODE_PRIVATE)
    var savedUserId by remember { mutableStateOf(prefs.getString("saved_user_id", null)) }
    var appData by remember { mutableStateOf<AppData?>(null) }
    var appInitError by remember { mutableStateOf<String?>(null) }

    val currentUser = savedUserId?.let { id ->
        appData?.users?.find { it.id == id }
            ?: appData?.users?.find { it.email == prefs.getString("saved_user_email", "") }
            ?: run {
                if (prefs.getString("saved_user_id", null) != id) return@run null
                val login = prefs.getString("saved_user_login", null) ?: return@run null
                val displayName = prefs.getString("saved_user_display_name", "") ?: id
                val email = prefs.getString("saved_user_email", "") ?: id
                Users(id, login, "", displayName, email, UserRole.REGULAR_USER, null)
            }
    }

    if (currentUser == null) {
        LoginScreen(onLoginSuccess = { user, accessToken ->
            prefs.edit()
                .putString("saved_user_id", user.id)
                .putString("saved_user_login", user.login)
                .putString("saved_user_display_name", user.displayName)
                .putString("saved_user_email", user.email)
                .apply()
            savedUserId = user.id
            accessToken?.let { token ->
                prefs.edit().putString("supabase_access_token", token).apply()
            }
        })
    } else if (appData == null) {
        appInitError?.let { msg ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(msg, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        prefs.edit()
                            .remove("saved_user_id")
                            .remove("saved_user_login")
                            .remove("saved_user_display_name")
                            .remove("saved_user_email")
                            .remove("supabase_access_token")
                            .apply()
                        savedUserId = null
                        appInitError = null
                    }
                ) {
                    Text("Выйти")
                }
            }
        } ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LaunchedEffect(Unit) {
                val token = prefs.getString("supabase_access_token", null)
                if (token.isNullOrBlank()) {
                    appInitError = "Нет токена. Выйдите и войдите снова."
                    return@LaunchedEffect
                }
                loadAppInit(token)
                    .onSuccess { appData = it }
                    .onFailure { e ->
                        appInitError = "Ошибка после вызова: app_init. ${e.message ?: "Ошибка загрузки данных"}"
                    }
            }
        }
    } else {
        MainScreen(
            currentUser = currentUser,
            appData = appData!!,
            onLogout = {
                prefs.edit()
                    .remove("saved_user_id")
                    .remove("saved_user_login")
                    .remove("saved_user_display_name")
                    .remove("saved_user_email")
                    .remove("supabase_access_token")
                    .apply()
                savedUserId = null
                appData = null
            }
        )
    }
}
