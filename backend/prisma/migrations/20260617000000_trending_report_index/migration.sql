-- CreateIndex
-- Supports GET /trending: recent spam reports within a rolling window.
CREATE INDEX "reports_vote_created_at_idx" ON "reports"("vote", "created_at");
