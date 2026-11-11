package com.whocalled.android.data

import androidx.annotation.StringRes
import com.whocalled.android.R

/**
 * Predefined report categories (no free text → safe for RGPD, no moderation).
 * `api` is the value sent to the backend; `labelRes` is shown in the UI.
 */
enum class ReportCategory(val api: String, @param:StringRes val labelRes: Int) {
    TELEMARKETING("telemarketing", R.string.category_telemarketing),
    SCAM("scam", R.string.category_scam),
    ROBOCALL("robocall", R.string.category_robocall),
    SILENT("silent", R.string.category_silent),
    DEBT("debt", R.string.category_debt),
    SURVEY("survey", R.string.category_survey),
    OTHER("unknown", R.string.category_other);

    companion object {
        fun fromApi(value: String?): ReportCategory? =
            entries.firstOrNull { it.api == value }
    }
}
