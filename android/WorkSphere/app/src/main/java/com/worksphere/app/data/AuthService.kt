package com.worksphere.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton authentication manager.
 *
 * Call [init] once from Application/Activity before using any other method.
 * Credentials are persisted in SharedPreferences so the user stays logged in
 * across app restarts.
 */
object AuthService {

    private const val PREFS_NAME = "worksphere_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_DEBUG_USER_ID = "debug_user_id"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TENANT_ID = "tenant_id"

    private lateinit var prefs: SharedPreferences

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    var token: String? = null
        private set
    var userId: Long = 0L
        private set
    var username: String = ""
        private set
    var isAdmin: Boolean = false
        private set
    var debugMode: Boolean = false
        private set
    var tenantId: String? = null
        private set

    // ---------------------------------------------------------------------
    // Initialisation
    // ---------------------------------------------------------------------

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restoreSession()
    }

    private fun restoreSession() {
        val savedUrl = prefs.getString(KEY_SERVER_URL, null)
        if (savedUrl != null) {
            ApiClient.baseUrl = savedUrl
        }

        // Restore tenant
        val savedTenantId = prefs.getString(KEY_TENANT_ID, null)
        if (savedTenantId != null) {
            tenantId = savedTenantId
            ApiClient.tenantId = savedTenantId
        }

        val savedToken = prefs.getString(KEY_TOKEN, null)
        val savedDebugMode = prefs.getBoolean(KEY_DEBUG_MODE, false)
        val savedUserId = prefs.getLong(KEY_USER_ID, 0L)

        if (savedDebugMode && savedUserId != 0L) {
            debugMode = true
            userId = savedUserId
            username = prefs.getString(KEY_USERNAME, "") ?: ""
            isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
            ApiClient.debugUserId = savedUserId
            ApiClient.token = null
            _isAuthenticated.value = true
        } else if (savedToken != null) {
            token = savedToken
            userId = savedUserId
            username = prefs.getString(KEY_USERNAME, "") ?: ""
            isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
            debugMode = false
            ApiClient.token = savedToken
            ApiClient.debugUserId = null
            _isAuthenticated.value = true
        }
    }

    // ---------------------------------------------------------------------
    // Login / Register / Logout
    // ---------------------------------------------------------------------

    /**
     * Authenticate with username + password. Throws on failure.
     */
    suspend fun login(username: String, password: String): LoginResponse {
        val response = ApiClient.post<LoginResponse>(
            path = "/auth/login",
            body = LoginRequest(username, password)
        )

        applyLoginResponse(response)
        return response
    }

    /**
     * Register a new account and immediately log in.
     */
    suspend fun register(
        username: String,
        displayName: String,
        email: String,
        password: String,
        bio: String? = null
    ): LoginResponse {
        val response = ApiClient.post<LoginResponse>(
            path = "/auth/register",
            body = RegisterRequest(username, displayName, email, password, bio)
        )

        applyLoginResponse(response)
        return response
    }

    /**
     * Debug / impersonation login – skips real auth, sets X-Debug-User-Id header.
     */
    suspend fun loginDebug(debugUserId: Long) {
        // Validate the user exists by fetching their profile
        val user = ApiClient.get<UserDto>(
            path = "/users/$debugUserId",
            queryParams = emptyMap()
        )

        token = null
        userId = user.id
        username = user.username
        isAdmin = user.admin ?: false
        debugMode = true

        ApiClient.token = null
        ApiClient.debugUserId = debugUserId

        prefs.edit()
            .putString(KEY_TOKEN, null)
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_IS_ADMIN, isAdmin)
            .putBoolean(KEY_DEBUG_MODE, true)
            .putLong(KEY_DEBUG_USER_ID, debugUserId)
            .putString(KEY_TENANT_ID, tenantId)
            .apply()

        _isAuthenticated.value = true
    }

    fun logout() {
        token = null
        userId = 0L
        username = ""
        isAdmin = false
        debugMode = false
        tenantId = null

        ApiClient.token = null
        ApiClient.debugUserId = null
        ApiClient.tenantId = null

        prefs.edit().clear().apply()
        _isAuthenticated.value = false
    }

    // ---------------------------------------------------------------------
    // Tenant selection
    // ---------------------------------------------------------------------

    fun setTenant(id: String?) {
        tenantId = id
        ApiClient.tenantId = id
    }

    // ---------------------------------------------------------------------
    // Server configuration
    // ---------------------------------------------------------------------

    fun configureServer(url: String) {
        val trimmed = url.trimEnd('/')
        ApiClient.baseUrl = trimmed
        prefs.edit().putString(KEY_SERVER_URL, trimmed).apply()
    }

    fun getServerUrl(): String = ApiClient.baseUrl

    // ---------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------

    private fun applyLoginResponse(response: LoginResponse) {
        token = response.token
        userId = response.userId
        username = response.username
        isAdmin = response.admin
        debugMode = false

        ApiClient.token = response.token
        ApiClient.debugUserId = null

        prefs.edit()
            .putString(KEY_TOKEN, response.token)
            .putLong(KEY_USER_ID, response.userId)
            .putString(KEY_USERNAME, response.username)
            .putBoolean(KEY_IS_ADMIN, response.admin)
            .putBoolean(KEY_DEBUG_MODE, false)
            .putString(KEY_TENANT_ID, tenantId)
            .apply()

        _isAuthenticated.value = true
    }
}
