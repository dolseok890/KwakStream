import SwiftUI

/// Simulator-only geometry reference. This draws in the view, not through AVFoundation.
struct SimulatorPattern: View {
    var body: some View {
        Canvas { context, size in
            let w = size.width, h = size.height
            let step = min(w, h) / 9
            var grid = Path()
            for i in -16...16 {
                let x = w / 2 + CGFloat(i) * step
                let y = h / 2 + CGFloat(i) * step
                grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x, y: h))
                grid.move(to: CGPoint(x: 0, y: y)); grid.addLine(to: CGPoint(x: w, y: y))
            }
            context.stroke(grid, with: .color(.white.opacity(0.3)), lineWidth: 0.5)
            let diameter = min(w, h) * 0.55
            let circle = CGRect(x: (w-diameter)/2, y: (h-diameter)/2, width: diameter, height: diameter)
            context.stroke(Path(ellipseIn: circle), with: .color(.cyan), lineWidth: 2)
            var cross = Path()
            cross.move(to: CGPoint(x: w/2, y: 0)); cross.addLine(to: CGPoint(x: w/2, y: h))
            cross.move(to: CGPoint(x: 0, y: h/2)); cross.addLine(to: CGPoint(x: w, y: h/2))
            context.stroke(cross, with: .color(.cyan.opacity(0.8)), lineWidth: 1)
            let margin: CGFloat = 6, length: CGFloat = 18
            var corners = Path()
            for (x, dx) in [(margin, length), (w-margin, -length)] {
                for (y, dy) in [(margin, length), (h-margin, -length)] {
                    corners.move(to: CGPoint(x: x+dx, y: y)); corners.addLine(to: CGPoint(x: x, y: y)); corners.addLine(to: CGPoint(x: x, y: y+dy))
                }
            }
            context.stroke(corners, with: .color(.cyan), lineWidth: 3)
            func label(_ text: String, _ x: CGFloat, _ y: CGFloat) {
                context.draw(Text(text).font(.system(size: 12, weight: .bold)).foregroundColor(.white), at: CGPoint(x: x, y: y))
            }
            label("TOP", w/2, 18); label("BOTTOM", w/2, h-18)
            label("LEFT", 24, h/2-14); label("RIGHT", w-28, h/2-14)
        }
        .background(Color(white: 0.08))
        .overlay(alignment: .topLeading) {
            Text("SIMULATOR · TEST IMAGE").font(.caption2.bold()).padding(5)
                .foregroundColor(.black).background(Color.yellow).padding(10)
        }
        .accessibilityIdentifier("simulatorPattern")
    }
}
