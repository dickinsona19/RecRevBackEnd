-- Add calculated status and user type fields to user_business table
-- These will be calculated and stored when relevant events occur
ALTER TABLE user_business
ADD COLUMN calculated_status VARCHAR(50),
ADD COLUMN calculated_user_type VARCHAR(50);


