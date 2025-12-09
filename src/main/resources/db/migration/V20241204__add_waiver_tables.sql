-- Create waiver template table for storing the latest uploaded liability waiver per business
CREATE TABLE IF NOT EXISTS waiver_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_id BIGINT NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    version INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_id, version),
    CONSTRAINT fk_waiver_template_business
        FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
);

-- Create user waiver table to capture signed waiver instances
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
    CONSTRAINT fk_user_waiver_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_waiver_business
        FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_waiver_template
        FOREIGN KEY (waiver_template_id) REFERENCES waiver_template(id) ON DELETE CASCADE
);

-- Add waiver status column to users to make it easy to track signing progress
ALTER TABLE users
    ADD COLUMN waiver_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SIGNED';

