package com.worksphere.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.worksphere.app.data.*
import com.worksphere.app.ui.components.Avatar
import com.worksphere.app.ui.feed.PostCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: Long,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    onNavigateToPage: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit,
    onNavigateToMessages: (Long) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserDto?>(null) }
    var posts by remember { mutableStateOf<List<PostDto>>(emptyList()) }
    var isFollowing by remember { mutableStateOf(false) }
    var isFriend by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditSheet by remember { mutableStateOf(false) }
    var orgExpanded by remember { mutableStateOf(false) }
    var reportingChain by remember { mutableStateOf<List<OrgMemberDto>>(emptyList()) }
    var directReports by remember { mutableStateOf<List<OrgMemberDto>>(emptyList()) }

    val isOwnProfile = userId == AuthService.userId

    LaunchedEffect(userId) {
        try {
            user = ApiClient.get<UserDto>("/users/$userId")
            posts = ApiClient.get<List<PostDto>>("/users/$userId/posts")
            if (!isOwnProfile) {
                isFollowing = try {
                    ApiClient.get<Map<String, Boolean>>("/users/$userId/follow-status")["following"] ?: false
                } catch (_: Exception) { false }
            }
            try {
                reportingChain = ApiClient.get<List<OrgMemberDto>>("/org/users/$userId/chain")
                directReports = ApiClient.get<List<OrgMemberDto>>("/org/users/$userId/reports")
            } catch (_: Exception) { }
        } catch (_: Exception) { }
        isLoading = false
    }

    // Edit profile bottom sheet
    if (showEditSheet && user != null) {
        EditProfileSheet(
            user = user!!,
            onDismiss = { showEditSheet = false },
            onSave = { updated ->
                scope.launch {
                    try {
                        user = ApiClient.post<UserDto>("/users/${user!!.id}", updated)
                        showEditSheet = false
                    } catch (_: Exception) { }
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

    val u = user ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Cover image
        item {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                if (u.coverUrl != null) {
                    AsyncImage(
                        model = u.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) { }
                }
            }
        }

        // Avatar + name
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Box(Modifier.offset(y = (-24).dp)) {
                    Avatar(url = u.avatarUrl, name = u.displayName, size = 64.dp)
                }

                Text(
                    u.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-16).dp)
                )

                Column(modifier = Modifier.offset(y = (-12).dp)) {
                    Text("@${u.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    u.pronouns?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    u.jobTitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                    u.department?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    u.bio?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(6.dp))

                    // Follower / following
                    Row {
                        Text(
                            "${u.followerCount ?: 0} followers",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${u.followingCount ?: 0} following",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Action buttons
                    if (isOwnProfile) {
                        OutlinedButton(
                            onClick = { showEditSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit Profile", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            if (isFollowing) {
                                                ApiClient.post<Any>("/users/$userId/unfollow", Unit)
                                            } else {
                                                ApiClient.post<Any>("/users/$userId/follow", Unit)
                                            }
                                            isFollowing = !isFollowing
                                        } catch (_: Exception) { }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    if (isFollowing) "Unfollow" else "Follow",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            ApiClient.post<Any>("/friends/request", mapOf("userId" to userId))
                                        } catch (_: Exception) { }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Friend Request", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { onNavigateToMessages(userId) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Org section
        item {
            Spacer(Modifier.height(8.dp))
            Divider()
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { orgExpanded = !orgExpanded }.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (orgExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Organization", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (orgExpanded) {
            if (reportingChain.isNotEmpty()) {
                item {
                    Text(
                        "Reporting Chain",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(reportingChain.reversed()) { member ->
                    OrgPersonRow(
                        member = member,
                        isHighlighted = member.userId == userId,
                        onClick = { onNavigateToProfile(member.userId) }
                    )
                }
            }
            if (directReports.isNotEmpty()) {
                item {
                    Text(
                        "Direct Reports",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(directReports) { member ->
                    OrgPersonRow(
                        member = member,
                        isHighlighted = false,
                        onClick = { onNavigateToProfile(member.userId) }
                    )
                }
            }
        }

        // Posts header
        item {
            Divider()
            Text(
                "Posts",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToPost = onNavigateToPost
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun OrgPersonRow(
    member: OrgMemberDto,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 3.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            Avatar(url = member.userAvatarUrl, name = member.userName, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(member.userName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                member.title?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    user: UserDto,
    onDismiss: () -> Unit,
    onSave: (Map<String, String?>) -> Unit
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var bio by remember { mutableStateOf(user.bio ?: "") }
    var jobTitle by remember { mutableStateOf(user.jobTitle ?: "") }
    var department by remember { mutableStateOf(user.department ?: "") }
    var pronouns by remember { mutableStateOf(user.pronouns ?: "") }
    var phone by remember { mutableStateOf(user.phone ?: "") }
    var location by remember { mutableStateOf(user.location ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Edit Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = displayName, onValueChange = { displayName = it },
                label = { Text("Display Name", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("Bio", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                maxLines = 3, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pronouns, onValueChange = { pronouns = it },
                label = { Text("Pronouns", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = jobTitle, onValueChange = { jobTitle = it },
                label = { Text("Job Title", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = department, onValueChange = { department = it },
                label = { Text("Department", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = location, onValueChange = { location = it },
                label = { Text("Location", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    onSave(
                        mapOf(
                            "displayName" to displayName,
                            "bio" to bio.ifBlank { null },
                            "pronouns" to pronouns.ifBlank { null },
                            "jobTitle" to jobTitle.ifBlank { null },
                            "department" to department.ifBlank { null },
                            "phone" to phone.ifBlank { null },
                            "location" to location.ifBlank { null }
                        )
                    )
                }) { Text("Save", style = MaterialTheme.typography.labelSmall) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
