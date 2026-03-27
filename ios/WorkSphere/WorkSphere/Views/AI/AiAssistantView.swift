import SwiftUI

struct AiAssistantView: View {
    let context: String
    var contextId: Int64?

    @State private var expanded = false
    @State private var question = ""
    @State private var response = ""
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            if !expanded {
                Button {
                    expanded = true
                } label: {
                    Label("Ask AI", systemImage: "sparkles")
                        .font(.subheadline.bold())
                        .foregroundStyle(.purple)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.purple.opacity(0.1))
                        .clipShape(Capsule())
                }
            } else {
                VStack(spacing: 12) {
                    // Header
                    HStack {
                        Label("AI Assistant", systemImage: "sparkles")
                            .font(.subheadline.bold())
                            .foregroundStyle(.purple)
                        Spacer()
                        Button("Close") {
                            expanded = false
                            response = ""
                            error = nil
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }

                    // Suggestions
                    if response.isEmpty && !loading {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(suggestions, id: \.self) { s in
                                    Button(s) { question = s }
                                        .font(.caption)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(.white)
                                        .clipShape(Capsule())
                                        .overlay(Capsule().stroke(.purple.opacity(0.3)))
                                }
                            }
                        }
                    }

                    // Response
                    if !response.isEmpty || loading {
                        ScrollView {
                            if loading && response.isEmpty {
                                HStack(spacing: 8) {
                                    ProgressView().tint(.purple)
                                    Text("Thinking...").font(.subheadline).foregroundStyle(.secondary)
                                }
                                .padding()
                            } else {
                                MarkdownText(content: response, font: .caption)
                                    .textSelection(.enabled)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding()
                            }
                        }
                        .frame(maxHeight: 250)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }

                    if let error {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }

                    // Input
                    HStack(spacing: 8) {
                        TextField("Ask about this content...", text: $question)
                            .textFieldStyle(.roundedBorder)
                            .font(.subheadline)
                            .onSubmit { ask() }

                        Button { ask() } label: {
                            if loading {
                                ProgressView().tint(.white)
                            } else {
                                Text("Ask")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.purple)
                        .controlSize(.small)
                        .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || loading)
                    }
                }
                .padding()
                .background(.purple.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(.purple.opacity(0.15)))
            }
        }
    }

    private var suggestions: [String] {
        switch context {
        case "conversation": return ["Summarize this conversation", "What are the key decisions?", "List action items"]
        case "group": return ["Summarize recent activity", "Trending topics?", "Important announcements?"]
        case "page": return ["Summarize recent updates", "What's new?", "Key highlights"]
        case "feed": return ["Catch me up", "What's trending?", "Summarize important posts"]
        default: return ["Summarize"]
        }
    }

    private func ask() {
        guard !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        loading = true
        response = ""
        error = nil

        Task {
            do {
                try await APIClient.shared.streamAI(context: context, contextId: contextId, question: question) { token in
                    response += token
                }
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }
}
