-- CreateTable
CREATE TABLE "sia_sync" (
    "region" TEXT NOT NULL,
    "db_ver" INTEGER NOT NULL DEFAULT 0,
    "synced_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sia_sync_pkey" PRIMARY KEY ("region")
);
