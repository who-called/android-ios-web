package com.whocalled.android.util

import android.content.Context
import java.util.Locale

/**
 * Locale used to format dates and numbers so they match the language the UI
 * strings are resolved in. The app ships fr (default `values/`) plus one
 * `values-<lang>/` per entry of [SUPPORTED]: an unsupported device language
 * falls back to the French resources, so formatting falls back to French too.
 */
object UiLocale {
    private val SUPPORTED = setOf(
        "fr", "en", "es", "it", "pt", "ru",
        "pl", "cs", "de", "uk", "in", "id", "sv", "nl", "sk",
    )

    fun of(context: Context): Locale {
        val locales = context.resources.configuration.locales
        val current = if (locales.isEmpty) null else locales[0]
        return if (current != null && current.language in SUPPORTED) current else Locale.FRANCE
    }
}
