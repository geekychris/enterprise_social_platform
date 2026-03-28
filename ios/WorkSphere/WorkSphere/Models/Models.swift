import Foundation

// MARK: - Auth

struct LoginRequest: Codable {
    let username: String
    let password: String
}

struct RegisterRequest: Codable {
    let username: String
    let displayName: String
    let email: String
    let password: String
    let bio: String?
}

struct LoginResponse: Codable {
    let token: String
    let userId: Int64
    let username: String
    let admin: Bool
}

// MARK: - User

struct UserDto: Codable, Identifiable {
    let id: Int64
    let username: String
    let displayName: String
    let email: String?
    let avatarUrl: String?
    let coverUrl: String?
    let bio: String?
    let visibility: String?
    let followerCount: Int?
    let followingCount: Int?
    let admin: Bool?
    let phone: String?
    let location: String?
    let jobTitle: String?
    let department: String?
    let joinedCompanyAt: String?
    let managerId: Int64?
    let managerName: String?
    let interests: String?
    let skills: String?
    let linkedinUrl: String?
    let timezone: String?
    let pronouns: String?
}

struct AuthorDto: Codable, Identifiable, Hashable {
    let id: Int64
    let username: String
    let displayName: String
    let avatarUrl: String?
}

struct UserSummaryDto: Codable, Identifiable {
    let id: Int64
    let username: String
    let displayName: String
    let avatarUrl: String?
}

struct PageFollowerDto: Codable, Identifiable {
    let id: Int64
    let username: String
    let displayName: String
    let avatarUrl: String?
}

// MARK: - Org

struct OrgAssignmentDto: Codable, Identifiable {
    let id: Int64
    let userId: Int64
    let nodeId: Int64
    let roleName: String?
    let userName: String?
    let userAvatarUrl: String?
    let nodeName: String?
    let parentNodeId: Int64?
}

struct OrgChainDto: Codable, Identifiable {
    let id: Int64
    let userId: Int64
    let userName: String?
    let userAvatarUrl: String?
    let roleName: String?
    let nodeName: String?
    let depth: Int?
}

struct OrgReportDto: Codable, Identifiable {
    let id: Int64
    let userId: Int64
    let userName: String?
    let userAvatarUrl: String?
    let roleName: String?
    let nodeName: String?
}

// MARK: - Post

struct PostDto: Codable, Identifiable {
    let id: Int64
    let author: AuthorDto
    let content: String
    let targetType: String?
    let targetId: Int64?
    let visibility: String?
    let attachments: [AttachmentDto]
    let reactionCounts: [String: Int]
    let currentUserReaction: String?
    let commentCount: Int
    let createdAt: String
    let recommended: Bool?
    let recommendationScore: Double?
    let poll: PollDto?
}

struct PollDto: Codable, Identifiable {
    let id: Int64
    let question: String
    let allowMultiple: Bool
    let closesAt: String?
    let closed: Bool
    let options: [PollOptionDto]
    let totalVotes: Int
    let currentUserVotes: [Int64]?
}

struct PollOptionDto: Codable, Identifiable {
    let id: Int64
    let label: String
    let voteCount: Int
}

struct FeedResponse: Codable {
    let posts: [PostDto]
    let nextCursor: String?
    let hasMore: Bool
}

struct CreatePostRequest: Codable {
    let content: String
    let visibility: String
    let targetType: String?
    let targetId: Int64?
    let attachmentIds: [Int64]?
}

// MARK: - Comment

struct CommentDto: Codable, Identifiable {
    let id: Int64
    let author: AuthorDto
    let content: String
    let depth: Int
    let postId: Int64
    let parentCommentId: Int64?
    let attachments: [AttachmentDto]?
    let reactionCounts: [String: Int]?
    let currentUserReaction: String?
    let replies: [CommentDto]?
    let createdAt: String
}

struct CreateCommentRequest: Codable {
    let postId: Int64
    let parentCommentId: Int64?
    let content: String
    let attachmentIds: [Int64]?
}

// MARK: - Attachment

struct AttachmentDto: Codable, Identifiable {
    let id: Int64
    let fileUrl: String
    let mediaType: String?
    let fileSize: Int?
    let width: Int?
    let height: Int?
}

struct UploadResponse: Codable {
    let id: Int64
    let fileUrl: String
    let mediaType: String?
}

// MARK: - Group

struct GroupDto: Codable, Identifiable {
    let id: Int64
    let name: String
    let slug: String?
    let description: String?
    let avatarUrl: String?
    let coverUrl: String?
    let visibility: String?
    let memberCount: Int?
    let pinnedPostId: Int64?
}

struct MembershipDto: Codable {
    let userId: Int64
    let groupId: Int64
    let role: String
    let status: String
    let userName: String?
    let userAvatarUrl: String?
    let joinedAt: String?
}

struct CreateGroupRequest: Codable {
    let name: String
    let description: String?
    let visibility: String?
}

// MARK: - Page

struct PageDto: Codable, Identifiable {
    let id: Int64
    let name: String
    let slug: String?
    let description: String?
    let avatarUrl: String?
    let coverUrl: String?
    let visibility: String?
    let ownerType: String?
    let ownerId: Int64?
    let followerCount: Int?
    let pinnedPostId: Int64?
}

// MARK: - Team

struct TeamDto: Codable, Identifiable {
    let id: Int64
    let name: String
    let description: String?
    let avatarUrl: String?
    let memberCount: Int?
    let visibility: String?
}

// MARK: - Conversation & Messages

struct ConversationDto: Codable, Identifiable {
    let id: Int64
    let name: String?
    let type: String
    let participants: [AuthorDto]
    let lastMessage: MessageDto?
    let unreadCount: Int
    let createdAt: String?
}

struct MessageDto: Codable, Identifiable {
    let id: Int64
    let conversationId: Int64
    let sender: AuthorDto
    let content: String?
    let attachments: [AttachmentDto]?
    let read: Bool?
    let createdAt: String
}

struct CreateConversationRequest: Codable {
    let participantIds: [Int64]
    let name: String?
}

// MARK: - Search

struct SearchResultDto: Codable {
    let hits: [SearchHit]
    let totalHits: Int
}

struct SearchHit: Codable, Identifiable, Hashable {
    let id: Int64
    let objectType: String
    let name: String
    let description: String?
    let avatarUrl: String?
    let score: Double?
}

// MARK: - Notifications

struct NotificationDto: Codable, Identifiable {
    let id: Int64
    let type: String
    let actorId: Int64?
    let actorName: String?
    let actorAvatarUrl: String?
    let targetId: Int64?
    let postId: Int64?
    let message: String?
    let read: Bool
    let createdAt: String
}

// MARK: - Reactions

enum ReactionType: String, CaseIterable {
    case like = "LIKE"
    case love = "LOVE"
    case haha = "HAHA"
    case wow = "WOW"
    case sad = "SAD"
    case angry = "ANGRY"

    var emoji: String {
        switch self {
        case .like: return "👍"
        case .love: return "❤️"
        case .haha: return "😂"
        case .wow: return "😮"
        case .sad: return "😢"
        case .angry: return "😠"
        }
    }
}

// MARK: - Friend Request

struct FriendRequestDto: Codable, Identifiable {
    let id: Int64
    let senderId: Int64
    let senderUsername: String?
    let senderDisplayName: String?
    let senderAvatarUrl: String?
    let receiverId: Int64
    let receiverUsername: String?
    let receiverDisplayName: String?
    let receiverAvatarUrl: String?
    let status: String
    let createdAt: String?
}
