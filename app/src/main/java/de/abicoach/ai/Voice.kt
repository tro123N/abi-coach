package de.abicoach.ai

/** Everything a live voice session (examiner or transcriber) can report back to the app. */
sealed interface LiveEvent {
    data object Ready : LiveEvent

    class Audio(val pcm: ByteArray) : LiveEvent

    /** A piece of the transcript of what the model says. */
    data class ModelText(val text: String) : LiveEvent

    /** A piece (examiner session) or a finished utterance (transcription session) of what the student said. */
    data class UserText(val text: String) : LiveEvent

    /** The current, still changing hypothesis of the transcription session. */
    data class UserInterim(val text: String) : LiveEvent

    data object TurnComplete : LiveEvent

    data object Interrupted : LiveEvent

    data class Info(val message: String) : LiveEvent

    data class Failed(val message: String) : LiveEvent
}

/** A live speech-to-text stream that writes down what the student says ("second ear"). */
interface LiveTranscriber {
    /** True once the stream is connected and writes along. */
    val ready: Boolean

    fun connect()

    /** 16 kHz mono PCM from the microphone, in chunks of about 100 ms. */
    fun sendAudio(pcm16k: ByteArray)

    fun sendStreamEnd()

    /** Renews a connection that seems to hang. */
    fun reconnectNow() {}

    fun close()
}

/** A spoken conversation partner: the examiner (Gemini Live, GPT-Live or Claude with the phone's voice). */
interface VoiceAgent {
    /** Shown in the app, e.g. "Gemini 3.8 Live". */
    val label: String

    /** Sample rate of the audio this agent sends back. */
    val outputRate: Int

    fun connect()

    /** 16 kHz mono PCM from the microphone, in chunks of about 100 ms. */
    fun sendAudio(pcm16k: ByteArray)

    /** An instruction from the app (start, coach correction, time is up). The examiner answers it with his voice at once. */
    fun sendInstruction(text: String): Boolean

    /** A note from the app that the examiner takes into account in his next turn, without answering it at once. */
    fun sendNote(text: String): Boolean = sendInstruction(text)

    fun sendStreamEnd()

    /**
     * "Knopf gedrückt halten": the student pressed the talk button – he starts his turn now (the examiner stops
     * talking). Only used when the agent was made with manual turns.
     */
    fun turnStart() {}

    /** The student let go of the talk button: his turn is over, the examiner answers at once (no waiting for a pause). */
    fun turnEnd() {}

    fun close()
}

/** Gemini Live as examiner. */
class GeminiVoiceAgent(
    private val socket: LiveSocket,
    override val label: String,
) : VoiceAgent {
    override val outputRate: Int = 24_000

    override fun connect() = socket.connect()

    override fun sendAudio(pcm16k: ByteArray) = socket.sendAudio(pcm16k)

    override fun sendInstruction(text: String): Boolean = socket.sendUserTurn(text)

    override fun sendNote(text: String): Boolean = socket.sendUserTurn(text, complete = false)

    override fun sendStreamEnd() = socket.sendStreamEnd()

    override fun turnStart() = socket.activity(start = true)

    override fun turnEnd() = socket.activity(start = false)

    override fun close() = socket.close()
}
