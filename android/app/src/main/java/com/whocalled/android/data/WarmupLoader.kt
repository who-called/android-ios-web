package com.whocalled.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Preloads the embedded warm-up database (assets/warmup.sqlite) into the single
 * Room cache on first launch, so the app blocks/labels known spam numbers
 * immediately — offline, before any network sync. No-op once the cache is
 * populated. After this, GET /lists deltas keep the same table up to date.
 */
object WarmupLoader {
    private const val TAG = "WarmupLoader"
    private const val ASSET = "warmup.sqlite"

    suspend fun loadIfNeeded(context: Context, db: WhoCalledDatabase) {
        if (db.scoredNumberDao().count() > 0) return

        val tmp = File(context.cacheDir, "warmup.sqlite")
        try {
            context.assets.open(ASSET).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "no warm-up asset bundled ($ASSET): ${e.message}")
            return
        }

        var loaded = 0
        try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { src ->
                src.rawQuery(
                    "SELECT phone, status, spamScore, category, updatedAt FROM scored_numbers", null,
                ).use { c ->
                    val batch = ArrayList<ScoredNumberEntity>(2000)
                    while (c.moveToNext()) {
                        batch.add(
                            ScoredNumberEntity(
                                phone = c.getString(0),
                                status = c.getString(1),
                                spamScore = c.getInt(2),
                                category = c.getString(3),
                                updatedAt = c.getLong(4),
                            ),
                        )
                        if (batch.size >= 2000) {
                            db.scoredNumberDao().upsertAll(batch)
                            loaded += batch.size
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) {
                        db.scoredNumberDao().upsertAll(batch)
                        loaded += batch.size
                    }
                }
            }
            Log.i(TAG, "Warm-up loaded $loaded numbers")
        } catch (e: Exception) {
            Log.e(TAG, "warm-up load failed: ${e.message}", e)
        } finally {
            tmp.delete()
        }
    }
}
