package com.worksphere.app.ui.ai

import com.worksphere.app.data.ApiClient
import com.worksphere.app.data.AuthService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private val SUGGESTION_CHIPS = mapOf(
    "feed" to listOf("Summarize recent posts", "What's trending?", "Who is most active?"),
    "group" to listOf("Summarize group activity", "Key topics discussed", "Active members"),
    "page" to listOf("Summarize this page", "Recent updates", "Who follows this?"),
    "conversation" to listOf("Summarize this chat", "Action items", "Key decisions")
)

@Composable
fun AiAssistantComposable(
    context: String,
    contextId: Long?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val suggestions = SUGGESTION_CHIPS[context] ?: emptyList()

    fun askAi(question: String) {
        if (question.isBlank() || isStreaming) return
        isStreaming = true
        responseText = ""
        inputText = ""

        scope.launch {
            try {
                val body = buildString {
                    append("{\"question\":\"${question.replace("\"", "\\\"")}\",")
                    append("\"context\":\"$context\"")
                    if (contextId != null) append(",\"contextId\":$contextId")
                    append("}")
                }

                withContext(Dispatchers.IO) {
                    val url = URL("${ApiClient.baseUrl}/ai/ask")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "text/event-stream")
                    if (ApiClient.debugUserId != null) {
                        conn.setRequestProperty("X-Debug-User-Id", ApiClient.debugUserId.toString())
                    } else if (ApiClient.token != null) {
                        conn.setRequestProperty("Authorization", "Bearer ${ApiClient.token}")
                    }
                    conn.doOutput = true
                    conn.connectTimeout = 10000
                    conn.readTimeout = 120000

                    conn.outputStream.use { os ->
                        os.write(body.toByteArray())
                    }

                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String?
                    var currentEvent = ""
                    while (reader.readLine().also { line = it } != null) {
                        val l = line?.trim() ?: continue
                        if (l.startsWith("event:")) {
                            currentEvent = l.removePrefix("event:").trim()
                        } else if (l.startsWith("data:")) {
                            val data = l.removePrefix("data:")
                            if (currentEvent == "token") {
                                val decoded = data.replace("⏎", "\n")
                                withContext(Dispatchers.Main) {
                                    responseText += decoded
                                }
                            } else if (currentEvent == "done") {
                                break
                            } else if (currentEvent == "error") {
                                withContext(Dispatchers.Main) {
                                    responseText = "Error: $data"
                                }
                                break
                            }
                            currentEvent = ""
                        }
                    }
                    reader.close()
                    conn.disconnect()
                }
            } catch (_: Exception) {
                responseText = "Sorry, something went wrong. Please try again."
            }
            isStreaming = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Ask AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                if (expanded) {
                    IconButton(
                        onClick = {
                            expanded = false
                            responseText = ""
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (!expanded) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Tap to ask a question", style = MaterialTheme.typography.labelSmall)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(6.dp))

                    // Suggestion chips
                    if (responseText.isEmpty() && !isStreaming) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(suggestions) { suggestion ->
                                SuggestionChip(
                                    onClick = { askAi(suggestion) },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // Response area
                    if (responseText.isNotEmpty() || isStreaming) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                Text(responseText, style = MaterialTheme.typography.bodySmall)
                                if (isStreaming) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // Input
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask something...", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            enabled = !isStreaming,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { askAi(inputText) }),
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { askAi(inputText) },
                            enabled = inputText.isNotBlank() && !isStreaming,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
