package com.worksphere.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksphere.app.data.*
import com.worksphere.app.ui.components.Avatar
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            notifications = ApiClient.get<List<NotificationDto>>("/notifications")
        } catch (_: Exception) { }
        isLoading = false
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                ApiClient.post<Any>("/notifications/read-all", Unit)
                                notifications = notifications.map { it.copy(read = true) }
                            } catch (_: Exception) { }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mark all read", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = {
                            // Mark as read
                            scope.launch {
                                try {
                                    ApiClient.post<Any>("/notifications/${notification.id}/read", Unit)
                                    notifications = notifications.map {
                                        if (it.id == notification.id) it.copy(read = true) else it
                                    }
                                } catch (_: Exception) { }
                            }
                            // Navigate
                            when {
                                notification.postId != null -> onNavigateToPost(notification.postId)
                                notification.actorId != null -> onNavigateToProfile(notification.actorId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.read) 0.dp else 0.5.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.read)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread dot
            if (!notification.read) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .offset(y = 4.dp)
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(12.dp))
            }

            Avatar(
                url = notification.actorAvatarUrl,
                name = notification.actorName ?: "",
                size = 28.dp
            )
            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    notification.message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    notification.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
