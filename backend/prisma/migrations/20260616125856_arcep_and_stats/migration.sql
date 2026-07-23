-- AlterTable
ALTER TABLE "numbers" ADD COLUMN     "source" TEXT NOT NULL DEFAULT 'community';

-- CreateTable
CREATE TABLE "patterns" (
    "pattern" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'block',
    "category" TEXT NOT NULL DEFAULT 'telemarketing',
    "source" TEXT NOT NULL DEFAULT 'arcep',
    "name" TEXT,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "patterns_pkey" PRIMARY KEY ("pattern")
);

-- CreateIndex
CREATE INDEX "patterns_updated_at_idx" ON "patterns"("updated_at");
