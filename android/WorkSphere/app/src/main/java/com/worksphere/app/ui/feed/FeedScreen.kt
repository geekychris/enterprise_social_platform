package com.worksphere.app.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksphere.app.data.*
import com.worksphere.app.ui.ai.AiAssistantComposable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    onNavigateToPage: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<PostDto>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var newPostContent by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val pullRefreshState = null

    suspend fun loadFeed(refresh: Boolean = false) {
        if (isLoading) return
        isLoading = true
        try {
            val cursor = if (refresh) null else nextCursor
            val path = buildString {
                append("/feed?limit=20")
                if (cursor != null) append("&cursor=$cursor")
            }
            val response = ApiClient.get<FeedResponse>(path)
            posts = if (refresh) response.posts else posts + response.posts
            nextCursor = response.nextCursor
            hasMore = response.hasMore
        } catch (_: Exception) { }
        isLoading = false
    }

    LaunchedEffect(Unit) { loadFeed(refresh = true) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Post", style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = newPostContent,
                    onValueChange = { newPostContent = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("What's on your mind?", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                ApiClient.post<PostDto>("/posts", mapOf("content" to newPostContent))
                                newPostContent = ""
                                showCreateDialog = false
                                loadFeed(refresh = true)
                            } catch (_: Exception) { }
                        }
                    },
                    enabled = newPostContent.isNotBlank()
                ) { Text("Post", style = MaterialTheme.typography.labelSmall) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, contentDescription = "New Post") }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
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

                if (showAiAssistant) {
                    item {
                        AiAssistantComposable(context = "feed", contextId = null)
                    }
                }

                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToPost = onNavigateToPost,
                        onNavigateToGroup = onNavigateToGroup,
                        onNavigateToPage = onNavigateToPage
                    )
                }

                // Infinite scroll trigger
                item {
                    if (hasMore && !isLoading) {
                        LaunchedEffect(posts.size) { loadFeed() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (!hasMore && posts.isNotEmpty()) {
                        Text(
                            "You've reached the end",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isLoading && posts.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
