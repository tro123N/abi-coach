package de.abicoach.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Wraps raw 16-bit mono PCM into a WAV file in memory (for Gemini, which listens to WAV). */
object Wav {
    fun of(pcm: ByteArray, rate: Int = 16_000): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size).put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size).put(pcm)
        return b.array()
    }
}
