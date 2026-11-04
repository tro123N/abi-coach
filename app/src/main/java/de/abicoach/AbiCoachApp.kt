package de.abicoach

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
import de.abicoach.path.Curriculum
import de.abicoach.path.PathStore
import de.abicoach.path.World
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
    lateinit var path: PathStore
        private set
    lateinit var vocab: de.abicoach.practice.VocabStore
        private set
    lateinit var pron: de.abicoach.practice.PronStore
        private set
    lateinit var briefings: de.abicoach.coach.BriefingStore
        private set
    val analysis = de.abicoach.session.AnalysisCenter()

    /** Progress of the exam material that is written in the background. */
    val material = de.abicoach.session.MaterialCenter()
    lateinit var drafts: de.abicoach.session.DraftStore
        private set
    lateinit var shelf: de.abicoach.session.MaterialShelf
        private set
    lateinit var pendingMaterial: de.abicoach.session.PendingMaterialStore
        private set

    /** Every kind of mistake the student makes, followed across sessions and Klausuren. */
    lateinit var errorMemory: de.abicoach.learn.ErrorMemoryStore
        private set

    /** The condensed knowledge per topic field (the long-term memory of the course). */
    lateinit var dossiers: de.abicoach.memory.DossierStore
        private set

    /** A message from background work (e.g. "Gemini hat übernommen") that the app shows once. */
    @Volatile var pendingNotice: String? = null

    private var startedActivities = 0

    /** True while an activity of the app is on screen. */
    val isVisible: Boolean get() = startedActivities > 0

    /** The worlds of the Abi-Weg; topic fields with the most material come first. */
    fun worlds(): List<World> = Curriculum.worlds(course.coveredTopics().map { it.first })

    /** Name resolution with replacements, for phones with a flaky or filtered DNS. */
    val dns: de.abicoach.ai.ResilientDns by lazy { de.abicoach.ai.ResilientDns(this) }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dns)
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
        dossiers = de.abicoach.memory.DossierStore(this)
        errorMemory = de.abicoach.learn.ErrorMemoryStore(this)
        course = CourseContext(memory, Retriever(memory, ai), settings, profiles, dossiers)
        feedback = Feedback(this, { settings.sound }, { settings.vibration })
        path = PathStore(this)
        vocab = de.abicoach.practice.VocabStore(this)
        pron = de.abicoach.practice.PronStore(this)
        briefings = de.abicoach.coach.BriefingStore(this)
        drafts = de.abicoach.session.DraftStore(this)
        shelf = de.abicoach.session.MaterialShelf(this).also { it.tidy() }
        pendingMaterial = de.abicoach.session.PendingMaterialStore(this)
        de.abicoach.path.Reminder.createChannel(this)
        de.abicoach.session.Notifications.createChannels(this)
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        resumeReading()
        // Renews dossiers whose material changed (the job checks this itself and is cheap when nothing changed).
        if (settings.geminiKey.isNotBlank()) de.abicoach.memory.DossierScheduler.kick(this)
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
