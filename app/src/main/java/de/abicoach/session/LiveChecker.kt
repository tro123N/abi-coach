package de.abicoach.session

import de.abicoach.ai.AiException
import de.abicoach.ai.AiHub
import de.abicoach.data.LiveError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** What the live check is doing, shown on the Flüstern/Coach card so problems are never silent. */
data class CheckerStatus(
    val label: String = "",
    val checks: Int = 0,
    val lastOkAt: Long = 0,
    val failure: String? = null,
    /** "Audio" (the AI hears the recording) or "Text" (checks the live transcript). */
    val mode: String = "Audio",
    /** Average time from the end of an utterance to the answer. */
    val avgMs: Int = 0,
    val failures: Int = 0,
)

/**
 * The "second ear": checks what the student says while he speaks.
 *
 * Main path: the [SpeechSegmenter] cuts the microphone at natural pauses and the AI listens to each utterance itself
 * (transcript and mistakes in one call). This does not depend on the live transcription and avoids false hints
 * from misheard words. If listening fails several times in a row, the checker falls back for a minute to the text of
 * the live transcription (sentence by sentence), then tries listening again.
 */
class LiveChecker(
    private val hub: AiHub,
    private val scope: CoroutineScope,
    private val context: String,
    private val onErrors: (List<LiveError>) -> Unit,
    private val onStatus: (CheckerStatus) -> Unit,
    /** What the ear understood in an utterance (used as a backup transcript and for the coach's "say it again"). */
    private val onHeard: (SpeechSegment, String) -> Unit = { _, _ -> },
    /** False when the AI cannot listen itself (Claude): then the live transcript of the phone is checked. */
    private val listen: Boolean = true,
) {
    private val lock = Any()
    private val segments = ArrayDeque<SpeechSegment>()
    private val queue = ArrayDeque<String>()
    private val seen = ArrayDeque<List<String>>()
    private val recentQuotes = HashMap<String, Long>()
    private var interim = ""
    private var interimChangedAt = 0L
    private var job: Job? = null
    private val inFlight = AtomicInteger(0)
    private var lastHeard = ""

    @Volatile private var lastCallAt = 0L

    @Volatile private var pausedUntil = 0L

    @Volatile private var audioFailuresInRow = 0

    @Volatile private var textUntil = 0L

    @Volatile private var totalMs = 0L

    @Volatile private var status = CheckerStatus(label = hub.fastLabel(), mode = if (listen) "Audio" else "Text")

    private val audioMode: Boolean get() = listen && System.currentTimeMillis() >= textUntil

    /** One utterance of the student, cut at a pause. */
    fun feedSegment(segment: SpeechSegment) = synchronized(lock) {
        segments.addLast(segment)
        // If the network is slow, the oldest utterances are dropped: a late hint is worth less than a current one.
        while (segments.size > 3) segments.removeFirst()
    }

    /** Text the transcriber has finished (a sentence or a whole turn). Used only when listening does not work. */
    fun feed(text: String) = synchronized(lock) {
        interim = ""
        enqueueSentences(text, includeLast = true)
    }

    /** The transcriber's running guess. Complete sentences are checked at once, the rest once it stays unchanged. */
    fun feedInterim(text: String) = synchronized(lock) {
        val clean = text.trim()
        if (clean != interim) {
            interim = clean
            interimChangedAt = System.currentTimeMillis()
        }
        enqueueSentences(clean, includeLast = false)
    }

    fun start() {
        onStatus(status)
        job =
            scope.launch {
                while (isActive) {
                    delay(200)
                    promoteStableInterim()
                    launchNext()
                }
            }
    }

    fun stop() {
        job?.cancel()
    }

    private fun promoteStableInterim() =
        synchronized(lock) {
            if (interim.isNotBlank() && System.currentTimeMillis() - interimChangedAt >= STABLE_MS) {
                enqueueSentences(interim, includeLast = true)
                interim = ""
            }
        }

    private fun enqueueSentences(text: String, includeLast: Boolean) {
        val sentences = splitSentences(text)
        // Without the last piece: it may still be growing.
        val usable = if (includeLast) sentences else sentences.dropLast(1)
        usable.forEach { sentence ->
            val words = Prompts.normalize(sentence).split(" ").filter { it.isNotBlank() }
            if (words.size < 3 || alreadySeen(words)) return@forEach
            seen.addLast(words)
            while (seen.size > 40) seen.removeFirst()
            queue.addLast(sentence.trim())
            while (queue.size > 12) queue.removeFirst()
        }
    }

    private fun alreadySeen(words: List<String>): Boolean {
        val set = words.toSet()
        return seen.any { old ->
            val other = old.toSet()
            val common = set.intersect(other).size
            common >= 0.8 * minOf(set.size, other.size) && minOf(set.size, other.size) >= 3
        }
    }

    /**
     * Listening to the utterances comes first. Alongside, every finished sentence of the live transcript is checked at
     * once (it is there before the pause that ends the utterance): from that fast lane only clear, serious mistakes
     * count, so a misheard word cannot trigger a wrong hint – the small ones come from listening.
     */
    private fun launchNext() {
        val now = System.currentTimeMillis()
        if (now < pausedUntil || inFlight.get() >= MAX_IN_FLIGHT) return
        if (audioMode) {
            val segment = synchronized(lock) { segments.removeFirstOrNull() }
            if (segment != null) {
                launchAudio(segment)
                return
            }
            if (now - lastCallAt < FAST_GAP_MS || inFlight.get() >= MAX_IN_FLIGHT - 1) return
            val sentence = synchronized(lock) { queue.removeLastOrNull()?.also { queue.clear() } } ?: return
            lastCallAt = now
            launchText(sentence, seriousOnly = true)
            return
        }
        if (now - lastCallAt < MIN_GAP_MS) return
        val text =
            synchronized(lock) {
                segments.clear()
                if (queue.isEmpty()) return
                val parts = ArrayList<String>()
                while (queue.isNotEmpty() && parts.size < 3) parts += queue.removeFirst()
                parts.joinToString(" ")
            }
        lastCallAt = now
        launchText(text, seriousOnly = false)
    }

    private fun launchAudio(segment: SpeechSegment) {
        inFlight.incrementAndGet()
        val before = synchronized(lock) { lastHeard }
        scope.launch {
            try {
                val result = EarCheck.check(hub, segment, context, before)
                audioFailuresInRow = 0
                synchronized(lock) { if (result.transcript.isNotBlank()) lastHeard = (lastHeard + " " + result.transcript).takeLast(600) }
                val latency = System.currentTimeMillis() - segment.endMs
                ok(latency, "Audio")
                if (result.transcript.isNotBlank()) onHeard(segment, result.transcript)
                deliver(result.errors)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                audioFailuresInRow++
                if (e is AiException && e.code == 429) pausedUntil = System.currentTimeMillis() + 20_000
                if (audioFailuresInRow >= 3) {
                    // Listening is broken right now: use the live transcript for a minute, then try again.
                    textUntil = System.currentTimeMillis() + 60_000
                    audioFailuresInRow = 0
                }
                failed(e, if (audioMode) "Audio" else "Text")
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private fun launchText(text: String, seriousOnly: Boolean) {
        inFlight.incrementAndGet()
        val started = System.currentTimeMillis()
        scope.launch {
            try {
                val json = hub.fastJson(Prompts.CHECK_SYSTEM, Prompts.checkRequest(text, context), Prompts.checkSchema)
                val reference = Prompts.normalize(text)
                val all =
                    Prompts.parseErrors(json.optJSONArray("errors")).filter { Prompts.isQuoted(it.quote, reference) }
                        .filter { !seriousOnly || it.severity == "major" || it.severity == "content" }
                if (!seriousOnly) ok(System.currentTimeMillis() - started, "Text")
                deliver(all)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is AiException && e.code == 429) pausedUntil = System.currentTimeMillis() + 20_000
                // The fast lane is a bonus: its failures do not count while listening works.
                if (!seriousOnly) failed(e, "Text")
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    /** Each mistake is reported once, even if both lanes (or two overlapping checks) found it. */
    private fun deliver(errors: List<LiveError>) {
        val fresh =
            synchronized(lock) {
                val t = System.currentTimeMillis()
                recentQuotes.entries.removeAll { t - it.value > 90_000 }
                errors.filter { e ->
                    val quote = "q:" + Prompts.normalize(e.quote)
                    val fix = "c:" + Prompts.normalize(e.correction)
                    val known = recentQuotes.containsKey(quote) || recentQuotes.containsKey(fix)
                    recentQuotes[quote] = t
                    recentQuotes[fix] = t
                    !known
                }
            }
        if (fresh.isNotEmpty()) onErrors(fresh)
    }

    private fun ok(latency: Long, mode: String) {
        totalMs += latency.coerceIn(0, 60_000)
        val checks = status.checks + 1
        status = status.copy(label = hub.fastLabel(), checks = checks, lastOkAt = System.currentTimeMillis(), failure = null, mode = mode, avgMs = (totalMs / checks).toInt())
        onStatus(status)
    }

    private fun failed(e: Exception, mode: String) {
        val message = (e.message ?: (e as? AiException)?.let { "Fehler ${it.code}" } ?: e.javaClass.simpleName).take(120)
        status = status.copy(failure = message, mode = mode, failures = status.failures + 1)
        onStatus(status)
    }

    companion object {
        private const val STABLE_MS = 1_300L
        private const val MIN_GAP_MS = 1_000L

        /** The fast lane checks a finished sentence at most this often. */
        private const val FAST_GAP_MS = 600L

        /** Checks running at the same time, so a slow answer does not hold up the next utterance. */
        private const val MAX_IN_FLIGHT = 3

        /** Splits at . ? ! and cuts very long run-on sentences after 16 words (speech often has little punctuation). */
        fun splitSentences(text: String): List<String> =
            text.trim().split(Regex("(?<=[.!?])\\s+")).flatMap { part ->
                part.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.chunked(16).map { it.joinToString(" ") }
            }
    }
}
