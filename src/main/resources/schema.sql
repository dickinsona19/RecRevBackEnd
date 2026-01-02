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
    contact_email VARCHAR(255),
    referred_user_discount_months INT DEFAULT 1,
    referred_user_waive_activation_fee BOOLEAN DEFAULT TRUE,
    referrer_discount_months INT DEFAULT 1,
    referrer_waive_activation_fee BOOLEAN DEFAULT TRUE,
    UNIQUE (business_tag),
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

-- Membership table
CREATE TABLE IF NOT EXISTS membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    price VARCHAR(50) NOT NULL,
    charge_interval VARCHAR(50) NOT NULL,
    club_tag VARCHAR(100),
    business_tag VARCHAR(100),
    stripe_price_id VARCHAR(255),
    archived BOOLEAN DEFAULT FALSE,
    is_public BOOLEAN DEFAULT FALSE,
    public_display_name VARCHAR(255),
    public_description TEXT,
    public_benefits TEXT
);

-- User table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    phone_number VARCHAR(20),
    is_in_good_standing BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    entry_qrcode_token VARCHAR(255),
    user_stripe_member_id VARCHAR(255),
    user_title_id BIGINT,
    "isOver18" BOOLEAN DEFAULT FALSE,
    "lockedInRate" VARCHAR(50),
    signature_data TEXT,
    waiver_signed_date DATETIME,
    user_type VARCHAR(32) NOT NULL DEFAULT 'REGULAR',
    profile_picture_url VARCHAR(500),
    referral_code VARCHAR(50) UNIQUE,
    parent_id BIGINT,
    referred_by_id BIGINT,
    membership_id BIGINT,
    waiver_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SIGNED',
    FOREIGN KEY (user_title_id) REFERENCES user_titles(id),
    FOREIGN KEY (parent_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (referred_by_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (membership_id) REFERENCES membership(id) ON DELETE SET NULL
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
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE SET NULL
);

-- Waiver templates table
CREATE TABLE IF NOT EXISTS waiver_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_id BIGINT NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    version INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_id, version),
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

-- User waiver signatures table
CREATE TABLE IF NOT EXISTS user_waiver (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    waiver_template_id BIGINT NOT NULL,
    signed_at DATETIME NOT NULL,
    signature_image_url VARCHAR(500),
    final_pdf_url VARCHAR(500),
    signer_ip VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, waiver_template_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    FOREIGN KEY (waiver_template_id) REFERENCES waiver_template(id) ON DELETE CASCADE
);

-- Failed Payment Attempts table - tracks retry attempts for failed payments
CREATE TABLE IF NOT EXISTS failed_payment_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    user_business_id BIGINT,
    business_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    failure_reason TEXT,
    status VARCHAR(50) NOT NULL, -- PENDING_RETRY, RETRYING, SUCCEEDED, EXHAUSTED, MANUAL_RETRY
    retry_attempt_count INT DEFAULT 0,
    max_retry_attempts INT DEFAULT 4,
    next_retry_date DATETIME,
    last_retry_date DATETIME,
    succeeded_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    INDEX idx_status (status),
    INDEX idx_next_retry_date (next_retry_date),
    INDEX idx_invoice_id (invoice_id),
    INDEX idx_user_id (user_id)
);

-- UserBusiness table (links users to businesses/clubs)
CREATE TABLE IF NOT EXISTS user_business (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    stripe_id VARCHAR(255),
    status VARCHAR(50),
    notes TEXT,
    has_ever_had_membership BOOLEAN DEFAULT FALSE,
    is_delinquent BOOLEAN DEFAULT FALSE,
    calculated_status VARCHAR(50),
    calculated_user_type VARCHAR(50),
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    UNIQUE (user_id, business_id)
);

