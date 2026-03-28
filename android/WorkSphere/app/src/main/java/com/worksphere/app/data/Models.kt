package com.worksphere.app.data

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type

// ---------------------------------------------------------------------------
// Custom Gson TypeAdapter: backend may send Long IDs as JSON strings or numbers
// ---------------------------------------------------------------------------

class SafeLongAdapter : TypeAdapter<Long>() {
    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Long? {
        return when (reader.peek()) {
            JsonToken.NULL -> { reader.nextNull(); null }
            JsonToken.STRING -> {
                val s = reader.nextString()
                s.toLongOrNull() ?: 0L
            }
            JsonToken.NUMBER -> reader.nextLong()
            else -> { reader.skipValue(); null }
        }
    }
}

class SafeLongAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: com.google.gson.reflect.TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType == Long::class.java || type.rawType == java.lang.Long::class.java) {
            @Suppress("UNCHECKED_CAST")
            return SafeLongAdapter() as TypeAdapter<T>
        }
        return null
    }
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: Long,
    val username: String,
    val admin: Boolean
)

data class RegisterRequest(
    val username: String,
    val displayName: String,
    val email: String,
    val password: String,
    val bio: String?
)

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

data class UserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val bio: String?,
    val visibility: String?,
    val followerCount: Int?,
    val followingCount: Int?,
    val admin: Boolean?,
    val phone: String?,
    val location: String?,
    val jobTitle: String?,
    val department: String?,
    val pronouns: String?,
    val interests: String?,
    val skills: String?,
    val timezone: String?
)

data class AuthorDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String?
)

// ---------------------------------------------------------------------------
// Posts & Feed
// ---------------------------------------------------------------------------

data class PostDto(
    val id: Long,
    val author: AuthorDto,
    val content: String,
    val targetType: String?,
    val targetId: Long?,
    val visibility: String?,
    val attachments: List<AttachmentDto>,
    val reactionCounts: Map<String, Int>,
    val currentUserReaction: String?,
    val commentCount: Int,
    val createdAt: String,
    val recommended: Boolean?,
    val poll: PollDto?
)

data class PollDto(
    val id: Long,
    val question: String,
    val allowMultiple: Boolean,
    val closesAt: String?,
    val closed: Boolean,
    val options: List<PollOptionDto>,
    val totalVotes: Int,
    val currentUserVotes: List<Long>?
)

data class PollOptionDto(
    val id: Long,
    val label: String,
    val voteCount: Int
)

data class FeedResponse(
    val posts: List<PostDto>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class AttachmentDto(
    val id: Long,
    val fileUrl: String,
    val mediaType: String?,
    val fileSize: Int?,
    val width: Int?,
    val height: Int?
)

// ---------------------------------------------------------------------------
// Comments
// ---------------------------------------------------------------------------

data class CommentDto(
    val id: Long,
    val author: AuthorDto,
    val content: String,
    val depth: Int,
    val postId: Long,
    val parentCommentId: Long?,
    val replies: List<CommentDto>?,
    val createdAt: String
)

// ---------------------------------------------------------------------------
// Messaging
// ---------------------------------------------------------------------------

data class ConversationDto(
    val id: Long,
    val name: String?,
    val type: String,
    val participants: List<AuthorDto>,
    val lastMessage: MessageDto?,
    val unreadCount: Int,
    val createdAt: String?
)

data class MessageDto(
    val id: Long,
    val conversationId: Long,
    val sender: AuthorDto,
    val content: String?,
    val attachments: List<AttachmentDto>?,
    val read: Boolean?,
    val createdAt: String
)

// ---------------------------------------------------------------------------
// Groups
// ---------------------------------------------------------------------------

data class GroupDto(
    val id: Long,
    val name: String,
    val slug: String?,
    val description: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val visibility: String?,
    val memberCount: Int?,
    val pinnedPostId: Long?
)

// ---------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------

data class PageDto(
    val id: Long,
    val name: String,
    val slug: String?,
    val description: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val visibility: String?,
    val followerCount: Int?,
    val ownerId: Long?
)

// ---------------------------------------------------------------------------
// Memberships
// ---------------------------------------------------------------------------

data class MembershipDto(
    val userId: Long,
    val groupId: Long,
    val role: String,
    val status: String,
    val userName: String?,
    val userAvatarUrl: String?,
    val joinedAt: String?
)

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

data class SearchResultDto(
    val hits: List<SearchHit>,
    val totalHits: Int
)

data class SearchHit(
    val id: Long,
    val objectType: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val score: Double?
)

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------

data class NotificationDto(
    val id: Long,
    val type: String,
    val actorId: Long?,
    val actorName: String?,
    val actorAvatarUrl: String?,
    val targetId: Long?,
    val postId: Long?,
    val message: String?,
    val read: Boolean,
    val createdAt: String
)

// ---------------------------------------------------------------------------
// Org Chart
// ---------------------------------------------------------------------------

data class OrgUnitDto(
    val id: Long,
    val name: String,
    val type: String,
    val parentId: Long?,
    val headUserId: Long?,
    val headUserName: String?,
    val description: String?,
    val childCount: Int,
    val memberCount: Int
)

data class OrgMemberDto(
    val id: Long,
    val userId: Long,
    val userName: String,
    val userAvatarUrl: String?,
    val title: String?,
    val relationshipType: String?,
    val level: String?,
    val orgUnitName: String?
)
