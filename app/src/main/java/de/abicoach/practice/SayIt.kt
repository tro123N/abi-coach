package de.abicoach.practice

import de.abicoach.AbiCoachApp
import de.abicoach.ai.GeminiRest
import de.abicoach.ai.bool
import de.abicoach.ai.obj
import de.abicoach.ai.str

/** A mistake the student wants to say correctly: what was said, the right form and why. */
data class SayTarget(val wrong: String, val right: String, val why: String = "")

/** Gemini listens to the student saying the corrected sentence and tells at once whether it was right. */
object SayItCheck {
    data class Result(val ok: Boolean, val heard: String, val tip: String, val by: String)

    private val SYSTEM =
        """
Du bist Englischlehrerin für einen deutschen Schüler, der sich auf die mündliche Englisch-Abiturprüfung vorbereitet.
Du bekommst den Satz, den der Schüler vorher falsch gesagt hat, die richtige Form und eine kurze Aufnahme, in der er es jetzt richtig sagen soll.
Entscheide:
- correct: true, wenn er die richtige Form (oder eine andere, ebenfalls richtige Formulierung mit gleichem Inhalt) fehlerfrei gesagt hat und gut verständlich ist. Kleine Aussprache-Abweichungen sind in Ordnung. false, wenn der Fehler wieder vorkommt, ein neuer Grammatikfehler dazukommt, die Aufnahme still ist oder etwas ganz anderes gesagt wird.
- heard: was du verstanden hast, wortgetreu auf Englisch (mit seinen Fehlern, nicht verbessert)
- tip: ein kurzer Satz auf Deutsch. Bei correct=true ein Lob, sonst ganz konkret, was noch nicht stimmt.
        """.trimIndent()

    private val schema = obj("correct" to bool(), "heard" to str(), "tip" to str())

    suspend fun check(app: AbiCoachApp, target: SayTarget, wav: ByteArray): Result {
        val parts =
            listOf(
                GeminiRest.textPart("Vorher falsch gesagt: \"${target.wrong}\"\nRichtige Form: \"${target.right}\"\nDie Aufnahme folgt."),
                GeminiRest.inlinePart("audio/wav", wav),
            )
        val answer = app.ai.quickListenJson(SYSTEM, parts, schema)
        val o = answer.json
        return Result(o.optBoolean("correct"), o.optString("heard"), o.optString("tip"), answer.by)
    }
}
