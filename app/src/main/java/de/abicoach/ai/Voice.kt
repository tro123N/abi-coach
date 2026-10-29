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

    /** Billed seconds of the live session so far (OpenAI reports them). */
    data class Usage(val seconds: Double) : LiveEvent

    data class Info(val message: String) : LiveEvent

    data class Failed(val message: String) : LiveEvent
}

/** A spoken conversation partner: the examiner. Implemented by Gemini Live and OpenAI GPT-Live. */
interface VoiceAgent {
    /** Shown in the app, e.g. "GPT-Live-1". */
    val label: String

    /** Sample rate of the audio this agent sends back. */
    val outputRate: Int

    fun connect()

    /** 16 kHz mono PCM from the microphone, in chunks of about 100 ms. */
    fun sendAudio(pcm16k: ByteArray)

    /** An instruction from the app (start, coach correction, time is up). */
    fun sendInstruction(text: String)

    fun sendStreamEnd()

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

    override fun sendInstruction(text: String) = socket.sendUserTurn(text)

    override fun sendStreamEnd() = socket.sendStreamEnd()

    override fun close() = socket.close()
}
