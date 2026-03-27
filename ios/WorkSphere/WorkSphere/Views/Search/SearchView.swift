import SwiftUI

struct SearchView: View {
    @State private var query = ""
    @State private var typeFilter = "ALL"
    @State private var results: [SearchHit] = []
    @State private var searching = false

    let types = ["ALL", "USER", "GROUP", "PAGE", "TEAM", "POST"]

    var body: some View {
        VStack(spacing: 0) {
            // Type filters
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(types, id: \.self) { type in
                        Button(type.capitalized) {
                            typeFilter = type
                            search()
                        }
                        .font(.caption.bold())
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(typeFilter == type ? Color.blue : Color(.systemGray5))
                        .foregroundStyle(typeFilter == type ? .white : .primary)
                        .clipShape(Capsule())
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }

            if results.isEmpty && !searching && !query.isEmpty {
                ContentUnavailableView("No results", systemImage: "magnifyingglass", description: Text("No results for \"\(query)\""))
            } else if results.isEmpty && query.isEmpty {
                ContentUnavailableView("Search", systemImage: "magnifyingglass", description: Text("Search for people, groups, pages, and posts"))
            } else {
                List(results) { hit in
                    NavigationLink(value: hit) {
                        HStack(spacing: 12) {
                            AvatarView(url: hit.avatarUrl, name: hit.name, size: 40)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(hit.name).font(.subheadline.bold())
                                HStack(spacing: 4) {
                                    Text(hit.objectType.capitalized)
                                        .font(.caption2)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color(.systemGray5))
                                        .clipShape(Capsule())
                                    if let desc = hit.description {
                                        Text(desc)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Search")
        .searchable(text: $query, prompt: "Search people, groups, pages...")
        .onChange(of: query) { _, _ in search() }
        .navigationDestination(for: SearchHit.self) { hit in
            switch hit.objectType {
            case "USER": ProfileView(userId: hit.id)
            case "GROUP": GroupView(groupId: hit.id)
            case "PAGE": PageView(pageId: hit.id)
            default: PostDetailView(postId: hit.id)
            }
        }
        .withGlobalNavDestinations()
    }

    private func search() {
        guard !query.isEmpty else { results = []; return }
        searching = true
        Task {
            do {
                var path = "/search?q=\(query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query)"
                if typeFilter != "ALL" { path += "&type=\(typeFilter)" }
                let result: SearchResultDto = try await APIClient.shared.get(path)
                results = result.hits
            } catch {}
            searching = false
        }
    }
}
