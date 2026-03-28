import SwiftUI

struct PollView: View {
    let postId: Int64

    @State private var poll: PollDto
    @State private var selectedOptionIds: Set<Int64> = []
    @State private var voting = false

    private var hasVoted: Bool {
        guard let votes = poll.currentUserVotes else { return false }
        return !votes.isEmpty
    }

    private var showResults: Bool {
        hasVoted || poll.closed
    }

    init(poll: PollDto, postId: Int64) {
        self._poll = State(initialValue: poll)
        self.postId = postId
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Question
            Text(poll.question)
                .font(.caption.bold())

            if showResults {
                resultsView
            } else {
                selectionView
            }

            // Footer
            HStack(spacing: 8) {
                Text("\(poll.totalVotes) vote\(poll.totalVotes == 1 ? "" : "s")")
                    .font(.caption2)
                    .foregroundStyle(.secondary)

                if let closesAt = poll.closesAt {
                    if poll.closed {
                        Text("Closed")
                            .font(.caption2)
                            .foregroundStyle(.red)
                    } else {
                        Text("Closes \(RelativeTime.format(closesAt))")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(10)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    // MARK: - Selection View (not yet voted)

    private var selectionView: some View {
        VStack(spacing: 4) {
            ForEach(poll.options) { option in
                Button {
                    toggleOption(option.id)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: optionIcon(for: option.id))
                            .font(.caption)
                            .foregroundStyle(selectedOptionIds.contains(option.id) ? .blue : .secondary)

                        Text(option.label)
                            .font(.caption)
                            .foregroundStyle(.primary)

                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        selectedOptionIds.contains(option.id)
                            ? Color.blue.opacity(0.1)
                            : Color(.systemGray5)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                }
                .buttonStyle(.plain)
            }

            // Vote button
            Button {
                submitVote()
            } label: {
                Text("Vote")
                    .font(.caption.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
            .disabled(selectedOptionIds.isEmpty || voting)
            .padding(.top, 4)
        }
    }

    // MARK: - Results View (voted or closed)

    private var resultsView: some View {
        VStack(spacing: 4) {
            ForEach(poll.options) { option in
                let percentage = poll.totalVotes > 0
                    ? Double(option.voteCount) / Double(poll.totalVotes)
                    : 0.0
                let isUserVote = poll.currentUserVotes?.contains(option.id) == true

                HStack(spacing: 6) {
                    Text(option.label)
                        .font(.caption)
                        .foregroundStyle(isUserVote ? .blue : .primary)
                        .lineLimit(1)

                    Spacer()

                    Text("\(option.voteCount)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    Text("\(Int(percentage * 100))%")
                        .font(.caption2.bold())
                        .foregroundStyle(isUserVote ? .blue : .secondary)
                        .frame(width: 32, alignment: .trailing)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color(.systemGray5))
                            .frame(height: 6)

                        RoundedRectangle(cornerRadius: 3)
                            .fill(isUserVote ? Color.blue : Color.blue.opacity(0.4))
                            .frame(width: max(geo.size.width * percentage, 0), height: 6)
                    }
                }
                .frame(height: 6)
            }
        }
    }

    // MARK: - Helpers

    private func optionIcon(for optionId: Int64) -> String {
        if poll.allowMultiple {
            return selectedOptionIds.contains(optionId)
                ? "checkmark.square.fill"
                : "square"
        } else {
            return selectedOptionIds.contains(optionId)
                ? "circle.inset.filled"
                : "circle"
        }
    }

    private func toggleOption(_ optionId: Int64) {
        if poll.allowMultiple {
            if selectedOptionIds.contains(optionId) {
                selectedOptionIds.remove(optionId)
            } else {
                selectedOptionIds.insert(optionId)
            }
        } else {
            selectedOptionIds = [optionId]
        }
    }

    private func submitVote() {
        voting = true
        Task {
            do {
                struct VoteRequest: Codable { let optionIds: [Int64] }
                let updated: PollDto = try await APIClient.shared.post(
                    "/polls/\(poll.id)/vote",
                    body: VoteRequest(optionIds: Array(selectedOptionIds))
                )
                poll = updated
            } catch {}
            voting = false
        }
    }
}