-- UserBusinessMembership table (links users to memberships within a business)
CREATE TABLE IF NOT EXISTS user_business_membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_business_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    stripe_subscription_id VARCHAR(255),
    anchor_date DATETIME,
    end_date DATETIME,
    pause_start_date DATETIME,
    pause_end_date DATETIME,
    actual_price DECIMAL(10, 2) NOT NULL DEFAULT 0,
    signature_data_url TEXT,
    signed_at DATETIME,
    signer_name VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_business_id) REFERENCES user_business(id) ON DELETE CASCADE,
    FOREIGN KEY (membership_id) REFERENCES membership(id) ON DELETE CASCADE
);

-- Member Logs table
CREATE TABLE IF NOT EXISTS member_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_business_id BIGINT NOT NULL,
    log_text TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    FOREIGN KEY (user_business_id) REFERENCES user_business(id) ON DELETE CASCADE
);

-- Create sign_in_logs table
CREATE TABLE IF NOT EXISTS sign_in_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sign_in_time DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Analytics Cache table
CREATE TABLE IF NOT EXISTS analytics_cache (
    cache_key VARCHAR(255) PRIMARY KEY,
    analytics_data TEXT,
    last_updated DATETIME
);

-- Recent Activity table
CREATE TABLE IF NOT EXISTS recent_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    amount DOUBLE,
    customer_name VARCHAR(255),
    stripe_event_id VARCHAR(255),
    created_at DATETIME NOT NULL
);

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    definition VARCHAR(255),
    price DOUBLE,
    image_url VARCHAR(500),
    category VARCHAR(255),
    stripe_product_id VARCHAR(255),
    stripe_price_id VARCHAR(255),
    business_tag VARCHAR(100),
    club_tag VARCHAR(100) -- Backward compatibility
);

-- Promo table
CREATE TABLE IF NOT EXISTS promo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    code_token VARCHAR(255) UNIQUE,
    business_id BIGINT,
    stripe_promo_code_id VARCHAR(255),
    stripe_coupon_id VARCHAR(255),
    discount_value DOUBLE,
    discount_type VARCHAR(50),
    duration VARCHAR(50),
    duration_in_months INT,
    free_pass_count INT DEFAULT 0,
    url_visit_count INT DEFAULT 0,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

