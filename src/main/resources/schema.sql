-- Create the user_titles table
CREATE TABLE user_titles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) UNIQUE NOT NULL
);


    CREATE TABLE clients (
        id INTEGER PRIMARY KEY AUTO_INCREMENT,
        email VARCHAR(255) NOT NULL,
        password VARCHAR(255) NOT NULL,
        created_at DATETIME NOT NULL,
        status VARCHAR(50),
        stripe_account_id VARCHAR(100),
        UNIQUE (email)
    );

   CREATE TABLE staff (
       id INTEGER PRIMARY KEY AUTO_INCREMENT,
       email VARCHAR(255) NOT NULL,
       password VARCHAR(255) NOT NULL,
       type VARCHAR(50) NOT NULL,
       UNIQUE (email)
   );

   CREATE TABLE clubs (
       id INTEGER PRIMARY KEY AUTO_INCREMENT,
       title VARCHAR(255) NOT NULL,
       logo_url VARCHAR(255),
       status VARCHAR(50),
       created_at DATETIME NOT NULL,
       user_id INTEGER,
       club_tag VARCHAR(100),
       client_id INTEGER NOT NULL,
       staff_id INTEGER,
       FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
       FOREIGN KEY (staff_id) REFERENCES staff(id)
   );
CREATE TABLE membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    price VARCHAR(50) NOT NULL,
    charge_interval VARCHAR(50) NOT NULL,
    club_tag VARCHAR(100) NOT NULL
);

-- Modified users table with membership_id foreign key
CREATE TABLE users (
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
    is_over_18 BOOLEAN NOT NULL DEFAULT FALSE,
    locked_in_rate VARCHAR(50),
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
CREATE TABLE user_clubs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    club_id INTEGER NOT NULL,
    membership_id BIGINT,
    stripe_id VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
    FOREIGN KEY (membership_id) REFERENCES membership(id),
    CONSTRAINT unique_user_club UNIQUE (user_id, club_id)
);

-- Create user_club_memberships junction table for multiple memberships per user-club relationship
CREATE TABLE user_club_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_club_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    anchor_date DATETIME NOT NULL,
    end_date DATETIME NULL,
    stripe_subscription_id VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ucm_user_club FOREIGN KEY (user_club_id) REFERENCES user_clubs(id) ON DELETE CASCADE,
    CONSTRAINT fk_ucm_membership FOREIGN KEY (membership_id) REFERENCES membership(id) ON DELETE CASCADE
);

-- Create indexes for user_club_memberships table
CREATE INDEX idx_ucm_user_club_id ON user_club_memberships(user_club_id);
CREATE INDEX idx_ucm_membership_id ON user_club_memberships(membership_id);
CREATE INDEX idx_ucm_status ON user_club_memberships(status);
CREATE INDEX idx_ucm_anchor_date ON user_club_memberships(anchor_date);
CREATE INDEX idx_ucm_stripe_subscription ON user_club_memberships(stripe_subscription_id);
CREATE INDEX idx_ucm_active_memberships ON user_club_memberships(user_club_id, status, anchor_date);
CREATE INDEX idx_ucm_membership_status ON user_club_memberships(membership_id, status);

CREATE TABLE sign_in_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sign_in_time DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
-- Creating the products table
CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    definition TEXT,
    price DOUBLE NOT NULL,
    image_url VARCHAR(255),
    category VARCHAR(100),
    stripe_product_id VARCHAR(100),
    club_tag VARCHAR(30) NOT NULL
);
INSERT INTO user_titles (title) VALUES
('Member'),
('VIP Member'),
('Guest');

INSERT INTO clients (email, password, created_at, status, stripe_account_id) VALUES
('anndreuis@gmail.com', '$2a$10$cE/mC1Kd/ADnIvX.uV/tgeiuXIdxYG7/qXRVYHEwz5BUcgmnvyaUC', '2025-08-19 11:00:00', 'ACTIVE', 'acct_1SAhtKLfLLcJtrGn'),
('jane.smith@example.com', 'hashed_password_456', '2025-08-18 09:30:00', 'INACTIVE', 'acct_2K8X1yJ2M3N4P5Q6'),
('bob.jones@example.com', 'hashed_password_789', '2025-08-17 14:15:00', 'ACTIVE', NULL);

