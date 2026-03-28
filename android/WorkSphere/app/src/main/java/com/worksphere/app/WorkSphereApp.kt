package com.worksphere.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worksphere.app.data.AuthService
import com.worksphere.app.ui.auth.LoginScreen

// ---------------------------------------------------------------------------
// Navigation destinations
// ---------------------------------------------------------------------------

enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Feed("feed", "Feed", Icons.Filled.Home),
    Search("search", "Search", Icons.Filled.Search),
    Messages("messages", "Chats", Icons.AutoMirrored.Filled.Chat),
    Org("org", "Org", Icons.Filled.AccountTree),
    More("more", "More", Icons.Filled.MoreHoriz),
}

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun WorkSphereApp() {
    val isAuthenticated by AuthService.isAuthenticated.collectAsState()

    if (!isAuthenticated) {
        LoginScreen()
        return
    }

    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val windowSizeClass = activity?.let {
        calculateWindowSizeClass(it)
    }
    val useRail = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    val navController = rememberNavController()
    var selectedIndex by remember { mutableIntStateOf(0) }
    val destinations = TopDestination.entries

    if (useRail) {
        RailLayout(
            destinations = destinations,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                selectedIndex = index
                navController.navigateToTab(destinations[index].route)
            },
            navController = navController
        )
    } else {
        PhoneLayout(
            destinations = destinations,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                selectedIndex = index
                navController.navigateToTab(destinations[index].route)
            },
            navController = navController
        )
    }
}

// ---------------------------------------------------------------------------
// Phone layout – bottom navigation bar
// ---------------------------------------------------------------------------

@Composable
private fun PhoneLayout(
    destinations: List<TopDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    navController: NavHostController
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, dest ->
                    NavigationBarItem(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

// ---------------------------------------------------------------------------
// Tablet layout – navigation rail on the left
// ---------------------------------------------------------------------------

@Composable
private fun RailLayout(
    destinations: List<TopDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    navController: NavHostController
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            destinations.forEachIndexed { index, dest ->
                NavigationRailItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) }
                )
            }
        }
        AppNavHost(
            navController = navController,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// NavHost – routes for all top-level screens
// ---------------------------------------------------------------------------

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navTo: (String) -> Unit = { route -> navController.navigate(route) }
    val toProfile: (Long) -> Unit = { id -> navController.navigate("profile/$id") }
    val toGroup: (Long) -> Unit = { id -> navController.navigate("group/$id") }
    val toPage: (Long) -> Unit = { id -> navController.navigate("page/$id") }
    val toPost: (Long) -> Unit = { id -> navController.navigate("post/$id") }
    val toThread: (Long) -> Unit = { id -> navController.navigate("thread/$id") }

    NavHost(
        navController = navController,
        startDestination = TopDestination.Feed.route,
        modifier = modifier
    ) {
        // ── Tab screens ──
        composable(TopDestination.Feed.route) {
            com.worksphere.app.ui.feed.FeedScreen(
                onNavigateToProfile = toProfile,
                onNavigateToGroup = toGroup,
                onNavigateToPage = toPage,
                onNavigateToPost = toPost
            )
        }
        composable(TopDestination.Search.route) {
            com.worksphere.app.ui.search.SearchScreen(
                onNavigateToProfile = toProfile,
                onNavigateToGroup = toGroup,
                onNavigateToPage = toPage,
                onNavigateToPost = toPost
            )
        }
        composable(TopDestination.Org.route) {
            com.worksphere.app.ui.org.OrgScreen(
                onNavigateToProfile = toProfile
            )
        }
        composable(TopDestination.Messages.route) {
            val aiScope = rememberCoroutineScope()
            com.worksphere.app.ui.messages.ConversationsScreen(
                onNavigateToThread = toThread,
                onNavigateToAiChat = {
                    aiScope.launch {
                        try {
                            data class BotInfo(val id: Long)
                            val bot = com.worksphere.app.data.ApiClient.get<BotInfo>("/ai/bot/info")
                            val conv = com.worksphere.app.data.ApiClient.post<com.worksphere.app.data.ConversationDto>("/conversations/direct/${bot.id}")
                            toThread(conv.id)
                        } catch (_: Exception) {}
                    }
                },
                onNavigateToProfile = toProfile
            )
        }
        composable(TopDestination.More.route) {
            MoreScreen(
                onNavigateToBrowse = { navController.navigate("browse") },
                onNavigateToNotifications = { navController.navigate("notifications") },
                onNavigateToProfile = { navController.navigate("profile/${AuthService.userId}") }
            )
        }
        composable("browse") {
            DetailScaffold("Browse", navController) {
                com.worksphere.app.ui.browse.BrowseScreen(
                    onNavigateToProfile = toProfile,
                    onNavigateToGroup = toGroup,
                    onNavigateToPage = toPage
                )
            }
        }
        composable("notifications") {
            DetailScaffold("Notifications", navController) {
                com.worksphere.app.ui.notifications.NotificationsScreen(
                    onNavigateToProfile = toProfile,
                    onNavigateToPost = toPost
                )
            }
        }

        // ── Detail screens (with back navigation) ──
        composable("profile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: return@composable
            DetailScaffold("Profile", navController) {
                com.worksphere.app.ui.profile.ProfileScreen(
                    userId = userId,
                    onNavigateToProfile = toProfile,
                    onNavigateToGroup = toGroup,
                    onNavigateToPage = toPage,
                    onNavigateToPost = toPost
                )
            }
        }
        composable("group/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")?.toLongOrNull() ?: return@composable
            DetailScaffold("Group", navController) {
                com.worksphere.app.ui.groups.GroupScreen(
                    groupId = groupId,
                    onNavigateToProfile = toProfile,
                    onNavigateToGroup = toGroup,
                    onNavigateToPage = toPage,
                    onNavigateToPost = toPost
                )
            }
        }
        composable("page/{pageId}") { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId")?.toLongOrNull() ?: return@composable
            DetailScaffold("Page", navController) {
                com.worksphere.app.ui.pages.PageScreen(
                    pageId = pageId,
                    onNavigateToProfile = toProfile,
                    onNavigateToGroup = toGroup,
                    onNavigateToPage = toPage,
                    onNavigateToPost = toPost
                )
            }
        }
        composable("post/{postId}") { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId")?.toLongOrNull() ?: return@composable
            DetailScaffold("Post", navController) {
                PostDetailScreen(postId = postId, onNavigateToProfile = toProfile)
            }
        }
        composable("thread/{conversationId}") { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId")?.toLongOrNull() ?: return@composable
            DetailScaffold("Messages", navController) {
                com.worksphere.app.ui.messages.MessageThreadScreen(
                    conversationId = convId,
                    onBack = { navController.popBackStack() },
                    onNavigateToProfile = toProfile
                )
            }
        }
    }
}

