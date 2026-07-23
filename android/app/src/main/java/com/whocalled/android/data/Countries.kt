package com.whocalled.android.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** A selectable country scope for the spam list. `dial` "" = worldwide ("Tous"). */
data class CountryOption(val code: String, val name: String, val dial: String, val flag: String)

/**
 * Curated country scopes for the list filter (FR-first), plus "Tous" (worldwide).
 * The dial code is what we send to GET /lists?country=…; "" means no scope.
 */
object Countries {
    val ALL = CountryOption("ALL", "Tous les pays", "", "🌍")

    val list = listOf(
        ALL,
        CountryOption("FR", "France", "33", "🇫🇷"),
        CountryOption("BE", "Belgique", "32", "🇧🇪"),
        CountryOption("CH", "Suisse", "41", "🇨🇭"),
        CountryOption("LU", "Luxembourg", "352", "🇱🇺"),
        CountryOption("ES", "Espagne", "34", "🇪🇸"),
        CountryOption("IT", "Italie", "39", "🇮🇹"),
        CountryOption("DE", "Allemagne", "49", "🇩🇪"),
        CountryOption("GB", "Royaume-Uni", "44", "🇬🇧"),
        CountryOption("PT", "Portugal", "351", "🇵🇹"),
        CountryOption("US", "États-Unis", "1", "🇺🇸"),
        CountryOption("CA", "Canada", "1", "🇨🇦"),
        CountryOption("MA", "Maroc", "212", "🇲🇦"),
        CountryOption("DZ", "Algérie", "213", "🇩🇿"),
        CountryOption("TN", "Tunisie", "216", "🇹🇳"),
    )

    fun byDial(dial: String): CountryOption = list.firstOrNull { it.dial == dial } ?: ALL

    /**
     * Best-effort default country, no permission required: SIM, then network,
     * then locale region. Falls back to "Tous" if the ISO isn't in our list.
     */
    fun detectDefault(context: Context): CountryOption {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = (tm?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country)
            .uppercase(Locale.ROOT)
        return list.firstOrNull { it.code == iso } ?: ALL
    }
}
