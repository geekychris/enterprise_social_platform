package com.worksphere.app.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.worksphere.app.data.*
import com.worksphere.app.ui.ai.AiAssistantComposable
import com.worksphere.app.ui.components.Avatar
import com.worksphere.app.ui.feed.PostCard
import kotlinx.coroutines.launch

@Composable
fun GroupScreen(
    groupId: Long,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    onNavigateToPage: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<GroupDto?>(null) }
    var members by remember { mutableStateOf<List<MembershipDto>>(emptyList()) }
    var posts by remember { mutableStateOf<List<PostDto>>(emptyList()) }
    var isMember by remember { mutableStateOf(false) }
    var membershipStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAiAssistant by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        try {
            group = ApiClient.get<GroupDto>("/groups/$groupId")
            members = ApiClient.get<List<MembershipDto>>("/groups/$groupId/members")
            posts = ApiClient.get<List<PostDto>>("/groups/$groupId/posts")
            val status = try {
                ApiClient.get<Map<String, String>>("/groups/$groupId/membership")
            } catch (_: Exception) { null }
            membershipStatus = status?.get("status")
            isMember = membershipStatus == "approved"
        } catch (_: Exception) { }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }

    val g = group ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Cover
        item {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                if (g.coverUrl != null) {
                    AsyncImage(
                        model = g.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) { }
                }
            }
        }

        // Group info
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Box(Modifier.offset(y = (-20).dp)) {
                    Avatar(url = g.avatarUrl, name = g.name, size = 56.dp)
                }
                Text(
                    g.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-12).dp)
                )
                g.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.offset(y = (-8).dp))
                }
                Text(
                    "${g.memberCount ?: members.size} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Join / Leave
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (isMember) {
                                    ApiClient.post<Any>("/groups/$groupId/leave", Unit)
                                    isMember = false
                                    membershipStatus = null
                                } else {
                                    ApiClient.post<Any>("/groups/$groupId/join", Unit)
                                    isMember = true
                                    membershipStatus = "approved"
                                }
                            } catch (_: Exception) { }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        when {
                            isMember -> "Leave Group"
                            membershipStatus == "pending" -> "Pending"
                            else -> "Join Group"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                // AI Assistant toggle
                FilledTonalButton(
                    onClick = { showAiAssistant = !showAiAssistant },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI Assistant", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showAiAssistant) {
            item {
                AiAssistantComposable(
                    context = "group",
                    contextId = groupId,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Members strip
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Members",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(members, key = { it.userId }) { member ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateToProfile(member.userId) }
                    ) {
                        Avatar(url = member.userAvatarUrl, name = member.userName ?: "", size = 32.dp)
                        Text(
                            member.userName ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Divider()
        }

        // Posts
        item {
            Text(
                "Posts",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        items(posts, key = { it.id }) { post ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                PostCard(
                    post = post,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToPost = onNavigateToPost
                )
            }
        }
    }
}
