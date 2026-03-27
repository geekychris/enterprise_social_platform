import SwiftUI

struct BrowseView: View {
    @Environment(AuthService.self) private var auth
    @State private var myGroups: [GroupDto] = []
    @State private var myPages: [PageDto] = []
    @State private var friends: [UserSummaryDto] = []
    @State private var pendingRequests: [FriendRequestDto] = []
    @State private var showCreateGroup = false
    @State private var showCreatePage = false

    var body: some View {
        List {
            // Friend Requests
            if !pendingRequests.isEmpty {
                Section {
                    ForEach(pendingRequests) { req in
                        FriendRequestRow(request: req, onAction: { Task { await load() } })
                    }
                } header: {
                    Label("Friend Requests", systemImage: "person.badge.plus")
                        .font(.caption.bold())
                }
            }

            // Friends
            Section {
                if friends.isEmpty {
                    Text("No friends yet").font(.caption).foregroundStyle(.secondary)
                } else {
                    ForEach(friends) { friend in
                        NavigationLink(value: ProfileNav(userId: friend.id)) {
                            HStack(spacing: 8) {
                                AvatarView(url: friend.avatarUrl, name: friend.displayName, size: 28)
                                Text(friend.displayName).font(.caption)
                            }
                        }
                    }
                }
            } header: {
                Label("Friends", systemImage: "person.2")
                    .font(.caption.bold())
            }

            // My Groups
            Section {
                ForEach(myGroups) { group in
                    NavigationLink(value: GroupNav(groupId: group.id)) {
                        HStack(spacing: 8) {
                            AvatarView(url: group.avatarUrl, name: group.name, size: 28)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(group.name).font(.caption.bold())
                                Text("\(group.memberCount ?? 0) members").font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Button { showCreateGroup = true } label: {
                    Label("Create Group", systemImage: "plus.circle")
                        .font(.caption)
                }
            } header: {
                Label("My Groups", systemImage: "person.3")
                    .font(.caption.bold())
            }

            // My Pages
            Section {
                ForEach(myPages) { page in
                    NavigationLink(value: PageNav(pageId: page.id)) {
                        HStack(spacing: 8) {
                            AvatarView(url: page.avatarUrl, name: page.name, size: 28)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(page.name).font(.caption.bold())
                                Text("\(page.followerCount ?? 0) followers").font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Button { showCreatePage = true } label: {
                    Label("Create Page", systemImage: "plus.circle")
                        .font(.caption)
                }
            } header: {
                Label("My Pages", systemImage: "doc.richtext")
                    .font(.caption.bold())
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Browse")
        .navigationDestination(for: GroupNav.self) { nav in
            GroupView(groupId: nav.groupId)
        }
        .navigationDestination(for: PageNav.self) { nav in
            PageView(pageId: nav.pageId)
        }
        .navigationDestination(for: ProfileNav.self) { nav in
            ProfileView(userId: nav.userId)
        }
        .sheet(isPresented: $showCreateGroup) {
            CreateGroupSheet { Task { await load() } }
        }
        .sheet(isPresented: $showCreatePage) {
            CreatePageSheet { Task { await load() } }
        }
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        async let g: [GroupDto] = APIClient.shared.get("/groups/mine")
        async let p: [PageDto] = APIClient.shared.get("/pages/mine")
        async let f: [UserSummaryDto] = APIClient.shared.get("/users/\(auth.userId ?? 0)/following")
        async let r: [FriendRequestDto] = APIClient.shared.get("/friend-requests/received")
        myGroups = (try? await g) ?? []
        myPages = (try? await p) ?? []
        friends = (try? await f) ?? []
        pendingRequests = (try? await r) ?? []
    }
}

// MARK: - Navigation types

struct GroupNav: Hashable { let groupId: Int64 }
struct PageNav: Hashable { let pageId: Int64 }
struct ProfileNav: Hashable { let userId: Int64 }
struct ConversationNav: Hashable { let conversationId: Int64 }

// MARK: - Friend Request Row

struct FriendRequestRow: View {
    let request: FriendRequestDto
    var onAction: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            AvatarView(url: request.senderAvatarUrl, name: request.senderDisplayName ?? "?", size: 28)
            VStack(alignment: .leading, spacing: 1) {
                Text(request.senderDisplayName ?? request.senderUsername ?? "Unknown")
                    .font(.caption.bold())
                Text("wants to be friends")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            Button("Accept") { respond(accept: true) }
                .buttonStyle(.borderedProminent)
                .controlSize(.mini)
            Button("Decline") { respond(accept: false) }
                .buttonStyle(.bordered)
                .controlSize(.mini)
        }
    }

    private func respond(accept: Bool) {
        Task {
            let action = accept ? "accept" : "reject"
            try? await APIClient.shared.postVoid("/friend-requests/\(request.id)/\(action)")
            onAction()
        }
    }
}

// MARK: - Create Group

struct CreateGroupSheet: View {
    var onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var description = ""
    @State private var visibility = "PUBLIC"

    var body: some View {
        NavigationStack {
            Form {
                TextField("Group Name", text: $name)
                TextField("Description", text: $description, axis: .vertical).lineLimit(3)
                Picker("Visibility", selection: $visibility) {
                    Text("Public").tag("PUBLIC")
                    Text("Restricted").tag("RESTRICTED")
                }
            }
            .navigationTitle("New Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        Task {
                            let _: GroupDto = try await APIClient.shared.post("/groups",
                                body: CreateGroupRequest(name: name, description: description.isEmpty ? nil : description, visibility: visibility))
                            onDone()
                            dismiss()
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

// MARK: - Create Page

struct CreatePageSheet: View {
    var onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var description = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Page Name", text: $name)
                TextField("Description", text: $description, axis: .vertical).lineLimit(3)
            }
            .navigationTitle("New Page")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        Task {
                            struct CreatePage: Codable { let name: String; let description: String? }
                            let _: PageDto = try await APIClient.shared.post("/pages",
                                body: CreatePage(name: name, description: description.isEmpty ? nil : description))
                            onDone()
                            dismiss()
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
