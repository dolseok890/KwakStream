import SwiftUI
import AVFoundation
import UIKit

@main struct KwakStreamApp: App {
    var body: some Scene {
        WindowGroup { CameraScreen().persistentSystemOverlays(.hidden) }
    }
}

/// AVCaptureSession operations are serialized off the main thread.
/// On Simulator, no AVCaptureDevice is ever opened: only a UI test pattern is used.
final class CameraModel: ObservableObject {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "live.kwak.stream.camera")
    private var input: AVCaptureDeviceInput?
    private var position: AVCaptureDevice.Position = .back
    private var shouldRun = false
    private var observers: [NSObjectProtocol] = []

    @Published private(set) var status = "카메라 준비 중"
    @Published private(set) var cameraSide = "후면"
    @Published private(set) var zoomFactors: [Double] = [1.0]
    @Published private(set) var selectedZoom = 1.0
    @Published private(set) var hasError = false

    init() {
        #if !targetEnvironment(simulator)
        observers.append(NotificationCenter.default.addObserver(
            forName: AVCaptureSession.runtimeErrorNotification, object: session, queue: nil
        ) { [weak self] note in
            let error = note.userInfo?[AVCaptureSessionErrorKey] as? NSError
            self?.fail("E_RUNTIME", error?.localizedDescription ?? "세션 실행 오류")
        })
        observers.append(NotificationCenter.default.addObserver(
            forName: AVCaptureSession.wasInterruptedNotification, object: session, queue: nil
        ) { [weak self] _ in self?.fail("E_INTERRUPTED", "카메라 사용이 일시 중단되었습니다") })
        observers.append(NotificationCenter.default.addObserver(
            forName: AVCaptureSession.interruptionEndedNotification, object: session, queue: nil
        ) { [weak self] _ in
            self?.queue.async { [weak self] in
                guard let self = self, self.shouldRun else { return }
                if !self.session.isRunning { self.session.startRunning() }
            }
        })
        #endif
    }

    deinit { observers.forEach(NotificationCenter.default.removeObserver) }

    private func publish(_ text: String, error: Bool = false,
                         side: String? = nil, zooms: [Double]? = nil, zoom: Double? = nil) {
        DispatchQueue.main.async {
            self.status = text
            self.hasError = error
            if let side = side { self.cameraSide = side }
            if let zooms = zooms { self.zoomFactors = zooms }
            if let zoom = zoom { self.selectedZoom = zoom }
        }
    }

    private func fail(_ code: String, _ detail: String) {
        NSLog("[KwakStream][%@] %@", code, detail)
        publish("\(code): \(detail)", error: true)
    }

    func start() {
        #if targetEnvironment(simulator)
        queue.async {
            self.shouldRun = true
            let side = self.position == .front ? "전면" : "후면"
            self.publish("SIMULATOR · 카메라 [\(side)] (테스트 화면)", side: side, zooms: [1], zoom: 1)
        }
        #else
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            queue.async {
                self.shouldRun = true
                if self.input == nil { self.configureOnQueue(target: self.position) }
                else if !self.session.isRunning { self.session.startRunning() }
            }
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted { self?.start() }
                else { self?.fail("E_PERMISSION", "카메라 권한이 거부되었습니다") }
            }
        default:
            fail("E_PERMISSION", "설정에서 카메라 권한을 허용해 주세요")
        }
        #endif
    }

    func stop() {
        queue.async {
            self.shouldRun = false
            #if !targetEnvironment(simulator)
            if self.session.isRunning { self.session.stopRunning() }
            #endif
        }
    }

    func switchCamera() {
        queue.async {
            let target: AVCaptureDevice.Position = self.position == .back ? .front : .back
            #if targetEnvironment(simulator)
            self.position = target
            let side = target == .front ? "전면" : "후면"
            NSLog("[KwakStream][SIM_SWITCH] %@", side)
            self.publish("SIMULATOR · 카메라 전환: [\(side)] (가상)", side: side, zooms: target == .front ? [1, 1.3] : [1], zoom: 1)
            #else
            if self.shouldRun { self.configureOnQueue(target: target) }
            else { self.position = target; self.publish("카메라 준비 중", side: target == .front ? "전면" : "후면") }
            #endif
        }
    }

    func setZoom(_ zoom: Double) {
        queue.async {
            #if targetEnvironment(simulator)
            let valid = self.position == .front ? [1.0, 1.3] : [1.0]
            guard valid.contains(where: { abs($0 - zoom) < 0.005 }) else { self.fail("E_ZOOM_RANGE", "지원되지 않는 가상 줌"); return }
            self.publish("SIMULATOR · 카메라 [\(self.position == .front ? "전면" : "후면")] (가상)", zoom: zoom)
            #else
            guard let device = self.input?.device else { self.fail("E_ZOOM", "카메라가 준비되지 않았습니다"); return }
            let factor = CGFloat(zoom)
            guard self.zoomChoices(for: device).contains(where: { abs($0 - zoom) < 0.005 }) else {
                self.fail("E_ZOOM_RANGE", "지원되지 않는 줌 값: \(zoom)")
                return
            }
            do {
                try device.lockForConfiguration()
                defer { device.unlockForConfiguration() }
                device.videoZoomFactor = factor
                self.publish("\(self.position == .front ? "전면" : "후면") · API 줌 \(Self.zoomText(zoom))", zoom: zoom)
            } catch {
                self.fail("E_ZOOM", error.localizedDescription)
            }
            #endif
        }
    }

    static func zoomText(_ value: Double) -> String {
        if abs(value.rounded() - value) < 0.005 { return "\(Int(value.rounded()))×" }
        return String(format: "%.2g×", value)
    }

    #if !targetEnvironment(simulator)
    /// Only actual device-reported virtual-camera switchover points (and 1×).
    /// These are raw AVFoundation factors, not invented 0.6/2/3× lens labels.
    private func zoomChoices(for device: AVCaptureDevice) -> [Double] {
        let lower = Double(device.minAvailableVideoZoomFactor)
        let upper = Double(min(device.maxAvailableVideoZoomFactor, device.activeFormat.videoMaxZoomFactor))
        if device.position == .front {
            guard lower <= upper else { return [] }
            let wide = lower
            let normal = min(upper, wide * 1.3)
            return normal - wide >= 0.05 ? [wide, normal] : [wide]
        }
        let candidates = [1.0] + device.virtualDeviceSwitchOverVideoZoomFactors.map { $0.doubleValue }
        return Array(Set(candidates.filter { $0 >= lower - 0.0001 && $0 <= upper + 0.0001 }))
            .sorted()
    }

    private func findDevice(_ target: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let types: [AVCaptureDevice.DeviceType]
        if target == .front {
            types = [.builtInWideAngleCamera, .builtInTrueDepthCamera]
        } else {
            types = [.builtInTripleCamera, .builtInDualWideCamera, .builtInDualCamera, .builtInWideAngleCamera]
        }
        let discovery = AVCaptureDevice.DiscoverySession(deviceTypes: types, mediaType: .video, position: target)
        return types.compactMap { type in discovery.devices.first(where: { $0.deviceType == type }) }.first
    }

    private func configureOnQueue(target: AVCaptureDevice.Position) {
        guard shouldRun else { return }
        guard let device = findDevice(target) else {
            fail("E_NO_CAMERA", target == .front ? "전면 카메라 없음" : "후면 카메라 없음")
            return
        }
        let next: AVCaptureDeviceInput
        do { next = try AVCaptureDeviceInput(device: device) }
        catch { fail("E_INPUT", error.localizedDescription); return }

        // Never leave a session in an unfinished configuration; restore old input on failure.
        session.beginConfiguration()
        let previous = input
        if let previous = previous { session.removeInput(previous) }
        guard session.canAddInput(next) else {
            if let previous = previous, session.canAddInput(previous) { session.addInput(previous) }
            session.commitConfiguration()
            fail("E_SWITCH", "카메라 입력 전환 실패 (기존 입력 유지)")
            return
        }
        session.addInput(next)
        input = next
        if session.canSetSessionPreset(.hd1280x720) { session.sessionPreset = .hd1280x720 }
        else if session.canSetSessionPreset(.high) { session.sessionPreset = .high }
        session.commitConfiguration()

        position = target
        let side = target == .front ? "전면" : "후면"
        let choices = zoomChoices(for: device)
        let first = choices.contains(1) ? 1.0 : (choices.first ?? Double(device.minAvailableVideoZoomFactor))
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = CGFloat(first)
            device.unlockForConfiguration()
        } catch {
            fail("E_ZOOM_RESET", error.localizedDescription)
        }
        if !session.isRunning { session.startRunning() }
        if session.isRunning {
            NSLog("[KwakStream][CAM_OK] %@ zooms=%@", side, String(describing: choices))
            publish("\(side) 카메라 · API 줌 전환점", side: side, zooms: choices, zoom: first)
        } else {
            fail("E_START", "카메라 세션을 시작하지 못했습니다")
        }
    }
    #endif
}

