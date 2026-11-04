package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.ai.AiException
import de.abicoach.ai.LiveEvent
import de.abicoach.data.LiveError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

enum class CheckState { WAIT, RUN, OK, WARN, FAIL }

data class CheckLine(val key: String, val title: String, val state: CheckState = CheckState.WAIT, val detail: String = "")

/**
 * Tests every Google part of the app for real, with a few cents of usage: the key, the brain, the quick error check,
 * the read-aloud voice, the verbatim transcriber (fed with a spoken test sentence), the examiner's voice and – most
 * important for the coach – a correction that is sent to the examiner in the middle of a conversation.
 */
class SystemCheck(private val app: AbiCoachApp) {
    private val titles =
        listOf(
            "key" to "Google-Schlüssel",
            "brain" to "Bewertung und Aufgaben",
            "fast" to "Fehlerprüfung beim Sprechen",
            "tts" to "Vorlesen",
            "ear" to "Wortgetreue Mitschrift",
            "examiner" to "Prüfer spricht",
            "coach" to "Coach-Hinweis im Gespräch",
            "memory" to "Suche im Kursmaterial",
            "net" to "Netz und Namensauflösung",
        )

    private val lines = LinkedHashMap<String, CheckLine>()
    private var publish: (List<CheckLine>) -> Unit = {}

    private fun set(key: String, state: CheckState, detail: String = "") {
        val title = titles.first { it.first == key }.second
        lines[key] = CheckLine(key, title, state, detail)
        publish(lines.values.toList())
    }

    private fun seconds(since: Long): String = "%.1f s".format((System.currentTimeMillis() - since) / 1000.0)

    /** Runs all tests one after the other and reports the progress through [onUpdate]. */
    suspend fun runAll(onUpdate: (List<CheckLine>) -> Unit) {
        publish = onUpdate
        titles.forEach { (key, _) -> set(key, CheckState.WAIT) }

        set("key", CheckState.RUN)
        var keyError = if (app.ai.hasGemini) app.ai.gemini.checkKey() else "Es ist kein Schlüssel eingetragen."
        if (keyError != null && isNetwork(keyError)) {
            // A lost connection often comes back within seconds.
            kotlinx.coroutines.delay(3_000)
            keyError = app.ai.gemini.checkKey()
        }
        if (keyError != null) {
            val network = isNetwork(keyError)
            set("key", CheckState.FAIL, if (network) NETWORK_HELP else keyError)
            titles.drop(1).forEach { (key, _) -> set(key, CheckState.WARN, if (network) "Übersprungen – zuerst muss die Verbindung zu Google stehen." else "Übersprungen, solange der Schlüssel nicht funktioniert.") }
            return
        }
        set("key", CheckState.OK, "Der Schlüssel funktioniert.")

        guarded("brain") { checkBrain() }
        guarded("fast") { checkFast() }
        var speech: ByteArray? = null
        guarded("tts") { checkTts()?.also { speech = it } }
        guarded("ear") { checkEar(speech) }
        guarded("examiner") { checkExaminerAndCoach() }
        val coach = lines["coach"]?.state
        if (coach == CheckState.WAIT || coach == CheckState.RUN) set("coach", CheckState.WARN, "Nicht geprüft, weil der Prüfer nicht antwortet.")
        guarded("memory") { checkMemory() }
        val dns = app.dns
        if (dns.fallbacks > 0 || dns.lastProblem != null) {
            set(
                "net",
                CheckState.WARN,
                "Die Namensauflösung deines Handys hat zwischendurch versagt (${dns.lastProblem ?: "unbekannt"}). " +
                    "Die App hat ${dns.fallbacks}× Ersatzwege genutzt. Wenn das oft passiert: " + TIPS,
            )
        } else {
            set("net", CheckState.OK, "Stabil – keine Aussetzer.")
        }
    }

    private fun isNetwork(message: String): Boolean =
        listOf("resolve host", "Keine Verbindung", "Netzwerkfehler", "timeout", "timed out", "Unable to", "failed to connect", "UnknownHost").any {
            message.contains(it, ignoreCase = true)
        }

