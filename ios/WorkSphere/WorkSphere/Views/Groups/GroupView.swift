import SwiftUI

struct GroupView: View {
    let groupId: Int64

    @Environment(AuthService.self) private var auth
    @State private var group: GroupDto?
    @State private var membership: MembershipDto?
    @State private var members: [MembershipDto] = []
    @State private var pendingMembers: [MembershipDto] = []
    @State private var posts: [PostDto] = []
    @State private var showEdit = false
    @State private var showCreatePost = false

    private var isMember: Bool { membership?.status == "APPROVED" }
    private var isOwnerOrAdmin: Bool { membership?.role == "OWNER" || membership?.role == "ADMIN" }

    var body: some View {
        ScrollView {
            if let group {
                VStack(spacing: 16) {
                    // Header
                    groupHeader(group)

                    // AI Assistant
                    AiAssistantView(context: "group", contextId: groupId)
                        .padding(.horizontal)

                    // Pending members
                    if isOwnerOrAdmin && !pendingMembers.isEmpty {
                        pendingSection
                    }

                    // Create post
                    if isMember {
                        Button { showCreatePost = true } label: {
                            Label("Create Post", systemImage: "square.and.pencil")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .padding(.horizontal)
                    }

                    // Posts
                    LazyVStack(spacing: 12) {
                        ForEach(posts) { post in
                            NavigationLink(value: post.id) {
                                PostCard(post: post)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal)
                }
            } else {
                ProgressView().padding(.top, 100)
            }
        }
        .navigationTitle(group?.name ?? "Group")
        .navigationBarTitleDisplayMode(.inline)
        .withGlobalNavDestinations()
        .toolbar {
            if isOwnerOrAdmin {
                ToolbarItem(placement: .primaryAction) {
                    Button("Edit") { showEdit = true }
                }
            }
        }
        .sheet(isPresented: $showCreatePost) {
            CreatePostView(targetType: "GROUP_FEED", targetId: groupId) { Task { await load() } }
        }
        .sheet(isPresented: $showEdit) {
            if let group {
                EditGroupView(group: group) { Task { await load() } }
            }
        }
        .task { await load() }
    }

    @ViewBuilder
    private func groupHeader(_ group: GroupDto) -> some View {
        VStack(spacing: 8) {
            // Cover
            if let cover = group.coverUrl, let url = URL(string: cover) {
                AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                placeholder: { Rectangle().fill(.blue.opacity(0.1)) }
                    .frame(height: 100).clipped()
            }

            AvatarView(url: group.avatarUrl, name: group.name, size: 48)

            Text(group.name).font(.headline)

            if let desc = group.description {
                MarkdownText(content: desc, font: .caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal)
            }

            HStack(spacing: 16) {
                Label("\(group.memberCount ?? 0) members", systemImage: "person.2")
                if let vis = group.visibility {
                    Label(vis.lowercased(), systemImage: vis == "PUBLIC" ? "globe" : "lock")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            // Join/Leave
            if !isOwnerOrAdmin {
                if isMember {
                    Button("Leave Group") { leaveGroup() }
                        .buttonStyle(.bordered)
                } else if membership?.status == "PENDING" {
                    Button("Pending Approval") {}
                        .buttonStyle(.bordered)
                        .disabled(true)
                } else {
                    Button("Join Group") { joinGroup() }
                        .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding()
    }

    private var pendingSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pending Members").font(.headline).padding(.horizontal)
            ForEach(pendingMembers, id: \.userId) { member in
                HStack {
                    AvatarView(url: member.userAvatarUrl, name: member.userName ?? "?", size: 32)
                    Text(member.userName ?? "Unknown").font(.subheadline)
                    Spacer()
                    Button("Approve") { approveMember(member.userId) }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                    Button("Reject") { rejectMember(member.userId) }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                }
                .padding(.horizontal)
            }
        }
    }

    private func load() async {
        do {
            group = try await APIClient.shared.get("/groups/\(groupId)")
            membership = try? await APIClient.shared.get("/groups/\(groupId)/membership")
            members = (try? await APIClient.shared.get("/groups/\(groupId)/members")) ?? []
            posts = (try? await APIClient.shared.get("/groups/\(groupId)/posts")) ?? []
            if isOwnerOrAdmin {
                pendingMembers = (try? await APIClient.shared.get("/groups/\(groupId)/pending")) ?? []
            }
        } catch {}
    }

    private func joinGroup() {
        Task {
            try? await APIClient.shared.postVoid("/groups/\(groupId)/join")
            await load()
        }
    }

    private func leaveGroup() {
        Task {
            try? await APIClient.shared.delete("/groups/\(groupId)/leave")
            await load()
        }
    }

    private func approveMember(_ userId: Int64) {
        Task {
            try? await APIClient.shared.postVoid("/groups/\(groupId)/members/\(userId)/approve")
            await load()
        }
    }

    private func rejectMember(_ userId: Int64) {
        Task {
            try? await APIClient.shared.postVoid("/groups/\(groupId)/members/\(userId)/reject")
            await load()
        }
    }
}

struct EditGroupView: View {
    let group: GroupDto
    var onSave: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var description: String

    init(group: GroupDto, onSave: @escaping () -> Void) {
        self.group = group; self.onSave = onSave
        _name = State(initialValue: group.name)
        _description = State(initialValue: group.description ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                TextField("Description", text: $description, axis: .vertical).lineLimit(4)
            }
            .navigationTitle("Edit Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            try? await APIClient.shared.putVoid("/groups/\(group.id)", body: ["name": name, "description": description])
                            onSave(); dismiss()
                        }
                    }
                }
            }
        }
    }
}
