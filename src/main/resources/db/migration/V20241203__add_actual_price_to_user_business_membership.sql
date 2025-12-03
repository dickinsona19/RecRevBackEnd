-- Add actual_price column to store per-membership price snapshots
ALTER TABLE user_business_membership
    ADD COLUMN actual_price DECIMAL(10, 2) NULL;

-- Backfill actual_price from the current membership price definition
UPDATE user_business_membership ubm
LEFT JOIN membership m ON ubm.membership_id = m.id
SET ubm.actual_price = CASE
        WHEN m.price IS NULL OR m.price = '' THEN NULL
        WHEN m.price REGEXP '^[0-9]+(\\.[0-9]{1,2})?$' THEN CAST(m.price AS DECIMAL(10, 2))
        ELSE CAST(REPLACE(REPLACE(m.price, '$', ''), ',', '') AS DECIMAL(10, 2))
    END;

-- Ensure no nulls remain
UPDATE user_business_membership
SET actual_price = 0
WHERE actual_price IS NULL;

-- Enforce not-null going forward
ALTER TABLE user_business_membership
    MODIFY COLUMN actual_price DECIMAL(10, 2) NOT NULL DEFAULT 0;

