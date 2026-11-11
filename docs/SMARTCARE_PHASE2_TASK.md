# SmartCare: finish the User Account module (Phase 2)

> How to use this file: put it at `docs/SMARTCARE_PHASE2_TASK.md`. Then tell Claude Code: "Read docs/SMARTCARE_PHASE2_TASK.md fully, then make a short plan and wait for my OK before changing any file."

## 0. Where we are
The UI phase (`docs/SMARTCARE_UI_TASK.md`) is done and pushed on `feature/kiwansz`. The user has tested on the emulator:
- Patient login with IC number: works.
- Patient sign up: works, rows appear in `user_account` and `patient`.
- Staff login, forced change password, then staff home: works. `must_change_password` becomes false.
- Remember me: reopening the app goes straight to Home.
- Forgot password: the email arrives, but the link opens nothing. The user has now switched Supabase to send a **code** instead of a link (see section 2).

Supabase setup the user already did (do not ask them to redo it):
- Custom SMTP through a dedicated Gmail account, sender name "SmartCare Clinic".
- The "Reset password" email template now shows `{{ .Token }}`, a numeric code. **The code currently has 8 digits.** Accept 6 to 8 digits so it keeps working if the length setting changes.

## 1. Goal and scope
Finish module A (User Account) in the Android app.

**In scope**
1. Forgot password with an emailed code (section 2).
2. Security: stop exposing the `user_account` table to anyone with the anon key (section 3).
3. Patient profile screen (section 4).
4. Small finishing items (section 5).

**Out of scope**
- The Admin web panel, appointments, chatbot and every other module.
- New libraries. Ask first.
- Any change to tables or columns. Only functions and Row Level Security policies, using the SQL in section 3.

## 2. Forgot password with a code
Flow:
1. **Request screen** (exists): user types email, taps Send. Call Supabase Auth's reset-password request for that email. Always show the "Link sent" card, even if the email is not registered, so the screen does not reveal which emails exist. Change the card text to "Code sent. Check your inbox for a code from SmartCare Clinic."
2. **New "Enter code" screen** (same design style, same theme as where the user came from: Clinic Sky for patients, navy for staff):
   - Code field: digits only, 6 to 8 digits, large letter-spaced text.
   - New password and confirm password, with the same live rule chips and strength bar as sign up (8+ characters, uppercase, number, passwords match).
   - Button "Reset password", disabled until the code length is valid and all rules pass.
   - "Resend code" link with a 60-second countdown.
3. On submit: verify the code with Supabase Auth as a **recovery** email OTP for that email, which signs the user in. Then update the password. Then sign out, return to the correct login screen, and show "Password updated. Log in with your new password."
4. Errors: wrong or expired code shows "That code is wrong or has expired. Request a new one." Never show raw Supabase error text.
5. Remove the old `// TODO(supabase)` for forgot password.

Test: request a code, enter it with a new password, log in with the new password. A wrong code shows the error. Works from both the patient and staff login screens.

## 3. Security: lock down `user_account`
**The problem.** Patient login looks up the IC number before the user is signed in. That only works because anyone holding the anon key can read `user_account`, which means anyone can list every user's email, IC number and role. The anon key is inside the APK, so this must be fixed before the project is submitted.

**The fix.** Small Postgres functions (`security definer`) answer only the narrow questions the app needs, and Row Level Security limits each user to their own rows. Clinic staff can still read patient data, because the appointment and follow-up modules will need it.

### 3.1 Check the current state first
Ask the user to run this in the Supabase **SQL Editor** and paste the result back to you:
```sql
select tablename, rowsecurity from pg_tables
where schemaname = 'public' and tablename in ('user_account','patient','staff','doctor');

select tablename, policyname, roles, cmd, qual, with_check
from pg_policies where schemaname = 'public';
```
Read the result before writing any SQL. If existing policies give `anon` read access to `user_account`, `patient` or `staff`, the script must drop them by name.

