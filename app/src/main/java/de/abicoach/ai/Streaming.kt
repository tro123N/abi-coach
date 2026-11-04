package de.abicoach.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs a request whose answer arrives piece by piece (speech). [read] gets the response on OkHttp's own thread;
 * cancelling the coroutine cancels the request at once, so "Stopp" really stops the download.
 */
internal suspend fun <T> OkHttpClient.streamCall(request: Request, read: (Response) -> T): T =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use(read) }
                    if (!cont.isActive) return
                    result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
                }
            },
        )
    }

/** Hands out 16-bit PCM in whole samples: an odd byte at the end of one piece waits for the next one. */
internal class SampleAligner(private val onPcm: (ByteArray) -> Unit) {
    private var carry = -1
    private val all = java.io.ByteArrayOutputStream()

    fun add(bytes: ByteArray, from: Int = 0, count: Int = bytes.size - from) {
        if (count <= 0) return
        val piece = java.io.ByteArrayOutputStream(count + 1)
        if (carry >= 0) piece.write(carry)
        piece.write(bytes, from, count)
        var pcm = piece.toByteArray()
        carry = -1
        if (pcm.size % 2 == 1) {
            carry = pcm.last().toInt() and 0xFF
            pcm = pcm.copyOf(pcm.size - 1)
        }
        if (pcm.isEmpty()) return
        all.write(pcm)
        onPcm(pcm)
    }

    /** Everything handed out so far. */
    fun result(): ByteArray = all.toByteArray()

    /** Reads raw PCM from [input] until it ends. */
    fun pump(input: InputStream) {
        val buffer = ByteArray(9_600)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            add(buffer, 0, read)
        }
    }
}
