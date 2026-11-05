import SwiftUI

/// origin ●- - ✈ - -● destination: the route connector, drawn the same in rows and on the card.
struct RouteConnector: View {
    var planeSize: CGFloat = 13

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                let y = geometry.size.height / 2
                Path { path in
                    path.move(to: CGPoint(x: 3, y: y))
                    path.addLine(to: CGPoint(x: geometry.size.width - 3, y: y))
                }
                .stroke(Palette.accent, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                Circle().fill(Palette.accent).frame(width: 6, height: 6).position(x: 3, y: y)
                Circle().fill(Palette.accent).frame(width: 6, height: 6).position(x: geometry.size.width - 3, y: y)
            }
            Image(systemName: "airplane")
                .font(.system(size: planeSize, weight: .semibold))
                .foregroundStyle(Palette.accent)
                .padding(2)
                .background(Palette.surface, in: Circle())
        }
        .frame(height: 20)
        .accessibilityHidden(true)
    }
}

struct SaveIconButton: View {
    let flight: FlightItem
    let isSaved: Bool
    let isPending: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if isPending {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(isSaved ? Palette.accent : Palette.muted)
                        .contentTransition(.symbolEffect(.replace))
                }
            }
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .disabled(isPending)
        .accessibilityLabel(isSaved ? "Remove \(flight.routeLabel) from saved" : "Save \(flight.routeLabel)")
        .accessibilityIdentifier("save-\(flight.id)")
    }
}

struct FlightRow: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let flight: FlightItem
    let isSelected: Bool
    let isSaved: Bool
    let isPending: Bool
    let onToggleSaved: () -> Void

    var body: some View {
        Group {
            // Side by side there's no room at accessibility sizes: codes and prices would wrap a
            // letter at a time. Stacked, every line gets the full width.
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 6) {
                    route
                    details
                    HStack {
                        price(alignment: .leading)
                        Spacer(minLength: 4)
                        saveButton
                    }
                }
            } else {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 3) {
                        route
                        details
                    }
                    Spacer(minLength: 4)
                    price(alignment: .trailing)
                    saveButton
                }
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, 2)
        .padding(.vertical, 10)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(isSelected ? Palette.accent : Palette.hairline, lineWidth: isSelected ? 1.5 : 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("flight-\(flight.id)")
    }

    private var route: some View {
        HStack(spacing: 6) {
            Text(flight.origin).font(.headline).fixedSize()
            RouteConnector(planeSize: 11).frame(width: 34)
            Text(flight.destination).font(.headline).fixedSize()
        }
        .foregroundStyle(Palette.ink)
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("\(flight.originCity) to \(flight.destinationCity)")
            Text("\(flight.departureRowLabel) · \(flight.cabin)")
        }
        .font(.footnote)
        .foregroundStyle(Palette.muted)
        // Wraps rather than truncates: the destination and cabin are what people scan rows for.
        .fixedSize(horizontal: false, vertical: true)
    }

    private func price(alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 3) {
            Text(flight.pointsLabel)
                .font(.headline.monospacedDigit())
                .foregroundStyle(Palette.ink)
                .fixedSize()
            Text(flight.program)
                .font(.footnote)
                .foregroundStyle(Palette.muted)
                .fixedSize()
        }
    }

    private var saveButton: some View {
        SaveIconButton(flight: flight, isSaved: isSaved, isPending: isPending, action: onToggleSaved)
    }
}

