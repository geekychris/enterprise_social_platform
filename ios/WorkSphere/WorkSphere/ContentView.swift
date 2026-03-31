import SwiftUI

struct ContentView: View {
    @Environment(AuthService.self) private var auth

    var body: some View {
        if auth.isAuthenticated {
            AdaptiveMainView()
                .task {
                    // Connect WebSocket gateway as soon as user is authenticated
                    WebSocketService.shared.connect()
                }
        } else {
            LoginView()
        }
    }
}

/// Uses TabView on iPhone, NavigationSplitView on iPad
struct AdaptiveMainView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        if sizeClass == .compact {
            PhoneTabView()
        } else {
            iPadSplitView()
        }
    }
}

// MARK: - iPhone (Tab-based)

struct PhoneTabView: View {
    @Environment(AuthService.self) private var auth
    @State private var unreadMessages = 0
    @State private var unreadNotifications = 0

    var body: some View {
        TabView {
            NavigationStack {
                FeedView()
                    .withGlobalNavDestinations()
            }
            .tabItem { Label("Feed", systemImage: "house.fill") }

            NavigationStack {
                SearchView()
            }
            .tabItem { Label("Search", systemImage: "magnifyingglass") }

            NavigationStack {
                BrowseView()
            }
            .tabItem { Label("Browse", systemImage: "square.grid.2x2") }

            NavigationStack {
                OrgView()
            }
            .tabItem { Label("Org", systemImage: "building.2") }

            NavigationStack {
                ConversationsView()
            }
            .tabItem { Label("Messages", systemImage: "bubble.left.fill") }
            .badge(unreadMessages)

            NavigationStack {
                MoreView()
            }
            .tabItem { Label("More", systemImage: "ellipsis") }
            .badge(unreadNotifications)
        }
        .task { await pollUnread() }
    }

    private func pollUnread() async {
        while !Task.isCancelled {
            do {
                struct C: Codable { let unreadCount: Int?; let count: Int? }
                let m: C = try await APIClient.shared.get("/messages/unread-count")
                let n: C = try await APIClient.shared.get("/notifications/unread-count")
                await MainActor.run {
                    unreadMessages = m.unreadCount ?? m.count ?? 0
                    unreadNotifications = n.unreadCount ?? n.count ?? 0
                }
            } catch {}
            try? await Task.sleep(for: .seconds(15))
        }
    }
}

// MARK: - iPad (Split-based)

enum iPadSection: String, CaseIterable, Identifiable {
    case feed = "Feed"
    case search = "Search"
    case messages = "Messages"
    case org = "Org"
    case groups = "Groups"
    case pages = "Pages"
    case friends = "Friends"
    case notifications = "Notifications"
    case profile = "Profile"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .feed: return "house.fill"
        case .search: return "magnifyingglass"
        case .messages: return "bubble.left.fill"
        case .org: return "building.2"
        case .groups: return "person.3.fill"
        case .pages: return "doc.richtext.fill"
        case .friends: return "person.2.fill"
        case .notifications: return "bell.fill"
        case .profile: return "person.circle"
        }
    }
}

struct iPadSplitView: View {
    @Environment(AuthService.self) private var auth
    @State private var selection: iPadSection? = .feed
    @State private var unreadMessages = 0
    @State private var unreadNotifications = 0

