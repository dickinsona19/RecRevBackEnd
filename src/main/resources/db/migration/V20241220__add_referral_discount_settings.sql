-- Add referral program discount settings to businesses table
ALTER TABLE businesses
ADD COLUMN referred_user_discount_months INT DEFAULT 1,
ADD COLUMN referred_user_waive_activation_fee BOOLEAN DEFAULT TRUE,
ADD COLUMN referrer_discount_months INT DEFAULT 1,
ADD COLUMN referrer_waive_activation_fee BOOLEAN DEFAULT TRUE;

