package com.worksphere.app.ui.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.launch

private val REACTIONS = listOf(
    "\uD83D\uDC4D" to "like",
    "\u2764\uFE0F" to "love",
    "\uD83D\uDE02" to "haha",
    "\uD83D\uDE2E" to "wow",
    "\uD83D\uDE22" to "sad",
    "\uD83D\uDE21" to "angry"
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
    post: PostDto,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToPost: (Long) -> Unit,
    onNavigateToGroup: (Long) -> Unit = {},
    onNavigateToPage: (Long) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var currentReaction by remember(post.id) { mutableStateOf(post.currentUserReaction) }
    var reactionCounts by remember(post.id) { mutableStateOf(post.reactionCounts) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var pollVotes by remember(post.id) { mutableStateOf(post.poll?.currentUserVotes ?: emptyList()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onNavigateToProfile(post.author.id) }
            ) {
                Avatar(url = post.author.avatarUrl, name = post.author.displayName, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        post.author.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        post.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Source (group/page)
            if (post.targetType != null && post.targetId != null && post.targetId != 0L) {
                val sourceLabel = when (post.targetType) {
                    "GROUP_FEED" -> "in group"
                    "PAGE_FEED" -> "on page"
                    "TEAM_FEED" -> "in team"
                    else -> null
                }
                if (sourceLabel != null) {
                    Text(
                        "Posted $sourceLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable {
                                when (post.targetType) {
                                    "GROUP_FEED" -> onNavigateToGroup(post.targetId)
                                    "PAGE_FEED" -> onNavigateToPage(post.targetId)
                                }
                            }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Content
            Text(
                post.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onNavigateToPost(post.id) }
            )

            // Attachments
            if (post.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(post.attachments, key = { it.id }) { attachment ->
                        if (attachment.mediaType?.startsWith("image") == true) {
                            AsyncImage(
                                model = attachment.fileUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 160.dp, height = 120.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // Poll
            post.poll?.let { poll ->
                Spacer(Modifier.height(8.dp))
                PollSection(
                    poll = poll,
                    votedOptions = pollVotes,
                    onVote = { optionId ->
                        scope.launch {
                            try {
                                val result = ApiClient.post<PollDto>("/polls/${poll.id}/vote", mapOf("optionIds" to listOf(optionId)))
                                pollVotes = result.currentUserVotes ?: listOf(optionId)
                            } catch (_: Exception) {
                                pollVotes = pollVotes + optionId
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Divider(thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            // Reaction bar + comment count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                // Reaction summary
                reactionCounts.forEach { (emoji, count) ->
                    if (count > 0) {
                        Text(
                            "$emoji $count",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Comment count - clickable to open post detail
                Row(
                    modifier = Modifier.clickable { onNavigateToPost(post.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(14.dp))
                    Text("${post.commentCount} comments", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Emoji reaction buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                REACTIONS.take(if (showEmojiPicker) REACTIONS.size else 3).forEach { (emoji, type) ->
                    val isSelected = currentReaction == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                try {
                                    if (isSelected) {
                                        ApiClient.post<Any>("/posts/${post.id}/reactions/remove", mapOf("type" to type))
                                        val newCounts = reactionCounts.toMutableMap()
                                        newCounts[emoji] = (newCounts[emoji] ?: 1) - 1
                                        reactionCounts = newCounts
                                        currentReaction = null
                                    } else {
                                        ApiClient.post<Any>("/posts/${post.id}/reactions", mapOf("type" to type))
                                        val newCounts = reactionCounts.toMutableMap()
                                        if (currentReaction != null) {
                                            val prevEmoji = REACTIONS.find { it.second == currentReaction }?.first
                                            if (prevEmoji != null) newCounts[prevEmoji] = (newCounts[prevEmoji] ?: 1) - 1
                                        }
                                        newCounts[emoji] = (newCounts[emoji] ?: 0) + 1
                                        reactionCounts = newCounts
                                        currentReaction = type
                                    }
                                } catch (_: Exception) { }
                            }
                        },
                        label = { Text(emoji, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp).padding(end = 4.dp)
                    )
                }

                TextButton(
                    onClick = { showEmojiPicker = !showEmojiPicker },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        if (showEmojiPicker) "less" else "more",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PollSection(
    poll: PollDto,
    votedOptions: List<Long>,
    onVote: (Long) -> Unit
) {
    val hasVoted = votedOptions.isNotEmpty()

    Column {
        Text(poll.question, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))

        poll.options.forEach { option ->
            val fraction = if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes else 0f
            val isVoted = option.id in votedOptions

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .then(
                        if (!hasVoted || poll.allowMultiple) Modifier.clickable { onVote(option.id) }
                        else Modifier
                    )
            ) {
                if (!hasVoted) {
                    RadioButton(
                        selected = isVoted,
                        onClick = { onVote(option.id) },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }

                Column(Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.labelSmall)
                    if (hasVoted) {
                        LinearProgressIndicator(
                            progress = fraction,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                if (hasVoted) {
                    Text(
                        "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        if (poll.totalVotes > 0) {
            Text(
                "${poll.totalVotes} votes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