### 3.2 Write the script
Create `supabase/phase2_security.sql` in the project, based on the reference below. Adjust it to the result of 3.1 and to the real column types. Keep it idempotent (`create or replace`, `drop policy if exists`). Do not run anything yourself: the **user** runs it in the SQL Editor.

Reference SQL:
```sql
-- Helpers. security definer so policies never recurse into user_account.
create or replace function public.current_user_id() returns integer
language sql stable security definer set search_path = public as $$
  select user_id from user_account
  where lower(email) = lower(auth.jwt() ->> 'email') limit 1;
$$;

create or replace function public.current_role_name() returns text
language sql stable security definer set search_path = public as $$
  select role from user_account where user_id = public.current_user_id();
$$;

-- Before login: IC number -> email, patients only. Returns null when not found.
create or replace function public.get_login_email(p_ic text) returns text
language sql stable security definer set search_path = public as $$
  select email from user_account
  where ic_number = regexp_replace(p_ic, '\D', '', 'g') and role = 'PATIENT'
  limit 1;
$$;

-- Sign up duplicate checks.
create or replace function public.is_ic_registered(p_ic text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from user_account
                 where ic_number = regexp_replace(p_ic, '\D', '', 'g'));
$$;

create or replace function public.is_email_registered(p_email text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from user_account
                 where lower(email) = lower(trim(p_email)));
$$;

-- After login: the only updates a user may make to their own account row.
create or replace function public.mark_login() returns void
language sql security definer set search_path = public as $$
  update user_account set last_login_at = now()
  where user_id = public.current_user_id();
$$;

create or replace function public.complete_password_change() returns void
language sql security definer set search_path = public as $$
  update user_account
  set must_change_password = false, temporary_password_expires_at = null
  where user_id = public.current_user_id();
$$;

create or replace function public.update_my_phone(p_phone text) returns void
language sql security definer set search_path = public as $$
  update user_account set phone_number = p_phone
  where user_id = public.current_user_id();
$$;

-- Who may call what.
revoke execute on function public.get_login_email(text), public.is_ic_registered(text),
  public.is_email_registered(text), public.mark_login(), public.complete_password_change(),
  public.update_my_phone(text), public.current_user_id(), public.current_role_name()
  from public;
grant execute on function public.get_login_email(text), public.is_ic_registered(text),
  public.is_email_registered(text) to anon, authenticated;
grant execute on function public.mark_login(), public.complete_password_change(),
  public.update_my_phone(text), public.current_user_id(), public.current_role_name()
  to authenticated;

-- Row Level Security.
alter table user_account enable row level security;
alter table patient enable row level security;
alter table staff enable row level security;

drop policy if exists "read own account" on user_account;
create policy "read own account" on user_account for select to authenticated
  using (lower(email) = lower(auth.jwt() ->> 'email'));

drop policy if exists "clinic reads accounts" on user_account;
create policy "clinic reads accounts" on user_account for select to authenticated
  using (public.current_role_name() in ('STAFF','DOCTOR','ADMIN'));

drop policy if exists "patient sign up" on user_account;
create policy "patient sign up" on user_account for insert to authenticated
  with check (lower(email) = lower(auth.jwt() ->> 'email')
              and role = 'PATIENT' and must_change_password = false);
-- No update or delete policy on user_account: changes go through the functions above,
-- so a user can never change their own role or status.

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

drop policy if exists "own staff row" on staff;
create policy "own staff row" on staff for select to authenticated
  using (user_id = public.current_user_id());

drop policy if exists "clinic reads staff" on staff;
create policy "clinic reads staff" on staff for select to authenticated
  using (public.current_role_name() in ('STAFF','DOCTOR','ADMIN'));
```
Also create `supabase/phase2_rollback.sql` that disables RLS on the three tables, so the user can undo quickly if login breaks during a demo.

Leave `doctor` and every other table untouched. Teammates will add policies for their own modules.