    private suspend fun <T> guarded(key: String, block: suspend () -> T?): T? {
        set(key, CheckState.RUN)
        repeat(2) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                val network = (e as? AiException)?.isNetwork == true || isNetwork(message)
                if (network && attempt == 0) {
                    // One more try after a short pause: a weak network or a DNS hiccup often passes.
                    set(key, CheckState.RUN, "Keine Verbindung – neuer Versuch …")
                    kotlinx.coroutines.delay(3_000)
                } else {
                    set(
                        key,
                        CheckState.FAIL,
                        when {
                            network -> NETWORK_HELP
                            (e as? AiException)?.isOverloaded == true -> "Google ist gerade überlastet (${message.take(80)}). Das ist vorübergehend – die App weicht beim Üben automatisch auf ein anderes Modell aus."
                            else -> message
                        },
                    )
                    return null
                }
            }
        }
        return null
    }

    private suspend fun checkBrain(): Boolean {
        app.ai.lastNotice = null
        val start = System.currentTimeMillis()
        val (_, model) = app.ai.brainText("Answer with one word.", listOf("user" to "Say: ready"))
        val notice = app.ai.lastNotice
        if (notice != null) {
            set("brain", CheckState.WARN, notice)
        } else {
            set("brain", CheckState.OK, "$model antwortet (${seconds(start)}).")
        }
        return true
    }

    private suspend fun checkFast(): Boolean {
        val start = System.currentTimeMillis()
        val json = app.ai.fastJson(Prompts.CHECK_SYSTEM, Prompts.checkRequest("I goed to school yesterday and my friends was there.", "Test"), Prompts.checkSchema)
        val found = Prompts.parseErrors(json.optJSONArray("errors")).size
        val time = seconds(start)
        if (found > 0) {
            set("fast", CheckState.OK, "${app.ai.fastLabel()}: $found Fehler im Testsatz erkannt ($time).")
        } else {
            set("fast", CheckState.WARN, "Antwort kam nach $time, aber der Testsatz mit Fehlern wurde als fehlerfrei bewertet.")
        }
        return true
    }

    private suspend fun checkTts(): ByteArray? {
        val start = System.currentTimeMillis()
        val pcm = app.ai.speak("I goed to school yesterday and I have seen many friends there.")
        return if (pcm != null && pcm.size > 1_000) {
            set("tts", CheckState.OK, "Gemini-Stimme funktioniert (${seconds(start)}).")
            pcm
        } else {
            set("tts", CheckState.WARN, "Keine Gemini-Stimme – die App liest dann mit der Handy-Stimme vor.")
            null
        }
    }

    private suspend fun checkEar(speech: ByteArray?): Boolean {
        val events = Channel<LiveEvent>(Channel.UNLIMITED)
        val ear = app.ai.transcriber(emptyList()) { events.trySend(it) }
        val start = System.currentTimeMillis()
        try {
            ear.connect()
            val first = waitFor(events, 25_000) { it is LiveEvent.Ready || it is LiveEvent.Failed }
            if (first !is LiveEvent.Ready) {
                val message = (first as? LiveEvent.Failed)?.message
                set("ear", CheckState.FAIL, if (message == null || isNetwork(message)) NETWORK_HELP else message)
                return false
            }
            if (speech == null) {
                set("ear", CheckState.WARN, "Verbunden (${seconds(start)}). Der Sprachtest entfällt, weil das Vorlesen nicht ging.")
                return true
            }
            val pcm = to16k(speech)
            var pos = 0
            val chunk = 3_200
            while (pos < pcm.size) {
                val end = minOf(pos + chunk, pcm.size)
                ear.sendAudio(pcm.copyOfRange(pos, end))
                pos = end
                kotlinx.coroutines.delay(100)
            }
            ear.sendAudio(ByteArray(chunk * 8))
            ear.sendStreamEnd()
            val heard = StringBuilder()
            withTimeoutOrNull(10_000) {
                for (event in events) {
                    if (event is LiveEvent.UserText) heard.append(event.text).append(' ')
                    if (event is LiveEvent.UserInterim && event.text.isNotBlank() && heard.isEmpty()) heard.append(event.text)
                    if (heard.length > 40) break
                }
            }
            val text = heard.toString().trim()
            when {
                text.isEmpty() -> set("ear", CheckState.FAIL, "Verbunden, aber es kam kein Text zurück. Dann gibt es keine Hinweise beim Sprechen.")
                text.contains("goed", ignoreCase = true) -> set("ear", CheckState.OK, "Hat den Fehler wortgetreu mitgeschrieben: „${text.take(70)}“ (${seconds(start)}).")
                else -> set("ear", CheckState.OK, "Hat den Testsatz verstanden: „${text.take(70)}“ – aber nicht ganz wortgetreu (der Fehler wurde ausgebessert).")
            }
            return true
        } finally {
            ear.close()
            events.close()
        }
    }

    /** Examiner reply to a start instruction, then a coach instruction in the middle of the conversation. Returns null if the examiner is not usable. */
    private suspend fun checkExaminerAndCoach(): Boolean? {
        val events = Channel<LiveEvent>(Channel.UNLIMITED)
        val agent =
            app.ai.examiner(
                "You are a friendly English examiner in an oral exam. Answer every message from the app with one or two short spoken sentences.",
            ) { events.trySend(it) }
        val start = System.currentTimeMillis()
        try {
            agent.connect()
            val ready = waitFor(events, 25_000) { it is LiveEvent.Ready || it is LiveEvent.Failed }
            if (ready !is LiveEvent.Ready) {
                val message = (ready as? LiveEvent.Failed)?.message
                set("examiner", CheckState.FAIL, if (message == null || isNetwork(message)) NETWORK_HELP else message)
                return null
            }
            val connected = seconds(start)
            val askedAt = System.currentTimeMillis()
            if (!agent.sendInstruction("START: Greet the student in one short sentence and ask for their name.")) {
                set("examiner", CheckState.FAIL, "Die Verbindung steht, aber die Startanweisung konnte nicht gesendet werden.")
                return null
            }
            val firstAudio = waitFor(events, 15_000) { it is LiveEvent.Audio || it is LiveEvent.Failed }
            if (firstAudio !is LiveEvent.Audio) {
                set("examiner", CheckState.FAIL, (firstAudio as? LiveEvent.Failed)?.message ?: "Verbunden ($connected), aber der Prüfer spricht nicht von selbst.")
                return null
            }
            set("examiner", CheckState.OK, "${app.ai.examinerLabel()} verbindet in $connected und spricht nach ${seconds(askedAt)}.")
            // Let him finish, like a student who talks while the examiner is silent.
            waitFor(events, 15_000) { it is LiveEvent.TurnComplete }
            drain(events)
            set("coach", CheckState.RUN)
            try {
                val error = LiveError("I goed home", "I went home", "Past tense of go", "TENSE", "major")
                val coachAt = System.currentTimeMillis()
                agent.sendInstruction(Prompts.coachMessage(error))
                val answer = waitFor(events, 12_000) { it is LiveEvent.Audio || it is LiveEvent.Failed }
                if (answer is LiveEvent.Audio) {
                    set("coach", CheckState.OK, "Der Prüfer sagt die Korrektur selbst (Antwort nach ${seconds(coachAt)}).")
                } else {
                    set("coach", CheckState.WARN, "Der Prüfer antwortet nicht auf Coach-Hinweise. Der Coach spricht dann automatisch mit der Handy-Stimme.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set("coach", CheckState.FAIL, e.message ?: e.javaClass.simpleName)
            }
            return true
        } finally {
            agent.close()
            events.close()
        }
    }

    private suspend fun checkMemory(): Boolean {
        val start = System.currentTimeMillis()
        val vectors = app.ai.embed(listOf("The Hate U Give"), query = true)
        if (vectors.isEmpty() || vectors[0].isEmpty()) {
            set("memory", CheckState.FAIL, "Keine Suchvektoren erhalten.")
        } else {
            set("memory", CheckState.OK, "Funktioniert (${seconds(start)}).")
        }
        return true
    }

    private suspend fun waitFor(events: Channel<LiveEvent>, millis: Long, match: (LiveEvent) -> Boolean): LiveEvent? =
        withTimeoutOrNull(millis) {
            var found: LiveEvent? = null
            for (event in events) {
                if (match(event)) {
                    found = event
                    break
                }
            }
            found
        }

    companion object {
        private const val TIPS =
            "1. WLAN aus- und wieder einschalten oder mobile Daten testen. 2. Einstellungen → Netzwerk → „Privates DNS“ auf „Automatisch“. " +
                "3. VPN oder Werbeblocker kurz ausschalten. 4. Im Schul-WLAN ist Google-KI manchmal gesperrt – dann mobile Daten nutzen."

        const val NETWORK_HELP = "Keine Verbindung zu Google: Das Handy findet den Server gerade nicht. $TIPS"
    }

    private fun drain(events: Channel<LiveEvent>) {
        while (events.tryReceive().isSuccess) {
            // throw the old events away
        }
    }

    /** 24 kHz → 16 kHz (linear interpolation), the microphone format of the live sessions. */
    private fun to16k(pcm24: ByteArray): ByteArray {
        val n = pcm24.size / 2
        if (n < 2) return ByteArray(0)
        val samples = ShortArray(n) { ((pcm24[2 * it + 1].toInt() shl 8) or (pcm24[2 * it].toInt() and 0xff)).toShort() }
        val outCount = (n / 1.5).toInt()
        val out = ByteArray(outCount * 2)
        for (i in 0 until outCount) {
            val position = i * 1.5
            val i0 = position.toInt()
            val fraction = position - i0
            val a = samples[i0].toInt()
            val b = samples[minOf(i0 + 1, n - 1)].toInt()
            val v = (a + (b - a) * fraction).toInt().coerceIn(-32768, 32767)
            out[2 * i] = (v and 0xff).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }
}
