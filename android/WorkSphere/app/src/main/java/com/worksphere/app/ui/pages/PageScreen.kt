package com.worksphere.app.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.worksphere.app.data.*
import com.worksphere.app.ui.components.Avatar
import com.worksphere.app.ui.feed.PostCard
import kotlinx.coroutines.launch

@Composable
fun PageScreen(
    pageId: Long,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    onNavigateToPage: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<PageDto?>(null) }
    var followers by remember { mutableStateOf<List<AuthorDto>>(emptyList()) }
    var posts by remember { mutableStateOf<List<PostDto>>(emptyList()) }
    var isFollowing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageId) {
        try {
            page = ApiClient.get<PageDto>("/pages/$pageId")
            followers = try { ApiClient.get<List<AuthorDto>>("/pages/$pageId/followers") } catch (_: Exception) { emptyList() }
            posts = ApiClient.get<List<PostDto>>("/pages/$pageId/posts")
            isFollowing = try {
                ApiClient.get<Map<String, Boolean>>("/pages/$pageId/follow-status")["following"] ?: false
            } catch (_: Exception) { false }
        } catch (_: Exception) { }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }

    val p = page ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Cover
        item {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                if (p.coverUrl != null) {
                    AsyncImage(
                        model = p.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) { }
                }
            }
        }

        // Page info
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Box(Modifier.offset(y = (-20).dp)) {
                    Avatar(url = p.avatarUrl, name = p.name, size = 56.dp)
                }
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-12).dp)
                )
                p.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.offset(y = (-8).dp))
                }
                Text(
                    "${p.followerCount ?: followers.size} followers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Follow / Unfollow
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (isFollowing) {
                                    ApiClient.post<Any>("/pages/$pageId/unfollow", Unit)
                                } else {
                                    ApiClient.post<Any>("/pages/$pageId/follow", Unit)
                                }
                                isFollowing = !isFollowing
                            } catch (_: Exception) { }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        if (isFollowing) "Unfollow" else "Follow",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Follower avatars strip
        if (followers.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Followers",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(followers, key = { it.id }) { follower ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onNavigateToProfile(follower.id) }
                        ) {
                            Avatar(url = follower.avatarUrl, name = follower.displayName, size = 32.dp)
                            Text(follower.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Divider()
            }
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
