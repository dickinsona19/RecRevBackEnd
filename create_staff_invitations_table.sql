-- Manual script to create staff_invitations table
-- Run this if the Flyway migration hasn't executed yet

CREATE TABLE IF NOT EXISTS staff_invitations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_id BIGINT NOT NULL,
    invited_email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    invite_token VARCHAR(255) NOT NULL UNIQUE,
    invite_token_expiry DATETIME NOT NULL,
    invited_by INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at DATETIME,
    staff_id INT,
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_business_status ON staff_invitations(business_id, status);
CREATE INDEX IF NOT EXISTS idx_invite_token ON staff_invitations(invite_token);
CREATE INDEX IF NOT EXISTS idx_invited_email ON staff_invitations(invited_email);
