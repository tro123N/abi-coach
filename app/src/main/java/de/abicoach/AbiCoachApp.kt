package de.abicoach

import android.app.Application
import de.abicoach.ai.AiHub
import de.abicoach.audio.Feedback
import de.abicoach.data.SessionStore
import de.abicoach.data.Settings
import de.abicoach.memory.CourseContext
import de.abicoach.memory.DocStatus
import de.abicoach.memory.Importer
import de.abicoach.memory.IngestScheduler
import de.abicoach.memory.MemoryStore
import de.abicoach.memory.Retriever
import de.abicoach.profile.ProfileStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Holds the app-wide singletons: settings, stores, the AI hub and the course memory. */
class AbiCoachApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var store: SessionStore
        private set
    lateinit var profiles: ProfileStore
        private set
    lateinit var memory: MemoryStore
        private set
    lateinit var importer: Importer
        private set
    lateinit var ai: AiHub
        private set
    lateinit var course: CourseContext
        private set
    lateinit var feedback: Feedback
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        store = SessionStore(this)
        profiles = ProfileStore(this)
        memory = MemoryStore(this)
        importer = Importer(this, memory)
        ai = AiHub(http, settings)
        course = CourseContext(memory, Retriever(memory, ai), settings, profiles)
        feedback = Feedback(this, { settings.sound }, { settings.vibration })
        resumeReading()
    }

    /** Continues reading material that is not finished yet (e.g. after a restart or a new key). */
    fun resumeReading() {
        val docs = memory.docs.value
        docs.filter { it.status == DocStatus.WAITING_KEY && settings.geminiKey.isNotBlank() }.forEach { d ->
            memory.update(d.id) { it.copy(status = DocStatus.QUEUED) }
        }
        if (memory.docs.value.any { it.isActive }) IngestScheduler.kick(this)
    }
}
