-- Multi-game leaderboards: add a game discriminator so DEFENSE and TRACE (and
-- future games) each get their own board. Existing rows are DEFENSE.
ALTER TABLE "game_scores" ADD COLUMN "game_type" TEXT NOT NULL DEFAULT 'defense';

-- Rework the primary key to be per (day, device, game).
ALTER TABLE "game_scores" DROP CONSTRAINT "game_scores_pkey";
ALTER TABLE "game_scores" ADD CONSTRAINT "game_scores_pkey" PRIMARY KEY ("day", "device_id", "game_type");

-- Leaderboard queries always filter by game first now.
DROP INDEX "game_scores_day_score_idx";
CREATE INDEX "game_scores_game_type_day_score_idx" ON "game_scores"("game_type", "day", "score");
