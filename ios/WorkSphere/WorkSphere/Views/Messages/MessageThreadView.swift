import SwiftUI

struct MessageThreadView: View {
    let conversationId: Int64

    @Environment(AuthService.self) private var auth
    @State private var conversation: ConversationDto?
    @State private var messages: [MessageDto] = []
    @State private var messageText = ""
    @State private var sending = false
    @State private var showInfo = false
    @State private var showSummary = false
    @State private var summaryText = ""
    @State private var summaryLoading = false
    @State private var summaryError: String?

    var body: some View {
        VStack(spacing: 0) {
            // AI Assistant
            AiAssistantView(context: "conversation", contextId: conversationId)
                .padding(.horizontal)
                .padding(.top, 4)

            // Summary panel
            if showSummary {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Label("Summary", systemImage: "doc.text")
                            .font(.caption.bold())
                            .foregroundStyle(.purple)
                        Spacer()
                        Button { withAnimation { showSummary = false; summaryText = ""; summaryError = nil } } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                    }
                    if summaryLoading && summaryText.isEmpty {
                        HStack(spacing: 8) {
                            ProgressView().tint(.purple)
                            Text("Summarizing...").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    if !summaryText.isEmpty {
                        ScrollView {
                            MarkdownText(content: summaryText, font: .caption)
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(maxHeight: 200)
                    }
                    if let summaryError {
                        Text(summaryError).font(.caption).foregroundStyle(.red)
                    }
                }
                .padding()
                .background(.purple.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(.purple.opacity(0.15)))
                .padding(.horizontal)
            }

            // Messages
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(messages) { msg in
                            MessageBubble(
                                message: msg,
                                isSent: msg.sender.id == auth.userId,
                                isGroup: conversation?.type == "GROUP"
                            )
                            .id(msg.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: messages.count) { _, _ in
                    if let last = messages.last {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }

            // Input
            HStack(spacing: 6) {
                SpeechButton(text: $messageText)

                TextField("Type a message...", text: $messageText)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { sendMessage() }

                Button {
                    sendMessage()
                } label: {
                    if sending {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "arrow.up.circle.fill")
                    }
                }
                .font(.title2)
                .disabled(messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || sending)
            }
            .padding()
            .background(.bar)
        }
        .navigationTitle(displayName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: 12) {
                    Button { summarizeConversation() } label: {
                        Image(systemName: "doc.text.magnifyingglass")
                    }
                    Button { showInfo.toggle() } label: {
                        Image(systemName: "person.2")
                    }
                }
            }
        }
        .sheet(isPresented: $showInfo) {
            if let conversation {
                ConversationInfoView(conversation: conversation) {
                    Task { await loadConversation() }
                }
            }
        }
        .task {
            await loadConversation()
            await loadMessages()
            markRead()
        }
        .task { await pollMessages() }
    }

    private var displayName: String {
        guard let conversation else { return "Messages" }
        if let name = conversation.name, !name.isEmpty { return name }
        let others = conversation.participants.filter { $0.id != auth.userId }
        if conversation.type == "DIRECT" {
            return others.first?.displayName ?? "Chat"
        }
        return others.map(\.displayName).joined(separator: ", ")
    }

    private func loadConversation() async {
        conversation = try? await APIClient.shared.get("/conversations/\(conversationId)")
    }

    private func loadMessages() async {
        do {
            let msgs: [MessageDto] = try await APIClient.shared.get("/conversations/\(conversationId)/messages")
            messages = msgs.reversed()
        } catch {}
    }

    private func pollMessages() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(3))
            await loadMessages()
        }
    }

    private func markRead() {
        Task { try? await APIClient.shared.postVoid("/conversations/\(conversationId)/read") }
    }

    private func summarizeConversation() {
        withAnimation { showSummary = true }
        summaryText = ""
        summaryLoading = true
        summaryError = nil
        Task {
            do {
                struct SummarizeRequest: Codable { let conversationId: Int64 }
                try await APIClient.shared.streamAI(context: "conversation", contextId: conversationId, question: "Summarize this conversation") { token in
                    summaryText += token
                }
            } catch {
                summaryError = error.localizedDescription
            }
            summaryLoading = false
        }
    }

    private func sendMessage() {
        let text = messageText
        messageText = ""
        sending = true
        Task {
            do {
                let _: MessageDto = try await APIClient.shared.post("/conversations/\(conversationId)/messages", body: ["content": text])
                await loadMessages()
            } catch {}
            sending = false
        }
    }
}