/// The selected flight's details, shared by the list, the map and the saved screen.
struct FlightCard<Secondary: View>: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let flight: FlightItem
    let isSaved: Bool
    let isPending: Bool
    let onToggleSaved: () -> Void
    let onClose: () -> Void
    @ViewBuilder var secondary: () -> Secondary

    var body: some View {
        Group {
            // Scrolls when it can't fit. Left to grow, a card taller than the space pushes the whole
            // screen up under the status bar.
            if typeSize.isAccessibilitySize {
                ViewThatFits(in: .vertical) {
                    details
                    ScrollView { details }
                }
            } else {
                details
            }
        }
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Palette.hairline)
        }
        .shadow(color: .black.opacity(0.16), radius: 18, y: 6)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("flight-card")
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(flight.departureLabel)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Palette.muted)
                        .frame(width: 32, height: 32)
                        .background(Palette.canvas, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close flight details")
                .accessibilityIdentifier("close-card")
            }
            HStack(spacing: 12) {
                Text(flight.origin).font(.system(size: 28, weight: .bold))
                RouteConnector()
                Text(flight.destination).font(.system(size: 28, weight: .bold))
            }
            .foregroundStyle(Palette.ink)
            .padding(.top, 2)
            HStack {
                Text(flight.originCity)
                Spacer()
                Text(flight.destinationCity)
            }
            .font(.footnote)
            .foregroundStyle(Palette.muted)
            // Stacked at accessibility sizes, where the price and the button can't share a line.
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 10) {
                    priceSummary
                    saveButton
                }
                .padding(.top, 14)
            } else {
                HStack(alignment: .center) {
                    priceSummary
                    Spacer()
                    saveButton
                }
                .padding(.top, 14)
            }
            secondary()
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 16)
    }

    private var priceSummary: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(flight.pointsLabel)
                    .font(.title2.weight(.bold).monospacedDigit())
                    .foregroundStyle(Palette.ink)
                    .fixedSize()
                Text("pts").font(.footnote).foregroundStyle(Palette.muted).fixedSize()
            }
            Text("\(flight.cabin) · \(flight.program)")
                .font(.footnote)
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var saveButton: some View {
        Button(action: onToggleSaved) {
            HStack(spacing: 6) {
                if isPending { ProgressView().controlSize(.small) }
                Text(isSaved ? "Remove" : "Save").fontWeight(.semibold)
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 11)
            .foregroundStyle(isSaved ? Palette.ink : Palette.onAccent)
            .background(isSaved ? Palette.canvas : Palette.accent, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(isPending)
        .accessibilityIdentifier("card-save")
    }
}

extension FlightCard where Secondary == EmptyView {
    init(
        flight: FlightItem,
        isSaved: Bool,
        isPending: Bool,
        onToggleSaved: @escaping () -> Void,
        onClose: @escaping () -> Void
    ) {
        self.init(
            flight: flight, isSaved: isSaved, isPending: isPending,
            onToggleSaved: onToggleSaved, onClose: onClose, secondary: { EmptyView() }
        )
    }
}

/// Shared layout for the error and empty states.
struct MessageState: View {
    let systemImage: String
    let title: String
    let message: String
    var actionTitle: String?
    var action: () -> Void = {}

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(size: 26, weight: .medium))
                .foregroundStyle(Palette.accent)
                .frame(width: 64, height: 64)
                .background(Palette.accentSoft, in: Circle())
                .padding(.bottom, 10)
            Text(title).font(.headline).foregroundStyle(Palette.ink)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
            if let actionTitle {
                Button(action: action) {
                    Text(actionTitle)
                        .fontWeight(.semibold)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 11)
                        .foregroundStyle(Palette.onAccent)
                        .background(Palette.accent, in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 10)
                .accessibilityIdentifier("state-action")
            }
        }
        .padding(.horizontal, 36)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.canvas)
    }
}

/// Skeleton rows for the first load, so the layout doesn't jump when results arrive.
struct LoadingRows: View {
    var body: some View {
        // In a ScrollView so the rows take the space they're offered. As a plain stack their fixed
        // heights overflow the screen and push the search header up under the status bar.
        ScrollView {
            VStack(spacing: 10) {
                ForEach(0..<7, id: \.self) { _ in
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 8) {
                            bar(width: 120, height: 16)
                            bar(width: 180, height: 12)
                            bar(width: 150, height: 12)
                        }
                        Spacer()
                        bar(width: 56, height: 16)
                    }
                    .padding(14)
                    .background(Palette.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
        .scrollDisabled(true)
        .background(Palette.canvas)
        .accessibilityElement()
        .accessibilityLabel("Loading flights")
    }

    private func bar(width: CGFloat, height: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 6).fill(Palette.hairline).frame(width: width, height: height)
    }
}
