package kr.kwakstream.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.log10
import kotlin.math.sqrt

/** Meter-only capture. No audio is stored or transmitted. */
class MicrophoneMeter(private val onLevels: (List<Float>) -> Unit, private val onError: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var worker: Thread? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    @Synchronized fun start() {
        if (running) return
        try {
            val rate = 44100
            // Prefer a stereo stream; devices may expose stereo even when their source is mono.
            var input: AudioRecord? = null
            var channelCount = 1
            for ((mask, count) in listOf(AudioFormat.CHANNEL_IN_STEREO to 2, AudioFormat.CHANNEL_IN_MONO to 1)) {
                val minBuffer = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuffer <= 0) continue
                val candidate = try {
                    AudioRecord(MediaRecorder.AudioSource.MIC, rate, mask, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 8192))
                } catch (_: Exception) { continue }
                if (candidate.state != AudioRecord.STATE_INITIALIZED) { candidate.release(); continue }
                try { candidate.startRecording() } catch (_: Exception) { candidate.release(); continue }
                if (candidate.recordingState != AudioRecord.RECORDSTATE_RECORDING) { candidate.release(); continue }
                input = candidate
                channelCount = count
                break
            }
            val active = input ?: error("microphone unavailable")
            record = active
            running = true
            val countChannels = channelCount
            worker = Thread {
                val data = ShortArray(2048)
                try {
                    while (running) {
                        val count = active.read(data, 0, data.size)
                        if (count <= 0) { if (running) main.post { onError() }; break }
                        val frames = count / countChannels
                        if (frames == 0) continue
                        val sums = DoubleArray(countChannels)
                        for (frame in 0 until frames) for (channel in 0 until countChannels) {
                            val sample = data[frame * countChannels + channel] / 32768.0
                            sums[channel] += sample * sample
                        }
                        val db = sums.map { sum ->
                            (20.0 * log10(sqrt(sum / frames).coerceAtLeast(0.000001))).toFloat().coerceIn(-60f, 0f)
                        }
                        if (running) main.post { if (running) onLevels(db) }
                    }
                } catch (_: Exception) { if (running) main.post { onError() } }
            }.apply { name = "KwakMicrophoneMeter"; start() }
        } catch (_: Exception) { running = false; record?.release(); record = null; onError() }
    }
    @Synchronized fun stop() {
        running = false
        val input = record
        record = null
        try { input?.stop() } catch (_: Exception) { }
        try { worker?.join(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        input?.release()
        onLevels(emptyList())
    }
}
