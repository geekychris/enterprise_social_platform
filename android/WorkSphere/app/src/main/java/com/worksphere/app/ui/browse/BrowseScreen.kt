package com.worksphere.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksphere.app.data.*
import com.worksphere.app.ui.components.Avatar
import kotlinx.coroutines.launch

data class FriendRequest(
    val id: Long,
    val fromUserId: Long,
    val fromUserName: String,
    val fromUserAvatarUrl: String?,
    val status: String
)

@Composable
fun BrowseScreen(
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    onNavigateToPage: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var friendRequests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var myGroups by remember { mutableStateOf<List<GroupDto>>(emptyList()) }
    var myPages by remember { mutableStateOf<List<PageDto>>(emptyList()) }
    var friends by remember { mutableStateOf<List<AuthorDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreatePageDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newPageName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            friendRequests = try { ApiClient.get<List<FriendRequest>>("/friends/requests") } catch (_: Exception) { emptyList() }
            myGroups = try { ApiClient.get<List<GroupDto>>("/groups/mine") } catch (_: Exception) { emptyList() }
            myPages = try { ApiClient.get<List<PageDto>>("/pages/mine") } catch (_: Exception) { emptyList() }
            friends = try { ApiClient.get<List<AuthorDto>>("/friends") } catch (_: Exception) { emptyList() }
        } catch (_: Exception) { }
        isLoading = false
    }

    // Create group dialog
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("New Group", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group Name", style = MaterialTheme.typography.labelSmall) },
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
                                val group = ApiClient.post<GroupDto>("/groups", mapOf("name" to newGroupName))
                                myGroups = myGroups + group
                                showCreateGroupDialog = false
                                newGroupName = ""
                            } catch (_: Exception) { }
                        }
                    },
                    enabled = newGroupName.isNotBlank()
                ) { Text("Create", style = MaterialTheme.typography.labelSmall) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }

    // Create page dialog
    if (showCreatePageDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePageDialog = false },
            title = { Text("New Page", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = newPageName,
                    onValueChange = { newPageName = it },
                    label = { Text("Page Name", style = MaterialTheme.typography.labelSmall) },
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
                                val page = ApiClient.post<PageDto>("/pages", mapOf("name" to newPageName))
                                myPages = myPages + page
                                showCreatePageDialog = false
                                newPageName = ""
                            } catch (_: Exception) { }
                        }
                    },
                    enabled = newPageName.isNotBlank()
                ) { Text("Create", style = MaterialTheme.typography.labelSmall) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePageDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Friend Requests
        if (friendRequests.isNotEmpty()) {
            item {
                Text("Friend Requests", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            items(friendRequests, key = { it.id }) { request ->
                FriendRequestRow(
                    request = request,
                    onAccept = {
                        scope.launch {
                            try {
                                ApiClient.post<Any>("/friends/requests/${request.id}/accept", Unit)
                                friendRequests = friendRequests.filter { it.id != request.id }
                            } catch (_: Exception) { }
                        }
                    },
                    onDecline = {
                        scope.launch {
                            try {
                                ApiClient.post<Any>("/friends/requests/${request.id}/decline", Unit)
                                friendRequests = friendRequests.filter { it.id != request.id }
                            } catch (_: Exception) { }
                        }
                    },
                    onNavigateToProfile = onNavigateToProfile
                )
            }
            item { Divider() }
        }

        // My Groups
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Groups", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { showCreateGroupDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Group", modifier = Modifier.size(16.dp))
                }
            }
        }
        items(myGroups, key = { it.id }) { group ->
            BrowseItemRow(
                avatarUrl = group.avatarUrl,
                name = group.name,
                subtitle = "${group.memberCount ?: 0} members",
                onClick = { onNavigateToGroup(group.id) }
            )
        }

        item { Divider() }

        // My Pages
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Pages", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { showCreatePageDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Page", modifier = Modifier.size(16.dp))
                }
            }
        }
        items(myPages, key = { it.id }) { page ->
            BrowseItemRow(
                avatarUrl = page.avatarUrl,
                name = page.name,
                subtitle = "${page.followerCount ?: 0} followers",
                onClick = { onNavigateToPage(page.id) }
            )
        }

        item { Divider() }

        // Friends
        item {
            Text("Friends", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        if (friends.isEmpty()) {
            item {
                Text("No friends yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(friends, key = { it.id }) { friend ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onNavigateToProfile(friend.id) }.width(56.dp)
                        ) {
                            Avatar(url = friend.avatarUrl, name = friend.displayName, size = 40.dp)
                            Text(
                                friend.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onNavigateToProfile: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                url = request.fromUserAvatarUrl,
                name = request.fromUserName,
                size = 32.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                request.fromUserName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToProfile(request.fromUserId) }
            )
            IconButton(onClick = onAccept, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Check, contentDescription = "Accept", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDecline, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Decline", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BrowseItemRow(
    avatarUrl: String?,
    name: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(url = avatarUrl, name = name, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
