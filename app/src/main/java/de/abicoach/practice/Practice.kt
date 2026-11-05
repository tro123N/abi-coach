package de.abicoach.practice

import android.content.Context
import de.abicoach.AbiCoachApp
import de.abicoach.ai.GeminiRest
import de.abicoach.ai.arr
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.audio.MicStreamer
import de.abicoach.data.Debrief
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.data.strings
import de.abicoach.memory.Digest
import de.abicoach.session.Prompts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Turns debriefs and course material into vocabulary cards. */
object PracticeFeed {
    fun fromDebrief(d: Debrief, topicId: Int): List<VocabCard> {
        val cards = ArrayList<VocabCard>()
        d.phrases.forEach { p ->
            cards += VocabCard(UUID.randomUUID().toString(), front = p.meaningDe, back = p.phrase, example = p.example, source = CardSource.PHRASE, topic = topicId)
        }
        d.upgrades.forEach { u ->
            cards += VocabCard(UUID.randomUUID().toString(), front = u.original, back = u.upgraded, example = u.why, source = CardSource.UPGRADE, topic = topicId)
        }
        d.errors.filter { it.severity == "major" }.take(4).forEach { e ->
            cards += VocabCard(UUID.randomUUID().toString(), front = e.quote, back = e.correction, example = e.explanation, source = CardSource.ERROR, topic = topicId)
        }
        return cards
    }

    fun fromDigest(d: Digest, limit: Int = 40): List<VocabCard> =
        d.vocabulary.take(limit).map { v ->
            VocabCard(UUID.randomUUID().toString(), front = v.de, back = v.en, example = "", source = CardSource.COURSE, topic = v.topic)
        }
}

/** Numbers about how a presentation was delivered (Vortrags-Coach). */
data class TalkMetrics(
    val seconds: Int,
    val words: Int,
    val wordsPerMinute: Int,
    val fillers: Int,
    val longPauses: Int,
    /** Task number to seconds spoken on it; empty if the tasks could not be found in the transcript. */
    val perTask: List<Pair<Int, Int>>,
    val readingAloud: Boolean,
) {
    companion object {
        private val fillerRegex = Regex("\\b(um+|uh+m*|uhm|erm*|ähm*|äh|hmm+)\\b", RegexOption.IGNORE_CASE)

        /** Target seconds for the three tasks of a full presentation. */
        val TARGET = mapOf(1 to 120, 2 to 240, 3 to 180)

        fun compute(record: SessionRecord, debrief: Debrief): TalkMetrics? {
            val part1 = record.utterances.filter { it.part == 1 && it.speaker == Speaker.STUDENT }
            if (part1.isEmpty() || record.part1Sec <= 0) return null
            val text = part1.joinToString(" ") { it.text }
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            val seconds = record.part1Sec
            val wpm = if (seconds > 0) (words * 60 / seconds) else 0
            val fillers = fillerRegex.findAll(text).count()
            // Utterance times mark when a sentence was finished, so the gap also contains the time it took to say it (~2.5 words/s).
            var pauses = 0
            for (i in 1 until part1.size) {
                val speakingMs = part1[i].text.split(Regex("\\s+")).count { it.isNotBlank() } * 400L
                if (part1[i].atMs - part1[i - 1].atMs - speakingMs > 12_000) pauses++
            }
            // Where each task starts: the utterance that contains the first words the grader named.
            val starts =
                debrief.segments.sortedBy { it.first }.mapNotNull { (number, firstWords) ->
                    val ref = Prompts.normalize(firstWords)
                    if (ref.isBlank()) return@mapNotNull null
                    val index = part1.indexOfFirst { u -> Prompts.isQuoted(firstWords, Prompts.normalize(u.text)) }
                    if (index < 0) null else number to (if (index == 0) 0L else part1[index - 1].atMs)
                }.distinctBy { it.first }.sortedBy { it.second }
            val perTask =
                starts.mapIndexed { i, (number, startMs) ->
                    val endMs = starts.getOrNull(i + 1)?.second ?: (seconds * 1000L)
                    number to ((endMs - startMs) / 1000).toInt().coerceAtLeast(0)
                }
            return TalkMetrics(seconds, words, wpm, fillers, pauses, perTask, debrief.readingAloud)
        }
    }
}

