package de.abicoach.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Wraps raw 16-bit mono PCM into a WAV file in memory and reads WAV files back. */
object Wav {
    fun of(pcm: ByteArray, rate: Int = 16_000): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size).put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size).put(pcm)
        return b.array()
    }

    /** A 16-bit WAV file (mono or stereo, any rate) → 16-bit mono PCM at [targetRate]; null if it is not such a file. */
    fun toPcm(wav: ByteArray, targetRate: Int): ByteArray? {
        if (wav.size < 44 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        var pos = 12
        var rate = 22_050
        var channels = 1
        var bits = 16
        var data: ByteArray? = null
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4, Charsets.US_ASCII)
            val size = le32(wav, pos + 4)
            val start = pos + 8
            if (id == "fmt " && start + 16 <= wav.size) {
                channels = le16(wav, start + 2).coerceAtLeast(1)
                rate = le32(wav, start + 4)
                bits = le16(wav, start + 14)
            } else if (id == "data") {
                val end = if (size <= 0 || start + size > wav.size) wav.size else start + size
                data = wav.copyOfRange(start, end)
                break
            }
            if (size < 0) return null
            pos = start + size + (size and 1)
        }
        val pcm = data ?: return null
        if (bits != 16 || rate <= 0) return null
        val frames = pcm.size / (2 * channels)
        if (frames == 0) return ByteArray(0)
        val mono = IntArray(frames) { i -> (pcm[2 * i * channels].toInt() and 0xFF) or (pcm[2 * i * channels + 1].toInt() shl 8) }
        if (rate == targetRate) return toBytes(mono)
        val outCount = (frames.toLong() * targetRate / rate).toInt()
        val out =
            IntArray(outCount) { j ->
                val t = j.toDouble() * rate / targetRate
                val i = t.toInt().coerceAtMost(frames - 1)
                val next = (i + 1).coerceAtMost(frames - 1)
                (mono[i] + (mono[next] - mono[i]) * (t - i)).toInt()
            }
        return toBytes(out)
    }

    /**
     * Repairs a recording whose writer was never closed (the app was killed): the lengths in the header are set from
     * the real size of the file. Returns the seconds of sound, 0 if the file is no usable recording.
     */
    fun repair(file: java.io.File, rate: Int = 16_000): Int {
        if (!file.exists() || file.length() <= 44) return 0
        return runCatching {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                val head = ByteArray(44)
                raf.readFully(head)
                if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF") return@use 0
                val data = (raf.length() - 44) and 1L.inv()
                if (le32(head, 40).toLong() != data) {
                    val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    raf.seek(4)
                    raf.write(b.putInt(0, (36 + data).toInt()).array())
                    raf.seek(40)
                    raf.write(b.putInt(0, data.toInt()).array())
                }
                (data / (rate * 2L)).toInt()
            }
        }.getOrDefault(0)
    }

    private fun toBytes(samples: IntArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, v ->
            val s = v.coerceIn(-32768, 32767)
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun le16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int = le16(b, at) or (le16(b, at + 2) shl 16)
}
