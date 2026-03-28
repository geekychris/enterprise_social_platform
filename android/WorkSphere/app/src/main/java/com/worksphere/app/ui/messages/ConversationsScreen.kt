package com.worksphere.app.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksphere.app.data.*
import com.worksphere.app.ui.components.Avatar
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToThread: (Long) -> Unit,
    onNavigateToAiChat: () -> Unit,
    onNavigateToProfile: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showNewDialog by remember { mutableStateOf(false) }
    var newConvoUsername by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            conversations = ApiClient.get<List<ConversationDto>>("/conversations")
        } catch (_: Exception) { }
        isLoading = false
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("New Conversation", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = newConvoUsername,
                    onValueChange = { newConvoUsername = it },
                    label = { Text("Username", style = MaterialTheme.typography.labelSmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val convo = ApiClient.post<ConversationDto>(
                                    "/conversations",
                                    mapOf("username" to newConvoUsername)
                                )
                                showNewDialog = false
                                newConvoUsername = ""
                                onNavigateToThread(convo.id)
                            } catch (_: Exception) { }
                        }
                    },
                    enabled = newConvoUsername.isNotBlank()
                ) { Text("Start", style = MaterialTheme.typography.labelSmall) }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, contentDescription = "New Conversation") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Chat with Roid button
            item {
                Button(
                    onClick = onNavigateToAiChat,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C3AED)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat with Roid", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(conversations, key = { it.id }) { convo ->
                ConversationRow(
                    conversation = convo,
                    onClick = { onNavigateToThread(convo.id) }
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationDto,
    onClick: () -> Unit
) {
    val otherParticipant = conversation.participants.firstOrNull()
    val displayName = conversation.name ?: otherParticipant?.displayName ?: "Conversation"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                url = otherParticipant?.avatarUrl,
                name = displayName,
                size = 36.dp
            )
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                conversation.lastMessage?.let { msg ->
                    Text(
                        msg.content ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (conversation.unreadCount > 0) {
                @OptIn(ExperimentalMaterial3Api::class)
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "${conversation.unreadCount}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
