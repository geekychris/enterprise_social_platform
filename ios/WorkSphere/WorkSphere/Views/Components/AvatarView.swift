import SwiftUI

struct AvatarView: View {
    let url: String?
    let name: String
    var size: CGFloat = 40

    var body: some View {
        if let url, let imgURL = URL(string: url) {
            AsyncImage(url: imgURL) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                initialCircle
            }
            .frame(width: size, height: size)
            .clipShape(Circle())
        } else {
            initialCircle
        }
    }

    private var initialCircle: some View {
        Circle()
            .fill(.blue.gradient)
            .frame(width: size, height: size)
            .overlay {
                Text(String(name.prefix(1)).uppercased())
                    .font(.system(size: size * 0.4, weight: .semibold))
                    .foregroundStyle(.white)
            }
    }
}

struct ReactionButton: View {
    let reactionCounts: [String: Int]
    let currentReaction: String?
    let targetId: Int64
    var onReact: (String?) -> Void = { _ in }

    @State private var showPicker = false

    var body: some View {
        HStack(spacing: 4) {
            // Show existing reactions
            ForEach(sortedReactions, id: \.0) { type, count in
                Button {
                    if currentReaction == type {
                        onReact(nil)
                    } else {
                        onReact(type)
                    }
                } label: {
                    HStack(spacing: 2) {
                        Text(ReactionType(rawValue: type)?.emoji ?? "👍")
                            .font(.caption2)
                        Text("\(count)")
                            .font(.caption2)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(currentReaction == type ? Color.blue.opacity(0.15) : Color.gray.opacity(0.1))
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }

            // Add reaction button
            Menu {
                ForEach(ReactionType.allCases, id: \.rawValue) { type in
                    Button("\(type.emoji) \(type.rawValue.capitalized)") {
                        onReact(type.rawValue)
                    }
                }
            } label: {
                Image(systemName: "face.smiling")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(6)
                    .background(Color.gray.opacity(0.1))
                    .clipShape(Circle())
            }
        }
    }

    private var sortedReactions: [(String, Int)] {
        reactionCounts.sorted { $0.value > $1.value }.filter { $0.value > 0 }
    }
}

struct RelativeTime {
    static func format(_ dateString: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: dateString) ?? ISO8601DateFormatter().date(from: dateString) else {
            return dateString
        }
        let seconds = -date.timeIntervalSinceNow
        if seconds < 60 { return "just now" }
        if seconds < 3600 { return "\(Int(seconds / 60))m ago" }
        if seconds < 86400 { return "\(Int(seconds / 3600))h ago" }
        if seconds < 604800 { return "\(Int(seconds / 86400))d ago" }
        return date.formatted(.dateTime.month(.abbreviated).day())
    }
}
