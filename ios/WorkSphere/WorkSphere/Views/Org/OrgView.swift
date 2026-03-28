import SwiftUI

struct OrgUnitLocal: Codable, Identifiable {
    let id: Int64
    let name: String
    let type: String
    let parentId: Int64?
    let headUserId: Int64?
    let headUserName: String?
    let description: String?
    let childCount: Int
    let memberCount: Int
}

struct OrgMemberLocal: Codable, Identifiable {
    let id: Int64
    let userId: Int64
    let userName: String
    let userAvatarUrl: String?
    let title: String?
    let relationshipType: String?
    let level: String?
}

struct OrgChainLocal: Codable, Identifiable {
    let id: Int64
    let userId: Int64
    let userName: String
    let userAvatarUrl: String?
    let title: String?
    let level: String?
}

struct OrgView: View {
    @State private var searchQuery = ""
    @State private var selectedUserId: Int64?

    var body: some View {
        VStack(spacing: 0) {
            // Search
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Search for a person...", text: $searchQuery)
                    .textFieldStyle(.plain)
                    .font(.subheadline)
                if !searchQuery.isEmpty {
                    Button { searchQuery = ""; selectedUserId = nil } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                    }
                }
            }
            .padding(10)
            .background(.bar)

            if searchQuery.count > 1 {
                PersonSearchList(query: searchQuery) { id in
                    selectedUserId = id
                    searchQuery = ""
                }
            } else if let userId = selectedUserId {
                PersonOrgDetail(userId: userId, onNavigate: { selectedUserId = $0 })
            } else {
                OrgTreeList(onSelectUser: { selectedUserId = $0 })
            }
        }
        .navigationTitle("Organization")
        .withGlobalNavDestinations()
    }
}

// MARK: - Person Search

struct PersonSearchList: View {
    let query: String
    var onSelect: (Int64) -> Void

    @State private var results: [UserDto] = []

