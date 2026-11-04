import SwiftUI
@preconcurrency import Shared

struct RootView: View {
    @Environment(ExplorerModel.self) private var model

    var body: some View {
        TabView {
            ExploreView()
                .tabItem { Label("Explore", systemImage: "airplane.departure") }
            SavedView()
                .tabItem { Label("Saved", systemImage: "bookmark") }
                .badge(model.saved.count)
        }
        .overlay(alignment: .top) {
            if let message = model.message {
                MessageToast(message: message)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: model.message?.id)
    }
}

/// Failed saves and removes land here, so they can never pass silently.
private struct MessageToast: View {
    @Environment(ExplorerModel.self) private var model
    let message: UserMessage

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Palette.accent)
            Text(message.text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(Palette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if message.canRetry {
                Button("Retry") { model.retryMessage() }
                    .font(.footnote.weight(.semibold))
            }
            Button {
                model.dismissMessage()
            } label: {
                Image(systemName: "xmark").font(.caption.weight(.bold))
            }
            .foregroundStyle(Palette.muted)
            .accessibilityLabel("Dismiss")
        }
        .padding(14)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: 14, y: 6)
        .padding(.horizontal, 14)
        .accessibilityIdentifier("message-toast")
        .task(id: message.id) {
            try? await Task.sleep(for: .seconds(8))
            if !Task.isCancelled { model.dismissMessage() }
        }
    }
}
