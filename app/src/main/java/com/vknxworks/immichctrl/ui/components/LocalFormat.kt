package com.vknxworks.immichctrl.ui.components

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A date pattern written in the phone's language ("za 12 okt." on a Dutch phone). The formatter is kept
 * until the phone's language changes, then built again, so dates follow a language switch without a restart.
 */
class LocalFormat(private val pattern: String) {
    @Volatile private var cached: Pair<Locale, DateTimeFormatter>? = null

    fun get(): DateTimeFormatter {
        val locale = Locale.getDefault(Locale.Category.FORMAT)
        cached?.let { if (it.first == locale) return it.second }
        return DateTimeFormatter.ofPattern(pattern, locale).also { cached = locale to it }
    }
}
