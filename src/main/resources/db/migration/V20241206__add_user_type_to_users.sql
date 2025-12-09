-- Add user_type to users for distinguishing free day pass users
ALTER TABLE users
ADD COLUMN user_type VARCHAR(32) NOT NULL DEFAULT 'REGULAR';

