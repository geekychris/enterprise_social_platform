import SwiftUI

struct ConversationsView: View {
    @Environment(AuthService.self) private var auth
    @State private var conversations: [ConversationDto] = []
    @State private var loading = true
    @State private var showNewConversation = false

    @State private var navigateToBotConvId: Int64?
    @State private var loadingBot = false

    private let ws = WebSocketService.shared

    var body: some View {
        List {
            // Chat with Roid button
            Button {
                startBotChat()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                        .foregroundStyle(.purple)
                    Text("Chat with Roid")
                        .font(.caption.bold())
                        .foregroundStyle(.purple)
                    Spacer()
                    if loadingBot {
                        ProgressView().controlSize(.small)
                    }
                }
                .padding(.vertical, 4)
            }
            .listRowBackground(Color.purple.opacity(0.08))

            ForEach(conversations) { conv in
                NavigationLink(value: ConversationNav(conversationId: conv.id)) {
                    ConversationRow(conversation: conv, currentUserId: auth.userId ?? 0)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Messages")
        .navigationDestination(for: ConversationNav.self) { nav in
            MessageThreadView(conversationId: nav.conversationId)
        }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Circle()
                    .fill(ws.isConnected ? .green : .orange)
                    .frame(width: 8, height: 8)
            }
            ToolbarItem(placement: .primaryAction) {
                Button { showNewConversation = true } label: {
                    Image(systemName: "square.and.pencil")
                }
            }
        }
        .sheet(isPresented: $showNewConversation) {
            NewConversationView { convId in
                showNewConversation = false
                // Navigate handled by parent
            }
        }
        .navigationDestination(isPresented: Binding(
            get: { navigateToBotConvId != nil },
            set: { if !$0 { navigateToBotConvId = nil } }
        )) {
            if let convId = navigateToBotConvId {
                MessageThreadView(conversationId: convId)
            }
        }
        .overlay {
            if conversations.isEmpty && !loading {
                ContentUnavailableView("No conversations", systemImage: "bubble.left.and.bubble.right", description: Text("Start a conversation"))
            }
        }
        .refreshable { await load() }
        .task { await load() }
        .task {
            ws.connect()
            ws.onAnyMessage = { _, _ in
                Task { await load() }
            }
        }
        .task {
            // Fallback polling
            while !Task.isCancelled {
                let interval: Duration = ws.isConnected ? .seconds(30) : .seconds(10)
                try? await Task.sleep(for: interval)
                await load()
            }
        }
    }

    private func load() async {
        do {
            conversations = try await APIClient.shared.get("/conversations")
        } catch {}
        loading = false
    }

    private func startBotChat() {
        guard !loadingBot else { return }
        loadingBot = true
        Task {
            do {
                struct BotInfo: Codable { let id: Int64; let username: String; let displayName: String }
                let bot: BotInfo = try await APIClient.shared.get("/ai/bot/info")
                let conv: ConversationDto = try await APIClient.shared.post("/conversations/direct/\(bot.id)")
                navigateToBotConvId = conv.id
            } catch {}
            loadingBot = false
        }
    }
}

struct ConversationRow: View {
    let conversation: ConversationDto
    let currentUserId: Int64

    var body: some View {
        HStack(spacing: 10) {
            avatar

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(displayName)
                        .font(.caption.bold())
                        .lineLimit(1)
                    Spacer()
                    if let msg = conversation.lastMessage {
                        Text(RelativeTime.format(msg.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                HStack {
                    Text(lastMessagePreview)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    Spacer()
                    if conversation.unreadCount > 0 {
                        Text("\(min(conversation.unreadCount, 99))")
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                            .frame(width: 18, height: 18)
                            .background(.blue)
                            .clipShape(Circle())
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }

    private var displayName: String {
        if let name = conversation.name, !name.isEmpty { return name }
        let others = conversation.participants.filter { $0.id != currentUserId }
        if conversation.type == "DIRECT" {
            return others.first?.displayName ?? "Unknown"
        }
        return others.map(\.displayName).joined(separator: ", ")
    }

    private var lastMessagePreview: String {
        guard let msg = conversation.lastMessage else { return "No messages yet" }
        if conversation.type == "GROUP" {
            return "\(msg.sender.displayName): \(msg.content ?? "")"
        }
        return msg.content ?? ""
    }

    @ViewBuilder
    private var avatar: some View {
        let others = conversation.participants.filter { $0.id != currentUserId }
        if conversation.type == "DIRECT", let other = others.first {
            AvatarView(url: other.avatarUrl, name: other.displayName, size: 44)
        } else {
            ZStack {
                if let first = others.first {
                    AvatarView(url: first.avatarUrl, name: first.displayName, size: 30)
                        .offset(x: -6, y: -6)
                }
                if others.count > 1 {
                    AvatarView(url: others[1].avatarUrl, name: others[1].displayName, size: 30)
                        .offset(x: 6, y: 6)
                }
            }
            .frame(width: 44, height: 44)
        }
    }
}

struct NewConversationView: View {
    var onCreated: (Int64) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var search = ""
    @State private var results: [UserDto] = []
    @State private var selected: [AuthorDto] = []
    @State private var groupName = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Selected users
                if !selected.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(selected) { user in
                                HStack(spacing: 4) {
                                    Text(user.displayName)
                                        .font(.caption)
                                    Button {
                                        selected.removeAll { $0.id == user.id }
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.caption)
                                    }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(.blue.opacity(0.1))
                                .clipShape(Capsule())
                            }
                        }
                        .padding()
                    }
                }

                // Search
                TextField("Search users...", text: $search)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)
                    .onChange(of: search) { _, newValue in searchUsers(newValue) }

                if selected.count > 1 {
                    TextField("Group name (optional)", text: $groupName)
                        .textFieldStyle(.roundedBorder)
                        .padding(.horizontal)
                        .padding(.top, 8)
                }

                List(results, id: \.id) { user in
                    Button {
                        let author = AuthorDto(id: user.id, username: user.username, displayName: user.displayName, avatarUrl: user.avatarUrl)
                        if selected.contains(where: { $0.id == user.id }) {
                            selected.removeAll { $0.id == user.id }
                        } else {
                            selected.append(author)
                        }
                    } label: {
                        HStack {
                            AvatarView(url: user.avatarUrl, name: user.displayName, size: 36)
                            Text(user.displayName)
                                .font(.subheadline)
                            Spacer()
                            if selected.contains(where: { $0.id == user.id }) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.blue)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("New Conversation")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(selected.count > 1 ? "Create Group" : "Start Chat") {
                        create()
                    }
                    .disabled(selected.isEmpty)
                }
            }
        }
    }

    private func searchUsers(_ query: String) {
        guard !query.isEmpty else { results = []; return }
        Task {
            results = (try? await APIClient.shared.get("/users/search?q=\(query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query)")) ?? []
        }
    }

    private func create() {
        Task {
            do {
                let conv: ConversationDto = try await APIClient.shared.post("/conversations", body: CreateConversationRequest(
                    participantIds: selected.map(\.id),
                    name: groupName.isEmpty ? nil : groupName
                ))
                onCreated(conv.id)
                dismiss()
            } catch {}
        }
    }
}
