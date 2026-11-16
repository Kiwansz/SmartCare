-- ============================================================================
-- SmartCare Phase 2 security, PART A: functions only.
-- Run this FIRST in the Supabase SQL Editor. Safe to run more than once.
-- It does not turn on Row Level Security, so nothing can break yet.
--
-- Checked before writing (2026-11-10): RLS is off on user_account, patient,
-- staff and doctor; there are no policies in the public schema; user_id is
-- integer and email / ic_number / role / phone_number / gender are varchar.
-- ============================================================================

-- Helpers. security definer so policies never recurse into user_account.
create or replace function public.current_user_id() returns integer
language sql stable security definer set search_path = public as $$
  select user_id from user_account
  where lower(email) = lower(auth.jwt() ->> 'email') limit 1;
$$;

create or replace function public.current_role_name() returns text
language sql stable security definer set search_path = public as $$
  select role::text from user_account where user_id = public.current_user_id();
$$;

-- Before login: IC number -> email, patients only. Returns null when not found.
create or replace function public.get_login_email(p_ic text) returns text
language sql stable security definer set search_path = public as $$
  select email::text from user_account
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
-- Supabase grants EXECUTE on new functions to anon and authenticated directly
-- (not only through PUBLIC), so revoke from all three, then grant back exactly.
revoke execute on function
  public.get_login_email(text), public.is_ic_registered(text), public.is_email_registered(text),
  public.mark_login(), public.complete_password_change(), public.update_my_phone(text),
  public.current_user_id(), public.current_role_name()
  from public, anon, authenticated;

-- Before login (anon) and after login.
grant execute on function
  public.get_login_email(text), public.is_ic_registered(text), public.is_email_registered(text)
  to anon, authenticated;

-- Logged-in users only.
grant execute on function
  public.mark_login(), public.complete_password_change(), public.update_my_phone(text),
  public.current_user_id(), public.current_role_name()
  to authenticated;
