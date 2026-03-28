package com.worksphere.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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

private val FILTER_TYPES = listOf("All", "Users", "Groups", "Pages", "Posts")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    onNavigateToPage: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var totalHits by remember { mutableStateOf(0) }

    fun doSearch() {
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            try {
                val typeParam = when (selectedFilter) {
                    "All" -> ""
                    "Users" -> "&type=user"
                    "Groups" -> "&type=group"
                    "Pages" -> "&type=page"
                    "Posts" -> "&type=post"
                    else -> ""
                }
                val response = ApiClient.get<SearchResultDto>("/search?q=${query}$typeParam")
                results = response.hits
                totalHits = response.totalHits
            } catch (_: Exception) { }
            isSearching = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search WorkSphere...", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    TextButton(onClick = { doSearch() }) {
                        Text("Search", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(FILTER_TYPES) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        selectedFilter = filter
                        if (query.isNotBlank()) doSearch()
                    },
                    label = { Text(filter, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isSearching) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            if (results.isNotEmpty()) {
                Text(
                    "$totalHits results",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = { "${it.objectType}_${it.id}" }) { hit ->
                    SearchResultRow(
                        hit = hit,
                        onClick = {
                            when (hit.objectType.uppercase()) {
                                "USER" -> onNavigateToProfile(hit.id)
                                "GROUP" -> onNavigateToGroup(hit.id)
                                "PAGE" -> onNavigateToPage(hit.id)
                                "POST" -> onNavigateToPost(hit.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchHit,
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
            Avatar(url = hit.avatarUrl, name = hit.name, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hit.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            hit.objectType,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                hit.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
