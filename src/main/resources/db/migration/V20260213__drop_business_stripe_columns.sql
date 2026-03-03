-- Single-tenant: remove Stripe Connect / onboarding columns from businesses
ALTER TABLE businesses DROP COLUMN IF EXISTS stripe_account_id;
ALTER TABLE businesses DROP COLUMN IF EXISTS onboarding_status;
