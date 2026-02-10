-- Add processing/application fee to packages
ALTER TABLE packages ADD COLUMN processing_fee DECIMAL(10, 2) NULL;

-- Add waive application fee flag to promos (default false)
ALTER TABLE promo ADD COLUMN waive_application_fee BOOLEAN NOT NULL DEFAULT FALSE;

