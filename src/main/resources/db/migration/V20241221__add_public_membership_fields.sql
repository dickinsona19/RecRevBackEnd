-- Add public membership fields to membership table
ALTER TABLE membership 
ADD COLUMN is_public BOOLEAN DEFAULT FALSE,
ADD COLUMN public_display_name VARCHAR(255),
ADD COLUMN public_description TEXT,
ADD COLUMN public_benefits TEXT;


