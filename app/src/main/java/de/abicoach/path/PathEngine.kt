package de.abicoach.path

import de.abicoach.data.Debrief
import de.abicoach.profile.Profile
import de.abicoach.profile.Skill
import kotlin.math.max

/** Unlocking, stars and the review plan of the Abi-Weg (like the Kletterweg of Mental Math Plus). */
object PathEngine {
    private const val DAY = 86_400_000L

    /** Days until the next review after passing: 1, 3, 7, 14, 30. */
    private val REVIEW_DAYS = listOf(1, 3, 7, 14, 30)

    /** The main competence of each skill world: decides what the placement test unlocks. */
    private val WORLD_SKILL =
        mapOf("w2" to Skill.ZUSAMMENFASSEN, "w3" to Skill.ANALYSE, "w4" to Skill.KOMMENTAR, "w5" to Skill.STRUKTUR, "w6" to Skill.INTERAKTION)

    data class Thresholds(val one: Int, val two: Int, val three: Int)

    fun thresholds(level: Level): Thresholds {
        val three = max(13, level.goal + 2)
        val two = (level.goal + three + 1) / 2
        return Thresholds(level.goal, two, three).let { if (it.two >= it.three) it.copy(two = it.three - 1) else it }
    }

    fun stars(level: Level, points: Int): Int {
        val t = thresholds(level)
        return when {
            points >= t.three -> 3
            points >= t.two -> 2
            points >= t.one -> 1
            else -> 0
        }
    }

    /** The points that count for a level: the task of part 1, else the measured skill, else the total. */
    fun pointsFor(level: Level, debrief: Debrief): Int {
        if (level.focusTask in 1..3) debrief.taskReviews.firstOrNull { it.number == level.focusTask }?.let { return it.points }
        level.skill?.let { s -> debrief.skills[s]?.let { return it } }
        return debrief.totalPoints
    }

    fun isWorldUnlocked(worlds: List<World>, index: Int, progress: PathProgress, profile: Profile): Boolean {
        val world = worlds.getOrNull(index) ?: return false
        if (index == 0) return true
        if (world.levels.any { progress.of(it.id).unlocked || progress.of(it.id).passed }) return true
        val basicsDone = worlds.first().levels.all { progress.of(it.id).passed } || profile.placementDone
        if (world.free) return basicsDone
        if (world.id == "g") {
            return worlds.filter { it.id in WORLD_SKILL.keys }.all { w -> w.levels.any { progress.of(it.id).passed } }
        }
        val previous = worlds.getOrNull(index - 1) ?: return true
        val passedBefore = previous.levels.count { progress.of(it.id).passed }
        return passedBefore >= minOf(2, previous.levels.size) || (index == 1 && basicsDone)
    }

    fun isLevelUnlocked(world: World, levelIndex: Int, progress: PathProgress, worldUnlocked: Boolean): Boolean {
        if (!worldUnlocked) return false
        if (levelIndex == 0 || world.free) return true
        val level = world.levels[levelIndex]
        val p = progress.of(level.id)
        return p.unlocked || p.passed || progress.of(world.levels[levelIndex - 1].id).passed
    }

    data class Located(val world: World, val worldIndex: Int, val level: Level, val levelIndex: Int)

    fun all(worlds: List<World>): List<Located> =
        worlds.flatMapIndexed { wi, w -> w.levels.mapIndexed { li, l -> Located(w, wi, l, li) } }

    fun locate(worlds: List<World>, levelId: String): Located? = all(worlds).firstOrNull { it.level.id == levelId }

    fun isUnlocked(worlds: List<World>, levelId: String, progress: PathProgress, profile: Profile): Boolean {
        val l = locate(worlds, levelId) ?: return false
        return isLevelUnlocked(l.world, l.levelIndex, progress, isWorldUnlocked(worlds, l.worldIndex, progress, profile))
    }

    /** The next level to climb: the first open one in the skill worlds, then topic worlds, then the dress rehearsal. */
    fun next(worlds: List<World>, progress: PathProgress, profile: Profile): Located? {
        val open = all(worlds).filter { !progress.of(it.level.id).passed && isUnlocked(worlds, it.level.id, progress, profile) }
        return open.firstOrNull { !it.world.free && it.world.id != "g" } ?: open.firstOrNull { it.world.free } ?: open.firstOrNull()
    }

    /** Levels whose review is due (or that got rusty), oldest first. */
    fun due(worlds: List<World>, progress: PathProgress, now: Long = System.currentTimeMillis()): List<Located> =
        all(worlds).filter { l ->
            val p = progress.of(l.level.id)
            l.level.type != LevelType.LEARN && p.passed && (p.rusty || (p.nextReviewAt in 1..now))
        }.sortedWith(compareByDescending<Located> { progress.of(it.level.id).rusty }.thenBy { progress.of(it.level.id).nextReviewAt })

    fun applyResult(progress: PathProgress, level: Level, points: Int, now: Long = System.currentTimeMillis()): PathProgress {
        val old = progress.of(level.id)
        val passedNow = points >= level.goal
        val newStars = if (passedNow) stars(level, points) else 0
        val next =
            when {
                passedNow && !old.passed ->
                    old.copy(stars = newStars, passedAt = now, reviewStage = 1, nextReviewAt = now + REVIEW_DAYS[0] * DAY, rusty = false)
                passedNow -> {
                    val due = old.rusty || old.nextReviewAt in 1..now
                    val stage = if (due) (old.reviewStage + 1).coerceAtMost(REVIEW_DAYS.size) else old.reviewStage.coerceAtLeast(1)
                    old.copy(stars = max(old.stars, newStars), reviewStage = stage, nextReviewAt = now + REVIEW_DAYS[stage - 1] * DAY, rusty = false)
                }
                old.passed -> old.copy(rusty = true, reviewStage = 1, nextReviewAt = now + DAY)
                else -> old
            }
        val final = next.copy(attempts = old.attempts + 1, lastAt = now, best = max(old.best, points))
        return progress.copy(levels = progress.levels + (level.id to final))
    }

    fun markLearned(progress: PathProgress, level: Level, now: Long = System.currentTimeMillis()): PathProgress {
        val old = progress.of(level.id)
        if (old.passed) return progress
        return progress.copy(levels = progress.levels + (level.id to old.copy(passedAt = now, lastAt = now, attempts = old.attempts + 1)))
    }

    /** After the placement test: skill worlds the student already masters are opened completely. */
    fun applyPlacement(progress: PathProgress, worlds: List<World>, profile: Profile): PathProgress {
        val levels = progress.levels.toMutableMap()
        worlds.filter { it.id in WORLD_SKILL }.forEach { w ->
            val score = profile.skills[WORLD_SKILL.getValue(w.id)]?.score ?: return@forEach
            if (score >= 11.0) w.levels.forEach { l -> levels[l.id] = (levels[l.id] ?: LevelProgress()).copy(unlocked = true) }
        }
        // The explanations of the first world count as read: the student has just done an exam-like test.
        worlds.firstOrNull()?.levels?.filter { it.type == LevelType.LEARN }?.forEach { l ->
            val p = levels[l.id] ?: LevelProgress()
            if (!p.passed) levels[l.id] = p.copy(passedAt = System.currentTimeMillis())
        }
        return progress.copy(levels = levels)
    }

    fun starsTotal(worlds: List<World>, progress: PathProgress): Pair<Int, Int> {
        val scored = all(worlds).filter { it.level.type != LevelType.LEARN }
        return scored.sumOf { progress.of(it.level.id).stars } to scored.size * 3
    }
}