struct MessageBubble: View {
    let message: MessageDto
    let isSent: Bool
    let isGroup: Bool

    var body: some View {
        HStack {
            if isSent { Spacer(minLength: 60) }

            VStack(alignment: isSent ? .trailing : .leading, spacing: 2) {
                if isGroup && !isSent {
                    Text(message.sender.displayName)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                HStack(alignment: .bottom, spacing: 6) {
                    if !isSent {
                        AvatarView(url: message.sender.avatarUrl, name: message.sender.displayName, size: 24)
                    }
                    Text(LocalizedStringKey(message.content ?? ""))
                        .font(.caption)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(isSent ? Color.blue : Color(.systemGray5))
                        .foregroundStyle(isSent ? .white : .primary)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                Text(RelativeTime.format(message.createdAt))
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            if !isSent { Spacer(minLength: 60) }
        }
    }
}

struct ConversationInfoView: View {
    let conversation: ConversationDto
    var onUpdate: () -> Void = {}

    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var addSearch = ""
    @State private var searchResults: [UserDto] = []
    @State private var shareHistory = true

    init(conversation: ConversationDto, onUpdate: @escaping () -> Void = {}) {
        self.conversation = conversation
        self.onUpdate = onUpdate
        self._name = State(initialValue: conversation.name ?? "")
    }

    var body: some View {
        NavigationStack {
            List {
                nameSection
                membersSection
                addPeopleSection
            }
            .navigationTitle("Conversation Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var nameSection: some View {
        Section("Name") {
            HStack {
                TextField("Conversation name", text: $name)
                Button("Save") { saveName() }
                    .disabled(name == (conversation.name ?? ""))
            }
        }
    }

    private var membersSection: some View {
        Section("Members (\(conversation.participants.count))") {
            ForEach(conversation.participants) { p in
                HStack {
                    AvatarView(url: p.avatarUrl, name: p.displayName, size: 32)
                    Text(p.displayName).font(.subheadline)
                    if p.id == auth.userId {
                        Text("(you)").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var addPeopleSection: some View {
        Section("Add People") {
            TextField("Search by name...", text: $addSearch)
                .onChange(of: addSearch) { _, q in searchPeople(q) }

            Toggle("See full conversation history", isOn: $shareHistory)
                .font(.caption)

            ForEach(filteredResults, id: \.id) { user in
                Button { addPerson(user) } label: {
                    HStack {
                        AvatarView(url: user.avatarUrl, name: user.displayName, size: 32)
                        Text(user.displayName).font(.subheadline)
                        Spacer()
                        Text("Add").font(.caption).foregroundStyle(.blue)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var filteredResults: [UserDto] {
        let existingIds = Set(conversation.participants.map(\.id))
        return searchResults.filter { !existingIds.contains($0.id) && $0.id != auth.userId }
    }

    private func saveName() {
        Task {
            try? await APIClient.shared.putVoid("/conversations/\(conversation.id)", body: ["name": name])
            onUpdate()
        }
    }

    private func searchPeople(_ q: String) {
        guard !q.isEmpty else { searchResults = []; return }
        Task { searchResults = (try? await APIClient.shared.get("/users/search?q=\(q)")) ?? [] }
    }

    private func addPerson(_ user: UserDto) {
        Task {
            struct AddBody: Codable { let userId: Int64; let shareHistory: Bool }
            try? await APIClient.shared.postVoid("/conversations/\(conversation.id)/participants",
                body: AddBody(userId: user.id, shareHistory: shareHistory))
            addSearch = ""
            searchResults = []
            onUpdate()
            dismiss()
        }
    }
}
