import SwiftUI

struct PostDetailView: View {
    let postId: Int64
    @State private var post: PostDto?
    @State private var comments: [CommentDto] = []
    @State private var commentText = ""
    @State private var loading = true

    var body: some View {
        Group {
            if let post {
                postContent(post)
            } else if loading {
                ProgressView()
            } else {
                ContentUnavailableView("Post not found", systemImage: "exclamationmark.triangle")
            }
        }
        .navigationTitle("Post")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @ViewBuilder
    private func postContent(_ post: PostDto) -> some View {
        ScrollView {
            VStack(spacing: 0) {
                PostCard(post: post, showFullComments: true)
                    .padding()

                Divider()

                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(comments) { comment in
                        CommentView(comment: comment, postId: postId, onRefresh: loadComments)
                        Divider().padding(.leading, comment.depth > 0 ? 56 : 16)
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            commentInputBar
        }
    }

    private var commentInputBar: some View {
        HStack(spacing: 6) {
            SpeechButton(text: $commentText)

            TextField("Write a comment...", text: $commentText)
                .textFieldStyle(.roundedBorder)

            Button(action: submitComment) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.title2)
            }
            .disabled(commentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding()
        .background(.bar)
    }

    private func load() async {
        do {
            post = try await APIClient.shared.get("/posts/\(postId)")
            loadComments()
        } catch {}
        loading = false
    }

    private func loadComments() {
        Task {
            do {
                comments = try await APIClient.shared.get("/posts/\(postId)/comments")
            } catch {}
        }
    }

    private func submitComment() {
        let text = commentText
        commentText = ""
        Task {
            do {
                let _: CommentDto = try await APIClient.shared.post("/comments", body: CreateCommentRequest(
                    postId: postId, parentCommentId: nil, content: text, attachmentIds: nil
                ))
                loadComments()
            } catch {}
        }
    }
}

struct CommentView: View {
    let comment: CommentDto
    let postId: Int64
    var onRefresh: () -> Void = {}

    @State private var showReply = false
    @State private var replyText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            commentHeader
            commentActions
            replyInput
            nestedReplies
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .padding(.leading, comment.depth > 0 ? 28 : 0)
    }

    private var commentHeader: some View {
        HStack(alignment: .top, spacing: 8) {
            AvatarView(url: comment.author.avatarUrl, name: comment.author.displayName, size: 28)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(comment.author.displayName)
                        .font(.caption.bold())
                    Text(RelativeTime.format(comment.createdAt))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Text(comment.content)
                    .font(.subheadline)
            }
        }
    }

    private var commentActions: some View {
        HStack(spacing: 16) {
            if comment.depth < 1 {
                Button("Reply") { showReply.toggle() }
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.leading, 36)
    }

    @ViewBuilder
    private var replyInput: some View {
        if showReply {
            HStack(spacing: 4) {
                SpeechButton(text: $replyText)
                TextField("Write a reply...", text: $replyText)
                    .textFieldStyle(.roundedBorder)
                    .font(.caption)
                Button(action: submitReply) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.body)
                }
                .disabled(replyText.isEmpty)
            }
            .padding(.leading, 36)
        }
    }

    @ViewBuilder
    private var nestedReplies: some View {
        if let replies = comment.replies {
            ForEach(replies) { reply in
                CommentView(comment: reply, postId: postId, onRefresh: onRefresh)
                    .padding(.leading, 28)
            }
        }
    }

    private func submitReply() {
        let text = replyText
        replyText = ""
        showReply = false
        Task {
            do {
                let _: CommentDto = try await APIClient.shared.post("/comments", body: CreateCommentRequest(
                    postId: postId, parentCommentId: comment.id, content: text, attachmentIds: nil
                ))
                onRefresh()
            } catch {}
        }
    }
}
