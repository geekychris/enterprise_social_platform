import SwiftUI

struct FeedView: View {
    @State private var posts: [PostDto] = []
    @State private var cursor: String?
    @State private var hasMore = true
    @State private var loading = false
    @State private var showCreatePost = false

    var body: some View {
        List {
            // AI Assistant
            AiAssistantView(context: "feed")
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))

            ForEach(posts) { post in
                NavigationLink(value: post.id) {
                    PostCard(post: post)
                }
                .buttonStyle(.plain)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                .onAppear {
                    if post.id == posts.last?.id { loadMore() }
                }
            }

            if loading {
                HStack { Spacer(); ProgressView(); Spacer() }
                    .listRowSeparator(.hidden)
            }

            if !hasMore && !posts.isEmpty {
                Text("You've reached the end")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .navigationTitle("Feed")
        .withGlobalNavDestinations()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showCreatePost = true } label: {
                    Image(systemName: "square.and.pencil")
                }
            }
        }
        .sheet(isPresented: $showCreatePost) {
            CreatePostView { loadFresh() }
        }
        .refreshable { loadFresh() }
        .task { if posts.isEmpty { loadFresh() } }
    }

    private func loadFresh() {
        cursor = nil
        hasMore = true
        posts = []
        loadMore()
    }

    private func loadMore() {
        guard hasMore, !loading else { return }
        loading = true
        Task {
            do {
                var path = "/feed?limit=20"
                if let cursor { path += "&cursor=\(cursor)" }
                let response: FeedResponse = try await APIClient.shared.get(path)
                await MainActor.run {
                    posts.append(contentsOf: response.posts)
                    cursor = response.nextCursor
                    hasMore = response.hasMore
                    loading = false
                }
            } catch {
                loading = false
            }
        }
    }
}

struct CreatePostView: View {
    var targetType: String?
    var targetId: Int64?
    var onDone: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @State private var content = ""
    @State private var visibility = "PUBLIC"
    @State private var loading = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Post") {
                    HStack(alignment: .top) {
                        TextEditor(text: $content)
                            .frame(minHeight: 120)
                        SpeechButton(text: $content)
                            .padding(.top, 4)
                    }
                }
                Section("Visibility") {
                    Picker("Visibility", selection: $visibility) {
                        Text("Public").tag("PUBLIC")
                        Text("Team").tag("TEAM_VISIBLE")
                        Text("Restricted").tag("RESTRICTED")
                        Text("Private").tag("PRIVATE")
                    }
                    .pickerStyle(.segmented)
                }
            }
            .navigationTitle("New Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Post") { submit() }
                        .disabled(content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || loading)
                }
            }
        }
    }

    private func submit() {
        loading = true
        Task {
            do {
                let _: PostDto = try await APIClient.shared.post("/posts", body: CreatePostRequest(
                    content: content, visibility: visibility,
                    targetType: targetType, targetId: targetId, attachmentIds: nil
                ))
                onDone()
                dismiss()
            } catch {
                loading = false
            }
        }
    }
}
