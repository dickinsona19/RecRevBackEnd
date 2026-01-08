-- Add fields to track membership history and delinquent status
ALTER TABLE user_business
ADD COLUMN has_ever_had_membership BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN is_delinquent BOOLEAN DEFAULT FALSE NOT NULL;