struct CameraScreen: View {
    @StateObject private var model = CameraModel()
    @StateObject private var microphone = MicrophoneMeter()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var colorScheme
    @State private var frontMenuExpanded = false
    @State private var batteryLevel: Float = -1
    @State private var batteryState: UIDevice.BatteryState = .unknown

    private func frontLensIcon(_ symbol: String, label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        return Button(action: action) {
            Image(systemName: symbol)
                .foregroundColor(selected ? .black : .white)
                .frame(width: 44, height: 44)
                .background(selected ? Color.white : Color.black.opacity(0.65))
                .clipShape(Circle())
        }.buttonStyle(.plain).accessibilityLabel(label)
    }
    private func panelPlaceholder(_ icon: String, _ title: String, size: CGFloat) -> some View {
        VStack(spacing: 0) {
            Image(systemName: icon).font(.system(size: size * 0.52)).frame(width: size, height: size)
                .foregroundColor(title == "LIVE" ? .white : .primary.opacity(0.45))
                .background(title == "LIVE" ? Color(red: 211/255, green: 47/255, blue: 47/255) : Color.primary.opacity(0.12)).clipShape(Circle())
            Text(title).font(.caption2)
        }.accessibilityLabel("\(title) 준비 중")
    }
    var body: some View {
        GeometryReader { geometry in
            // Explicit, measured rectangle: the UI test reads THIS element rather
            // than an accessibility container expanded by padding/overlays.
            let landscape = geometry.size.width > geometry.size.height
            let aspect: CGFloat = landscape ? (16.0 / 9.0) : (9.0 / 16.0)
            let fittedWidth = max(0, min(geometry.size.width - 88, max(1, geometry.size.height - 84) * aspect))
            let fittedHeight = fittedWidth / aspect
            let controlSize = max(36, min(56, (geometry.size.height - 114) / 5))
            ZStack {
                Color.black
                ZStack {
                    #if targetEnvironment(simulator)
                    SimulatorPattern().accessibilityHidden(true)
                    #else
                    CameraPreview(session: model.session).accessibilityHidden(true)
                    #endif
                    PreviewGuides().allowsHitTesting(false).accessibilityHidden(true)
                    Color.clear
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("실제 미리보기 영역")
                        .accessibilityIdentifier("previewArea")
                        .accessibilityValue(String(format: "%.2f x %.2f", fittedWidth, fittedHeight))
                }
                .frame(width: fittedWidth, height: fittedHeight)
                .offset(x: -44, y: -14)
                HStack {
                    Text("대기 · \(model.cameraSide)")
                    Spacer()
                    BatteryIndicator(level: batteryLevel, state: batteryState)
                }
                .font(.caption2).foregroundColor(.white).padding(.horizontal, 12)
                .frame(height: 28)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(.trailing, 88)
                AudioMeterView(meter: microphone)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                    .padding(.leading, 8).padding(.bottom, 5).padding(.trailing, 88)
                VStack(spacing: 6) {
                        Text(model.status)
                            .font(.caption)
                            .multilineTextAlignment(.center)
                            .foregroundColor(.white)
                            .padding(6)
                            .background(Color.black.opacity(0.75))
                            .accessibilityIdentifier("cameraStatus")
                        if model.cameraSide == "전면", let wide = model.zoomFactors.min(), let normal = model.zoomFactors.max() {
                            let hasTwo = normal - wide >= 0.05
                            let isWide = hasTwo && abs(model.selectedZoom - wide) < abs(model.selectedZoom - normal)
                            HStack(spacing: 6) {
                                frontLensIcon(isWide ? "arrow.up.left.and.arrow.down.right" : "viewfinder", label: isWide ? "넓게 선택됨, 목록" : "일반 선택됨, 목록", selected: true) { frontMenuExpanded.toggle() }
                                if frontMenuExpanded && hasTwo {
                                    frontLensIcon(isWide ? "viewfinder" : "arrow.up.left.and.arrow.down.right", label: isWide ? "일반" : "넓게", selected: false) {
                                        model.setZoom(isWide ? normal : wide)
                                        frontMenuExpanded = false
                                    }
                                }
                            }
                            if frontMenuExpanded && !hasTwo { Text("다른 전면 화각을 지원하지 않습니다").font(.caption).foregroundColor(.white) }
                        }
                        HStack(spacing: 8) {
                            if model.cameraSide != "전면" {
                                ForEach(model.zoomFactors, id: \.self) { zoom in
                                    Button { model.setZoom(zoom) } label: {
                                        let selected = abs(model.selectedZoom - zoom) < 0.01
                                        Text(CameraModel.zoomText(zoom).replacingOccurrences(of: "×", with: ""))
                                            .foregroundColor(selected ? .black : .white)
                                            .frame(width: 44, height: 44)
                                            .background(selected ? Color.white : Color.black.opacity(0.65))
                                            .clipShape(Circle())
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityIdentifier("zoom_\(zoom)")
                                }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .padding(.horizontal, 5)
                    }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.trailing, 88)
                .padding(.bottom, 4)
            }
            .overlay(alignment: .trailing) {
                VStack(spacing: 4) {
                    Button { model.switchCamera() } label: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: controlSize * 0.52))
                            .frame(width: controlSize, height: controlSize)
                            .foregroundColor(.white)
                            .background(Color(red: 25/255, green: 118/255, blue: 210/255))
                            .clipShape(Circle())
                    }.accessibilityIdentifier("switchCamera")
                    Text("카메라").font(.caption2)
                    panelPlaceholder("mic", "마이크", size: controlSize)
                    panelPlaceholder("circle.fill", "LIVE", size: controlSize + 8)
                    panelPlaceholder("record.circle", "녹화", size: controlSize)
                    panelPlaceholder("square.3.layers.3d", "오버레이", size: controlSize)
                }
                .frame(width: 88)
                .frame(maxHeight: .infinity)
                .background(colorScheme == .dark ? Color(red: 24/255, green: 26/255, blue: 30/255) : Color(red: 246/255, green: 246/255, blue: 249/255))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
        }
        .background(Color.black)
        .onChange(of: model.cameraSide) { _ in frontMenuExpanded = false }
        .onAppear {
            UIDevice.current.isBatteryMonitoringEnabled = true
            batteryLevel = UIDevice.current.batteryLevel
            batteryState = UIDevice.current.batteryState
            model.start()
            microphone.startIfAuthorized()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIDevice.batteryLevelDidChangeNotification)) { _ in
            batteryLevel = UIDevice.current.batteryLevel
        }
        .onReceive(NotificationCenter.default.publisher(for: UIDevice.batteryStateDidChangeNotification)) { _ in
            batteryState = UIDevice.current.batteryState
        }
        .onDisappear { model.stop(); microphone.stop() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { model.start(); microphone.startIfAuthorized() }
            else { model.stop(); microphone.stop() }
        }
    }
}

