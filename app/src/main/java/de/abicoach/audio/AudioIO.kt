package de.abicoach.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

object AudioUtil {
    /** Loudness of a 16-bit PCM chunk, scaled to 0..1 for the level meter. */
    fun level(pcm: ByteArray): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt()
            sum += sample.toDouble() * sample
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count) / 32768.0
        return (rms * 6).toFloat().coerceIn(0f, 1f)
    }

    fun vibrate(context: Context, millis: Long = 160) {
        val vibrator = vibrator(context) ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/** Records the microphone at 16 kHz mono in 100 ms chunks, with echo cancellation where the phone supports it. */
class MicStreamer(private val onChunk: (ByteArray) -> Unit) {
    private var record: AudioRecord? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        val rate = 16_000
        val chunkBytes = rate / 10 * 2
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, chunkBytes * 4),
            )
        if (AcousticEchoCanceler.isAvailable()) {
            echo = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.setEnabled(true) }
        }
        if (NoiseSuppressor.isAvailable()) {
            noise = NoiseSuppressor.create(rec.audioSessionId)?.also { it.setEnabled(true) }
        }
        rec.startRecording()
        record = rec
        job =
            scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(chunkBytes)
                while (isActive) {
                    var filled = 0
                    while (filled < chunkBytes && isActive) {
                        val n = rec.read(buffer, filled, chunkBytes - filled)
                        if (n <= 0) break
                        filled += n
                    }
                    if (filled > 0) onChunk(buffer.copyOf(filled))
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        record?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        record = null
        echo?.release()
        noise?.release()
        echo = null
        noise = null
    }
}

/** Plays the examiner's 24 kHz PCM audio as it streams in. */
class PcmPlayer(private val rate: Int = 24_000) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    @Volatile private var playingUntil = 0L

    fun start() {
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuffer * 2, rate))
                .build()
        track = t
        t.play()
        running = true
        thread =
            Thread({
                while (running) {
                    val chunk = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    t.write(chunk, 0, chunk.size)
                }
            }, "examiner-audio").also { it.start() }
    }

    fun enqueue(pcm: ByteArray) {
        val now = System.currentTimeMillis()
        val durationMs = pcm.size * 1000L / (rate * 2)
        playingUntil = maxOf(playingUntil, now) + durationMs
        queue.offer(pcm)
    }

    /** True while examiner audio is (probably) audible, plus a short tail for room echo. */
    fun isPlaying(now: Long): Boolean = now < playingUntil + 400

    /** Stops the current answer immediately, e.g. when the student interrupts. */
    fun flush() {
        queue.clear()
        playingUntil = 0
        track?.let { t ->
            runCatching {
                t.pause()
                t.flush()
                t.play()
            }
        }
    }

    fun stop() {
        running = false
        queue.clear()
        runCatching { thread?.join(500) }
        track?.let { t ->
            runCatching { t.stop() }
            t.release()
        }
        track = null
    }
}

/** Writes 16 kHz mono PCM into a WAV file for the pronunciation analysis. */
class WavWriter(file: File, private val rate: Int) {
    private val out = RandomAccessFile(file, "rw").apply {
        setLength(0)
        write(ByteArray(44))
    }
    private var dataBytes = 0L
    private var closed = false

    @Synchronized
    fun write(pcm: ByteArray) {
        if (closed) return
        out.write(pcm)
        dataBytes += pcm.size
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        runCatching {
            out.seek(0)
            out.write(header(dataBytes))
        }
        runCatching { out.close() }
    }

    private fun header(length: Long): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt((36 + length).toInt())
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1.toShort())
        b.putShort(1.toShort())
        b.putInt(rate)
        b.putInt(rate * 2)
        b.putShort(2.toShort())
        b.putShort(16.toShort())
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(length.toInt())
        return b.array()
    }
}

/** Puts the phone into call mode (best echo cancellation) and plays through the loudspeaker or a headset. */
class AudioRouting(context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL

    /** With headphones the microphone cannot hear the examiner, so the student may talk over him. */
    @Volatile var usingHeadset = false
        private set

    @Suppress("DEPRECATION")
    fun enter() {
        previousMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val devices = manager.availableCommunicationDevices
            val headsetTypes =
                setOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                )
            val headset = devices.firstOrNull { it.type in headsetTypes }
            usingHeadset = headset != null
            val target = headset ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (target != null) manager.setCommunicationDevice(target)
        } else {
            usingHeadset = headsetConnected()
            manager.isSpeakerphoneOn = !usingHeadset
        }
    }

    @Suppress("DEPRECATION")
    fun exit() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                manager.clearCommunicationDevice()
            } else {
                manager.isSpeakerphoneOn = false
            }
            manager.mode = previousMode
        }
    }

    private fun headsetConnected(): Boolean =
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
}

/** Android's own text-to-speech in British English, for short prompts and "read aloud" buttons. */
class Tts(context: Context) {
    @Volatile private var ready = false
    private val engine: TextToSpeech =
        TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }

    fun say(text: String) {
        if (!ready) return
        engine.setLanguage(Locale.UK)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "abicoach-${System.nanoTime()}")
    }

    fun stop() {
        runCatching { engine.stop() }
    }

    fun shutdown() {
        runCatching {
            engine.stop()
            engine.shutdown()
        }
    }
}
