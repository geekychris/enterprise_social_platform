import SwiftUI

struct ProfileView: View {
    let userId: Int64
    @Environment(AuthService.self) private var auth
    @State private var user: UserDto?
    @State private var posts: [PostDto] = []
    @State private var isFollowing = false
    @State private var friendStatusText = "NONE"
    @State private var friendRequestId: Int64?
    @State private var showEdit = false
    @State private var loading = true
    @State private var orgAssignments: [OrgAssignmentDto] = []
    @State private var orgChain: [OrgChainDto] = []
    @State private var orgReports: [OrgReportDto] = []
    @State private var orgExpanded = false
    @State private var orgLoaded = false

    private var isOwnProfile: Bool { userId == auth.userId }

    var body: some View {
        ScrollView {
            if let user {
                VStack(spacing: 0) {
                    // Cover
                    if let cover = user.coverUrl, let url = URL(string: cover) {
                        AsyncImage(url: url) { img in
                            img.resizable().scaledToFill()
                        } placeholder: {
                            Rectangle().fill(.blue.opacity(0.1))
                        }
                        .frame(height: 120)
                        .clipped()
                    } else {
                        Rectangle().fill(.blue.gradient).frame(height: 120)
                    }

                    VStack(spacing: 8) {
                        AvatarView(url: user.avatarUrl, name: user.displayName, size: 60)
                            .overlay(Circle().stroke(.white, lineWidth: 2))
                            .offset(y: -30)
                            .padding(.bottom, -30)

                        Text(user.displayName).font(.headline)
                        Text("@\(user.username)")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        if let pronouns = user.pronouns, !pronouns.isEmpty {
                            Text(pronouns).font(.caption2).foregroundStyle(.secondary)
                        }

                        if let job = user.jobTitle {
                            HStack(spacing: 4) {
                                Text(job)
                                if let dept = user.department {
                                    Text("·").foregroundStyle(.secondary)
                                    Text(dept)
                                }
                            }
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        }

                        if let bio = user.bio, !bio.isEmpty {
                            MarkdownText(content: bio, font: .caption)
                                .padding(.horizontal)
                        }

                        // Stats
                        HStack(spacing: 20) {
                            VStack {
                                Text("\(user.followerCount ?? 0)").font(.subheadline.bold())
                                Text("Followers").font(.caption2).foregroundStyle(.secondary)
                            }
                            VStack {
                                Text("\(user.followingCount ?? 0)").font(.subheadline.bold())
                                Text("Following").font(.caption2).foregroundStyle(.secondary)
                            }
                        }

                        // Actions
                        if isOwnProfile {
                            Button("Edit Profile") { showEdit = true }
                                .buttonStyle(.bordered)
                        } else {
                            HStack(spacing: 8) {
                                Button(isFollowing ? "Unfollow" : "Follow") {
                                    toggleFollow()
                                }
                                .buttonStyle(.borderedProminent)
                                .controlSize(.small)

                                friendButton
                                    .controlSize(.small)

                                NavigationLink(value: MessageUserNavigation(userId: userId)) {
                                    Label("Message", systemImage: "bubble.left")
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                            }
                        }

                        // Details
                        profileDetails(user)

                        // Organization
                        if !orgAssignments.isEmpty {
                            orgSection
                        }
                    }
                    .padding()

                    Divider()

                    // Posts
                    LazyVStack(spacing: 12) {
                        ForEach(posts) { post in
                            NavigationLink(value: post.id) {
                                PostCard(post: post)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding()
                }
            } else if loading {
                ProgressView().padding(.top, 100)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .withGlobalNavDestinations()
        .navigationDestination(for: MessageUserNavigation.self) { nav in
            MessageUserRedirectView(userId: nav.userId)
        }
        .sheet(isPresented: $showEdit) {
            if let user {
                EditProfileView(user: user) { Task { await load() } }
            }
        }
        .task { await load() }
    }

    @ViewBuilder
    private func profileDetails(_ user: UserDto) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let loc = user.location { detailRow("mappin", loc) }
            if let phone = user.phone { detailRow("phone", phone) }
            if let interests = user.interests { detailRow("star", interests) }
            if let skills = user.skills { detailRow("wrench.and.screwdriver", skills) }
            if let tz = user.timezone { detailRow("clock", tz) }
        }
        .padding(.top, 8)
    }

    @ViewBuilder
    private var orgSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            DisclosureGroup(isExpanded: $orgExpanded) {
                if !orgLoaded {
                    ProgressView()
                        .padding(.vertical, 8)
                        .task { await loadOrgDetails() }
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        // Reporting chain (reversed so CEO at top)
                        ForEach(Array(orgChain.reversed().enumerated()), id: \.element.id) { index, person in
                            NavigationLink(value: ProfileNavigation(userId: person.userId)) {
                                HStack(spacing: 6) {
                                    AvatarView(url: person.userAvatarUrl, name: person.userName ?? "?", size: 24)
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(person.userName ?? "Unknown")
                                            .font(.caption)
                                            .fontWeight(person.userId == userId ? .bold : .regular)
                                        if let role = person.roleName {
                                            Text(role).font(.caption2).foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                .padding(.leading, CGFloat(index) * 12)
                            }
                            .buttonStyle(.plain)
                        }

                        // Current user marker if not in chain
                        let chainUserIds = Set(orgChain.map(\.userId))
                        if !chainUserIds.contains(userId), let assignment = orgAssignments.first {
                            HStack(spacing: 6) {
                                AvatarView(url: assignment.userAvatarUrl, name: assignment.userName ?? "?", size: 24)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(assignment.userName ?? "Unknown")
                                        .font(.caption.bold())
                                    if let role = assignment.roleName {
                                        Text(role).font(.caption2).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .padding(.leading, CGFloat(orgChain.count) * 12)
                        }

                        // Direct reports
                        if !orgReports.isEmpty {
                            let indent = CGFloat(orgChain.count + 1) * 12
                            ForEach(orgReports) { report in
                                NavigationLink(value: ProfileNavigation(userId: report.userId)) {
                                    HStack(spacing: 6) {
                                        AvatarView(url: report.userAvatarUrl, name: report.userName ?? "?", size: 22)
                                        VStack(alignment: .leading, spacing: 0) {
                                            Text(report.userName ?? "Unknown").font(.caption)
                                            if let role = report.roleName {
                                                Text(role).font(.caption2).foregroundStyle(.secondary)
                                            }
                                        }
                                    }
                                    .padding(.leading, indent)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.top, 4)
                }
            } label: {
                Label("Organization", systemImage: "building.2")
                    .font(.subheadline.bold())
            }
        }
        .padding(.top, 12)
    }

    private func loadOrgDetails() async {
        orgChain = (try? await APIClient.shared.get("/org/assignments/chain/\(userId)")) ?? []
        orgReports = (try? await APIClient.shared.get("/org/assignments/reports/\(userId)")) ?? []
        orgLoaded = true
    }

    private func detailRow(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon).font(.caption).foregroundStyle(.secondary).frame(width: 20)
            Text(text).font(.caption)
        }
    }

    @ViewBuilder
    private var friendButton: some View {
        switch friendStatusText {
        case "FRIENDS":
            Label("Friends", systemImage: "checkmark.circle.fill")
                .font(.caption)
                .foregroundStyle(.green)
        case "PENDING_SENT":
            Button("Requested") {}
                .buttonStyle(.bordered)
                .disabled(true)
        case "PENDING_RECEIVED":
            Button("Accept Request") { acceptFriendRequest() }
                .buttonStyle(.borderedProminent)
                .tint(.green)
        default:
            Button("Add Friend") { sendFriendRequest() }
                .buttonStyle(.bordered)
        }
    }

    private func sendFriendRequest() {
        Task {
            try? await APIClient.shared.postVoid("/friend-requests/\(userId)")
            await loadFriendStatus()
        }
    }

    private func acceptFriendRequest() {
        guard let reqId = friendRequestId else { return }
        Task {
            try? await APIClient.shared.postVoid("/friend-requests/\(reqId)/accept")
            await loadFriendStatus()
        }
    }

    private func loadFriendStatus() async {
        struct FriendStatus: Codable { let status: String; let requestId: Int64? }
        if let status: FriendStatus = try? await APIClient.shared.get("/friend-requests/status/\(userId)") {
            friendStatusText = status.status
            friendRequestId = status.requestId
        }
    }

    private func load() async {
        do {
            user = try await APIClient.shared.get("/users/\(userId)")
            let feedResp: FeedResponse = try await APIClient.shared.get("/feed?limit=20")
            posts = feedResp.posts.filter { $0.author.id == userId }
            orgAssignments = (try? await APIClient.shared.get("/org/assignments/user/\(userId)")) ?? []
            if !isOwnProfile {
                let following: [UserSummaryDto] = try await APIClient.shared.get("/users/\(auth.userId ?? 0)/following")
                isFollowing = following.contains { $0.id == userId }
                await loadFriendStatus()
            }
        } catch {}
        loading = false
    }

    private func toggleFollow() {
        Task {
            if isFollowing {
                try? await APIClient.shared.delete("/follow/\(userId)")
            } else {
                try? await APIClient.shared.postVoid("/follow/\(userId)")
            }
            isFollowing.toggle()
        }
    }
}

struct MessageUserNavigation: Hashable {
    let userId: Int64
}

struct MessageUserRedirectView: View {
    let userId: Int64
    @State private var conversationId: Int64?

    var body: some View {
        Group {
            if let convId = conversationId {
                MessageThreadView(conversationId: convId)
            } else {
                ProgressView()
                    .task {
                        let conv: ConversationDto? = try? await APIClient.shared.post("/conversations/direct/\(userId)")
                        conversationId = conv?.id
                    }
            }
        }
    }
}

struct EditProfileView: View {
    let user: UserDto
    var onSave: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var displayName: String
    @State private var bio: String
    @State private var location: String
    @State private var phone: String
    @State private var jobTitle: String
    @State private var department: String
    @State private var pronouns: String
    @State private var interests: String
    @State private var skills: String
    @State private var avatarUrl: String?
    @State private var coverUrl: String?
    @State private var uploading = false
    @State private var saving = false

    init(user: UserDto, onSave: @escaping () -> Void) {
        self.user = user; self.onSave = onSave
        _displayName = State(initialValue: user.displayName)
        _bio = State(initialValue: user.bio ?? "")
        _location = State(initialValue: user.location ?? "")
        _phone = State(initialValue: user.phone ?? "")
        _jobTitle = State(initialValue: user.jobTitle ?? "")
        _department = State(initialValue: user.department ?? "")
        _pronouns = State(initialValue: user.pronouns ?? "")
        _interests = State(initialValue: user.interests ?? "")
        _skills = State(initialValue: user.skills ?? "")
        _avatarUrl = State(initialValue: user.avatarUrl)
        _coverUrl = State(initialValue: user.coverUrl)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Photos") {
                    ImagePickerButton(label: "Profile Photo", currentURL: avatarUrl, size: 56) { data, name, mime in
                        uploadImage(data: data, filename: name, mimeType: mime) { url in avatarUrl = url }
                    }
                    ImagePickerButton(label: "Cover Image", currentURL: coverUrl, size: 56) { data, name, mime in
                        uploadImage(data: data, filename: name, mimeType: mime) { url in coverUrl = url }
                    }
                    if uploading {
                        HStack { ProgressView(); Text("Uploading...").font(.caption).foregroundStyle(.secondary) }
                    }
                }
                Section("Basic Info") {
                    TextField("Display Name", text: $displayName)
                    TextField("Bio", text: $bio, axis: .vertical)
                    TextField("Pronouns", text: $pronouns)
                }
                Section("Work") {
                    TextField("Job Title", text: $jobTitle)
                    TextField("Department", text: $department)
                }
                Section("Contact") {
                    TextField("Location", text: $location)
                    TextField("Phone", text: $phone)
                }
                Section("About") {
                    TextField("Interests", text: $interests)
                    TextField("Skills", text: $skills)
                }
            }
            .navigationTitle("Edit Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(saving || uploading)
                }
            }
        }
    }

    private func uploadImage(data: Data, filename: String, mimeType: String, completion: @escaping (String) -> Void) {
        uploading = true
        Task {
            do {
                let result = try await APIClient.shared.upload(data: data, filename: filename, mimeType: mimeType)
                await MainActor.run { completion(result.fileUrl) }
            } catch {}
            await MainActor.run { uploading = false }
        }
    }

    private func save() {
        saving = true
        Task {
            struct ProfileUpdate: Codable {
                let displayName: String?; let bio: String?; let location: String?
                let phone: String?; let jobTitle: String?; let department: String?
                let pronouns: String?; let interests: String?; let skills: String?
                let avatarUrl: String?; let coverUrl: String?
            }
            try? await APIClient.shared.putVoid("/users/\(user.id)", body: ProfileUpdate(
                displayName: displayName,
                bio: bio.isEmpty ? nil : bio,
                location: location.isEmpty ? nil : location,
                phone: phone.isEmpty ? nil : phone,
                jobTitle: jobTitle.isEmpty ? nil : jobTitle,
                department: department.isEmpty ? nil : department,
                pronouns: pronouns.isEmpty ? nil : pronouns,
                interests: interests.isEmpty ? nil : interests,
                skills: skills.isEmpty ? nil : skills,
                avatarUrl: avatarUrl,
                coverUrl: coverUrl
            ))
            onSave()
            dismiss()
        }
    }
}
