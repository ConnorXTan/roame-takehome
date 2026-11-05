import SwiftUI

private enum DiscoveryMode: String, CaseIterable, Identifiable {
    case list = "List", map = "Map"
    var id: Self { self }
}

struct ExploreView: View {
    @Environment(ExplorerModel.self) private var model
    @State private var mode = DiscoveryMode.list
    @State private var mapRequested = false
    @State private var showFilters = false
    @FocusState private var focusedField: PlaceField?

    private enum PlaceField { case origin, destination }

    var body: some View {
        VStack(spacing: 0) {
            header
            if model.isStale { staleBanner.transition(.move(edge: .top).combined(with: .opacity)) }
            content.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Palette.canvas)
        .animation(.snappy(duration: 0.25), value: model.isStale)
        .sheet(isPresented: $showFilters) {
            FiltersSheet()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: Header

    private var header: some View {
        @Bindable var model = model
        return VStack(spacing: 8) {
            VStack(spacing: 0) {
                placeField("From", systemImage: "airplane.departure", text: $model.originText, field: .origin, id: "origin-field")
                Divider().padding(.leading, 44)
                placeField("To", systemImage: "airplane.arrival", text: $model.destinationText, field: .destination, id: "destination-field")
            }
            .background(Palette.canvas, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

            HStack(spacing: 10) {
                Text(model.resultSummary)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.ink)
                    .contentTransition(.numericText())
                    .accessibilityIdentifier("result-count")
                Spacer()
                Picker("View", selection: $mode.animation(.snappy(duration: 0.2))) {
                    Image(systemName: "list.bullet").tag(DiscoveryMode.list).accessibilityLabel("List view")
                    Image(systemName: "map").tag(DiscoveryMode.map).accessibilityLabel("Map view")
                }
                .pickerStyle(.segmented)
                .frame(width: 104)
                .accessibilityIdentifier("mode-picker")
                filterButton
            }

            if !model.activeFilters.isEmpty {
                activeFilterChips.transition(.opacity)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(Palette.surface)
        .overlay(alignment: .bottom) { Rectangle().fill(Palette.hairline).frame(height: 0.5) }
        .onChange(of: mode) { _, next in
            focusedField = nil
            if next == .map { mapRequested = true }
        }
        .animation(.snappy(duration: 0.2), value: model.activeFilters)
    }

    private func placeField(
        _ label: String,
        systemImage: String,
        text: Binding<String>,
        field: PlaceField,
        id: String
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Palette.muted)
                .frame(width: 20)
            TextField("\(label) · City or airport", text: text)
                .font(.subheadline)
                .foregroundStyle(Palette.ink)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .submitLabel(field == .origin ? .next : .done)
                .focused($focusedField, equals: field)
                .onSubmit { focusedField = field == .origin ? .destination : nil }
                .accessibilityLabel(label)
                .accessibilityIdentifier(id)
            if !text.wrappedValue.isEmpty {
                Button {
                    text.wrappedValue = ""
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Palette.muted)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear \(label)")
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 44)
    }

    private var filterButton: some View {
        Button {
            focusedField = nil
            showFilters = true
        } label: {
            Image(systemName: "slider.horizontal.3")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Palette.ink)
                .frame(width: 36, height: 32)
                .overlay(alignment: .topTrailing) {
                    if model.filterCount > 0 {
                        Text("\(model.filterCount)")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Palette.onAccent)
                            .frame(minWidth: 16, minHeight: 16)
                            .background(Palette.accent, in: Circle())
                            .offset(x: 4, y: -4)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(model.filterCount > 0 ? "Filters, \(model.filterCount) active" : "Filters")
        .accessibilityIdentifier("filters-button")
    }

    private var activeFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                Button("Reset") {
                    focusedField = nil
                    model.resetFilters()
                }
                .font(.footnote.weight(.semibold))
                .padding(.trailing, 4)
                .accessibilityIdentifier("reset-filters")
                ForEach(model.activeFilters) { filter in
                    Button {
                        model.removeFilter(filter)
                    } label: {
                        HStack(spacing: 5) {
                            Text(filter.label).font(.footnote.weight(.medium))
                            Image(systemName: "xmark").font(.system(size: 9, weight: .bold))
                        }
                        .foregroundStyle(Palette.ink)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Palette.accentSoft, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove \(filter.label)")
                }
            }
        }
        .scrollClipDisabled()
    }

    private var staleBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "icloud.slash").font(.footnote.weight(.semibold))
            Text(model.staleMessage ?? "")
                .font(.footnote.weight(.medium))
                .frame(maxWidth: .infinity, alignment: .leading)
            Button("Retry") { model.refresh() }
                .font(.footnote.weight(.semibold))
                .disabled(model.isRefreshing)
        }
        .foregroundStyle(Palette.warning)
        .padding(.horizontal, 16)
        .padding(.vertical, 9)
        .background(Palette.warningSoft)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("stale-banner")
    }

    // MARK: Content

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            LoadingRows()
        case .error:
            MessageState(
                systemImage: "icloud.slash",
                title: "Couldn't load flights",
                message: model.failureMessage ?? "Please try again.",
                actionTitle: "Try again",
                action: model.refresh
            )
        case .noFlights:
            ScrollView {
                MessageState(
                    systemImage: "tray",
                    title: "No flights available",
                    message: model.isStale
                        ? "The last update had no flights, and we couldn't check for new ones. Pull down to try again."
                        : "The flight service is working but has nothing to offer right now. Pull down to check again."
                )
                .containerRelativeFrame(.vertical)
            }
            .refreshable { await refreshAndWait() }
            .background(Palette.canvas)
        case .noMatches, .results:
            discovery
        }
    }

    private var discovery: some View {
        ZStack(alignment: .bottom) {
            // Kept alive under the list once opened, so returning to it keeps the camera and costs nothing.
            if mapRequested {
                FlightMapView()
                    .opacity(mode == .map ? 1 : 0)
                    .allowsHitTesting(mode == .map)
                    .accessibilityHidden(mode != .map)
            }
            // The list can't stay alive the same way: hidden rows would remain in the accessibility tree.
            if mode == .list {
                listContent
            }
        }
    }

    @ViewBuilder
    private var listContent: some View {
        if model.phase == .noMatches {
            MessageState(
                systemImage: "magnifyingglass",
                title: "No flights match",
                message: "Try a different airport, or loosen your filters.",
                actionTitle: "Reset filters",
                action: model.resetFilters
            )
        } else {
            ZStack(alignment: .bottom) {
                ResultsList(bottomInset: model.selectedFlight == nil ? 16 : 230, refresh: refreshAndWait)
                if let flight = model.selectedFlight {
                    FlightCard(
                        flight: flight,
                        isSaved: model.savedIds.contains(flight.id),
                        isPending: model.pendingIds.contains(flight.id),
                        onToggleSaved: { model.toggleSaved(flight) },
                        onClose: { model.selectFlight(nil) }
                    ) {
                        Button("Show on map") {
                            withAnimation(.snappy(duration: 0.2)) { mode = .map }
                        }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.top, 10)
                        .accessibilityIdentifier("show-on-map")
                    }
                    .padding(12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.snappy(duration: 0.25), value: model.selectedFlight?.id)
        }
    }

    private func refreshAndWait() async {
        model.refresh()
        // Holds the system spinner until the shared refresh finishes.
        while true {
            try? await Task.sleep(for: .milliseconds(120))
            if !model.isRefreshing || Task.isCancelled { return }
        }
    }
}

