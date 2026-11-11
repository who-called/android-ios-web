package com.whocalled.android.data

import android.content.Context
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import com.whocalled.android.R
import java.util.Locale

/** A selectable country scope for the spam list. `dial` "" = worldwide ("Tous"). */
data class CountryOption(val code: String, @param:StringRes val nameRes: Int, val dial: String, val flag: String)

/**
 * Curated country scopes for the list filter (FR-first), plus "Tous" (worldwide).
 * The dial code is what we send to GET /lists?country=…; "" means no scope.
 */
object Countries {
    val ALL = CountryOption("ALL", R.string.country_all, "", "🌍")

    val list = listOf(
        ALL,
        CountryOption("FR", R.string.country_fr, "33", "🇫🇷"),
        CountryOption("BE", R.string.country_be, "32", "🇧🇪"),
        CountryOption("CH", R.string.country_ch, "41", "🇨🇭"),
        CountryOption("LU", R.string.country_lu, "352", "🇱🇺"),
        CountryOption("ES", R.string.country_es, "34", "🇪🇸"),
        CountryOption("IT", R.string.country_it, "39", "🇮🇹"),
        CountryOption("DE", R.string.country_de, "49", "🇩🇪"),
        CountryOption("GB", R.string.country_gb, "44", "🇬🇧"),
        CountryOption("PT", R.string.country_pt, "351", "🇵🇹"),
        CountryOption("US", R.string.country_us, "1", "🇺🇸"),
        CountryOption("CA", R.string.country_ca, "1", "🇨🇦"),
        CountryOption("MA", R.string.country_ma, "212", "🇲🇦"),
        CountryOption("DZ", R.string.country_dz, "213", "🇩🇿"),
        CountryOption("TN", R.string.country_tn, "216", "🇹🇳"),
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
