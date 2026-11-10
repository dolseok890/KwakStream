import Foundation
import AVFoundation
import Combine

/// Preview-only meter; audio is not recorded or transmitted.
final class MicrophoneMeter: ObservableObject {
    @Published private(set) var levels: [Float] = []
    @Published private(set) var message = "마이크 허용"
    private let engine = AVAudioEngine()
    private var installed = false
    private var active = false

    func requestAndStart() {
        #if targetEnvironment(simulator)
        message = "시뮬레이터 마이크 미지원"
        #else
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] allowed in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if allowed { self.start() } else { self.message = "마이크 권한 필요" }
            }
        }
        #endif
    }
    func startIfAuthorized() {
        #if !targetEnvironment(simulator)
        if AVAudioSession.sharedInstance().recordPermission == .granted { start() }
        #endif
    }
    private func start() {
        guard !active else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .videoRecording, options: [.defaultToSpeaker, .mixWithOthers])
            try session.setActive(true)
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            guard format.channelCount > 0, format.sampleRate > 0 else { message = "마이크 사용 불가"; return }
            input.installTap(onBus: 0, bufferSize: 2048, format: format) { [weak self] buffer, _ in
                guard let channels = buffer.floatChannelData, buffer.frameLength > 0 else { return }
                let frames = Int(buffer.frameLength)
                let channelCount = min(Int(buffer.format.channelCount), 2)
                let stride = buffer.format.isInterleaved ? Int(buffer.format.channelCount) : 1
                let values = (0..<channelCount).map { channel -> Float in
                    let source = buffer.format.isInterleaved ? channels[0] : channels[channel]
                    var sum: Float = 0
                    for frame in 0..<frames { let value = source[frame * stride + (buffer.format.isInterleaved ? channel : 0)]; sum += value * value }
                    let rms = sqrt(sum / Float(frames))
                    return max(-60, min(0, 20 * log10(max(rms, 0.000001))))
                }
                DispatchQueue.main.async { [weak self] in guard let self = self, self.active else { return }; self.levels = values }
            }
            installed = true
            try engine.start()
            active = true
            message = ""
        } catch {
            if installed { engine.inputNode.removeTap(onBus: 0); installed = false }
            engine.stop(); active = false; levels = []; message = "마이크 사용 불가"
        }
    }
    func stop() {
        active = false
        if installed { engine.inputNode.removeTap(onBus: 0); installed = false }
        engine.stop(); levels = []
    }
}
