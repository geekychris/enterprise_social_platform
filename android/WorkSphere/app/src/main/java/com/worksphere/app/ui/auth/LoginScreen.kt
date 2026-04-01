package com.worksphere.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksphere.app.data.ApiClient
import com.worksphere.app.data.AuthService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Tenant model
// ---------------------------------------------------------------------------

data class TenantOption(val id: Long, val name: String, val slug: String, val plan: String)

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

private enum class AuthTab(val label: String) {
    Login("Login"),
    Register("Register"),
    Debug("Debug"),
}

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

@Composable
fun LoginScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = AuthTab.entries
    val scope = rememberCoroutineScope()
    val debugLog = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Server URL
    var serverUrl by remember { mutableStateOf(AuthService.getServerUrl()) }
    var serverReachable by remember { mutableStateOf<Boolean?>(null) }

    // Tenant selection
    var tenants by remember { mutableStateOf<List<TenantOption>>(emptyList()) }
    var selectedTenantId by remember { mutableStateOf("1") }

    LaunchedEffect(Unit) {
        try {
            val raw = ApiClient.getRaw("/tenants/list")
            val list = ApiClient.gson.fromJson(raw, Array<TenantOption>::class.java)
            tenants = list.toList()
            if (list.isNotEmpty()) {
                selectedTenantId = list[0].id.toString()
                AuthService.setTenant(list[0].id.toString())
            }
        } catch (_: Exception) {}
    }

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        debugLog.add(0, "[$ts] $msg")
        if (debugLog.size > 200) debugLog.removeAt(debugLog.lastIndex)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Title
            Text(
                text = "WorkSphere",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Enterprise Social Platform",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Build number
            Text(
                text = "Build ${"1.0"} (${1})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(16.dp))

            // ── Server URL ───────────────────────────────────────────
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    when (serverReachable) {
                        true -> Icon(Icons.Filled.Check, "Reachable", tint = MaterialTheme.colorScheme.primary)
                        false -> Icon(Icons.Filled.Close, "Unreachable", tint = MaterialTheme.colorScheme.error)
                        null -> {}
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        AuthService.configureServer(serverUrl)
                        log("Server URL set to $serverUrl")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save URL")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            AuthService.configureServer(serverUrl)
                            log("Pinging $serverUrl ...")
                            val code = ApiClient.ping()
                            serverReachable = code in 200..299
                            log(
                                if (serverReachable == true) "Server reachable (HTTP $code)"
                                else "Server unreachable (code=$code)"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ping Server")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Tenant selector ──────────────────────────────────────
            if (tenants.size > 1) {
                Text(
                    "Organization",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                tenants.forEach { tenant ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTenantId = tenant.id.toString()
                                AuthService.setTenant(tenant.id.toString())
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTenantId == tenant.id.toString())
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = if (selectedTenantId == tenant.id.toString())
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (selectedTenantId == tenant.id.toString()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tenant.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${tenant.slug}.worksphere.com",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                tenant.plan,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Tab row ──────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index; errorMessage = null },
                        text = { Text(tab.label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Error banner
            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }

            // ── Tab content ──────────────────────────────────────────
            when (tabs[selectedTab]) {
                AuthTab.Login -> LoginTab(
                    isLoading = isLoading,
                    onLogin = { user, pass ->
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                log("Logging in as $user ...")
                                val resp = AuthService.login(user, pass)
                                log("Logged in. userId=${resp.userId} admin=${resp.admin}")
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Login failed"
                                log("Login error: ${e.message}")
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                AuthTab.Register -> RegisterTab(
                    isLoading = isLoading,
                    onRegister = { user, display, email, pass, bio ->
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                log("Registering $user ...")
                                val resp = AuthService.register(user, display, email, pass, bio)
                                log("Registered & logged in. userId=${resp.userId}")
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Registration failed"
                                log("Register error: ${e.message}")
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                AuthTab.Debug -> DebugTab(
                    isLoading = isLoading,
                    onDebugLogin = { uid ->
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                log("Debug login as userId=$uid ...")
                                AuthService.loginDebug(uid)
                                log("Debug login successful.")
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Debug login failed"
                                log("Debug login error: ${e.message}")
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            }

            // ── Debug log panel ──────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Log",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { debugLog.clear() }) {
                    Text("Clear")
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (debugLog.isEmpty()) "No log entries yet." else debugLog.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Login tab
// ---------------------------------------------------------------------------

@Composable
private fun LoginTab(
    isLoading: Boolean,
    onLogin: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(username.trim(), password) },
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign In")
        }
    }
}

// ---------------------------------------------------------------------------
// Register tab
// ---------------------------------------------------------------------------

@Composable
private fun RegisterTab(
    isLoading: Boolean,
    onRegister: (username: String, displayName: String, email: String, password: String, bio: String?) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio (optional)") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onRegister(
                    username.trim(),
                    displayName.trim(),
                    email.trim(),
                    password,
                    bio.ifBlank { null }
                )
            },
            enabled = !isLoading && username.isNotBlank() && displayName.isNotBlank() &&
                    email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Account")
        }
    }
}

// ---------------------------------------------------------------------------
// Debug tab
// ---------------------------------------------------------------------------

@Composable
private fun DebugTab(
    isLoading: Boolean,
    onDebugLogin: (userId: Long) -> Unit
) {
    var userIdText by remember { mutableStateOf("1") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Debug login bypasses authentication and uses the X-Debug-User-Id header. " +
                    "This only works when the backend is running in debug mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = userIdText,
            onValueChange = { userIdText = it.filter { c -> c.isDigit() } },
            label = { Text("User ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Quick-select buttons for common test user IDs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1L, 2L, 3L, 4L, 5L).forEach { uid ->
                OutlinedButton(
                    onClick = { userIdText = uid.toString() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("$uid")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val uid = userIdText.toLongOrNull()
                if (uid != null && uid > 0) onDebugLogin(uid)
            },
            enabled = !isLoading && (userIdText.toLongOrNull() ?: 0L) > 0L,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Debug Login")
        }
    }
}
