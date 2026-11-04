# SmartCare: build the User Account screens (UI phase)

> How to use this file: put it at `docs/SMARTCARE_UI_TASK.md` and the design at `docs/design/smartcare-ui-design.html` in the project. Then tell Claude Code: "Read docs/SMARTCARE_UI_TASK.md fully, then make a short plan and wait for my OK before changing any file."

## 0. Goal and scope
Build the app UI for module A (User Account) so it matches the design. This is the **UI phase only**. Backend logic that already works must keep working; everything else is wired later.

**In scope**
- Colors, fonts, themes, shared styles and reusable components.
- All 8 screens as XML layouts with Kotlin screens/activities, plus navigation between them.
- Patient login with an **IC number** field, using the login logic that already works.
- Two screen-level behaviours with no database: (1) date of birth and gender fill in from the IC number on sign up, (2) password rules tick off live.

**Out of scope (do not build, do not change)**
- Saving sign up to Supabase (only keep it if it already works, see section 6).
- Sending the forgot-password email.
- Updating the password on the change-password screen.
- Row Level Security or any Supabase setup.
- The Admin web panel and any other module (appointments, chatbot, etc.).
- New libraries. Ask first.

## 1. Project facts
- Stack: Android Studio, Kotlin, XML layouts with Material Components (not Compose), Supabase.
- Package `com.example.smartcare`. Project folder `C:\Users\wawan\AndroidStudioProjects\SmartCare`.
- Work on branch `feature/kiwansz` only. Make one commit per finished step with a clear message. Never touch `main`. **Do not push.** The user pushes.

## 2. Read first, before writing code
1. `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`.
2. `LoginActivity.kt`, `RegisterActivity.kt`, `AuthRepository.kt`, `data/SupabaseProvider.kt`.
3. `activity_login.xml`, `activity_register.xml`, `colors.xml`, `themes.xml`, `values-night/themes.xml`, `strings.xml`.
4. The design file `docs/design/smartcare-ui-design.html`. Open it in a browser and match layout, spacing and hierarchy.
5. Check `minSdk`. If it is below 26, enable core library desugaring for `java.time`.

Then reply with a short plan (files you will add or change) and wait for the user's OK.

## 3. Rules that must not break
- Supabase URL and anon key come from `local.properties` through BuildConfig. Never hard-code them. Use the **anon key only**. Never use or mention the `service_role` key in the app.
- Never commit `local.properties`. Confirm it is in `.gitignore`.
- Do not log passwords, tokens or IC numbers. HTTP debug logging in `SupabaseProvider.kt` stays **debug builds only**.
- **Do not rewrite or remove working login code.** Restyle the screens and extend the repository.
- **Email lookup pitfall.** The Supabase library wraps filter values that contain a dot (`.`) in quotation marks, so `email=ilike.<email>` searches for an email with literal quote marks and finds nothing. `findAccountRowByEmail` in `AuthRepository.kt` fixes this by replacing every symbol in the email (`@`, `.`, `+`, `-`) with the wildcard `_` and then picking the exact match in code. Always use that helper. Never filter by a raw email string yourself.

## 4. Existing database facts (read only, do not change)
- `user_account`: `email` (unique), `role` (`PATIENT`, `STAFF`, `ADMIN`, `DOCTOR`), `account_status` (`ACTIVE`, `INACTIVE`, `SUSPENDED`), `must_change_password`, `temporary_password_expires_at`, `ic_number` (unique, exactly 12 digits, no dashes), `last_login_at`.
- `patient`: `user_id`, `full_name`, `date_of_birth`, `gender` (`MALE`, `FEMALE`, `OTHER`).
- Patient login today: find the `user_account` row, sign in with Supabase Auth, check role and status. Passwords live in Supabase Auth, never in the app.

## 5. Screens
All screens follow the design file. Patient screens use the **Clinic Sky** palette, staff screens use **Navy**.