    var body: some View {
        NavigationSplitView {
            List(selection: $selection) {
                ForEach(iPadSection.allCases) { section in
                    NavigationLink(value: section) {
                        HStack {
                            Label(section.rawValue, systemImage: section.icon)
                            Spacer()
                            if section == .messages && unreadMessages > 0 {
                                badgeView(unreadMessages)
                            }
                            if section == .notifications && unreadNotifications > 0 {
                                badgeView(unreadNotifications)
                            }
                        }
                    }
                }

                Section {
                    Button(role: .destructive) { auth.logout() } label: {
                        Label("Log Out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("WorkSphere")
            .listStyle(.sidebar)
        } detail: {
            detailView
        }
        .task { await pollUnread() }
    }

    @ViewBuilder
    private var detailView: some View {
        switch selection {
        case .feed:
            NavigationStack { FeedView().withGlobalNavDestinations() }
        case .search:
            NavigationStack { SearchView().withGlobalNavDestinations() }
        case .messages:
            NavigationStack { ConversationsView().withGlobalNavDestinations() }
        case .org:
            NavigationStack { OrgView().withGlobalNavDestinations() }
        case .groups:
            NavigationStack { iPadGroupsView().withGlobalNavDestinations() }
        case .pages:
            NavigationStack { iPadPagesView().withGlobalNavDestinations() }
        case .friends:
            NavigationStack { iPadFriendsView().withGlobalNavDestinations() }
        case .notifications:
            NavigationStack { NotificationsView().withGlobalNavDestinations() }
        case .profile:
            NavigationStack { ProfileView(userId: auth.userId ?? 0).withGlobalNavDestinations() }
        case nil:
            ContentUnavailableView("Select a section", systemImage: "sidebar.left")
        }
    }

    private func badgeView(_ count: Int) -> some View {
        Text("\(min(count, 99))")
            .font(.caption2.bold())
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(.red)
            .clipShape(Capsule())
    }

    private func pollUnread() async {
        while !Task.isCancelled {
            do {
                struct C: Codable { let unreadCount: Int?; let count: Int? }
                let m: C = try await APIClient.shared.get("/messages/unread-count")
                let n: C = try await APIClient.shared.get("/notifications/unread-count")
                await MainActor.run {
                    unreadMessages = m.unreadCount ?? m.count ?? 0
                    unreadNotifications = n.unreadCount ?? n.count ?? 0
                }
            } catch {}
            try? await Task.sleep(for: .seconds(15))
        }
    }
}

// MARK: - iPad detail views for Groups, Pages, Friends

struct iPadGroupsView: View {
    @State private var groups: [GroupDto] = []
    @State private var showCreate = false

    var body: some View {
        List(groups) { group in
            NavigationLink(value: GroupNav(groupId: group.id)) {
                HStack(spacing: 10) {
                    AvatarView(url: group.avatarUrl, name: group.name, size: 36)
                    VStack(alignment: .leading) {
                        Text(group.name).font(.subheadline.bold())
                        Text("\(group.memberCount ?? 0) members").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("My Groups")
        .toolbar {
            Button { showCreate = true } label: { Image(systemName: "plus") }
        }
        .sheet(isPresented: $showCreate) {
            CreateGroupSheet { Task { await load() } }
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        groups = (try? await APIClient.shared.get("/groups/mine")) ?? []
    }
}

struct iPadPagesView: View {
    @State private var pages: [PageDto] = []
    @State private var showCreate = false

    var body: some View {
        List(pages) { page in
            NavigationLink(value: PageNav(pageId: page.id)) {
                HStack(spacing: 10) {
                    AvatarView(url: page.avatarUrl, name: page.name, size: 36)
                    VStack(alignment: .leading) {
                        Text(page.name).font(.subheadline.bold())
                        Text("\(page.followerCount ?? 0) followers").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("My Pages")
        .toolbar {
            Button { showCreate = true } label: { Image(systemName: "plus") }
        }
        .sheet(isPresented: $showCreate) {
            CreatePageSheet { Task { await load() } }
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        pages = (try? await APIClient.shared.get("/pages/mine")) ?? []
    }
}

struct iPadFriendsView: View {
    @Environment(AuthService.self) private var auth
    @State private var friends: [UserSummaryDto] = []
    @State private var pendingRequests: [FriendRequestDto] = []

    var body: some View {
        List {
            if !pendingRequests.isEmpty {
                Section("Pending Requests") {
                    ForEach(pendingRequests) { req in
                        FriendRequestRow(request: req, onAction: { Task { await load() } })
                    }
                }
            }
            Section("Friends") {
                ForEach(friends) { friend in
                    NavigationLink(value: ProfileNav(userId: friend.id)) {
                        HStack(spacing: 10) {
                            AvatarView(url: friend.avatarUrl, name: friend.displayName, size: 32)
                            Text(friend.displayName).font(.subheadline)
                        }
                    }
                }
                if friends.isEmpty {
                    Text("No friends yet").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Friends")
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        friends = (try? await APIClient.shared.get("/users/\(auth.userId ?? 0)/following")) ?? []
        pendingRequests = (try? await APIClient.shared.get("/friend-requests/received")) ?? []
    }
}

// MARK: - Shared nav destinations modifier

struct GlobalNavDestinations: ViewModifier {
    func body(content: Content) -> some View {
        content
            .navigationDestination(for: ProfileNavigation.self) { nav in
                ProfileView(userId: nav.userId)
            }
            .navigationDestination(for: ProfileNav.self) { nav in
                ProfileView(userId: nav.userId)
            }
            .navigationDestination(for: GroupNav.self) { nav in
                GroupView(groupId: nav.groupId)
            }
            .navigationDestination(for: PageNav.self) { nav in
                PageView(pageId: nav.pageId)
            }
            .navigationDestination(for: Int64.self) { postId in
                PostDetailView(postId: postId)
            }
            .navigationDestination(for: ConversationNav.self) { nav in
                MessageThreadView(conversationId: nav.conversationId)
            }
            .navigationDestination(for: MessageUserNavigation.self) { nav in
                MessageUserRedirectView(userId: nav.userId)
            }
    }
}

extension View {
    func withGlobalNavDestinations() -> some View {
        modifier(GlobalNavDestinations())
    }
}

// MARK: - More tab (iPhone only)

struct MoreView: View {
    @Environment(AuthService.self) private var auth

    var body: some View {
        List {
            Section {
                NavigationLink(value: ProfileNav(userId: auth.userId ?? 0)) {
                    Label("My Profile", systemImage: "person.circle")
                }
                NavigationLink(value: NotificationsNav()) {
                    Label("Notifications", systemImage: "bell")
                }
            }
            Section {
                Button(role: .destructive) { auth.logout() } label: {
                    Label("Log Out", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }
        }
        .navigationTitle("More")
        .navigationDestination(for: ProfileNav.self) { nav in
            ProfileView(userId: nav.userId)
        }
        .navigationDestination(for: NotificationsNav.self) { _ in
            NotificationsView()
        }
    }
}

struct NotificationsNav: Hashable {}
