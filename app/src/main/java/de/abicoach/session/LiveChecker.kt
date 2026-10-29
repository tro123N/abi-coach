package de.abicoach.session

import de.abicoach.ai.AiException
import de.abicoach.ai.AiHub
import de.abicoach.data.LiveError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "second ear": checks each finished utterance for errors while the conversation goes on
 * (Claude Haiku when its key is set, otherwise Gemini Flash). Requests are batched and spaced out.
 */
class LiveChecker(
    private val hub: AiHub,
    private val scope: CoroutineScope,
    private val context: String,
    private val onErrors: (List<LiveError>) -> Unit,
) {
    private val queue = Channel<String>(Channel.UNLIMITED)
    private var job: Job? = null

    fun submit(utterance: String) {
        if (utterance.trim().split(Regex("\\s+")).size >= 3) queue.trySend(utterance.trim())
    }

    fun start() {
        job =
            scope.launch {
                var lastCall = 0L
                for (first in queue) {
                    val batch = StringBuilder(first)
                    delay(900)
                    drainInto(batch)
                    val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastCall)
                    if (wait > 0) delay(wait)
                    drainInto(batch)
                    lastCall = System.currentTimeMillis()
                    val text = batch.toString()
                    try {
                        val json = hub.fastJson(Prompts.CHECK_SYSTEM, Prompts.checkRequest(text, context), Prompts.checkSchema)
                        val reference = Prompts.normalize(text)
                        val errors = Prompts.parseErrors(json.optJSONArray("errors")).filter { Prompts.isQuoted(it.quote, reference) }
                        if (errors.isNotEmpty()) onErrors(errors)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AiException) {
                        if (e.code == 429) delay(30_000)
                    } catch (e: Exception) {
                        // A failed live check is not critical: the full debrief checks everything again.
                    }
                }
            }
    }

    private fun drainInto(batch: StringBuilder) {
        while (true) {
            val next = queue.tryReceive().getOrNull() ?: break
            batch.append(' ').append(next)
        }
    }

    fun stop() {
        job?.cancel()
        queue.close()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 4_500L
    }
}
