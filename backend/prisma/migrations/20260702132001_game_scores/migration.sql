-- CreateTable
CREATE TABLE "game_scores" (
    "day" INTEGER NOT NULL,
    "device_id" TEXT NOT NULL,
    "time_ms" INTEGER NOT NULL,
    "mistakes" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "game_scores_pkey" PRIMARY KEY ("day","device_id")
);

-- CreateIndex
CREATE INDEX "game_scores_day_time_ms_idx" ON "game_scores"("day", "time_ms");
