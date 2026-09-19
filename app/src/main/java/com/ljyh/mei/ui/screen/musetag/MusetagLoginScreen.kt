package com.ljyh.mei.ui.screen.musetag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ljyh.mei.R
import com.ljyh.mei.musetag.MusetagClient
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen
import kotlinx.coroutines.launch

@Composable
fun MusetagLoginScreen(
    onLoginSuccess: (() -> Unit)? = null,
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(MusetagClient.currentBase()) }
    var username by remember { mutableStateOf(MusetagClient.currentUsername()) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.musetag_login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.musetag_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.musetag_server)) },
                placeholder = { Text("192.168.1.200 或 192.168.1.200:9876") },
                singleLine = true,
                enabled = !loading,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.musetag_username)) },
                singleLine = true,
                enabled = !loading,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.musetag_password)) },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    if (loading) return@Button
                    loading = true
                    error = null
                    scope.launch {
                        val result = MusetagClient.login(server, username.trim(), password)
                        loading = false
                        result.fold(
                            onSuccess = {
                                onLoginSuccess?.invoke()
                                if (onLoginSuccess == null) {
                                    runCatching { navController.navigate(Screen.Home.route) }
                                }
                            },
                            onFailure = { error = it.message ?: "登录失败" },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !loading && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.musetag_login))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { navController.navigate(Screen.Home.route) },
                enabled = !loading,
            ) {
                Text(stringResource(R.string.musetag_skip))
            }
        }
    }
}
