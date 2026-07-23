# who-called proguard rules
#
# Gson (de)serializes by reflection on field names: every model that goes
# through WhoCalledApi MUST keep its fields, or release builds break with
# "Abstract classes can't be instantiated ... Class name: xy2" (R8 renaming).
-keep class com.whocalled.android.network.** { *; }
# TRACE puzzles live in the game package but come from GET /game/puzzle.
-keep class com.whocalled.android.game.TracePuzzleSet { *; }
-keep class com.whocalled.android.game.TraceGrid { *; }
