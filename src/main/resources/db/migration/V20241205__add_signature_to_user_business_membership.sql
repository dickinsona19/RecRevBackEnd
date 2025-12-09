-- Add signature fields to user_business_membership table
ALTER TABLE user_business_membership
ADD COLUMN signature_data_url VARCHAR(1000),
ADD COLUMN signed_at DATETIME,
ADD COLUMN signer_name VARCHAR(255);