struct AudioMeterView: View {
    @ObservedObject var meter: MicrophoneMeter
    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            if !meter.message.isEmpty {
                Button(meter.message) { meter.requestAndStart() }.font(.caption2).foregroundColor(.white)
            } else {
                HStack(spacing: 3) {
                    Image(systemName: "mic").foregroundColor(.white)
                    VStack(alignment: .leading, spacing: 1) {
                        ForEach(Array((meter.levels.isEmpty ? [-60.0] : meter.levels).prefix(2).enumerated()), id: \.offset) { item in
                            HStack(spacing: 2) {
                                Text(meter.levels.count < 2 ? "M" : (item.offset == 0 ? "L" : "R")).foregroundColor(.white)
                                HStack(spacing: 1) {
                                    ForEach(0..<20, id: \.self) { i in
                                        let threshold = -60.0 + 60.0 * Float(i + 1) / 20.0
                                        let color: Color = i >= 19 ? .red : (threshold > -12 ? .yellow : .green)
                                        Rectangle().fill(item.element >= threshold ? color : Color.gray.opacity(0.5)).frame(width: 4, height: 6)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("−24          −12        0 dBFS").foregroundColor(.gray)
            }
        }.font(.system(size: 9)).frame(width: 135, alignment: .leading)
        .accessibilityElement(children: .contain)
    }
}

struct BatteryIndicator: View {
    let level: Float
    let state: UIDevice.BatteryState
    private var percent: Int? { level >= 0 ? min(100, max(0, Int((level * 100).rounded()))) : nil }
    private var charging: Bool { state == .charging || state == .full }
    private var ink: Color {
        guard let percent = percent else { return .white }
        if percent <= 10 { return Color(red: 211/255, green: 47/255, blue: 47/255) }
        if percent <= 20 { return Color(red: 251/255, green: 192/255, blue: 45/255) }
        return .white
    }
    var body: some View {
        HStack(spacing: 1) {
            if charging { Image(systemName: "bolt.fill").foregroundColor(Color(red: 56/255, green: 142/255, blue: 60/255)).font(.system(size: 10)) }
            Text(percent.map(String.init) ?? "—").foregroundColor(ink).font(.system(size: 11, weight: .medium))
        }
        .frame(width: 48, height: 22)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white, lineWidth: 1.5))
        .overlay(alignment: .trailing) { Rectangle().fill(Color.white).frame(width: 3, height: 8).offset(x: 3) }
        .padding(.trailing, 3)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(percent.map { "배터리 \($0)%" + (charging ? ", 충전 중" : "") } ?? "배터리 잔량 확인 불가")
    }
}

struct PreviewGuides: View {
    var body: some View {
        GeometryReader { geo in
            Path { path in
                let w = geo.size.width, h = geo.size.height
                for n in 1...2 {
                    let x = w * CGFloat(n) / 3, y = h * CGFloat(n) / 3
                    path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: h))
                    path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: w, y: y))
                }
                path.move(to: CGPoint(x: w/2 - 8, y: h/2)); path.addLine(to: CGPoint(x: w/2 + 8, y: h/2))
                path.move(to: CGPoint(x: w/2, y: h/2 - 8)); path.addLine(to: CGPoint(x: w/2, y: h/2 + 8))
                path.addRect(CGRect(x: w*0.05, y: h*0.05, width: w*0.9, height: h*0.9))
            }.stroke(Color.white.opacity(0.55), lineWidth: 1)
        }
    }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.preview.session = session
        view.preview.videoGravity = .resizeAspect // FIT_CENTER, never stretch/crop
        return view
    }
    func updateUIView(_ uiView: PreviewView, context: Context) { uiView.setNeedsLayout() }
}

final class PreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard let connection = preview.connection, connection.isVideoOrientationSupported,
              let orientation = window?.windowScene?.interfaceOrientation else { return }
        switch orientation {
        case .portrait: connection.videoOrientation = .portrait
        case .portraitUpsideDown: connection.videoOrientation = .portraitUpsideDown
        case .landscapeLeft: connection.videoOrientation = .landscapeLeft
        case .landscapeRight: connection.videoOrientation = .landscapeRight
        default: break
        }
    }
}
