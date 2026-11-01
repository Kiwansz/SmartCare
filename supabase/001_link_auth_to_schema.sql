-- ============================================================
-- SmartCare: link Supabase Auth to the clinic schema
-- Run once in Supabase Dashboard > SQL Editor.
-- ============================================================

-- 1. Link user_account to Supabase Auth; passwords live in auth.users, not here.
ALTER TABLE user_account
    ADD COLUMN auth_id UUID UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE user_account
    ALTER COLUMN password_hash DROP NOT NULL;

-- 2. When someone signs up in the app, create their user_account + patient rows.
--    Runs as the table owner, so it works even when email confirmation is on
--    (the app has no session yet at that point).
--    Role is ALWAYS 'PATIENT' here: staff/doctor/admin accounts must be created by an admin,
--    never from client-supplied metadata.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    new_user_id INT;
BEGIN
    INSERT INTO user_account (email, phone_number, role, auth_id)
    VALUES (NEW.email, NEW.raw_user_meta_data->>'phone', 'PATIENT', NEW.id)
    RETURNING user_id INTO new_user_id;

    INSERT INTO patient (user_id, full_name)
    VALUES (new_user_id, COALESCE(NEW.raw_user_meta_data->>'full_name', ''));

    RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 3. SECURITY: tables created with plain SQL have Row Level Security OFF,
--    which means anyone holding the anon key (it ships inside the APK) can read/write them.
--    Turn RLS on everywhere; with no policy, the anon key gets no access.
ALTER TABLE clinic_setting          ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_account            ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE doctor                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment_slot        ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment             ENABLE ROW LEVEL SECURITY;
ALTER TABLE reminder                ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_record       ENABLE ROW LEVEL SECURITY;
ALTER TABLE follow_up               ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_session            ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_message            ENABLE ROW LEVEL SECURITY;
ALTER TABLE faq                     ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback                ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_history         ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_document        ENABLE ROW LEVEL SECURITY;
ALTER TABLE prescription            ENABLE ROW LEVEL SECURITY;
ALTER TABLE prescription_item       ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_certificate     ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment_event_log   ENABLE ROW LEVEL SECURITY;

-- 4. Minimal policies so a logged-in patient can read their own profile.
--    Add more per table as you build each feature.
CREATE POLICY "own account" ON user_account
    FOR SELECT TO authenticated USING (auth_id = auth.uid());

CREATE POLICY "own patient profile" ON patient
    FOR SELECT TO authenticated
    USING (user_id IN (SELECT user_id FROM user_account WHERE auth_id = auth.uid()));

CREATE POLICY "read clinic info" ON clinic_setting
    FOR SELECT TO anon, authenticated USING (true);
