import MapKit
import SwiftUI

struct FlightMapView: View {
    @Environment(ExplorerModel.self) private var model
    @State private var camera: MapCameraPosition = .region(Self.world)

    private static let world = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 25, longitude: 15),
        span: MKCoordinateSpan(latitudeDelta: 130, longitudeDelta: 300)
    )

    /// Which marker to raise and tint: the open destination, or the selected flight's.
    private var highlighted: String? {
        model.selectedDestination?.code ?? model.selectedFlight?.destination
    }

    /// Later annotations draw on top, so the highlighted one goes last, after the cheapest.
    private var orderedMarkers: [MarkerItem] {
        model.markers.sorted { a, b in
            if (a.code == highlighted) != (b.code == highlighted) { return b.code == highlighted }
            return a.lowestPoints > b.lowestPoints
        }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(position: $camera, interactionModes: [.pan, .zoom]) {
                if let flight = model.selectedFlight {
                    // Geodesic, so routes over the antimeridian take the short way across the Pacific.
                    MapPolyline(coordinates: [flight.originCoordinate, flight.destinationCoordinate], contourStyle: .geodesic)
                        .stroke(Palette.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    Annotation(flight.origin, coordinate: flight.originCoordinate, anchor: .center) {
                        OriginBadge(code: flight.origin)
                    }
                    .annotationTitles(.hidden)
                }
                ForEach(orderedMarkers) { marker in
                    Annotation(marker.city, coordinate: marker.coordinate, anchor: .center) {
                        MarkerPill(marker: marker, isHighlighted: marker.code == highlighted) {
                            model.selectDestination(marker.code)
                        }
                    }
                    .annotationTitles(.hidden)
                }
            }
            .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
            .onTapGesture { model.clearSelection() }
            .accessibilityIdentifier("flight-map")

            overlays
        }
        .onChange(of: model.selectedFlight?.id, initial: true) { _, _ in
            guard let flight = model.selectedFlight else { return }
            withAnimation(.easeInOut(duration: 0.45)) {
                camera = .region(model.routeRegion(for: flight))
            }
        }
        .animation(.snappy(duration: 0.25), value: model.selectedFlight?.id)
        .animation(.snappy(duration: 0.25), value: model.selectedDestination?.code)
    }

    @ViewBuilder
    private var overlays: some View {
        if let flight = model.selectedFlight {
            FlightCard(
                flight: flight,
                isSaved: model.savedIds.contains(flight.id),
                isPending: model.pendingIds.contains(flight.id),
                onToggleSaved: { model.toggleSaved(flight) },
                onClose: { model.selectFlight(nil) }
            )
            .padding(12)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        } else if let destination = model.selectedDestination {
            DestinationPanel(marker: destination)
                .padding(12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        } else if model.phase == .noMatches {
            VStack {
                HStack(spacing: 10) {
                    Text("No flights match your filters")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Palette.ink)
                    Button("Reset") { model.resetFilters() }
                        .font(.footnote.weight(.semibold))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .shadow(color: .black.opacity(0.12), radius: 10, y: 4)
                .padding(12)
                Spacer()
            }
        }
    }
}

private struct MarkerPill: View {
    let marker: MarkerItem
    let isHighlighted: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Text("\(marker.flightCount)")
                    .foregroundStyle(isHighlighted ? Palette.onAccent : Palette.accent)
                Text(marker.compactPointsLabel)
                    .foregroundStyle(isHighlighted ? Palette.onAccent : Palette.ink)
            }
            .font(.system(size: 12, weight: .bold).monospacedDigit())
            .padding(.horizontal, 9)
            .frame(height: 25)
            .background(isHighlighted ? Palette.accent : Palette.surface, in: Capsule())
            .overlay { Capsule().strokeBorder(isHighlighted ? Palette.accent : Palette.ink.opacity(0.2)) }
            .shadow(color: .black.opacity(0.15), radius: 3, y: 1)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(marker.city), \(marker.code). \(marker.flightCount) flights from \(marker.pointsLabel) points")
        .accessibilityIdentifier("marker-\(marker.code)")
    }
}

private struct OriginBadge: View {
    let code: String

    var body: some View {
        Text(code)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Palette.onAccent)
            .padding(.horizontal, 7)
            .frame(height: 20)
            .background(Palette.accent, in: Capsule())
            .overlay { Capsule().strokeBorder(.white.opacity(0.8), lineWidth: 1) }
            .allowsHitTesting(false)
    }
}

/// Slides up when a marker is tapped, so its flights can be browsed without leaving the map.
private struct DestinationPanel: View {
    @Environment(ExplorerModel.self) private var model
    let marker: MarkerItem

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text("\(marker.city) · \(marker.code)")
                        .font(.headline)
                        .foregroundStyle(Palette.ink)
                    Text("\(marker.flightCount) \(marker.flightCount == 1 ? "flight" : "flights") · from \(marker.pointsLabel) pts")
                        .font(.footnote)
                        .foregroundStyle(Palette.muted)
                }
                Spacer()
                Button {
                    model.clearSelection()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Palette.muted)
                        .frame(width: 32, height: 32)
                        .background(Palette.canvas, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close \(marker.city) flights")
                .accessibilityIdentifier("close-destination")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Palette.surface)
            Divider()
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(model.destinationFlights) { flight in
                        FlightRow(
                            flight: flight,
                            isSelected: false,
                            isSaved: model.savedIds.contains(flight.id),
                            isPending: model.pendingIds.contains(flight.id),
                            onToggleSaved: { model.toggleSaved(flight) }
                        )
                        .onTapGesture { model.selectFlight(flight.id) }
                    }
                }
                .padding(10)
            }
            .id(marker.code)
        }
        .frame(height: 320)
        .background(Palette.canvas)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.16), radius: 18, y: 6)
        .accessibilityIdentifier("destination-panel")
    }
}
