import SwiftUI
import AVFoundation
import UIKit

@main struct KwakStreamApp: App {
    var body: some Scene { WindowGroup { CameraScreen().persistentSystemOverlays(.hidden) } }
}

final class CameraModel: ObservableObject {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "kr.kwakstream.camera")
    private var input: AVCaptureDeviceInput?
    private var position: AVCaptureDevice.Position = .back
    @Published var status = "카메라 준비 중"
    private func report(_ text: String) { DispatchQueue.main.async { self.status = text } }
    func start() {
        #if targetEnvironment(simulator)
        report("시뮬레이터에서는 카메라를 사용할 수 없습니다")
        #else
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined: AVCaptureDevice.requestAccess(for: .video) { granted in if granted { self.configure() } else { self.report("설정에서 카메라 권한을 허용해 주세요") } }
        default: report("설정에서 카메라 권한을 허용해 주세요")
        }
        #endif
    }
    func switchCamera() { queue.async { self.position = self.position == .back ? .front : .back; self.configureOnQueue() } }
    private func configure() { queue.async { self.configureOnQueue() } }
    private func configureOnQueue() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else { report("선택한 카메라를 사용할 수 없습니다"); return }
        do {
            let next = try AVCaptureDeviceInput(device: device)
            session.beginConfiguration()
            let old = input
            if let old = old { session.removeInput(old) }
            if session.canAddInput(next) { session.addInput(next); input = next }
            else { if let old = old, session.canAddInput(old) { session.addInput(old) }; session.commitConfiguration(); report("카메라 전환 실패"); return }
            if session.canSetSessionPreset(.hd1280x720) { session.sessionPreset = .hd1280x720 }
            else if session.canSetSessionPreset(.high) { session.sessionPreset = .high }
            session.commitConfiguration()
            if !session.isRunning { session.startRunning() }
            report("카메라 미리보기")
        } catch { report("카메라 오류: \(error.localizedDescription)") }
    }
    func stop() { queue.async { if self.session.isRunning { self.session.stopRunning() } } }
}

struct CameraScreen: View {
    @StateObject private var model = CameraModel()
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        GeometryReader { geometry in
            let aspect: CGFloat = geometry.size.width > geometry.size.height ? 16.0 / 9.0 : 9.0 / 16.0
            ZStack {
                Color.black
                #if targetEnvironment(simulator)
                SimulatorPattern()
                #else
                CameraPreview(session: model.session)
                #endif
            }
            .accessibilityElement(children: .ignore)
            .accessibilityIdentifier("previewArea")
            .aspectRatio(aspect, contentMode: .fit)
            .overlay(alignment: .bottom) {
                VStack(spacing: 8) {
                    Text(model.status)
                        .accessibilityIdentifier("cameraStatus")
                        .foregroundColor(.white)
                        .padding(6)
                        .background(Color.black.opacity(0.65))
                    Button("전후면 전환") { model.switchCamera() }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("switchCamera")
                }.padding(12)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
        }.background(Color.black).onAppear { model.start() }.onDisappear { model.stop() }
        .onChange(of: scenePhase) { phase in if phase == .active { model.start() } else { model.stop() } }
    }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewView { let v = PreviewView(); v.preview.session = session; v.preview.videoGravity = .resizeAspect; return v }
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