-- Inserting sample data into staff table
INSERT INTO staff (email, password, type) VALUES
('alice.trainer@example.com', 'hashed_password_901', 'TRAINER'),
('bob.coach@example.com', 'hashed_password_902', 'COACH'),
('carol.instructor@example.com', 'hashed_password_903', 'INSTRUCTOR');
INSERT INTO clubs (title, logo_url, status, created_at, club_tag, client_id, staff_id) VALUES
('John''s Fitness Club', 'https://example.com/logos/johns_fitness.png', 'ACTIVE', '2025-08-19 12:00:00', 'JFC001', 1, 1),
('John''s Yoga Studio', 'https://example.com/logos/yoga_studio.png', 'ACTIVE', '2025-08-19 13:00:00', 'JYS002', 1, 2),
('Jane''s Gym', 'https://example.com/logos/janes_gym.png', 'INACTIVE', '2025-08-18 10:00:00', 'JG003', 2, 3),
('Bob''s Weightlifting Center', NULL, 'ACTIVE', '2025-08-17 15:00:00', 'BWC004', 3, NULL);
INSERT INTO membership (title, price, charge_interval, club_tag) VALUES
('Basic Membership', '29.99', 'MONTHLY', 'JFC001'),
('Premium Membership', '59.99', 'MONTHLY', 'JFC001'),
('Yoga Pass', '39.99', 'MONTHLY', 'JYS002'),
('Weightlifting Monthly', '49.99', 'MONTHLY', 'BWC004');



INSERT INTO users (first_name, last_name, email, password, phone_number, is_in_good_standing, created_at, entry_qrcode_token, user_stripe_member_id, user_title_id, is_over_18, locked_in_rate, signature_data, waiver_signed_date, profile_picture_url, referral_code, parent_id, referred_by_id, membership_id) VALUES
('Alice', 'Smith', 'alice.smith@example.com', 'hashed_password_111', '1234567890', TRUE, '2025-08-19 10:00:00', 'qrcode_001', 'stripe_mem_001', 1, TRUE, '29.99', 'signature_data_001', '2025-08-19 10:05:00', 'https://example.com/profiles/alice.png', 'REF001', NULL, NULL, 1),
('Bob', 'Johnson', 'bob.johnson@example.com', 'hashed_password_222', '0987654321', TRUE, '2025-08-19 11:00:00', 'qrcode_002', 'stripe_mem_002', 2, TRUE, '59.99', 'signature_data_002', '2025-08-19 11:05:00', NULL, 'REF002', NULL, 1, 2),
('Charlie', 'Brown', 'charlie.brown@example.com', 'hashed_password_333', '1122334455', FALSE, '2025-08-18 12:00:00', 'qrcode_003', NULL, 3, FALSE, NULL, NULL, NULL, NULL, 'REF003', 1, NULL, 3),
('Diana', 'Wilson', 'diana.wilson@example.com', 'hashed_password_444', '5566778899', TRUE, '2025-08-17 13:00:00', 'qrcode_004', 'stripe_mem_003', 1, TRUE, '49.99', 'signature_data_003', '2025-08-17 13:05:00', 'https://example.com/profiles/diana.png', 'REF004', NULL, 2, 4);

-- Inserting sample data into user_clubs table
INSERT INTO user_clubs (user_id, club_id, membership_id, stripe_id, status, created_at) VALUES
(1, 1, 1, NULL, 'ACTIVE', '2025-08-19 10:10:00'), -- Alice in John's Fitness Club with Basic Membership
(1, 2, 3, NULL, 'ACTIVE', '2025-08-19 10:15:00'), -- Alice in John's Yoga Studio with Yoga Pass
(2, 1, 2, 'stripe_sub_002', 'ACTIVE', '2025-08-19 11:10:00'), -- Bob in John's Fitness Club with Premium Membership
(3, 3, NULL, NULL, 'PENDING', '2025-08-18 12:10:00'), -- Charlie in Jane's Gym (no membership yet)
(4, 4, 4, 'stripe_sub_004', 'ACTIVE', '2025-08-17 13:10:00'); -- Diana in Bob's Weightlifting Center

-- Inserting sample data into sign_in_logs table
INSERT INTO sign_in_logs (user_id, sign_in_time) VALUES
(1, '2025-08-19 10:30:00'),
(1, '2025-08-19 14:00:00'),
(2, '2025-08-19 11:30:00'),
(4, '2025-08-17 14:00:00');


INSERT INTO products (name, definition, price, image_url, category, stripe_product_id, club_tag) VALUES
('Protein Shake', 'High-protein shake for post-workout recovery', 5.99, 'https://example.com/products/protein_shake.png', 'Supplement', 'prod_001', 'JFC001'),
('Yoga Mat', 'Non-slip yoga mat for studio use', 29.99, 'https://example.com/products/yoga_mat.png', 'Equipment', 'prod_002', 'JFC001'),
('Weightlifting Belt', 'Supportive belt for heavy lifts', 39.99, NULL, 'Equipment', 'prod_003', 'JFC001'),
('Energy Drink', 'Sugar-free energy drink', 3.99, 'https://example.com/products/energy_drink.png', 'Supplement', 'prod_004', 'JFC002');

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
WHERE uc.membership_id IS NOT NULL;