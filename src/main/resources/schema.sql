-- Create the user_titles table
CREATE TABLE IF NOT EXISTS user_titles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) UNIQUE NOT NULL
);

-- Create clients table
CREATE TABLE IF NOT EXISTS clients (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    status VARCHAR(50),
    UNIQUE (email)
);

-- Businesses table (primary business entities)
-- Note: staff_id foreign key will be added after staff table is populated
CREATE TABLE IF NOT EXISTS businesses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    logo_url VARCHAR(255),
    status VARCHAR(50),
    created_at DATETIME NOT NULL,
    business_tag VARCHAR(100),
    client_id INTEGER NOT NULL,
    staff_id INTEGER,
    stripe_account_id VARCHAR(100),
    onboarding_status VARCHAR(50) DEFAULT 'NOT_STARTED',
    UNIQUE (business_tag),
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);


-- Staff table (created AFTER businesses table, but business_id can be NULL initially)
CREATE TABLE IF NOT EXISTS staff (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    type VARCHAR(50),  -- Deprecated, kept for backward compatibility
    business_id BIGINT,
    club_id BIGINT,  -- Deprecated, kept for backward compatibility
    invite_token VARCHAR(255),
    invite_token_expiry DATETIME,
    is_active BOOLEAN DEFAULT TRUE,
    invited_by INTEGER,
    created_at DATETIME,
    last_login_at DATETIME,
    UNIQUE (email),
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE SET NULL,
    FOREIGN KEY (club_id) REFERENCES businesses(id) ON DELETE SET NULL
);

-- Membership table
CREATE TABLE IF NOT EXISTS membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    price VARCHAR(50) NOT NULL,
    charge_interval VARCHAR(50) NOT NULL,
    club_tag VARCHAR(100) NOT NULL,  -- Note: still using club_tag for backward compatibility
    business_tag VARCHAR(100),  -- Maps to business_tag in entity
    stripe_price_id VARCHAR(255),
    archived BOOLEAN DEFAULT FALSE
);

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50),
    is_in_good_standing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    entry_qrcode_token VARCHAR(255) NOT NULL,
    user_stripe_member_id VARCHAR(100),
    user_title_id INTEGER,
    "isOver18" BOOLEAN NOT NULL DEFAULT FALSE,
    "lockedInRate" VARCHAR(50),
    signature_data TEXT,
    waiver_signed_date DATETIME,
    profile_picture_url VARCHAR(255),
    referral_code VARCHAR(50),
    parent_id BIGINT,
    referred_by_id BIGINT,
    membership_id BIGINT,
    UNIQUE (email),
    UNIQUE (phone_number),
    UNIQUE (entry_qrcode_token),
    UNIQUE (user_stripe_member_id),
    UNIQUE (referral_code),
    FOREIGN KEY (user_title_id) REFERENCES user_titles(id),
    FOREIGN KEY (parent_id) REFERENCES users(id),
    FOREIGN KEY (referred_by_id) REFERENCES users(id),
    FOREIGN KEY (membership_id) REFERENCES membership(id)
);

-- User clubs table
CREATE TABLE IF NOT EXISTS user_clubs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    membership_id BIGINT,
    stripe_id VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (membership_id) REFERENCES membership(id),
    CONSTRAINT unique_user_business UNIQUE (user_id, business_id)
);

-- Create user_club_memberships junction table for multiple memberships per user-club relationship
CREATE TABLE IF NOT EXISTS user_club_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_club_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    anchor_date DATETIME NOT NULL,
    end_date DATETIME NULL,
    stripe_subscription_id VARCHAR(255) NULL,
    pause_start_date DATETIME NULL,
    pause_end_date DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ucm_user_club FOREIGN KEY (user_club_id) REFERENCES user_clubs(id) ON DELETE CASCADE,
    CONSTRAINT fk_ucm_membership FOREIGN KEY (membership_id) REFERENCES membership(id) ON DELETE CASCADE
);

-- Create member_logs table for tracking user activity at businesses
CREATE TABLE IF NOT EXISTS member_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_club_id BIGINT NOT NULL,
    log_text TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_ml_user_club FOREIGN KEY (user_club_id) REFERENCES user_clubs(id) ON DELETE CASCADE
);

-- Create indexes for user_club_memberships table
CREATE INDEX IF NOT EXISTS idx_ucm_user_club_id ON user_club_memberships(user_club_id);
CREATE INDEX IF NOT EXISTS idx_ucm_membership_id ON user_club_memberships(membership_id);
CREATE INDEX IF NOT EXISTS idx_ucm_status ON user_club_memberships(status);
CREATE INDEX IF NOT EXISTS idx_ucm_anchor_date ON user_club_memberships(anchor_date);
CREATE INDEX IF NOT EXISTS idx_ucm_stripe_subscription ON user_club_memberships(stripe_subscription_id);

-- Create indexes for member_logs table
CREATE INDEX IF NOT EXISTS idx_ml_user_club_id ON member_logs(user_club_id);
CREATE INDEX IF NOT EXISTS idx_ml_created_at ON member_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_ml_log_type ON member_logs(log_type);

