package com.worksphere.app.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/**
 * Singleton HTTP client for all API communication.
 *
 * Automatically attaches Bearer token or X-Debug-User-Id header depending
 * on which credential is set.
 */
object ApiClient {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api"
    @PublishedApi internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    var baseUrl: String = DEFAULT_BASE_URL

    /** JWT token – set after normal login. */
    var token: String? = null

    /** Debug user id – set when using debug/impersonation login. */
    var debugUserId: Long? = null

    /** Tenant id – set from the tenant selector on the login screen. */
    var tenantId: String? = null

    val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(SafeLongAdapterFactory())
        .create()

    // -----------------------------------------------------------------------
    // OkHttp client with auth interceptor
    // -----------------------------------------------------------------------

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        token?.let { t ->
            builder.addHeader("Authorization", "Bearer $t")
        }
        debugUserId?.let { uid ->
            builder.addHeader("X-Debug-User-Id", uid.toString())
        }
        tenantId?.let { builder.addHeader("X-Tenant-Id", it) }

        chain.proceed(builder.build())
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // -----------------------------------------------------------------------
    // Generic request helpers
    // -----------------------------------------------------------------------

    class ApiException(
        val statusCode: Int,
        override val message: String
    ) : IOException("HTTP $statusCode: $message")

    /**
     * Execute a GET request and deserialise the JSON body to [T].
     * Pass [typeToken] for generic types (e.g. `object : TypeToken<List<PostDto>>() {}`).
     */
    suspend inline fun <reified T> get(
        path: String,
        queryParams: Map<String, String?> = emptyMap(),
        typeToken: Type = object : TypeToken<T>() {}.type
    ): T = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl$path".toHttpUrl().newBuilder()
            
        queryParams.forEach { (key, value) ->
            if (value != null) urlBuilder.addQueryParameter(key, value)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        executeAndParse(request, typeToken)
    }

    /**
     * Execute a POST request with a JSON body and deserialise the response.
     */
    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        typeToken: Type = object : TypeToken<T>() {}.type
    ): T = withContext(Dispatchers.IO) {
        val json = if (body != null) gson.toJson(body) else "{}"
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(requestBody)
            .build()

        executeAndParse(request, typeToken)
    }

    /**
     * Execute a PUT request with a JSON body and deserialise the response.
     */
    suspend inline fun <reified T> put(
        path: String,
        body: Any? = null,
        typeToken: Type = object : TypeToken<T>() {}.type
    ): T = withContext(Dispatchers.IO) {
        val json = if (body != null) gson.toJson(body) else "{}"
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl$path")
            .put(requestBody)
            .build()

        executeAndParse(request, typeToken)
    }

    /**
     * Execute a DELETE request. Returns the raw response body string.
     */
    suspend fun delete(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .delete()
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw ApiException(response.code, bodyString)
        }
        bodyString
    }

    /**
     * Fire a quick GET to check connectivity. Returns the HTTP status code,
     * or -1 if the request fails entirely.
     */
    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.body?.close()
            response.code
        } catch (_: Exception) {
            -1
        }
    }

    // -----------------------------------------------------------------------
    // Raw request (returns body as String, no deserialisation)
    // -----------------------------------------------------------------------

    suspend fun getRaw(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(("$baseUrl$path").toHttpUrl())
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw ApiException(response.code, bodyString)
        bodyString
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    @PublishedApi
    internal fun <T> executeAndParse(request: Request, typeToken: Type): T {
        val response: Response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw ApiException(response.code, bodyString)
        }

        // Handle Unit / Void return types
        if (typeToken == Unit::class.java || typeToken == Void::class.java) {
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }

        // Handle plain String return type
        if (typeToken == String::class.java) {
            @Suppress("UNCHECKED_CAST")
            return bodyString as T
        }

        return gson.fromJson(bodyString, typeToken)
    }
}
