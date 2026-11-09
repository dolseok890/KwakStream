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
            self.publish("SIMULATOR · 카메라 전환: [\(side)] (가상)", side: side, zooms: [1], zoom: 1)
            #else
            if self.shouldRun { self.configureOnQueue(target: target) }
            else { self.position = target; self.publish("카메라 준비 중", side: target == .front ? "전면" : "후면") }
            #endif
        }
    }

    func setZoom(_ zoom: Double) {
        queue.async {
            #if targetEnvironment(simulator)
            self.publish("SIMULATOR · 1× (실제 렌즈 없음)", zoom: 1)
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
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        GeometryReader { geometry in
            // Explicit, measured rectangle: the UI test reads THIS element rather
            // than an accessibility container expanded by padding/overlays.
            let landscape = geometry.size.width > geometry.size.height
            let aspect: CGFloat = landscape ? (16.0 / 9.0) : (9.0 / 16.0)
            let fittedWidth = min(geometry.size.width, geometry.size.height * aspect)
            let fittedHeight = fittedWidth / aspect
            ZStack {
                Color.black
                ZStack {
                    #if targetEnvironment(simulator)
                    SimulatorPattern().accessibilityHidden(true)
                    #else
                    CameraPreview(session: model.session).accessibilityHidden(true)
                    #endif
                    Color.clear
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("실제 미리보기 영역")
                        .accessibilityIdentifier("previewArea")
                        .accessibilityValue(String(format: "%.2f x %.2f", fittedWidth, fittedHeight))
                }
                .frame(width: fittedWidth, height: fittedHeight)
                .overlay(alignment: .bottom) {
                    VStack(spacing: 6) {
                        Text(model.status)
                            .font(.caption)
                            .multilineTextAlignment(.center)
                            .foregroundColor(.white)
                            .padding(6)
                            .background(Color.black.opacity(0.75))
                            .accessibilityIdentifier("cameraStatus")
                        HStack(spacing: 8) {
                            Button("카메라 전환: [\(model.cameraSide)]") { model.switchCamera() }
                                .accessibilityIdentifier("switchCamera")
                            ForEach(model.zoomFactors, id: \.self) { zoom in
                                Button(CameraModel.zoomText(zoom)) { model.setZoom(zoom) }
                                    .accessibilityIdentifier("zoom_\(zoom)")
                                    .disabled(model.selectedZoom == zoom)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .padding(.horizontal, 5)
                    }
                    .padding(.bottom, 12)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
        }
        .background(Color.black)
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { model.start() } else { model.stop() }
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