-- Sign in logs table
CREATE TABLE IF NOT EXISTS sign_in_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sign_in_time DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create recent_activity table for tracking business activities
CREATE TABLE IF NOT EXISTS recent_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    amount DOUBLE,
    customer_name VARCHAR(255),
    stripe_event_id VARCHAR(255) UNIQUE,
    created_at DATETIME NOT NULL
);

-- Create indexes for recent_activity table
CREATE INDEX IF NOT EXISTS idx_ra_club_id ON recent_activity(club_id);
CREATE INDEX IF NOT EXISTS idx_ra_created_at ON recent_activity(created_at);
CREATE INDEX IF NOT EXISTS idx_ra_activity_type ON recent_activity(activity_type);

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    definition TEXT,
    price DOUBLE NOT NULL,
    image_url VARCHAR(255),
    category VARCHAR(100),
    stripe_product_id VARCHAR(100),
    club_tag VARCHAR(30) NOT NULL,  -- Note: kept for backward compatibility
    business_tag VARCHAR(100)  -- Maps to business_tag in entity
);

-- ============================================
-- INSERT DATA
-- ============================================

-- Insert user titles
INSERT INTO user_titles (title) VALUES
('Member'),
('VIP Member'),
('Guest')
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- Insert clients
INSERT INTO clients (id, email, password, created_at, status) VALUES
(1, 'anndreuis@gmail.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '2025-08-19 11:00:00', 'ACTIVE'),
(2, 'jane.smith@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '2025-08-18 09:30:00', 'INACTIVE'),
(3, 'bob.jones@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '2025-08-17 14:15:00', 'ACTIVE')
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Insert businesses (without staff_id initially to avoid circular dependency)
INSERT INTO businesses (id, title, logo_url, status, created_at, business_tag, client_id, staff_id, stripe_account_id, onboarding_status) VALUES
(1, 'John''s Fitness Club', 'https://example.com/logos/johns_fitness.png', 'ACTIVE', '2025-08-19 12:00:00', 'JFC001', 1, NULL, 'acct_1SAhtKLfLLcJtrGn', 'COMPLETED'),
(2, 'John''s Yoga Studio', 'https://example.com/logos/yoga_studio.png', 'ACTIVE', '2025-08-19 13:00:00', 'JYS002', 1, NULL, NULL, 'NOT_STARTED'),
(3, 'Jane''s Gym', 'https://example.com/logos/janes_gym.png', 'INACTIVE', '2025-08-18 10:00:00', 'JG003', 2, NULL, NULL, 'NOT_STARTED'),
(4, 'Bob''s Weightlifting Center', NULL, 'ACTIVE', '2025-08-17 15:00:00', 'BWC004', 3, NULL, NULL, 'NOT_STARTED')
ON DUPLICATE KEY UPDATE business_tag = VALUES(business_tag);


-- Insert staff (now that businesses exist)
-- Using new RBAC roles: ADMIN, MANAGER, TEAM_MEMBER
-- Password for all test staff: "RecRev" (hashed with BCrypt)
INSERT INTO staff (id, first_name, last_name, email, password, role, type, business_id, is_active, created_at) VALUES
(1, 'Alice', 'Admin', 'admin@test.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', 'ADMIN', 'ADMIN', 1, TRUE, '2025-08-19 12:00:00'),
(2, 'Bob', 'Manager', 'manager@test.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', 'MANAGER', 'MANAGER', 1, TRUE, '2025-08-19 12:00:00'),
(3, 'Carol', 'TeamMember', 'teammember@test.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', 'TEAM_MEMBER', 'TEAM_MEMBER', 1, TRUE, '2025-08-19 12:00:00'),
(4, 'David', 'Manager2', 'manager2@test.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', 'MANAGER', 'MANAGER', 2, TRUE, '2025-08-19 12:00:00'),
(5, 'Eve', 'TeamMember2', 'teammember2@test.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', 'TEAM_MEMBER', 'TEAM_MEMBER', 2, TRUE, '2025-08-19 12:00:00')
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Now update businesses to set staff_id (after staff is created)
-- Note: staff_id in businesses table is optional and can reference a primary staff member
UPDATE businesses SET staff_id = 1 WHERE id = 1;
UPDATE businesses SET staff_id = 4 WHERE id = 2;


-- Add foreign key constraints for staff_id (after data is populated)
-- Note: H2 may not support adding foreign keys after table creation, so we'll skip this if it fails
-- The constraint is not critical for functionality

-- Insert memberships
INSERT INTO membership (id, title, price, charge_interval, club_tag, business_tag, stripe_price_id, archived) VALUES
(1, 'Basic Membership', '29.99', 'MONTHLY', 'JFC001', 'JFC001', NULL, FALSE),
(2, 'Premium Membership', '59.99', 'MONTHLY', 'JFC001', 'JFC001', NULL, FALSE),
(3, 'Yoga Pass', '39.99', 'MONTHLY', 'JYS002', 'JYS002', NULL, FALSE),
(4, 'Weightlifting Monthly', '49.99', 'MONTHLY', 'BWC004', 'BWC004', NULL, FALSE),
(5, 'testing', '9.99', 'MONTHLY', 'JFC001', 'JFC001', 'price_1SQGLQLfLLcJtrGn9HSLDmka', FALSE)
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- Insert users
INSERT INTO users (id, first_name, last_name, email, password, phone_number, is_in_good_standing, created_at, entry_qrcode_token, user_stripe_member_id, user_title_id, "isOver18", "lockedInRate", signature_data, waiver_signed_date, profile_picture_url, referral_code, parent_id, referred_by_id, membership_id) VALUES
(1, 'Alice', 'Smith', 'alice.smith@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '1234567890', TRUE, '2025-08-19 10:00:00', 'qrcode_001', 'stripe_mem_001', 1, TRUE, '29.99', 'signature_data_001', '2025-08-19 10:05:00', 'https://example.com/profiles/alice.png', 'REF001', NULL, NULL, 1),
(2, 'Bob', 'Johnson', 'bob.johnson@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '0987654321', TRUE, '2025-08-19 11:00:00', 'qrcode_002', 'stripe_mem_002', 2, TRUE, '59.99', 'signature_data_002', '2025-08-19 11:05:00', NULL, 'REF002', NULL, 1, 2),
(3, 'Charlie', 'Brown', 'charlie.brown@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '1122334455', FALSE, '2025-08-18 12:00:00', 'qrcode_003', NULL, 3, FALSE, NULL, NULL, NULL, NULL, 'REF003', 1, NULL, 3),
(4, 'Diana', 'Wilson', 'diana.wilson@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '5566778899', TRUE, '2025-08-17 13:00:00', 'qrcode_004', 'stripe_mem_003', 1, TRUE, '49.99', 'signature_data_003', '2025-08-17 13:05:00', 'https://example.com/profiles/diana.png', 'REF004', NULL, 2, 4)
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Insert user_clubs
INSERT INTO user_clubs (id, user_id, business_id, membership_id, stripe_id, status, created_at) VALUES
(1, 1, 1, 1, NULL, 'ACTIVE', '2025-08-19 10:10:00'), -- Alice in John's Fitness Club with Basic Membership
(2, 1, 2, 3, NULL, 'ACTIVE', '2025-08-19 10:15:00'), -- Alice in John's Yoga Studio with Yoga Pass
(3, 2, 1, 2, 'stripe_sub_002', 'ACTIVE', '2025-08-19 11:10:00'), -- Bob in John's Fitness Club with Premium Membership
(4, 3, 3, NULL, NULL, 'PENDING', '2025-08-18 12:10:00'), -- Charlie in Jane's Gym (no membership yet)
(5, 4, 4, 4, 'stripe_sub_004', 'ACTIVE', '2025-08-17 13:10:00') -- Diana in Bob's Weightlifting Center
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- Insert sign_in_logs
INSERT INTO sign_in_logs (id, user_id, sign_in_time) VALUES
(1, 1, '2025-08-19 10:30:00'),
(2, 1, '2025-08-19 14:00:00'),
(3, 2, '2025-08-19 11:30:00'),
(4, 4, '2025-08-17 14:00:00')
ON DUPLICATE KEY UPDATE sign_in_time = VALUES(sign_in_time);

-- Insert products
INSERT INTO products (id, name, definition, price, image_url, category, stripe_product_id, club_tag, business_tag) VALUES
(1, 'Protein Shake', 'High-protein shake for post-workout recovery', 5.99, 'https://example.com/products/protein_shake.png', 'Supplement', 'prod_001', 'JFC001', 'JFC001'),
(2, 'Yoga Mat', 'Non-slip yoga mat for studio use', 29.99, 'https://example.com/products/yoga_mat.png', 'Equipment', 'prod_002', 'JFC001', 'JFC001'),
(3, 'Weightlifting Belt', 'Supportive belt for heavy lifts', 39.99, NULL, 'Equipment', 'prod_003', 'JFC001', 'JFC001'),
(4, 'Energy Drink', 'Sugar-free energy drink', 3.99, 'https://example.com/products/energy_drink.png', 'Supplement', 'prod_004', 'JYS002', 'JYS002')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Migrate existing memberships from user_clubs table to user_club_memberships junction table
-- This moves membership data from the old schema to the new multi-membership structure
INSERT INTO user_club_memberships (user_club_id, membership_id, status, anchor_date, stripe_subscription_id, created_at, updated_at)
SELECT
    uc.id as user_club_id,
    uc.membership_id,
    uc.status as status,
    uc.created_at as anchor_date,
    uc.stripe_id as stripe_subscription_id,
    uc.created_at as created_at,
    uc.created_at as updated_at
FROM user_clubs uc
WHERE uc.membership_id IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM user_club_memberships ucm 
    WHERE ucm.user_club_id = uc.id AND ucm.membership_id = uc.membership_id
);
