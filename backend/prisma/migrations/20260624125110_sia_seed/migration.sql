-- CreateTable
CREATE TABLE "sia_seed" (
    "phone" TEXT NOT NULL,
    "pos" INTEGER NOT NULL DEFAULT 0,
    "neg" INTEGER NOT NULL DEFAULT 0,
    "neu" INTEGER NOT NULL DEFAULT 0,
    "category_id" INTEGER NOT NULL DEFAULT 0,
    "category" TEXT NOT NULL DEFAULT 'unknown',
    "db_ver" INTEGER NOT NULL DEFAULT 0,
    "imported_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sia_seed_pkey" PRIMARY KEY ("phone")
);
