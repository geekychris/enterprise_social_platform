import SwiftUI

struct NotificationsView: View {
    @State private var notifications: [NotificationDto] = []
    @State private var loading = true

    var body: some View {
        List {
            ForEach(notifications) { notif in
                HStack(spacing: 12) {
                    AvatarView(url: notif.actorAvatarUrl, name: notif.actorName ?? "?", size: 36)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(notif.message ?? notificationText(notif))
                            .font(.subheadline)
                            .fontWeight(notif.read ? .regular : .semibold)
                        Text(RelativeTime.format(notif.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if !notif.read {
                        Circle()
                            .fill(.blue)
                            .frame(width: 8, height: 8)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .listStyle(.plain)
        .navigationTitle("Notifications")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Mark All Read") { markAllRead() }
                    .font(.caption)
            }
        }
        .overlay {
            if notifications.isEmpty && !loading {
                ContentUnavailableView("No notifications", systemImage: "bell.slash", description: Text("You're all caught up"))
            }
        }
        .refreshable { await load() }
        .task { await load() }
    }

    private func notificationText(_ notif: NotificationDto) -> String {
        let actor = notif.actorName ?? "Someone"
        switch notif.type {
        case "MENTION": return "\(actor) mentioned you"
        case "COMMENT": return "\(actor) commented on your post"
        case "REACTION": return "\(actor) reacted to your post"
        case "FRIEND_REQUEST": return "\(actor) sent you a friend request"
        case "FRIEND_ACCEPTED": return "\(actor) accepted your friend request"
        default: return "\(actor) interacted with your content"
        }
    }

    private func load() async {
        do {
            notifications = try await APIClient.shared.get("/notifications?limit=50")
        } catch {}
        loading = false
    }

    private func markAllRead() {
        Task {
            try? await APIClient.shared.postVoid("/notifications/mark-read")
            await load()
        }
    }
}
