import SwiftUI

/// Renders markdown with block-level support (lists, headings, paragraphs).
struct MarkdownText: View {
    let content: String
    var font: Font = .caption

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(parseBlocks().enumerated()), id: \.offset) { _, block in
                blockView(block)
            }
        }
    }

    @ViewBuilder
    private func blockView(_ block: Block) -> some View {
        switch block {
        case .heading(let level, let text):
            Text(inlineMarkdown(text))
                .font(level == 1 ? .headline : level == 2 ? .subheadline.bold() : .caption.bold())
        case .bullet(let text):
            HStack(alignment: .top, spacing: 6) {
                Text("\u{2022}")
                    .font(font)
                    .foregroundStyle(.secondary)
                Text(inlineMarkdown(text))
                    .font(font)
            }
            .padding(.leading, 8)
        case .numbered(let num, let text):
            HStack(alignment: .top, spacing: 6) {
                Text("\(num).")
                    .font(font)
                    .foregroundStyle(.secondary)
                    .frame(width: 16, alignment: .trailing)
                Text(inlineMarkdown(text))
                    .font(font)
            }
            .padding(.leading, 8)
        case .paragraph(let text):
            Text(inlineMarkdown(text))
                .font(font)
        }
    }

    private func inlineMarkdown(_ text: String) -> AttributedString {
        let options = AttributedString.MarkdownParsingOptions(
            interpretedSyntax: .inlineOnlyPreservingWhitespace,
            failurePolicy: .returnPartiallyParsedIfPossible
        )
        return (try? AttributedString(markdown: text, options: options)) ?? AttributedString(text)
    }

    private enum Block {
        case heading(Int, String)
        case bullet(String)
        case numbered(Int, String)
        case paragraph(String)
    }

    private func parseBlocks() -> [Block] {
        var blocks: [Block] = []
        var currentParagraph = ""
        var numberedCounter = 0

        for line in content.components(separatedBy: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            if trimmed.isEmpty {
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph))
                    currentParagraph = ""
                }
                numberedCounter = 0
                continue
            }

            // Headings
            if trimmed.hasPrefix("### ") {
                flushParagraph(&currentParagraph, &blocks)
                blocks.append(.heading(3, String(trimmed.dropFirst(4))))
            } else if trimmed.hasPrefix("## ") {
                flushParagraph(&currentParagraph, &blocks)
                blocks.append(.heading(2, String(trimmed.dropFirst(3))))
            } else if trimmed.hasPrefix("# ") {
                flushParagraph(&currentParagraph, &blocks)
                blocks.append(.heading(1, String(trimmed.dropFirst(2))))
            }
            // Bullet lists
            else if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") {
                flushParagraph(&currentParagraph, &blocks)
                blocks.append(.bullet(String(trimmed.dropFirst(2))))
                numberedCounter = 0
            }
            // Numbered lists
            else if let match = trimmed.range(of: #"^\d+\.\s+"#, options: .regularExpression) {
                flushParagraph(&currentParagraph, &blocks)
                numberedCounter += 1
                let text = String(trimmed[match.upperBound...])
                blocks.append(.numbered(numberedCounter, text))
            }
            // Regular text — accumulate into paragraph
            else {
                if !currentParagraph.isEmpty { currentParagraph += " " }
                currentParagraph += trimmed
            }
        }
        flushParagraph(&currentParagraph, &blocks)
        return blocks
    }

    private func flushParagraph(_ para: inout String, _ blocks: inout [Block]) {
        if !para.isEmpty {
            blocks.append(.paragraph(para))
            para = ""
        }
    }
}
