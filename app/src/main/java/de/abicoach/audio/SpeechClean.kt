package de.abicoach.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Cleans synthetic speech before it is played. Cloud voices sometimes add a burst of loud noise (hiss, crackle,
 * static) after the last word. This finds the end of the real speech – the last stretch with a clear voice pitch –
 * and cuts everything after it (keeping the final consonants), then fades both ends so nothing clicks.
 */
object SpeechClean {
    private const val FRAME_MS = 10

    /** How much after the last voiced sound is kept (final consonants such as "s" or "t"). */
    private const val KEEP_MS = 250

    /** Louder than this many times the normal speech level is never speech at the very end. */
    private const val LOUD = 3.0f

    /** Without a clear voice pitch, this loud right after the last word is noise as well. */
    private const val NOISY = 1.5f

    /** The tail without its noise, faded in and out. */
    fun clean(pcm: ByteArray, rate: Int): ByteArray {
        val n = pcm.size / 2
        if (n == 0) return ByteArray(0)
        val end = speechEnd(pcm, rate).coerceIn(1, n)
        val out = pcm.copyOf(end * 2)
        fadeIn(out, rate)
        fadeOut(out, rate)
        return out
    }

    /** The sample after which only noise or silence follows; the full length if nothing needs to go. */
    fun speechEnd(pcm: ByteArray, rate: Int): Int {
        val n = pcm.size / 2
        val frame = rate * FRAME_MS / 1000
        if (frame <= 0) return n
        val frames = n / frame
        if (frames < 30) return n
        val rms = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0.0
            val base = f * frame
            for (i in 0 until frame) {
                val s = sample(pcm, base + i)
                sum += s.toDouble() * s
            }
            rms[f] = sqrt(sum / frame).toFloat()
        }
        // The normal loudness of the voice, measured where no tail noise can be: the first 70 %.
        val peak = rms.maxOrNull() ?: 0f
        val floor = maxOf(120f, peak * 0.01f)
        val body = rms.copyOfRange(0, maxOf(10, frames * 7 / 10)).filter { it > floor }.sorted()
        if (body.size < 10) return n
        val level = body[(body.size * 6 / 10).coerceAtMost(body.lastIndex)]
        // From the end backwards: the last run of three voiced frames (30 ms with a clear pitch) is the last word.
        var last = -1
        var run = 0
        var f = frames - 1
        while (f >= 0) {
            val voiced = rms[f] > level * 0.12f && rms[f] <= level * LOUD && periodicity(pcm, f * frame, rate) > 0.45f
            if (voiced) {
                run++
                if (run >= 3) {
                    last = f + 2
                    break
                }
            } else {
                run = 0
            }
            f--
        }
        if (last < 0) return n
        val keep = KEEP_MS / FRAME_MS
        var end = minOf(frames, last + 1 + keep)
        for (g in last + 1 until end) {
            if (rms[g] > level * NOISY) {
                end = g
                break
            }
        }
        return if (end >= frames) n else end * frame
    }

    /** How strongly a 30 ms piece repeats at a voice pitch (70–400 Hz): near 1 for vowels, low for hiss and noise. */
    private fun periodicity(pcm: ByteArray, at: Int, rate: Int): Float {
        val n = pcm.size / 2
        val win = rate * 30 / 1000
        val minLag = rate / 400
        val maxLag = rate / 70
        if (n < win + maxLag) return 1f
        val start = at.coerceAtMost(n - win - maxLag)
        val step = maxOf(1, rate / 12_000)
        var best = 0.0
        var lag = minLag
        while (lag <= maxLag) {
            var xy = 0.0
            var xx = 0.0
            var yy = 0.0
            var i = 0
            while (i < win) {
                val a = sample(pcm, start + i).toDouble()
                val b = sample(pcm, start + i + lag).toDouble()
                xy += a * b
                xx += a * a
                yy += b * b
                i += step
            }
            if (xx > 0 && yy > 0) {
                val r = xy / sqrt(xx * yy)
                if (r > best) best = r
            }
            lag += step
        }
        return best.toFloat()
    }

    /** 5 ms soft start, in place. */
    fun fadeIn(pcm: ByteArray, rate: Int) = ramp(pcm, rate * 5 / 1000, fromStart = true)

    /** 20 ms soft end, in place. */
    fun fadeOut(pcm: ByteArray, rate: Int) = ramp(pcm, rate * 20 / 1000, fromStart = false)

    private fun ramp(pcm: ByteArray, length: Int, fromStart: Boolean) {
        val n = pcm.size / 2
        val len = minOf(length, n)
        for (k in 0 until len) {
            val gain = 0.5 - 0.5 * cos(PI * k / len)
            val idx = if (fromStart) k else n - 1 - k
            val v = (sample(pcm, idx) * gain).toInt()
            pcm[2 * idx] = (v and 0xFF).toByte()
            pcm[2 * idx + 1] = ((v shr 8) and 0xFF).toByte()
        }
    }

    private fun sample(pcm: ByteArray, i: Int): Int = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
}
