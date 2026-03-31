package com.worksphere.app.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject

/**
 * Manages WebSocket connection to the Netty gateway for real-time messaging.
 * Connects to ws://{host}:8090/ws?userId={id} or ws://{host}:8090/ws?token={jwt}
 * Falls back gracefully — messaging works via REST polling when disconnected.
 */
object WebSocketService {
    private const val TAG = "WebSocketService"

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    private val subscribedConversations = mutableSetOf<Long>()
    private val messageHandlers = mutableMapOf<Long, (MessageDto) -> Unit>()
    private val typingHandlers = mutableMapOf<Long, (Long) -> Unit>()
    var onAnyMessage: ((Long, MessageDto) -> Unit)? = null

    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        if (webSocket != null) return

        val baseUrl = ApiClient.baseUrl
            .replace("/api", "")
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        // Extract host (without port) and use gateway port 8090
        val parts = baseUrl.split(":")
        val host = parts.take(2).joinToString(":")

        val urlBuilder = StringBuilder("$host:8090/ws")
        ApiClient.token?.let { urlBuilder.append("?token=$it") }
            ?: ApiClient.debugUserId?.let { urlBuilder.append("?userId=$it") }
            ?: return // No auth

        val request = Request.Builder().url(urlBuilder.toString()).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                _isConnected.value = true
                // Re-subscribe
                subscribedConversations.forEach { convId ->
                    sendJSON(mapOf("type" to "SUBSCRIBE", "conversationId" to convId))
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                handleDisconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason")
                handleDisconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
        subscribedConversations.clear()
    }

    fun subscribe(conversationId: Long, onMessage: (MessageDto) -> Unit, onTyping: ((Long) -> Unit)? = null) {
        messageHandlers[conversationId] = onMessage
        onTyping?.let { typingHandlers[conversationId] = it }

        if (subscribedConversations.add(conversationId)) {
            sendJSON(mapOf("type" to "SUBSCRIBE", "conversationId" to conversationId))
        }
    }

    fun unsubscribe(conversationId: Long) {
        messageHandlers.remove(conversationId)
        typingHandlers.remove(conversationId)
        subscribedConversations.remove(conversationId)
    }

    fun sendMessage(conversationId: Long, content: String) {
        sendJSON(mapOf("type" to "SEND", "conversationId" to conversationId, "content" to content))
    }

    fun sendTyping(conversationId: Long) {
        sendJSON(mapOf("type" to "TYPING", "conversationId" to conversationId))
    }

    // -- Private --

    private fun sendJSON(data: Map<String, Any>) {
        val json = JSONObject(data).toString()
        webSocket?.send(json) ?: Log.w(TAG, "WebSocket not connected, dropping message")
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "CONNECTED" -> {
                    _isConnected.value = true
                    Log.i(TAG, "Connected, connections=${json.optInt("connections")}")
                }
                "MESSAGE" -> {
                    val convId = json.optLong("conversationId")
                    val dataObj = json.optJSONObject("data") ?: return
                    val msg = ApiClient.gson.fromJson(dataObj.toString(), MessageDto::class.java)
                    scope.launch(Dispatchers.Main) {
                        messageHandlers[convId]?.invoke(msg)
                        onAnyMessage?.invoke(convId, msg)
                    }
                }
                "TYPING" -> {
                    val convId = json.optLong("conversationId")
                    val userId = json.optLong("userId")
                    scope.launch(Dispatchers.Main) {
                        typingHandlers[convId]?.invoke(userId)
                    }
                }
                "ERROR" -> Log.w(TAG, "Server error: ${json.optString("message")}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WS message: ${e.message}")
        }
    }

    private fun handleDisconnect() {
        _isConnected.value = false
        webSocket = null
        // Auto-reconnect
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            if (isActive) connect()
        }
    }
}