-- Promo Users table (Many-to-Many relationship)
CREATE TABLE IF NOT EXISTS promo_users (
    promo_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (promo_id, user_id),
    FOREIGN KEY (promo_id) REFERENCES promo(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Insert user titles
INSERT INTO user_titles (title) VALUES
('Member'),
('VIP Member'),
('Guest')
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- Insert clients
INSERT INTO clients (id, email, password, created_at, status) VALUES
(1, 'anndreuis@gmail.com', '$2a$10$DwmUDtXsKsCL7sa1VdCSBuBDRbKY4qltEwId8yCvWovb8I26VZwG.', '2025-08-19 11:00:00', 'ACTIVE'),
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
(5, 'testing', '29.99', 'MONTHLY', 'JFC001', 'JFC001', 'price_1SQGLQLfLLcJtrGn9HSLDmka', FALSE)
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- Insert users
INSERT INTO users (id, first_name, last_name, email, password, phone_number, is_in_good_standing, created_at, entry_qrcode_token, user_stripe_member_id, user_title_id, "isOver18", "lockedInRate", signature_data, waiver_signed_date, profile_picture_url, referral_code, parent_id, referred_by_id, membership_id) VALUES
(1, 'Alice', 'Smith', 'alice.smith@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '1234567890', TRUE, '2025-08-19 10:00:00', 'qrcode_001', 'stripe_mem_001', 1, TRUE, '29.99', 'signature_data_001', '2025-08-19 10:05:00', 'https://example.com/profiles/alice.png', 'REF001', NULL, NULL, 1),
(2, 'Bob', 'Johnson', 'bob.johnson@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '0987654321', TRUE, '2025-08-19 11:00:00', 'qrcode_002', 'stripe_mem_002', 2, TRUE, '59.99', 'signature_data_002', '2025-08-19 11:05:00', NULL, 'REF002', NULL, 1, 2),
(3, 'Charlie', 'Brown', 'charlie.brown@example.com', '$2a$10$hjxdcTpSSMIzYVc4ajNWsurcT2vf7CUJuqqMAHLvFAQr8nmQbXLHm', '1122334455', FALSE, '2025-08-18 12:00:00', 'qrcode_003', NULL, 3, FALSE, NULL, NULL, NULL, NULL, 'REF003', 1, NULL, 3)
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Insert user_business relationships
INSERT INTO user_business (id, user_id, business_id, stripe_id, created_at) VALUES
(1, 1, 1, 'stripe_uc_001', '2025-08-19 10:00:00'),
(2, 2, 1, 'stripe_uc_002', '2025-08-19 11:00:00'),
(3, 3, 2, 'stripe_uc_003', '2025-08-18 12:00:00')
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- Insert user_business_membership relationships
INSERT INTO user_business_membership (id, user_business_id, membership_id, status, stripe_subscription_id, anchor_date, end_date, pause_start_date, pause_end_date, actual_price, created_at, updated_at) VALUES
(1, 1, 1, 'ACTIVE', 'sub_001', '2025-08-19 10:00:00', NULL, NULL, NULL, 29.99, '2025-08-19 10:00:00', '2025-08-19 10:00:00'),
(2, 2, 2, 'ACTIVE', 'sub_002', '2025-08-19 11:00:00', NULL, NULL, NULL, 49.99, '2025-08-19 11:00:00', '2025-08-19 11:00:00'),
(3, 3, 3, 'PAUSED', 'sub_003', '2025-08-18 12:00:00', NULL, '2025-08-20 00:00:00', '2025-09-20 00:00:00', 59.99, '2025-08-18 12:00:00', '2025-08-20 00:00:00')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- Insert products (Sample Data)
INSERT INTO products (id, name, definition, price, image_url, category, stripe_product_id, business_tag, club_tag) VALUES
(1, 'Whey Protein Powder', 'Premium whey protein for muscle building and recovery', 49.99, '', 'supplements', NULL, 'JFC001', 'JFC001'),
(2, 'Resistance Bands Set', 'Complete set of resistance bands for strength training', 29.99, '', 'equipment', NULL, 'JFC001', 'JFC001'),
(3, 'Gym T-Shirt', 'Comfortable moisture-wicking workout shirt', 24.99, '', 'apparel', NULL, 'JFC001', 'JFC001'),
(4, 'Water Bottle', 'Insulated stainless steel water bottle', 19.99, '', 'accessories', NULL, 'JFC001', 'JFC001'),
(5, 'Pre-Workout Supplement', 'Energy boost for intense workout sessions', 39.99, '', 'supplements', NULL, 'JFC001', 'JFC001'),
(6, 'Yoga Mat', 'Non-slip yoga mat for floor exercises', 34.99, '', 'equipment', NULL, 'JYS002', 'JYS002')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Insert recent activity (Seed Data for Dashboard)
INSERT INTO recent_activity (business_id, activity_type, description, amount, customer_name, stripe_event_id, created_at) VALUES
(1, 'PAYMENT', 'Payment received from Alice Smith', 29.99, 'Alice Smith', 'evt_001', CURRENT_TIMESTAMP),
(1, 'NEW_MEMBER', 'New member Bob Johnson joined', NULL, 'Bob Johnson', NULL, CURRENT_TIMESTAMP),
(1, 'PAYMENT', 'Payment received from Bob Johnson', 59.99, 'Bob Johnson', 'evt_002', DATEADD('HOUR', -2, CURRENT_TIMESTAMP)),
(1, 'FAILED_PAYMENT', 'Failed payment from Charlie Brown', 39.99, 'Charlie Brown', 'evt_003', DATEADD('DAY', -1, CURRENT_TIMESTAMP));

-- Update UserBusiness creation dates to be recent for analytics
UPDATE user_business SET created_at = CURRENT_TIMESTAMP WHERE id IN (1, 2);
UPDATE user_business SET created_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP) WHERE id = 3;