/// Reads its own slice of the model, so a keystroke in the header doesn't touch 1,000 rows.
private struct ResultsList: View {
    @Environment(ExplorerModel.self) private var model
    let bottomInset: CGFloat
    let refresh: () async -> Void

    var body: some View {
        ScrollViewReader { proxy in
            list
                .onChange(of: model.newResultsToken) { _, _ in
                    if let first = model.results.first { proxy.scrollTo(first.id, anchor: .top) }
                }
                // Coming back from the map, the selected flight is what the user was looking at.
                .onAppear {
                    if let id = model.selectedFlight?.id { proxy.scrollTo(id, anchor: .center) }
                }
        }
    }

    private var list: some View {
        List(model.results) { flight in
            FlightRow(
                flight: flight,
                isSelected: flight.id == model.selectedFlight?.id,
                isSaved: model.savedIds.contains(flight.id),
                isPending: model.pendingIds.contains(flight.id),
                onToggleSaved: { model.toggleSaved(flight) }
            )
            .onTapGesture {
                model.selectFlight(flight.id == model.selectedFlight?.id ? nil : flight.id)
            }
            // The gap above the first row is part of the row, not a content margin, because
            // scrollTo ignores margins and would otherwise park the row against the header.
            .listRowInsets(EdgeInsets(top: flight.id == model.results.first?.id ? 12 : 5, leading: 16, bottom: 5, trailing: 16))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .contentMargins(.bottom, bottomInset, for: .scrollContent)
        .background(Palette.canvas)
        .refreshable { await refresh() }
        .accessibilityIdentifier("results-list")
    }
}
