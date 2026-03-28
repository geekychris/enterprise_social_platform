package com.worksphere.app.ui.org

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksphere.app.data.*
import com.worksphere.app.ui.components.Avatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun OrgScreen(
    onNavigateToProfile: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<OrgMemberDto?>(null) }
    var reportingChain by remember { mutableStateOf<List<OrgMemberDto>>(emptyList()) }
    var directReports by remember { mutableStateOf<List<OrgMemberDto>>(emptyList()) }
    var orgUnits by remember { mutableStateOf<List<OrgUnitDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showPersonView by remember { mutableStateOf(false) }

    // Load root org units
    LaunchedEffect(Unit) {
        try {
            orgUnits = ApiClient.get<List<OrgUnitDto>>("/org/units")
        } catch (_: Exception) { }
        isLoading = false
    }

    fun searchPerson() {
        if (searchQuery.isBlank()) return
        scope.launch {
            try {
                val results = ApiClient.get<List<OrgMemberDto>>("/org/search?q=$searchQuery")
                if (results.isNotEmpty()) {
                    val person = results.first()
                    selectedPerson = person
                    reportingChain = ApiClient.get<List<OrgMemberDto>>("/org/users/${person.userId}/chain")
                    directReports = ApiClient.get<List<OrgMemberDto>>("/org/users/${person.userId}/reports")
                    showPersonView = true
                }
            } catch (_: Exception) { }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search for a person...", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    TextButton(onClick = { searchPerson() }) {
                        Text("Go", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (showPersonView && selectedPerson != null) {
            // Person view
            PersonOrgView(
                person = selectedPerson!!,
                reportingChain = reportingChain,
                directReports = directReports,
                onNavigateToProfile = onNavigateToProfile,
                onBack = { showPersonView = false }
            )
        } else {
            // Tree view
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(orgUnits, key = { it.id }) { unit ->
                    OrgUnitRow(
                        unit = unit,
                        onNavigateToProfile = onNavigateToProfile
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonOrgView(
    person: OrgMemberDto,
    reportingChain: List<OrgMemberDto>,
    directReports: List<OrgMemberDto>,
    onNavigateToProfile: (Long) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back to tree", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text("Reporting Chain", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }

        // Chain reversed so CEO is at top
        items(reportingChain.reversed()) { member ->
            PersonRow(
                member = member,
                isHighlighted = member.userId == person.userId,
                indent = 0,
                onClick = { onNavigateToProfile(member.userId) }
            )
        }

        // Highlighted current person if not in chain
        if (reportingChain.none { it.userId == person.userId }) {
            item {
                PersonRow(
                    member = person,
                    isHighlighted = true,
                    indent = 0,
                    onClick = { onNavigateToProfile(person.userId) }
                )
            }
        }

        if (directReports.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Direct Reports", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            items(directReports) { member ->
                PersonRow(
                    member = member,
                    isHighlighted = false,
                    indent = 1,
                    onClick = { onNavigateToProfile(member.userId) }
                )
            }
        }
    }
}

@Composable
private fun PersonRow(
    member: OrgMemberDto,
    isHighlighted: Boolean,
    indent: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp)
            .clickable(onClick = onClick),
        color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
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

@Composable
private fun OrgUnitRow(
    unit: OrgUnitDto,
    onNavigateToProfile: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var children by remember { mutableStateOf<List<OrgUnitDto>>(emptyList()) }
    var members by remember { mutableStateOf<List<OrgMemberDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    fun loadChildren() {
        if (loaded) return
        scope.launch {
            try {
                if (unit.childCount > 0) {
                    children = ApiClient.get<List<OrgUnitDto>>("/org/units/${unit.id}/children")
                }
                if (unit.memberCount > 0) {
                    members = ApiClient.get<List<OrgMemberDto>>("/org/units/${unit.id}/members")
                }
                loaded = true
            } catch (_: Exception) { }
        }
    }

    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                expanded = !expanded
                if (expanded) loadChildren()
            },
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            unit.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                unit.type,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        "${unit.memberCount} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                children.forEach { child ->
                    OrgUnitRow(unit = child, onNavigateToProfile = onNavigateToProfile)
                }
                members.forEach { member ->
                    PersonRow(
                        member = member,
                        isHighlighted = false,
                        indent = 0,
                        onClick = { onNavigateToProfile(member.userId) }
                    )
                }
            }
        }
    }
}