| # | Screen | Notes |
|---|---|---|
| 1 | Role picker | "I'm a patient" or "I'm clinic staff", then Continue |
| 2 | Patient login | IC number + password, remember me, forgot password, create account |
| 3a | Sign up, step 1 | Full name, IC number, phone. Auto "From IC" cards for date of birth and gender |
| 3b | Sign up, step 2 | Email, password with strength bar, confirm password, terms checkbox |
| 4 | Forgot password | Email field, "Link sent" card, resend button |
| 5 | Staff login | Work email + password, navy theme, no sign up link |
| 6 | Change password | Temporary password, new password with strength bar, rule chips, confirm |
| 7 | Patient home | Greeting, next appointment card, 4 shortcut tiles, floating tab bar |
| 8 | Staff home | Plain placeholder: "Staff home coming soon" and a log out button. No design exists yet |

Navigation
- App start: if a valid session exists, go to the correct home by role; otherwise show the Role picker. Keep whatever session behaviour already works.
- Role picker leads to patient login or staff login. Patient login links to sign up and forgot password. Staff login links to forgot password.
- Sign up has two steps in one place, with a back arrow between them (one Activity with two steps or two screens, whichever is simpler).
- `PATIENT` goes to the patient home. `STAFF` and `DOCTOR` go to the staff placeholder.

## 6. Behaviour for this phase

**Patient login (IC number)**
- First field is **IC number**, not email or username. Accept it with or without dashes (`010203-14-5677` or `010203145677`), strip everything except digits, and look up the account by `ic_number`.
- Helper text under the field: "12 digits, no need to type dashes."
- Keep the existing flow: find the account, take its email, sign in with Supabase Auth using that email and the password, then check `role = PATIENT` and `account_status = ACTIVE`.
- One generic error for both a missing IC and a wrong password: "IC number or password is incorrect."
- "Remember me" stores the IC number in SharedPreferences to pre-fill the field.
- Forgot password asks for an **email**, because Supabase sends the link to that address.

**Sign up, step 1**
- Accept the IC with or without dashes. Once 12 digits are entered, fill the two read-only "From IC" cards (date of birth, gender). Block Next if the IC is invalid or the date does not exist.
- Write this as a pure function with unit tests:
```kotlin
data class IcInfo(val dateOfBirth: LocalDate, val gender: String)

fun parseIc(ic: String): IcInfo? {
    val d = ic.filter { it.isDigit() }
    if (d.length != 12) return null
    val yy = d.substring(0, 2).toInt()
    val mm = d.substring(2, 4).toInt()
    val dd = d.substring(4, 6).toInt()
    return try {
        var dob = LocalDate.of(2000 + yy, mm, dd)
        if (dob.isAfter(LocalDate.now())) dob = dob.minusYears(100)
        val gender = if (d.last().digitToInt() % 2 == 1) "MALE" else "FEMALE"
        IcInfo(dob, gender)
    } catch (e: DateTimeException) { null }
}
```
- Tests: `010203-14-5677` gives 2001-02-03 and MALE; an invalid month returns null; a wrong length returns null.

**Sign up, step 2**
- Password rules: at least 8 characters, one uppercase letter, one number. Show the strength bar. Confirm password must match.
- If `RegisterActivity` already saves to Supabase, keep that working and connect the new two-step UI to it. Do not delete working save logic.
- If it does not save yet, show a short message "Sign up is not connected yet" and leave a `// TODO(supabase)` comment.

**Not wired yet (UI only)**
- Forgot password: validate the email format, then show the "Link sent" card. Add `// TODO(supabase)`.
- Change password: rules tick off live, the Save button stays disabled until all rules pass and both fields match, then navigate to the staff home. Add `// TODO(supabase)` for the real update.
- Staff login: if the existing repository can log in by email without changes, use it. Otherwise validate the fields, then navigate to the change-password screen as a demo route marked `// TODO(demo)`.

**Patient home**
- Show the real name and avatar initials if the logged-in patient's name is already available. Otherwise use the sample name. The appointment card, tiles and tab bar use hard-coded sample data, marked `// TODO(appointment module)`.

