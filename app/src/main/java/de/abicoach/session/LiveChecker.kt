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
)

/**
 * The "second ear": checks what the student says while the conversation goes on
 * (Claude Haiku when its key is set, otherwise Gemini Flash).
 *
 * It works sentence by sentence and does not wait for the end of a turn: a sentence is checked as soon as it is
 * complete, or when the interim transcript has not changed for a moment. Up to two checks run in parallel, so a
 * hint usually arrives within two or three seconds after the mistake.
 */
class LiveChecker(
    private val hub: AiHub,
    private val scope: CoroutineScope,
    private val context: String,
    private val onErrors: (List<LiveError>) -> Unit,
    private val onStatus: (CheckerStatus) -> Unit,
) {
    private val lock = Any()
    private val queue = ArrayDeque<String>()
    private val seen = ArrayDeque<List<String>>()
    private val recentQuotes = HashMap<String, Long>()
    private var interim = ""
    private var interimChangedAt = 0L
    private var job: Job? = null
    private val inFlight = AtomicInteger(0)

    @Volatile private var lastCallAt = 0L

    @Volatile private var pausedUntil = 0L

    @Volatile private var status = CheckerStatus(label = hub.fastLabel())

    /** Text the transcriber has finished (a sentence or a whole turn). */
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
                    delay(250)
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

    private fun launchNext() {
        val now = System.currentTimeMillis()
        if (now < pausedUntil || inFlight.get() >= 2 || now - lastCallAt < MIN_GAP_MS) return
        val text =
            synchronized(lock) {
                if (queue.isEmpty()) return
                val parts = ArrayList<String>()
                while (queue.isNotEmpty() && parts.size < 3) parts += queue.removeFirst()
                parts.joinToString(" ")
            }
        lastCallAt = now
        inFlight.incrementAndGet()
        scope.launch {
            try {
                val json = hub.fastJson(Prompts.CHECK_SYSTEM, Prompts.checkRequest(text, context), Prompts.checkSchema)
                val reference = Prompts.normalize(text)
                val all = Prompts.parseErrors(json.optJSONArray("errors")).filter { Prompts.isQuoted(it.quote, reference) }
                val fresh =
                    synchronized(lock) {
                        val t = System.currentTimeMillis()
                        recentQuotes.entries.removeAll { t - it.value > 90_000 }
                        all.filter { e -> recentQuotes.put(Prompts.normalize(e.quote), t) == null }
                    }
                status = status.copy(label = hub.fastLabel(), checks = status.checks + 1, lastOkAt = System.currentTimeMillis(), failure = null)
                onStatus(status)
                if (fresh.isNotEmpty()) onErrors(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                if (e.code == 429) pausedUntil = System.currentTimeMillis() + 20_000
                status = status.copy(failure = (e.message ?: "Fehler ${e.code}").take(120))
                onStatus(status)
            } catch (e: Exception) {
                status = status.copy(failure = (e.message ?: "Unbekannter Fehler").take(120))
                onStatus(status)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    companion object {
        private const val STABLE_MS = 1_300L
        private const val MIN_GAP_MS = 1_000L

        /** Splits at . ? ! and cuts very long run-on sentences after 16 words (speech often has little punctuation). */
        fun splitSentences(text: String): List<String> =
            text.trim().split(Regex("(?<=[.!?])\\s+")).flatMap { part ->
                part.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.chunked(16).map { it.joinToString(" ") }
            }
    }
}
