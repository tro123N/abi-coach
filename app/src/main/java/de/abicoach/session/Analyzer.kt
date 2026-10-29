package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.data.Debrief
import de.abicoach.data.ExamTask
import de.abicoach.data.SessionRecord
import de.abicoach.data.TaskItem
import de.abicoach.gemini.GeminiRest
import org.json.JSONObject

/** Creates new, unknown exam material for part 1 (like the examiner's "Prüfungsvorlage"). */
object MaterialGenerator {
    suspend fun generate(app: AbiCoachApp, topicArea: String, textType: String): ExamTask {
        val json =
            app.rest.generateJson(
                model = app.settings.analysisModel,
                system = Prompts.MATERIAL_SYSTEM,
                parts = listOf(GeminiRest.textPart(Prompts.materialRequest(topicArea, textType, app.settings.courseTopics))),
                schema = Prompts.materialSchema(),
                temperature = 0.9,
            )
        val tasksArray = json.optJSONArray("tasks")
        val tasks =
            if (tasksArray == null) {
                emptyList()
            } else {
                (0 until tasksArray.length()).mapNotNull { tasksArray.optJSONObject(it) }.map {
                    TaskItem(it.optString("operator"), it.optString("afb"), it.optString("instruction"))
                }
            }
        val topicsArray = json.optJSONArray("part2_topics")
        val topics =
            if (topicsArray == null) emptyList() else (0 until topicsArray.length()).map { topicsArray.optString(it) }.filter { it.isNotBlank() }
        val text = json.optString("text")
        if (text.isBlank() || tasks.isEmpty()) throw IllegalStateException("Das Material ist unvollständig – bitte nochmal versuchen.")
        return ExamTask(
            topicArea = topicArea,
            title = json.optString("title"),
            textType = json.optString("text_type"),
            text = text,
            sourceNote = json.optString("source_note"),
            tasks = tasks,
            part2Topics = topics,
        )
    }
}

/** The full debrief after a session: transcript and (if allowed) the recording go to Gemini for evaluation. */
class Analyzer(private val app: AbiCoachApp) {
    suspend fun analyze(record: SessionRecord, onProgress: (String) -> Unit): Debrief {
        val parts = mutableListOf<JSONObject>()
        parts += GeminiRest.textPart(Prompts.analysisContext(record))
        var audioAdded = false
        if (app.settings.uploadAudio) {
            for (part in listOf(1, 2, 0)) {
                val file = app.store.audioFile(record.id, part)
                if (!file.exists() || file.length() < 64_000) continue
                val label = when (part) { 1 -> "Teil 1"; 2 -> "Teil 2"; else -> "Training" }
                onProgress("Lade die Aufnahme hoch ($label) …")
                try {
                    val uri = app.rest.uploadFile(file, "audio/wav", "abicoach-${record.id}-$part")
                    parts += GeminiRest.textPart("Audioaufnahme des Prüflings ($label), nur sein Mikrofon:")
                    parts += GeminiRest.filePart("audio/wav", uri)
                    audioAdded = true
                } catch (e: Exception) {
                    onProgress("Aufnahme übersprungen: ${e.message}")
                }
            }
        }
        onProgress("Gemini wertet deine Leistung aus …")
        val json =
            app.rest.generateJson(
                model = app.settings.analysisModel,
                system = Prompts.ANALYSIS_SYSTEM,
                parts = parts,
                schema = Prompts.analysisSchema(),
                temperature = 0.3,
            )
        return Prompts.parseDebrief(json, record.studentText, audioAdded)
    }
}
