-- Increase signature_data_url column size to accommodate large base64 encoded images
-- Using VARCHAR with very large size for maximum compatibility (works with H2, MySQL, PostgreSQL)
ALTER TABLE user_business_membership ALTER COLUMN signature_data_url VARCHAR(1000000);

