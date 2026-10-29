/*
AbiCoach – Töne und Vibration wie in Mental Math Plus (tro123N), basierend auf Mental Math,
Copyright (C) 2025-2026 HeldDerTierwelt. Lizenz: GNU GPL v3 oder neuer.
*/

package de.abicoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import de.abicoach.R

enum class FeedbackType { TAP, ENABLE, DISABLE, START, STOP, CORRECT, WRONG, SKIP, RESULT_BAD, RESULT_OKAY, RESULT_GOOD, RESULT_PERFECT, ALERT }

/** Kurze Töne und Vibrationen für Knöpfe, Start, Ende und Ergebnisse. */
class Feedback(context: Context, private val soundOn: () -> Boolean, private val vibrationOn: () -> Boolean) {
    private val app = context.applicationContext

    private val pool: SoundPool =
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()

    private val sounds: Map<FeedbackType, Int> by lazy {
        mapOf(
            FeedbackType.TAP to pool.load(app, R.raw.tap, 1),
            FeedbackType.ENABLE to pool.load(app, R.raw.enable, 1),
            FeedbackType.DISABLE to pool.load(app, R.raw.disable, 1),
            FeedbackType.START to pool.load(app, R.raw.start, 1),
            FeedbackType.STOP to pool.load(app, R.raw.stop, 1),
            FeedbackType.CORRECT to pool.load(app, R.raw.correct, 1),
            FeedbackType.WRONG to pool.load(app, R.raw.wrong, 1),
            FeedbackType.SKIP to pool.load(app, R.raw.skip, 1),
            FeedbackType.RESULT_BAD to pool.load(app, R.raw.result_bad, 1),
            FeedbackType.RESULT_OKAY to pool.load(app, R.raw.result_okay, 1),
            FeedbackType.RESULT_GOOD to pool.load(app, R.raw.result_good, 1),
            FeedbackType.RESULT_PERFECT to pool.load(app, R.raw.result_perfect, 1),
        )
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Lädt die Töne vorab, damit der erste Ton nicht verzögert ist. */
    fun warmUp() {
        sounds.size
    }

    fun play(type: FeedbackType) {
        if (soundOn()) {
            sounds[type]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
        }
        if (vibrationOn()) vibrate(type)
    }

    /** Nur Vibration, z. B. bei einem Fehler während des Sprechens (Ton würde stören). */
    fun buzz(type: FeedbackType = FeedbackType.ALERT) {
        if (vibrationOn()) vibrate(type)
    }

    private fun vibrate(type: FeedbackType) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect =
            when (type) {
                FeedbackType.RESULT_GOOD, FeedbackType.RESULT_PERFECT, FeedbackType.CORRECT ->
                    VibrationEffect.createWaveform(longArrayOf(0, 30, 20, 60), intArrayOf(0, 60, 0, 140), -1)
                FeedbackType.RESULT_BAD, FeedbackType.WRONG -> VibrationEffect.createWaveform(longArrayOf(0, 180), intArrayOf(0, 120), -1)
                FeedbackType.ALERT -> VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 60), intArrayOf(0, 160, 0, 160), -1)
                else -> VibrationEffect.createOneShot(20, 80)
            }
        runCatching { v.vibrate(effect) }
    }

    companion object {
        fun forPoints(points: Int): FeedbackType =
            when {
                points >= 13 -> FeedbackType.RESULT_PERFECT
                points >= 10 -> FeedbackType.RESULT_GOOD
                points >= 7 -> FeedbackType.RESULT_OKAY
                else -> FeedbackType.RESULT_BAD
            }
    }
}
