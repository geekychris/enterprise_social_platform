import SwiftUI

struct PostCard: View {
    let post: PostDto
    var showFullComments = false

    @Environment(AuthService.self) private var auth
    @State private var reactionCounts: [String: Int]
    @State private var currentReaction: String?

    init(post: PostDto, showFullComments: Bool = false) {
        self.post = post
        self.showFullComments = showFullComments
        self._reactionCounts = State(initialValue: post.reactionCounts)
        self._currentReaction = State(initialValue: post.currentUserReaction)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack(spacing: 8) {
                NavigationLink(value: ProfileNavigation(userId: post.author.id)) {
                    AvatarView(url: post.author.avatarUrl, name: post.author.displayName, size: 32)
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 1) {
                    NavigationLink(value: ProfileNavigation(userId: post.author.id)) {
                        Text(post.author.displayName)
                            .font(.caption.bold())
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(.plain)

                    HStack(spacing: 4) {
                        Text(RelativeTime.format(post.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        if post.recommended == true {
                            Text("Suggested")
                                .font(.caption2)
                                .foregroundStyle(.blue)
                        }
                    }
                }

                Spacer()

                if let vis = post.visibility, vis != "PUBLIC" {
                    Image(systemName: vis == "PRIVATE" ? "lock.fill" : "person.2.fill")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            // Content
            MarkdownText(content: post.content, font: .caption)

            // Attachments
            if !post.attachments.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(post.attachments) { att in
                            if att.mediaType?.hasPrefix("image/") == true, let url = URL(string: att.fileUrl) {
                                AsyncImage(url: url) { image in
                                    image.resizable().scaledToFill()
                                } placeholder: {
                                    Rectangle().fill(.gray.opacity(0.1))
                                }
                                .frame(width: 140, height: 100)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                            }
                        }
                    }
                }
            }

            // Reactions + comments
            HStack(spacing: 8) {
                ReactionButton(
                    reactionCounts: reactionCounts,
                    currentReaction: currentReaction,
                    targetId: post.id
                ) { type in
                    react(type)
                }

                Spacer()

                HStack(spacing: 3) {
                    Image(systemName: "bubble.left")
                        .font(.caption2)
                    Text("\(post.commentCount)")
                        .font(.caption2)
                }
                .foregroundStyle(.secondary)
            }
        }
        .padding(10)
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .shadow(color: .black.opacity(0.04), radius: 1, y: 1)
    }

    private func react(_ type: String?) {
        Task {
            do {
                if let type {
                    if let old = currentReaction {
                        reactionCounts[old, default: 0] -= 1
                    }
                    reactionCounts[type, default: 0] += 1
                    currentReaction = type

                    struct ReactBody: Codable { let targetId: Int64; let type: String }
                    try await APIClient.shared.postVoid("/reactions", body: ReactBody(targetId: post.id, type: type))
                } else {
                    if let old = currentReaction {
                        reactionCounts[old, default: 0] -= 1
                    }
                    currentReaction = nil
                    try await APIClient.shared.delete("/reactions/\(post.id)")
                }
            } catch {}
        }
    }
}

struct ProfileNavigation: Hashable {
    let userId: Int64
}
