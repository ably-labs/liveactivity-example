import SwiftUI

struct TokenDisplayView: View {
    let label: String
    let token: String?
    let placeholder: String

    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            if let token {
                HStack(alignment: .top) {
                    Text(token)
                        .font(.system(.caption2, design: .monospaced))
                        .lineLimit(4)
                        .truncationMode(.middle)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = token
                        copied = true
                        Task {
                            try? await Task.sleep(for: .seconds(2))
                            copied = false
                        }
                    } label: {
                        Image(systemName: copied ? "checkmark" : "doc.on.doc")
                            .foregroundStyle(copied ? .green : .accentColor)
                    }
                    .buttonStyle(.borderless)
                }
            } else {
                Text(placeholder)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .italic()
            }
        }
    }
}
