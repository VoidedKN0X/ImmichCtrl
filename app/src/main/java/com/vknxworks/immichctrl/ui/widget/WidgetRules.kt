package com.vknxworks.immichctrl.ui.widget

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.random.Random

/** The "On this day" widget's choices, kept free of Android so they can be unit-tested. */
object WidgetRules {

    /** How long after midnight the daily refresh runs, so the server's day has turned too. */
    val AFTER_MIDNIGHT: Duration = Duration.ofMinutes(5)

    /** Time from [now] until a few minutes into the next day (in [now]'s time zone, daylight saving included). */
    fun delayToNextDay(now: ZonedDateTime): Duration {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plus(AFTER_MIDNIGHT)
        return Duration.between(now, next).let { if (it.isNegative) Duration.ZERO else it }
    }

    /**
     * The memory and photo the widget shows on [day]: picked the same way all day, so refreshes during the day
     * keep the same photo (and its small cached copy), and it changes with the date. [memories] holds each
     * memory's photo ids (stills only, when it has any). Null when there's nothing to show.
     */
    fun pickOfDay(memories: List<List<String>>, day: LocalDate): Pair<Int, Int>? {
        val usable = memories.indices.filter { memories[it].isNotEmpty() }
        if (usable.isEmpty()) return null
        val rnd = Random(day.toEpochDay())
        val m = usable[rnd.nextInt(usable.size)]
        return m to rnd.nextInt(memories[m].size)
    }

    /** What the cached photo was made from. */
    data class CacheKey(val account: String, val assetId: String)

    fun parseKey(text: String?): CacheKey? {
        val lines = text?.lines() ?: return null
        if (lines.size < 2 || lines[0].isBlank() || lines[1].isBlank()) return null
        return CacheKey(lines[0], lines[1])
    }

    fun formatKey(k: CacheKey): String = "${k.account.replace('\n', ' ')}\n${k.assetId.replace('\n', ' ')}"

    /** Longest side of the cached widget photo: enough for a home-screen widget, small on disk. */
    const val CACHE_SIDE = 512

    /** Sample size that brings [w]×[h] down to at most [maxSide] (a power of two, as BitmapFactory wants). */
    fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        var s = 1
        while (w / (s * 2) >= maxSide || h / (s * 2) >= maxSide) s *= 2
        return s
    }
}
