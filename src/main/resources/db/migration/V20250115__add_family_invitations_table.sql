-- Create family_invitations table to track pending family member invitations
CREATE TABLE IF NOT EXISTS family_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    primary_owner_id BIGINT NOT NULL,
    invited_email VARCHAR(255) NOT NULL,
    invited_first_name VARCHAR(255),
    invited_last_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    membership_id BIGINT,
    custom_price DECIMAL(10, 2),
    business_tag VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP NULL,
    user_id BIGINT NULL,
    FOREIGN KEY (primary_owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (membership_id) REFERENCES memberships(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_primary_owner_status (primary_owner_id, status),
    INDEX idx_invited_email (invited_email),
    INDEX idx_business_tag (business_tag)
);
