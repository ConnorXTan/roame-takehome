import SwiftUI
import UIKit

/// The same tokens as androidApp's Theme.kt, so the two apps read as one product.
enum Palette {
    static let canvas = Color(light: 0xF5F5F1, dark: 0x0E141B)
    static let surface = Color(light: 0xFFFFFF, dark: 0x18202A)
    static let ink = Color(light: 0x12202E, dark: 0xF1F3F5)
    static let muted = Color(light: 0x5E6B78, dark: 0x9AA6B2)
    static let hairline = Color(light: 0xE3E3DD, dark: 0x2B3440)
    static let accent = Color(light: 0xE5482A, dark: 0xFF7050)
    static let onAccent = Color(light: 0xFFFFFF, dark: 0x2A0E06)
    static let accentSoft = Color(light: 0xFCE9E4, dark: 0x3A211B)
    static let warning = Color(light: 0x8A5A00, dark: 0xF2C46B)
    static let warningSoft = Color(light: 0xFBF0D9, dark: 0x332A17)
}

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// Wraps chips onto new lines, which a plain HStack can't do.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        return CGSize(
            width: proposal.width ?? rows.map(\.width).max() ?? 0,
            height: rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        )
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = fittedSize(of: subviews[index], width: bounds.width)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    /// A chip's natural size, unless that's wider than the space: at large text sizes one can be.
    private func fittedSize(of subview: LayoutSubview, width: CGFloat) -> CGSize {
        let natural = subview.sizeThatFits(.unspecified)
        guard natural.width > width else { return natural }
        return subview.sizeThatFits(ProposedViewSize(width: width, height: nil))
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows = [Row()]
        for index in subviews.indices {
            let size = fittedSize(of: subviews[index], width: width)
            if !rows[rows.count - 1].indices.isEmpty, rows[rows.count - 1].width + spacing + size.width > width {
                rows.append(Row())
            }
            let gap = rows[rows.count - 1].indices.isEmpty ? 0 : spacing
            rows[rows.count - 1].indices.append(index)
            rows[rows.count - 1].width += gap + size.width
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
        }
        return rows
    }
}
