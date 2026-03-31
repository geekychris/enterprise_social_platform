package com.worksphere.app.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksphere.app.data.*
import com.worksphere.app.data.WebSocketService
import com.worksphere.app.ui.components.Avatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    conversationId: Long,
    onBack: () -> Unit,
    onNavigateToProfile: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf<ConversationDto?>(null) }
    var messages by remember { mutableStateOf<List<MessageDto>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val currentUserId = AuthService.userId
    val wsConnected by WebSocketService.isConnected.collectAsState()

    LaunchedEffect(conversationId) {
        try {
            conversation = ApiClient.get<ConversationDto>("/conversations/$conversationId")
            messages = ApiClient.get<List<MessageDto>>("/conversations/$conversationId/messages")
        } catch (_: Exception) { }
        isLoading = false
    }

    // WebSocket real-time subscription — reload from REST when push arrives
    LaunchedEffect(conversationId) {
        WebSocketService.connect()
        WebSocketService.subscribe(conversationId, onMessage = { _ ->
            scope.launch {
                try {
                    messages = ApiClient.get<List<MessageDto>>("/conversations/$conversationId/messages")
                } catch (_: Exception) { }
            }
        })
    }

    // Also listen for any message (auto-subscribed conversations)
    LaunchedEffect(conversationId) {
        WebSocketService.onAnyMessage = { convId, _ ->
            if (convId == conversationId) {
                scope.launch {
                    try {
                        messages = ApiClient.get<List<MessageDto>>("/conversations/$conversationId/messages")
                    } catch (_: Exception) { }
                }
            }
        }
    }

    // Cleanup on leave
    DisposableEffect(conversationId) {
        onDispose {
            WebSocketService.unsubscribe(conversationId)
        }
    }

    // Fallback polling (slower when WS connected)
    LaunchedEffect(conversationId, wsConnected) {
        while (true) {
            delay(if (wsConnected) 30_000L else 5_000L)
            try {
                messages = ApiClient.get<List<MessageDto>>("/conversations/$conversationId/messages")
            } catch (_: Exception) { }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank()) return
        inputText = ""

        // Always send via REST (guarantees persistence + returns saved MessageDto)
        // WebSocket is used for receiving real-time pushes, not for sending
        scope.launch {
            try {
                val msg = ApiClient.post<MessageDto>(
                    "/conversations/$conversationId/messages",
                    mapOf("content" to text)
                )
                // Optimistically add to local list immediately
                if (messages.none { it.id == msg.id }) {
                    messages = listOf(msg) + messages
                }
            } catch (_: Exception) { }
        }
    }

    val displayName = conversation?.name
        ?: conversation?.participants?.firstOrNull()?.displayName
        ?: "Conversation"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        conversation?.participants?.let { members ->
                            Text(
                                "${members.size} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic placeholder
                    IconButton(
                        onClick = { /* Speech-to-text placeholder */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice", modifier = Modifier.size(18.dp))
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isSent = message.sender.id == currentUserId,
                        onAvatarClick = { onNavigateToProfile(message.sender.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageDto,
    isSent: Boolean,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isSent) {
            Avatar(
                url = message.sender.avatarUrl,
                name = message.sender.displayName,
                size = 24.dp
            )
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            if (!isSent) {
                Text(
                    message.sender.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 1.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isSent) 12.dp else 4.dp,
                    bottomEnd = if (isSent) 4.dp else 12.dp
                ),
                color = if (isSent)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    message.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSent)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Text(
                message.createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 1.dp)
            )
        }
    }
}
