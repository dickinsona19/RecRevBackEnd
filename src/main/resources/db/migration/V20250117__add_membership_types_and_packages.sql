-- Add membership type and punch card fields to membership table
ALTER TABLE membership ADD COLUMN membership_type VARCHAR(50) DEFAULT 'UNLIMITED';
ALTER TABLE membership ADD COLUMN punch_count INT;
ALTER TABLE membership ADD COLUMN expiry_days INT;
ALTER TABLE membership ADD COLUMN one_off_type VARCHAR(50);
ALTER TABLE membership ADD COLUMN processing_fee DECIMAL(10, 2);

-- Create packages table
CREATE TABLE IF NOT EXISTS packages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    business_id BIGINT NOT NULL,
    business_tag VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stripe_product_id VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    archived BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

-- Create package_memberships join table
CREATE TABLE IF NOT EXISTS package_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    package_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE CASCADE,
    FOREIGN KEY (membership_id) REFERENCES membership(id) ON DELETE CASCADE,
    UNIQUE (package_id, membership_id)
);

-- Create user_package table (for tracking which users have which packages)
-- Note: This is handled via UserBusinessMembership with package_id, but we'll add the column there
ALTER TABLE user_business_membership ADD COLUMN package_id BIGINT;
ALTER TABLE user_business_membership ADD COLUMN punches_remaining INT;
ALTER TABLE user_business_membership ADD COLUMN punches_expiry_date DATETIME;
ALTER TABLE user_business_membership ADD COLUMN processing_fee_paid DECIMAL(10, 2);
ALTER TABLE user_business_membership ADD FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE SET NULL;

-- Make membership_id nullable since packages can be assigned without individual membership
ALTER TABLE user_business_membership ALTER COLUMN membership_id BIGINT NULL;

-- Create punch_card_scans table for double-scan protection
CREATE TABLE IF NOT EXISTS punch_card_scans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_business_membership_id BIGINT NOT NULL,
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_business_membership_id) REFERENCES user_business_membership(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_packages_business_tag ON packages(business_tag);
CREATE INDEX IF NOT EXISTS idx_packages_archived ON packages(archived);
CREATE INDEX IF NOT EXISTS idx_package_memberships_package ON package_memberships(package_id);
CREATE INDEX IF NOT EXISTS idx_package_memberships_membership ON package_memberships(membership_id);
CREATE INDEX IF NOT EXISTS idx_punch_scans_membership ON punch_card_scans(user_business_membership_id);
CREATE INDEX IF NOT EXISTS idx_punch_scans_time ON punch_card_scans(scanned_at);
