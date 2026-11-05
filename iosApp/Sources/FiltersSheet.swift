import SwiftUI

struct FiltersSheet: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var typeSize

    private static let pointsStep = 500.0

    /// Flight dates are plain calendar days. Parsing and printing with one formatter in the device's
    /// own time zone round-trips the same day that the picker shows, wherever the device is.
    private static let isoDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private var dateBounds: ClosedRange<Date>? {
        guard let first = model.firstDateIso.flatMap(Self.isoDay.date(from:)),
              let last = model.lastDateIso.flatMap(Self.isoDay.date(from:)) else { return nil }
        return first...last
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    if let bounds = dateBounds { datesSection(bounds) }
                    cabinSection
                    pointsSection
                    programsSection
                }
                .padding(20)
            }
            .background(Palette.surface)
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Reset") { model.resetFilters() }
                        .disabled(model.activeFilters.isEmpty)
                        .accessibilityIdentifier("sheet-reset")
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button {
                    dismiss()
                } label: {
                    Text(showLabel)
                        .fontWeight(.semibold)
                        .contentTransition(.numericText())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(Palette.onAccent)
                        .background(Palette.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(Palette.surface)
                .accessibilityIdentifier("show-results")
            }
        }
    }

    private var showLabel: String {
        model.results.count == 1 ? "Show 1 flight" : "Show \(model.results.count) flights"
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.ink)
    }

    private func datesSection(_ bounds: ClosedRange<Date>) -> some View {
        let start = Binding<Date>(
            get: { model.dateStartIso.flatMap(Self.isoDay.date(from:)) ?? bounds.lowerBound },
            set: { model.setDateStart(Self.isoDay.string(from: $0)) }
        )
        let end = Binding<Date>(
            get: { model.dateEndIso.flatMap(Self.isoDay.date(from:)) ?? bounds.upperBound },
            set: { model.setDateEnd(Self.isoDay.string(from: $0)) }
        )
        return VStack(alignment: .leading, spacing: 8) {
            sectionTitle("Departure dates")
            datePicker("From", selection: start, in: bounds, id: "date-start")
            datePicker("To", selection: end, in: bounds, id: "date-end")
        }
        .foregroundStyle(Palette.ink)
    }

    /// The label goes above the control at accessibility sizes, where side by side it would wrap
    /// a letter at a time.
    @ViewBuilder
    private func datePicker(_ title: String, selection: Binding<Date>, in bounds: ClosedRange<Date>, id: String) -> some View {
        if typeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).fixedSize()
                DatePicker(title, selection: selection, in: bounds, displayedComponents: .date)
                    .labelsHidden()
                    .accessibilityIdentifier(id)
            }
        } else {
            DatePicker(title, selection: selection, in: bounds, displayedComponents: .date)
                .accessibilityIdentifier(id)
        }
    }

    private var cabinSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle("Cabin")
            FlowLayout {
                ChoiceChip(label: "Any", isSelected: model.cabin == nil) { model.setCabin(nil) }
                ForEach(model.cabinOptions, id: \.self) { cabin in
                    ChoiceChip(label: cabin, isSelected: model.cabin == cabin) { model.setCabin(cabin) }
                }
            }
        }
    }

    @ViewBuilder
    private var pointsSection: some View {
        if model.maxPointsOption > model.minPointsOption {
            let limit = Binding<Double>(
                get: { Double(model.maxPoints ?? model.maxPointsOption) },
                set: { model.setMaxPoints(Int($0)) }
            )
            VStack(alignment: .leading, spacing: 8) {
                sectionTitle("Maximum points")
                Text(model.maxPoints.map { "Up to \($0.formatted()) pts" } ?? "No limit")
                    .font(.subheadline)
                    .foregroundStyle(Palette.muted)
                    .monospacedDigit()
                Slider(
                    value: limit,
                    in: Double(model.minPointsOption)...Double(model.maxPointsOption),
                    step: Self.pointsStep
                )
                .accessibilityIdentifier("points-slider")
            }
        }
    }

    private var programsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionTitle("Loyalty programs")
            Text("Show flights from any selected program.")
                .font(.footnote)
                .foregroundStyle(Palette.muted)
            FlowLayout {
                ForEach(model.programOptions, id: \.self) { program in
                    ChoiceChip(label: program, isSelected: model.selectedPrograms.contains(program)) {
                        model.toggleProgram(program)
                    }
                }
            }
        }
    }
}

private struct ChoiceChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(isSelected ? Palette.surface : Palette.ink)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(isSelected ? Palette.ink : Palette.surface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(isSelected ? Palette.ink : Palette.hairline)
                }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("chip-\(label)")
    }
}
