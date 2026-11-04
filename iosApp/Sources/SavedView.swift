import SwiftUI

/// Reads only `saved`, never the discovery results, so filters can't hide anything here. Its selection
/// is local for the same reason: a saved flight may not be in the discovery results at all.
struct SavedView: View {
    @Environment(ExplorerModel.self) private var model
    @State private var openId: String?

    private var open: FlightItem? { model.saved.first { $0.id == openId } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Saved").font(.largeTitle.weight(.bold)).foregroundStyle(Palette.ink)
                Text(model.saved.count == 1 ? "1 flight, available offline" : "\(model.saved.count) flights, available offline")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
                    .accessibilityIdentifier("saved-count")
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.surface)
            .overlay(alignment: .bottom) { Rectangle().fill(Palette.hairline).frame(height: 0.5) }

            if model.saved.isEmpty {
                MessageState(
                    systemImage: "bookmark",
                    title: "No saved flights yet",
                    message: "Tap the bookmark on any flight to keep it here, even offline."
                )
            } else {
                ZStack(alignment: .bottom) {
                    savedList
                    if let open {
                        FlightCard(
                            flight: open,
                            isSaved: true,
                            isPending: model.pendingIds.contains(open.id),
                            onToggleSaved: { model.remove(open) },
                            onClose: { openId = nil }
                        )
                        .padding(12)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .animation(.snappy(duration: 0.25), value: openId)
            }
        }
        .background(Palette.canvas)
    }

    private var savedList: some View {
        List(model.saved) { flight in
            FlightRow(
                flight: flight,
                isSelected: flight.id == openId,
                isSaved: true,
                isPending: model.pendingIds.contains(flight.id),
                onToggleSaved: { model.remove(flight) }
            )
            .onTapGesture { openId = openId == flight.id ? nil : flight.id }
            .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .swipeActions {
                Button("Remove", role: .destructive) { model.remove(flight) }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 7, for: .scrollContent)
        .contentMargins(.bottom, open == nil ? 16 : 230, for: .scrollContent)
        .accessibilityIdentifier("saved-list")
    }
}
