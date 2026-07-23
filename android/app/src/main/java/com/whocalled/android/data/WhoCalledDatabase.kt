package com.whocalled.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScoredNumberEntity::class,
        UserRuleEntity::class,
        CallLogEntity::class,
        MyReportEntity::class,
        PatternEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class WhoCalledDatabase : RoomDatabase() {
    abstract fun scoredNumberDao(): ScoredNumberDao
    abstract fun userRuleDao(): UserRuleDao
    abstract fun callLogDao(): CallLogDao
    abstract fun myReportDao(): MyReportDao
    abstract fun patternDao(): PatternDao

    companion object {
        @Volatile
        private var instance: WhoCalledDatabase? = null

        fun get(context: Context): WhoCalledDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): WhoCalledDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WhoCalledDatabase::class.java,
                "who-called.db",
            )
                // Screening service reads on the binder thread; queries are tiny + indexed.
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
