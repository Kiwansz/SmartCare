-- ============================================================================
-- SmartCare Phase 2: emergency rollback (e.g. login breaks during a demo).
-- Turns Row Level Security off again on the three tables, which restores the
-- old behaviour immediately. Policies and functions stay in place, so running
-- phase2_security_B_rls.sql later turns protection back on.
-- WARNING: while RLS is off, the anon key can read these tables again.
-- ============================================================================

alter table user_account disable row level security;
alter table patient disable row level security;
alter table staff disable row level security;
