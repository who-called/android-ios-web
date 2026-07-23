-- CreateTable
CREATE TABLE "numbers" (
    "phone" TEXT NOT NULL,
    "spam_score" INTEGER NOT NULL DEFAULT 0,
    "category" TEXT NOT NULL DEFAULT 'unknown',
    "report_count_spam" INTEGER NOT NULL DEFAULT 0,
    "report_count_legit" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'unknown',
    "first_reported_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "last_reported_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "numbers_pkey" PRIMARY KEY ("phone")
);

-- CreateTable
CREATE TABLE "reports" (
    "id" BIGSERIAL NOT NULL,
    "phone" TEXT NOT NULL,
    "device_id" TEXT NOT NULL,
    "vote" TEXT NOT NULL,
    "category" TEXT,
    "locale" TEXT NOT NULL DEFAULT 'fr',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "reports_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "devices" (
    "device_id" TEXT NOT NULL,
    "reputation_weight" DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    "reports_today" INTEGER NOT NULL DEFAULT 0,
    "last_report_date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "devices_pkey" PRIMARY KEY ("device_id")
);

-- CreateIndex
CREATE INDEX "numbers_status_idx" ON "numbers"("status");

-- CreateIndex
CREATE INDEX "numbers_updated_at_idx" ON "numbers"("updated_at");

-- CreateIndex
CREATE INDEX "reports_phone_idx" ON "reports"("phone");

-- CreateIndex
CREATE INDEX "reports_device_id_idx" ON "reports"("device_id");

-- AddForeignKey
ALTER TABLE "reports" ADD CONSTRAINT "reports_phone_fkey" FOREIGN KEY ("phone") REFERENCES "numbers"("phone") ON DELETE CASCADE ON UPDATE CASCADE;
