package de.abicoach.session

import de.abicoach.ai.AiHub
import de.abicoach.ai.GeminiRest
import de.abicoach.ai.arr
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.audio.AudioUtil
import de.abicoach.audio.Wav
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import java.io.ByteArrayOutputStream

/** One stretch of the student's speech between two pauses (16 kHz mono 16-bit PCM). */
class SpeechSegment(val pcm: ByteArray, val startMs: Long, val endMs: Long, val voicedMs: Long) {
    val durationMs: Long get() = pcm.size / 32L
}

/**
 * Cuts the microphone stream into utterances at natural pauses, with its own voice detection. The "ear" therefore
 * works even if the live transcription is silent or broken. Fed with the 100 ms chunks of the microphone.
 */
class SpeechSegmenter(private val onSegment: (SpeechSegment) -> Unit) {
    private class Frame(val pcm: ByteArray, val level: Float, val voiced: Boolean)

    private val pre = ArrayDeque<Frame>()
    private val current = ArrayList<Frame>()
    private var active = false
    private var startAt = 0L
    private var silentRun = 0
    private var noise = 0.02f

    /** Loudness of the last chunk (0..1). */
    @Volatile var level = 0f
        private set

    /** The loudest chunk so far – tells whether the microphone hears anything at all. */
    @Volatile var maxLevel = 0f
        private set

    /** When speech was last heard (0 = never). */
    @Volatile var lastVoiceAt = 0L
        private set

    @Volatile var segments = 0
        private set

    @Synchronized
    fun feed(pcm: ByteArray, now: Long) {
        val l = AudioUtil.level(pcm)
        level = l
        if (l > maxLevel) maxLevel = l
        val threshold = maxOf(MIN_LEVEL, noise * 2.6f)
        val voiced = l > threshold
        // The noise floor follows the quiet moments only, so a loud room does not count as speech.
        if (!voiced) noise = (noise * 0.94f + l * 0.06f).coerceIn(0.004f, 0.25f)
        if (voiced) lastVoiceAt = now
        val frame = Frame(pcm, l, voiced)
        if (!active) {
            pre.addLast(frame)
            while (pre.size > PRE_ROLL) pre.removeFirst()
            val run = pre.reversed().takeWhile { it.voiced }.size
            if (run >= START_FRAMES) {
                active = true
                startAt = now - (pre.size - 1) * FRAME_MS
                current.addAll(pre)
                pre.clear()
                silentRun = 0
            }
            return
        }
        current += frame
        silentRun = if (voiced) 0 else silentRun + 1
        if (silentRun >= END_SILENCE) {
            if (voicedMs() >= MIN_VOICED_MS) emit(current.size - silentRun + TAIL_FRAMES, now) else reset()
            return
        }
        if (current.size * FRAME_MS >= MAX_MS) {
            // Cut at the quietest moment of the last 1.5 seconds, so that no word is cut in half.
            val from = maxOf(current.size / 2, current.size - 15)
            val cut = (from until current.size).minByOrNull { current[it].level } ?: (current.size - 1)
            val rest = current.subList(cut + 1, current.size).toList()
            val endAt = now - rest.size * FRAME_MS
            emit(cut + 1, endAt)
            active = true
            startAt = endAt
            current.addAll(rest)
            silentRun = 0
        }
    }

    /** Ends the current utterance at once (the examiner starts talking, the part ends). */
    @Synchronized
    fun flush(now: Long) {
        if (active && voicedMs() >= MIN_VOICED_MS) emit(current.size, now) else reset()
    }

    private fun voicedMs(): Long = current.count { it.voiced } * FRAME_MS

    private fun emit(count: Int, endAt: Long) {
        val used = current.take(count.coerceIn(1, current.size))
        val out = ByteArrayOutputStream(used.size * 3_200)
        used.forEach { out.write(it.pcm) }
        val segment = SpeechSegment(out.toByteArray(), startAt, endAt, used.count { it.voiced } * FRAME_MS)
        reset()
        segments++
        onSegment(segment)
    }

    private fun reset() {
        active = false
        current.clear()
        silentRun = 0
    }

    companion object {
        private const val FRAME_MS = 100L
        private const val PRE_ROLL = 3
        private const val START_FRAMES = 2
        /** 0.4 s of silence ends an utterance: quick enough for a hint in the next breath. */
        private const val END_SILENCE = 4
        private const val TAIL_FRAMES = 2
        private const val MIN_VOICED_MS = 500L

        /** Long speech without a pause is cut after 4.5 s at its quietest moment, so the check never lags far behind. */
        private const val MAX_MS = 4_500L
        private const val MIN_LEVEL = 0.03f
    }
}

/**
 * The ear: the AI listens to one utterance of the student and writes down what he said (with his mistakes) and
 * which clear mistakes it contains – in one quick call. Judging the real audio avoids false hints that come from
 * a misheard transcript.
 */
object EarCheck {
    data class Result(val transcript: String, val errors: List<LiveError>, val by: String, val ms: Long)

    val SYSTEM =
        """
You are the live "second ear" of an English teacher. A German student is speaking English while practising for the oral Abitur exam (North Rhine-Westphalia). You get one short recording of what he just said (cut at a pause), the context and what he said just before.
1. transcript: write down exactly what you hear, word for word, INCLUDING all his grammar and vocabulary mistakes. Do not correct anything. Leave out fillers (um, uh, er). If the recording is silent, unclear or not English speech, return an empty transcript and no errors.
2. errors: only clear mistakes you are sure about: tenses, verb forms, third person -s, prepositions, articles, singular/plural, pronouns, word order, wrong words and collocations, German-English false friends, and statements about the topic that are factually wrong. Do NOT report the accent, style preferences, acceptable British or American variants, words cut off by the end of the recording, or mistakes he corrects himself right away (judge only his final version).
For each error:
- quote: the exact wrong words from your transcript, copied character for character, at most 12 words
- correction: the corrected words
- explanation: a very short reason in German, at most 12 words
- category: one of ${Knowledge.ERROR_CATEGORIES.keys.joinToString(", ")}
- severity: "major" for a basic error a top student would not make or one that makes the meaning unclear, "minor" for a small slip, "content" for a factually wrong statement
- rule: the rule behind the mistake in at most five English words, always worded the same way for the same kind of mistake (e.g. "third person -s", "since vs for", "present perfect for up to now", "make vs do", "article with general nouns", "false friend: become")
        """.trimIndent()

    private val schema = obj("transcript" to str(), "errors" to arr(Prompts.errorSchema))

    suspend fun check(hub: AiHub, segment: SpeechSegment, context: String, before: String): Result {
        val started = System.currentTimeMillis()
        val intro =
            "Context: ${context.ifBlank { "speaking practice" }}\n" +
                "Said just before: ${before.ifBlank { "(nothing yet)" }.takeLast(400)}\n" +
                "The recording (${segment.durationMs / 100 / 10.0} s) follows."
        val parts = listOf(GeminiRest.textPart(intro), GeminiRest.inlinePart("audio/wav", Wav.of(segment.pcm)))
        val answer = hub.quickListenJson(SYSTEM, parts, schema)
        val transcript = answer.json.optString("transcript").trim()
        val reference = Prompts.normalize(transcript)
        val errors =
            if (reference.isBlank()) {
                emptyList()
            } else {
                Prompts.parseErrors(answer.json.optJSONArray("errors")).filter { Prompts.isQuoted(it.quote, reference) }
            }
        return Result(transcript, errors, answer.by, System.currentTimeMillis() - started)
    }
}