/** Best pronunciation score per word (Aussprache-Labor). */
class PronStore(context: Context) {
    private val file = File(context.filesDir, "pronunciation.json")
    private val _scores = MutableStateFlow(load())
    val scores: StateFlow<Map<String, Int>> = _scores.asStateFlow()

    private fun load(): Map<String, Int> =
        runCatching {
            val o = JSONObject(file.readText())
            o.keys().asSequence().associateWith { o.optInt(it) }
        }.getOrElse { emptyMap() }

    @Synchronized
    fun record(word: String, score: Int) {
        val key = word.lowercase().trim()
        val next = _scores.value + (key to maxOf(score, _scores.value[key] ?: 0))
        _scores.value = next
        runCatching { file.writeText(JSONObject(next as Map<*, *>).toString()) }
    }
}

/** Records a few seconds from the microphone and returns a WAV file in memory. */
class ShortRecorder(private val maxSeconds: Int = 15) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val buffer = ByteArrayOutputStream()
    private var mic: MicStreamer? = null

    @Volatile var level: Float = 0f
        private set

    fun start() {
        buffer.reset()
        mic =
            MicStreamer { pcm ->
                synchronized(buffer) { if (buffer.size() < 16_000 * 2 * maxSeconds) buffer.write(pcm) }
                level = de.abicoach.audio.AudioUtil.level(pcm)
            }.also { it.start(scope) }
    }

    fun stop(): ByteArray {
        mic?.stop()
        mic = null
        val pcm = synchronized(buffer) { buffer.toByteArray() }
        return wav(pcm, 16_000)
    }

    fun release() {
        mic?.stop()
        scope.cancel()
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size).put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size).put(pcm)
        return b.array()
    }
}

/** Gemini listens to one word or sentence and rates the pronunciation. */
object PronunciationCheck {
    data class Result(val score: Int, val heard: String, val issues: List<String>, val tip: String, val by: String)

    private val SYSTEM =
        """
Du bist Aussprachetrainerin für einen deutschen Schüler, der sich auf die mündliche Englisch-Abiturprüfung vorbereitet.
Du hörst eine kurze Aufnahme, in der er das Zielwort oder den Zielsatz spricht. Bewerte nur die Aussprache (britisches oder amerikanisches Englisch sind beide richtig):
- score: 0–100 (90+ wie ein sicherer Sprecher, 70–89 gut verständlich mit kleinen Abweichungen, 50–69 deutlich deutscher Akzent, darunter schwer verständlich)
- heard: was du verstanden hast
- issues: konkrete Laute oder Betonungen, die nicht stimmen (auf Deutsch, kurz), leer wenn alles passt
- tip: ein konkreter Tipp auf Deutsch (z. B. Zungenstellung beim th, Betonung auf der zweiten Silbe)
Wenn die Aufnahme still ist oder etwas ganz anderes gesagt wird, score 0 und das in tip sagen.
        """.trimIndent()

    private val schema = obj("score" to int(), "heard" to str(), "issues" to arr(str()), "tip" to str())

    suspend fun check(app: AbiCoachApp, target: String, wav: ByteArray): Result {
        val parts =
            listOf(
                GeminiRest.textPart("Zielwort oder -satz: \"$target\". Die Aufnahme folgt."),
                GeminiRest.inlinePart("audio/wav", wav),
            )
        val answer = app.ai.quickListenJson(SYSTEM, parts, schema)
        val o = answer.json
        return Result(o.optInt("score").coerceIn(0, 100), o.optString("heard"), o.optJSONArray("issues").strings(), o.optString("tip"), answer.by)
    }
}
