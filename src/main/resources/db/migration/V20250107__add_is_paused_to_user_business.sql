-- Add is_paused boolean field to user_business table
-- This flag tracks if the user's subscriptions are paused in Stripe
ALTER TABLE user_business
ADD COLUMN is_paused BOOLEAN DEFAULT FALSE;

