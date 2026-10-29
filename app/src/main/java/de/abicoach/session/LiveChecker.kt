package de.abicoach.session

import de.abicoach.data.LiveError
import de.abicoach.gemini.GeminiException
import de.abicoach.gemini.GeminiRest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "second ear": checks each finished utterance for errors while the conversation goes on.
 * Requests are batched and spaced out so the free tier's per-minute limit is not exceeded.
 */
class LiveChecker(
    private val rest: GeminiRest,
    private val model: String,
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
                    delay(1200)
                    drainInto(batch)
                    val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastCall)
                    if (wait > 0) delay(wait)
                    drainInto(batch)
                    lastCall = System.currentTimeMillis()
                    val text = batch.toString()
                    try {
                        val json =
                            rest.generateJson(
                                model = model,
                                system = Prompts.CHECK_SYSTEM,
                                parts = listOf(GeminiRest.textPart(Prompts.checkRequest(text, context))),
                                schema = Prompts.checkSchema(),
                                temperature = 0.1,
                            )
                        val reference = Prompts.normalize(text)
                        val errors = Prompts.parseErrors(json.optJSONArray("errors")).filter { Prompts.isQuoted(it.quote, reference) }
                        if (errors.isNotEmpty()) onErrors(errors)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: GeminiException) {
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
        const val MIN_INTERVAL_MS = 6_500L
    }
}
