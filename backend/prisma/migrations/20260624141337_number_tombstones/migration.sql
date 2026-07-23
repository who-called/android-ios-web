-- CreateTable
CREATE TABLE "number_tombstones" (
    "phone" TEXT NOT NULL,
    "removed_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "number_tombstones_pkey" PRIMARY KEY ("phone")
);

-- CreateIndex
CREATE INDEX "number_tombstones_removed_at_idx" ON "number_tombstones"("removed_at");
