-- ============================================================================
-- SmartCare: appointment extras (spots left, remove a slot, book for a walk-in).
-- Run once in the Supabase SQL Editor. Safe to run more than once.
--
-- The app works without this file: "spots left", "Remove slot" and "Book for a
-- patient" only start working once these functions exist.
--
-- ASSUMPTIONS (the appointment tables are not in this repo, so check them):
--   appointment(appointment_id, patient_id, slot_id, booking_status, visit_reason, checkin_code)
--   appointment_slot(slot_id, doctor_id, slot_start, slot_end, capacity, slot_status)
--   clinic_setting(setting_id = 1, max_active_appointments)
--   booking_status of a live booking is 'BOOKED' or 'CONFIRMED'.
-- If an insert below fails with a missing / NOT NULL column, add that column to
-- the insert in staff_book_appointment.
-- ============================================================================

-- STAFF, DOCTOR or ADMIN (uses the helper from phase2_security_A_functions.sql).
create or replace function public.is_clinic_staff() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.current_role_name() in ('STAFF', 'DOCTOR', 'ADMIN'), false);
$$;

-- How many live bookings each slot has. Patients cannot read other people's
-- bookings (RLS), so this returns only counts, never who booked.
create or replace function public.get_slot_booked_counts()
returns table (slot_id integer, booked_count integer)
language sql stable security definer set search_path = public as $$
  select a.slot_id, count(*)::integer
  from appointment a
  where a.booking_status::text in ('BOOKED', 'CONFIRMED')
  group by a.slot_id;
$$;

-- Remove a slot nobody has booked (for example the doctor is on leave).
-- A slot that has ever had a booking is kept so the history stays correct.
create or replace function public.close_appointment_slot(p_slot_id integer)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_clinic_staff() then
    raise exception '[SC] Only clinic staff can remove a slot.';
  end if;
  if not exists (select 1 from appointment_slot where slot_id = p_slot_id) then
    raise exception '[SC] This slot no longer exists.';
  end if;
  if exists (select 1 from appointment where slot_id = p_slot_id) then
    raise exception '[SC] This slot already has bookings, so it cannot be removed. Cancel those bookings first.';
  end if;
  delete from appointment_slot where slot_id = p_slot_id;
end;
$$;

-- Staff books a slot for a walk-in patient. Same rules as the patient booking:
-- open slot, free place, and the patient's active-appointment limit.
create or replace function public.staff_book_appointment(
  p_patient_id integer,
  p_slot_id integer,
  p_visit_reason text default ''
)
returns json
language plpgsql security definer set search_path = public as $$
declare
  v_slot   appointment_slot%rowtype;
  v_booked integer;
  v_active integer;
  v_max    integer;
  v_code   text;
  v_id     integer;
begin
  if not public.is_clinic_staff() then
    raise exception '[SC] Only clinic staff can book for a patient.';
  end if;
  if not exists (select 1 from patient where patient_id = p_patient_id) then
    raise exception '[SC] Patient not found.';
  end if;

  -- Lock the slot so two bookings cannot take the last place together.
  select * into v_slot from appointment_slot where slot_id = p_slot_id for update;
  if not found then
    raise exception '[SC] This appointment time no longer exists.';
  end if;
  if v_slot.slot_status::text <> 'OPEN' then
    raise exception '[SC] This appointment time is not open for booking.';
  end if;

  select count(*) into v_booked from appointment
   where slot_id = p_slot_id and booking_status::text in ('BOOKED', 'CONFIRMED');
  if v_booked >= v_slot.capacity then
    raise exception '[SC] This appointment time is fully booked.';
  end if;

  if exists (select 1 from appointment
              where slot_id = p_slot_id and patient_id = p_patient_id
                and booking_status::text in ('BOOKED', 'CONFIRMED')) then
    raise exception '[SC] This patient is already booked for this time.';
  end if;

  select max_active_appointments into v_max from clinic_setting where setting_id = 1;
  select count(*) into v_active from appointment
   where patient_id = p_patient_id and booking_status::text in ('BOOKED', 'CONFIRMED');
  if v_max is not null and v_active >= v_max then
    raise exception '[SC] This patient has reached the maximum number of active appointments.';
  end if;

  v_code := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 10));

  insert into appointment (patient_id, slot_id, booking_status, visit_reason, checkin_code)
  values (p_patient_id, p_slot_id, 'BOOKED', nullif(trim(p_visit_reason), ''), v_code)
  returning appointment_id into v_id;

  return json_build_object('appointment_id', v_id, 'checkin_code', v_code);
end;
$$;

-- Who may call what: logged-in users only (the functions check the role themselves,
-- except get_slot_booked_counts which only returns counts).
revoke execute on function
  public.is_clinic_staff(),
  public.get_slot_booked_counts(),
  public.close_appointment_slot(integer),
  public.staff_book_appointment(integer, integer, text)
  from public, anon, authenticated;

grant execute on function
  public.is_clinic_staff(),
  public.get_slot_booked_counts(),
  public.close_appointment_slot(integer),
  public.staff_book_appointment(integer, integer, text)
  to authenticated;
