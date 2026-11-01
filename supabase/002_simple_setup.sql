-- Simple setup for the university project (no RLS, app inserts directly).
-- Run in Supabase Dashboard > SQL Editor. Safe to run even if 001 was never run.

-- Remove the trigger from 001 if it exists (the app inserts the rows itself now)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();

-- Passwords live in Supabase Auth, so this column must not be required
ALTER TABLE user_account ALTER COLUMN password_hash DROP NOT NULL;

-- Let the app read/write the tables freely
ALTER TABLE user_account DISABLE ROW LEVEL SECURITY;
ALTER TABLE patient      DISABLE ROW LEVEL SECURITY;
