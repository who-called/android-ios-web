-- DEFENSE arcade game: score-based leaderboard (higher = better).
ALTER TABLE "game_scores" ADD COLUMN "score" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "game_scores" ADD COLUMN "waves" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE "game_scores" ALTER COLUMN "time_ms" SET DEFAULT 0;

CREATE INDEX "game_scores_day_score_idx" ON "game_scores"("day", "score");
