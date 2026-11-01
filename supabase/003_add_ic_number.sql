-- ============================================================
-- SmartCare: Add ic_number to user_account table
-- Run in Supabase Dashboard > SQL Editor.
-- ============================================================

ALTER TABLE user_account
    ADD COLUMN ic_number VARCHAR(12) UNIQUE;

-- Enforce 12-digit Malaysian IC number format (numbers only)
ALTER TABLE user_account
    ADD CONSTRAINT chk_ic_number CHECK (ic_number ~ '^\d{12}$');