/**
 * More menu screen with links to Browse, Notifications, Profile, Logout.
 */
@Composable
private fun MoreScreen(
    onNavigateToBrowse: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

        listOf(
            Triple("Groups & Pages", Icons.Filled.Explore, onNavigateToBrowse),
            Triple("Notifications", Icons.Filled.Notifications, onNavigateToNotifications),
            Triple("My Profile", Icons.Filled.Person, onNavigateToProfile),
        ).forEach { (label, icon, action) ->
            Surface(
                onClick = action,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = { AuthService.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Log Out")
        }
    }
}

/**
 * Wrapper for detail screens with a top bar and back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(
    title: String,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            content()
        }
    }
}

/**
 * Simple post detail screen showing the post and its comments.
 */
@Composable
private fun PostDetailScreen(postId: Long, onNavigateToProfile: (Long) -> Unit) {
    var post by remember { mutableStateOf<com.worksphere.app.data.PostDto?>(null) }
    var comments by remember { mutableStateOf<List<com.worksphere.app.data.CommentDto>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(postId) {
        post = try { com.worksphere.app.data.ApiClient.get("/posts/$postId") } catch (_: Exception) { null }
        comments = try { com.worksphere.app.data.ApiClient.get("/posts/$postId/comments") } catch (_: Exception) { emptyList() }
    }

    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp)
        ) {
            post?.let { p ->
                item {
                    com.worksphere.app.ui.feed.PostCard(
                        post = p,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToPost = {}
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Comments (${comments.size})", style = MaterialTheme.typography.titleSmall) }
            items(comments.size) { idx ->
                val c = comments[idx]
                Row(
                    Modifier.padding(start = (c.depth * 16).dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.worksphere.app.ui.components.Avatar(url = c.author.avatarUrl, name = c.author.displayName, size = 28.dp)
                    Column {
                        Text(c.author.displayName, style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(c.content, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        // Comment input
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a comment...", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (commentText.isNotBlank()) {
                        val text = commentText
                        commentText = ""
                        scope.launch {
                            try {
                                com.worksphere.app.data.ApiClient.post<com.worksphere.app.data.CommentDto>(
                                    "/comments",
                                    mapOf("postId" to postId, "content" to text)
                                )
                                comments = com.worksphere.app.data.ApiClient.get("/posts/$postId/comments")
                            } catch (_: Exception) {}
                        }
                    }
                })
            )
            IconButton(onClick = {
                if (commentText.isNotBlank()) {
                    val text = commentText
                    commentText = ""
                    scope.launch {
                        try {
                            com.worksphere.app.data.ApiClient.post<com.worksphere.app.data.CommentDto>(
                                "/comments",
                                mapOf("postId" to postId, "content" to text)
                            )
                            comments = com.worksphere.app.data.ApiClient.get("/posts/$postId/comments")
                        } catch (_: Exception) {}
                    }
                }
            }) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

/**
 * Temporary placeholder for screens that haven't been built yet.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        // Pop up to the start destination so the back stack doesn't grow.
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