### 3.3 Change the app (two stages, test after each)
**Stage A, functions only (RLS still off, nothing can break):**
1. The user runs only the function part of the script (everything above `-- Row Level Security.`). Split the file or mark the two parts clearly so this is easy.
2. Patient login: replace the direct `user_account` lookup by IC with a call to `get_login_email`. Null means "IC number or password is incorrect."
3. Sign up: replace the duplicate IC and email checks with `is_ic_registered` and `is_email_registered`, before the Auth user is created. Keep the existing "Log in instead" link and the orphan-login protection.
4. After every successful login, call `mark_login` instead of updating `last_login_at` directly.
5. Change password: after the Auth password update succeeds, call `complete_password_change` instead of updating the row directly.
6. Reading the logged-in user's own `user_account` row after login can stay as it is (`findAccountRowByEmail`), because RLS will still allow a user to read their own row. Keep the email-lookup pitfall rule from the UI task: never filter by a raw email string.
7. Build, run, and ask the user to test login, sign up, staff change password and Remember me again.

**Stage B, enable RLS:**
1. The user runs the Row Level Security part.
2. Test everything from stage A again. If anything fails, read the error, fix the app or the script, and tell the user exactly what to run. Use the rollback script only as a last resort.
3. Prove the fix: give the user a one-line `curl` or REST request with the anon key and no login that tries to read `user_account`. It must return an empty list.

## 4. Patient profile screen
Open it from the Profile icon in the patient home's floating tab bar. Same Clinic Sky style.
- Avatar initials, full name, email.
- IC number masked except the last 4 digits (`******-**-5677`), date of birth, gender (or "Not set").
- Phone number with an Edit button: a small dialog or bottom sheet, validated, saved through `update_my_phone`.
- Gender can be set or changed if empty (Male, Female, or not set), saved to the user's own `patient` row.
- "Log out" button that signs out and returns to the Role picker.
- Leave the other tab bar icons as they are (still sample data, owned by other modules).

## 5. Finishing items
- Expired temporary password: confirm that a staff account whose `temporary_password_expires_at` is in the past sees "Your temporary password has expired. Ask your Admin to issue a new one." and is signed out. Fix it if not.
- `account_status` other than `ACTIVE` blocks login with a friendly message, for both patient and staff.
- HTTP logging in `SupabaseProvider.kt`: debug builds only, never logs passwords, tokens, codes or IC numbers.
- Search the code for remaining `TODO(supabase)` and `TODO(demo)` in module A and resolve them. `TODO(appointment module)` stays.
- Add unit tests for the code-length check and the IC masking.

## 6. Rules (same as the UI task)
- Work on `feature/kiwansz`. One commit per finished step. Never touch `main`. **Do not push.**
- Anon key only, from `local.properties` through BuildConfig. Never use or mention the `service_role` key in the app.
- Never commit `local.properties`.
- Never print raw Supabase error text to users.
- Do not rewrite working code. Extend it.

## 7. Order of work
1. Read this file and the current code, then reply with a plan and wait for the user's OK.
2. Forgot password with code (section 2). Commit.
3. Section 3.1: ask the user to run the check queries and paste the result.
4. Write the SQL script and rollback script. Commit.
5. Stage A app changes. Ask the user to run the function part and test. Commit.
6. Stage B. Ask the user to run the RLS part and test. Commit.
7. Profile screen (section 4). Commit.
8. Finishing items (section 5). Commit.
9. Final report: what works, what the user tested, anything still open, and the exact SQL the user ran.

## 8. Done when
- Forgot password works end to end with the emailed code, from both login screens.
- With no login, the anon key can no longer read `user_account`, `patient` or `staff`.
- Patient login with IC, sign up, staff first login, Remember me and log out all still work after RLS is on.
- The profile screen shows the right data and saves phone and gender.
- No `TODO(supabase)` or `TODO(demo)` left in module A.
- The app builds, unit tests pass, `local.properties` is not committed, and nothing was pushed.