**General**
- Loading state on buttons, inline field errors, friendly error messages (never raw exception text), working back buttons, and no crashes on rotation or empty input.

## 7. Design tokens

| Token | Patient (Clinic Sky) | Staff (Navy) |
|---|---|---|
| Main | `#0880B3` | `#0B2A5B` |
| Lighter accent | `#36B4EA` | `#3A86DC` |
| Header highlight | `#A6E3FF` | `#9FD0FF` |
| Soft background (chips, glow) | `#E3F4FC` | `#E5EFFD` |
| Button top (gradient start) | `#1D9BD3` | `#183F7F` |
| Header dark | `#065F88` | `#061A3D` |
| "From IC" card background | `#EEF8FD` | n/a |
| "From IC" card border | `#C9E7F6` | n/a |

Shared: text `#0F1F36`, secondary text `#66768C`, border `#E1E8F1`, field fill `#F2F6FB`, page background `#EEF3F9`, white `#FFFFFF`, error red, success green. The chatbot tile on the patient home is navy `#0B2A5B`.

**Type:** Figtree (bundle in `res/font` or use a downloadable font). Hero title 28 to 30sp bold with slightly tight letter spacing, labels 11sp, body 13 to 14sp, button text 14.5sp bold.

**Shape and spacing**
- Screen side padding 20dp. Sheet top corners 30dp, overlapping the header by 28dp, with a small grab handle at the top.
- Fields: 54dp high, 18dp corners, label floats inside the box. Active state has a 1.5dp accent border and a soft outer glow in the soft color.
- Buttons: fully rounded pill, 54dp high, vertical gradient from "button top" to "main", white text, trailing arrow where the design shows it.
- Header: layered gradient drawable (light accent at the top left, header highlight at the top right, header dark at the bottom) with two thin translucent white circles at the top right. Android has no mesh gradient, so approximate it with a layer-list of linear and radial gradients.
- Glass buttons (back arrow, step pill) on the header: white at about 16 percent opacity with a thin white border. No blur needed.
- Patient home: the next-appointment card uses the same gradient as the header with rounded 28dp corners. The tab bar floats as a dark pill 62dp high with the selected item as a white pill showing icon and label.

**Components**
- Use Material Components: `TextInputLayout` (outlined style with a filled background color and transparent normal stroke works well for the floating label), `MaterialButton`, `MaterialCardView`.
- Colors in `colors.xml`, styles in `themes.xml`. Create `Theme.SmartCare.Patient` and `Theme.SmartCare.Staff`, and apply the staff theme to staff screens only.
- Icons: Material Symbols vector assets (person, badge/IC card, mail, lock, visibility, calendar, add, chat, sync, description, home, notifications).
- Light mode only. Make sure `values-night` does not change these colors.

## 8. Order of work (build and run after each step, commit after each)
1. Read the code, then confirm the existing login works on the emulator **before changing anything**. Report what you found.
2. Colors, fonts, themes, shared styles and reusable components.
3. `parseIc` and the password-rule validators, with unit tests.
4. Role picker, patient login (IC field) and patient home. Login must still work.
5. Sign up, step 1 and step 2.
6. Forgot password.
7. Staff login, change password and the staff home placeholder.
8. Clean up: unused code, hard-coded strings moved to `strings.xml`, a final full walkthrough.

## 9. Done when
- Every screen matches the design closely on a Medium Phone emulator (API 35 recommended).
- Patient login with an IC number still works and opens the patient home.
- Typing a valid IC on sign up fills date of birth and gender; an invalid IC blocks Next with a clear message.
- Password rules and the strength bar update live.
- Staff screens use the navy theme and patient screens use Clinic Sky.
- The unit tests pass and the app builds without new warnings you introduced.
- `local.properties` is not in any commit, and nothing was pushed.
- At the end, list which parts are real and which are `TODO(supabase)`, `TODO(demo)` or `TODO(appointment module)`.
