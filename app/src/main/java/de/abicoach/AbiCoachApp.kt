package de.abicoach

import android.app.Application
import de.abicoach.data.SessionStore
import de.abicoach.data.Settings
import de.abicoach.gemini.GeminiRest
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Holds the app-wide singletons: settings, local session storage and the HTTP clients. */
class AbiCoachApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var store: SessionStore
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(240, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    val rest: GeminiRest by lazy { GeminiRest(http) { settings.geminiKey } }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        store = SessionStore(this)
    }
}
