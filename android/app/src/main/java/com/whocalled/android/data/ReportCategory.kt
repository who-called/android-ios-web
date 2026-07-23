package com.whocalled.android.data

/**
 * Predefined report categories (no free text → safe for RGPD, no moderation).
 * `api` is the value sent to the backend; `label` is shown in the UI.
 */
enum class ReportCategory(val api: String, val label: String) {
    TELEMARKETING("telemarketing", "Démarchage"),
    SCAM("scam", "Arnaque"),
    ROBOCALL("robocall", "Appel automatisé"),
    SILENT("silent", "Appel silencieux"),
    DEBT("debt", "Recouvrement"),
    SURVEY("survey", "Sondage"),
    OTHER("unknown", "Autre");

    companion object {
        fun fromApi(value: String?): ReportCategory? =
            entries.firstOrNull { it.api == value }
    }
}
