export interface UserDto {
  id: number;
  username: string;
  displayName: string;
  email: string;
  avatarUrl: string | null;
  coverUrl: string | null;
  bio: string | null;
  visibility: string;
  followerCount: number;
  followingCount: number;
  admin: boolean;
  phone: string | null;
  location: string | null;
  jobTitle: string | null;
  department: string | null;
  joinedCompanyAt: string | null;
  managerId: number | null;
  managerName: string | null;
  interests: string | null;
  skills: string | null;
  linkedinUrl: string | null;
  timezone: string | null;
  pronouns: string | null;
}

export interface AttachmentDto {
  id: number;
  fileUrl: string;
  mediaType: string;
  fileSize: number;
  width: number | null;
  height: number | null;
}

export interface AuthorDto {
  id: number;
  username: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface PostDto {
  id: number;
  author: AuthorDto;
  content: string;
  targetType: string | null;
  targetId: number | null;
  visibility: string;
  attachments: AttachmentDto[];
  reactionCounts: Record<string, number>;
  currentUserReaction: string | null;
  commentCount: number;
  createdAt: string;
  recommended: boolean;
  recommendationScore: number | null;
}

export interface CommentDto {
  id: number;
  author: AuthorDto;
  content: string;
  depth: number;
  postId: number;
  parentCommentId: number | null;
  attachments: AttachmentDto[];
  reactionCounts: Record<string, number>;
  currentUserReaction: string | null;
  replies: CommentDto[];
  createdAt: string;
}

export interface FeedResponse {
  posts: PostDto[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface SearchHit {
  id: number;
  objectType: string;
  name: string;
  description: string | null;
  avatarUrl: string | null;
  score: number;
}

export interface SearchResultDto {
  hits: SearchHit[];
  totalHits: number;
}

export interface TeamDto {
  id: number;
  name: string;
  description: string | null;
  avatarUrl: string | null;
  memberCount: number;
  visibility: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  admin: boolean;
}

export interface GroupDto {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  avatarUrl: string | null;
  coverUrl: string | null;
  visibility: string;
  memberCount: number;
  pinnedPostId: number | null;
}

export interface PageDto {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  avatarUrl: string | null;
  coverUrl: string | null;
  visibility: string;
  ownerType: string | null;
  ownerId: number;
  followerCount: number;
  pinnedPostId: number | null;
}

export interface MembershipDto {
  userId: number;
  groupId: number;
  role: string;
  status: string;
  userName: string;
  userAvatarUrl: string | null;
  joinedAt: string;
}

export interface MessageDto {
  id: number;
  conversationId: number;
  sender: AuthorDto;
  content: string;
  attachments: AttachmentDto[];
  read: boolean;
  createdAt: string;
}

export interface ConversationDto {
  id: number;
  name: string | null;
  type: 'DIRECT' | 'GROUP';
  participants: AuthorDto[];
  lastMessage: MessageDto | null;
  unreadCount: number;
  createdAt: string;
}

export type ReactionType = 'LIKE' | 'LOVE' | 'HAHA' | 'WOW' | 'SAD' | 'ANGRY';

export interface FriendRequestDto {
  id: number;
  senderId: number;
  senderUsername: string;
  senderDisplayName: string;
  senderAvatarUrl: string | null;
  receiverId: number;
  receiverUsername: string;
  receiverDisplayName: string;
  receiverAvatarUrl: string | null;
  status: string;
  createdAt: string;
}