    var body: some View {
        List(results, id: \.id) { user in
            Button { onSelect(user.id) } label: {
                HStack(spacing: 8) {
                    AvatarView(url: user.avatarUrl, name: user.displayName, size: 32)
                    VStack(alignment: .leading) {
                        Text(user.displayName).font(.caption.bold())
                        Text(user.jobTitle ?? user.username).font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .listStyle(.plain)
        .task(id: query) {
            results = (try? await APIClient.shared.get("/users/search?q=\(query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query)")) ?? []
        }
    }
}

// MARK: - Person Org Detail (chain up + reports down)

struct PersonOrgDetail: View {
    let userId: Int64
    var onNavigate: (Int64) -> Void

    @State private var chain: [OrgChainLocal] = []
    @State private var reports: [OrgMemberLocal] = []
    @State private var assignments: [OrgMemberLocal] = []
    @State private var loading = true

    var body: some View {
        List {
            if loading {
                ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden)
            } else {
                // Chain (reversed: CEO at top)
                if !chain.isEmpty {
                    Section("Reports To") {
                        ForEach(Array(chain.reversed().enumerated()), id: \.element.id) { idx, person in
                            NavigationLink(value: ProfileNavigation(userId: person.userId)) {
                                HStack(spacing: 6) {
                                    AvatarView(url: person.userAvatarUrl, name: person.userName, size: 24)
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(person.userName).font(.caption.bold())
                                        if let t = person.title { Text(t).font(.caption2).foregroundStyle(.secondary) }
                                    }
                                }
                                .padding(.leading, CGFloat(idx) * 12)
                            }
                        }
                    }
                }

                // Current person
                if let a = assignments.first {
                    Section {
                        HStack(spacing: 8) {
                            AvatarView(url: a.userAvatarUrl, name: a.userName, size: 36)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.userName).font(.subheadline.bold())
                                if let t = a.title { Text(t).font(.caption).foregroundStyle(.secondary) }
                                if let l = a.level {
                                    Text(l).font(.caption2).padding(.horizontal, 6).padding(.vertical, 2)
                                        .background(.blue.opacity(0.1)).clipShape(Capsule())
                                }
                            }
                        }
                    }
                    .listRowBackground(Color.blue.opacity(0.05))
                }

                // Direct reports
                if !reports.isEmpty {
                    Section("Direct Reports (\(reports.count))") {
                        ForEach(reports) { r in
                            Button { onNavigate(r.userId) } label: {
                                HStack(spacing: 6) {
                                    AvatarView(url: r.userAvatarUrl, name: r.userName, size: 24)
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(r.userName).font(.caption.bold())
                                        if let t = r.title { Text(t).font(.caption2).foregroundStyle(.secondary) }
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                if chain.isEmpty && reports.isEmpty && assignments.isEmpty {
                    Text("No org data for this person").font(.caption).foregroundStyle(.secondary)
                        .listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.insetGrouped)
        .task {
            async let c: [OrgChainLocal] = APIClient.shared.get("/org/assignments/chain/\(userId)")
            async let r: [OrgMemberLocal] = APIClient.shared.get("/org/assignments/reports/\(userId)")
            async let a: [OrgMemberLocal] = APIClient.shared.get("/org/assignments/user/\(userId)")
            chain = (try? await c) ?? []
            reports = (try? await r) ?? []
            assignments = (try? await a) ?? []
            loading = false
        }
    }
}

// MARK: - Org Tree

struct OrgTreeList: View {
    var onSelectUser: (Int64) -> Void

    @State private var roots: [OrgUnitLocal] = []
    @State private var loading = true

    var body: some View {
        List {
            if loading {
                ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden)
            } else if roots.isEmpty {
                ContentUnavailableView("No organization data", systemImage: "building.2")
                    .listRowSeparator(.hidden)
            } else {
                ForEach(roots) { unit in
                    OrgUnitRow(unit: unit, depth: 0, onSelectUser: onSelectUser)
                }
            }
        }
        .listStyle(.plain)
        .task {
            roots = (try? await APIClient.shared.get("/org/units")) ?? []
            loading = false
        }
    }
}

struct OrgUnitRow: View {
    let unit: OrgUnitLocal
    let depth: Int
    var onSelectUser: (Int64) -> Void

    @State private var expanded = false
    @State private var children: [OrgUnitLocal]?
    @State private var members: [OrgMemberLocal]?

    let typeColors: [String: Color] = [
        "COMPANY": .purple, "DIVISION": .blue, "DEPARTMENT": .green, "TEAM": .orange
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
                if expanded && children == nil {
                    Task {
                        if unit.childCount > 0 {
                            children = (try? await APIClient.shared.get("/org/units/\(unit.id)/children")) ?? []
                        } else {
                            children = []
                        }
                        members = (try? await APIClient.shared.get("/org/units/\(unit.id)/members")) ?? []
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: (unit.childCount > 0 || unit.memberCount > 0) ? (expanded ? "chevron.down" : "chevron.right") : "circle.fill")
                        .font(.system(size: 8))
                        .foregroundStyle(.secondary)
                        .frame(width: 12)
                    Text(unit.type.prefix(4))
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(typeColors[unit.type] ?? .gray)
                    Text(unit.name).font(.caption.bold())
                    Spacer()
                    if unit.memberCount > 0 {
                        Text("\(unit.memberCount)").font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .padding(.leading, CGFloat(depth) * 14)
            }
            .buttonStyle(.plain)

            if expanded {
                // Members
                if let members, !members.isEmpty {
                    ForEach(members) { m in
                        Button { onSelectUser(m.userId) } label: {
                            HStack(spacing: 5) {
                                AvatarView(url: m.userAvatarUrl, name: m.userName, size: 20)
                                Text(m.userName).font(.caption2)
                                if let t = m.title { Text(t).font(.system(size: 9)).foregroundStyle(.secondary) }
                                if m.relationshipType == "DOTTED" {
                                    Text("dotted").font(.system(size: 8)).foregroundStyle(.yellow)
                                }
                            }
                            .padding(.leading, CGFloat(depth + 1) * 14 + 18)
                        }
                        .buttonStyle(.plain)
                    }
                }

                // Children
                if let children {
                    ForEach(children) { child in
                        OrgUnitRow(unit: child, depth: depth + 1, onSelectUser: onSelectUser)
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }
}
