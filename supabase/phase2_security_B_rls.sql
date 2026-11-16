-- ============================================================================
-- SmartCare Phase 2 security, PART B: Row Level Security.
-- Run this ONLY after PART A is in and the app has been tested with it.
-- Safe to run more than once. Undo quickly with phase2_rollback.sql.
--
-- After this, the anon key (no login) can no longer read user_account,
-- patient or staff. Logged-in users see only their own rows; clinic staff
-- (STAFF, DOCTOR, ADMIN) can read patient and staff data for later modules.
-- The doctor table and every other table are left untouched.
-- ============================================================================

alter table user_account enable row level security;
alter table patient enable row level security;
alter table staff enable row level security;

-- ---------- user_account ----------
drop policy if exists "read own account" on user_account;
create policy "read own account" on user_account for select to authenticated
  using (lower(email) = lower(auth.jwt() ->> 'email'));

drop policy if exists "clinic reads accounts" on user_account;
create policy "clinic reads accounts" on user_account for select to authenticated
  using (public.current_role_name() in ('STAFF','DOCTOR','ADMIN'));

-- Sign up: a newly signed-up user (Confirm email is off, so they are logged in)
-- may insert only their own PATIENT row.
drop policy if exists "patient sign up" on user_account;
create policy "patient sign up" on user_account for insert to authenticated
  with check (lower(email) = lower(auth.jwt() ->> 'email')
              and role = 'PATIENT' and must_change_password = false);

-- No update or delete policy on user_account: changes go through the PART A
-- functions, so a user can never change their own role or status.

-- ---------- patient ----------
drop policy if exists "own patient row" on patient;
create policy "own patient row" on patient for select to authenticated
  using (user_id = public.current_user_id());

drop policy if exists "clinic reads patients" on patient;
create policy "clinic reads patients" on patient for select to authenticated
  using (public.current_role_name() in ('STAFF','DOCTOR','ADMIN'));

drop policy if exists "insert own patient row" on patient;
create policy "insert own patient row" on patient for insert to authenticated
  with check (user_id = public.current_user_id());

drop policy if exists "update own patient row" on patient;
create policy "update own patient row" on patient for update to authenticated
  using (user_id = public.current_user_id())
  with check (user_id = public.current_user_id());

-- ---------- staff ----------
drop policy if exists "own staff row" on staff;
create policy "own staff row" on staff for select to authenticated
  using (user_id = public.current_user_id());

drop policy if exists "clinic reads staff" on staff;
create policy "clinic reads staff" on staff for select to authenticated
  using (public.current_role_name() in ('STAFF','DOCTOR','ADMIN'));
