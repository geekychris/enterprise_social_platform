import SwiftUI

struct PageView: View {
    let pageId: Int64

    @Environment(AuthService.self) private var auth
    @State private var page: PageDto?
    @State private var posts: [PostDto] = []
    @State private var isFollowing = false
    @State private var showEdit = false
    @State private var showCreatePost = false

    private var isOwner: Bool { page?.ownerId == auth.userId }

    var body: some View {
        ScrollView {
            if let page {
                VStack(spacing: 16) {
                    // Cover
                    if let cover = page.coverUrl, let url = URL(string: cover) {
                        AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                        placeholder: { Rectangle().fill(.blue.opacity(0.1)) }
                            .frame(height: 140).clipped()
                    }

                    AvatarView(url: page.avatarUrl, name: page.name, size: 64)
                    Text(page.name).font(.title2.bold())

                    if let desc = page.description {
                        MarkdownText(content: desc, font: .caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center).padding(.horizontal)
                    }

                    HStack(spacing: 16) {
                        Label("\(page.followerCount ?? 0) followers", systemImage: "person.2")
                    }
                    .font(.caption).foregroundStyle(.secondary)

                    if isOwner {
                        Button("Edit Page") { showEdit = true }.buttonStyle(.bordered)
                    } else {
                        Button(isFollowing ? "Unfollow" : "Follow") { toggleFollow() }
                            .buttonStyle(.borderedProminent)
                    }

                    // AI
                    AiAssistantView(context: "page", contextId: pageId)
                        .padding(.horizontal)

                    if isOwner {
                        Button { showCreatePost = true } label: {
                            Label("Create Post", systemImage: "square.and.pencil").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered).padding(.horizontal)
                    }

                    LazyVStack(spacing: 12) {
                        ForEach(posts) { post in
                            NavigationLink(value: post.id) { PostCard(post: post) }
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal)
                }
            } else {
                ProgressView().padding(.top, 100)
            }
        }
        .navigationTitle(page?.name ?? "Page")
        .navigationBarTitleDisplayMode(.inline)
        .withGlobalNavDestinations()
        .sheet(isPresented: $showCreatePost) {
            CreatePostView(targetType: "PAGE_FEED", targetId: pageId) { Task { await load() } }
        }
        .sheet(isPresented: $showEdit) {
            if let page {
                EditPageView(page: page) { Task { await load() } }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            page = try await APIClient.shared.get("/pages/\(pageId)")
            posts = (try? await APIClient.shared.get("/pages/\(pageId)/posts")) ?? []
            if !isOwner {
                let following: Bool = (try? await APIClient.shared.get("/pages/\(pageId)/following")) ?? false
                isFollowing = following
            }
        } catch {}
    }

    private func toggleFollow() {
        Task {
            if isFollowing {
                try? await APIClient.shared.delete("/pages/\(pageId)/unfollow")
            } else {
                try? await APIClient.shared.postVoid("/pages/\(pageId)/follow")
            }
            isFollowing.toggle()
        }
    }
}

struct EditPageView: View {
    let page: PageDto
    var onSave: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var description: String

    init(page: PageDto, onSave: @escaping () -> Void) {
        self.page = page; self.onSave = onSave
        _name = State(initialValue: page.name)
        _description = State(initialValue: page.description ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                TextField("Description", text: $description, axis: .vertical).lineLimit(4)
            }
            .navigationTitle("Edit Page").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            try? await APIClient.shared.putVoid("/pages/\(page.id)", body: ["name": name, "description": description])
                            onSave(); dismiss()
                        }
                    }
                }
            }
        }
    }
}
