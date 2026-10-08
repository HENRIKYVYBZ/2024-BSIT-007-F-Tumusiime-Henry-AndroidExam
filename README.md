# USJM Smart Library Mobile

An Android app (Java) that lets University of Saint Joseph Mbarara (USJM) students browse the library
catalogue on their phone, check whether a copy is on the shelf, and reserve it for pickup at an express
counter, so they spend less time queueing at the main circulation desk.

Course unit: Android Programming (Year 3). Mobile companion to my final-year project, the USJM Smart
Library Management System.

## Features

| Screen | What it does |
|---|---|
| Sign in | Email and password (Firebase Authentication), Show/Hide password, links to Forgot password and Create account; skipped when already signed in |
| Create account | Full name, registration number, email, password and confirmation, all validated; sends a verification email |
| Reset password | Sends a password-reset link to the student's email |
| Catalogue | Greets the signed-in student; lists every book from the phone's SQLite database; live search by title, author or subject; shows how many copies are available |
| Book details | Shelf location, subject, year, description and availability; Reserve button (disabled when no copies are left) |
| Reserve | Form with full name, registration number and pickup date (calendar); validates every field; saves the reservation to SQLite in one transaction |
| My reservations | Lists reservations (Active / Cancelled / Expired); cancel with confirmation, which puts the copy back on the shelf |

Library rules enforced by the app:
- a book can be reserved only while a copy is available;
- a student cannot reserve the same book twice;
- at most 3 active reservations per registration number;
- the pickup date must be from today up to 7 days ahead, and not a Sunday;
- reservations not collected by their pickup date expire automatically and the copy returns to the shelf.

## Technology

- Java, Android SDK (min SDK 24), AppCompat
- Firebase Authentication (email and password accounts, password-reset email)
- SQLite through `SQLiteOpenHelper` (tables `books` and `reservations`)
- `SharedPreferences` to remember the student's name and registration number
- JUnit 4 unit tests for the form validation rules (20 tests)

## Project structure

```
app/src/main/java/ug/ac/usjm/smartlibrary/
  LoginActivity.java, SignUpActivity.java, ForgotPasswordActivity.java   account screens
  auth/   AuthMessages (friendly Firebase errors), ProfileStore, PasswordToggle
  MainActivity.java            Screen 1 - catalogue + search
  BookDetailActivity.java      Screen 2 - book details
  ReserveActivity.java         Screen 3 - reservation form
  MyReservationsActivity.java  Screen 4 - my reservations
  BookAdapter.java, ReservationAdapter.java   list rows
  data/   LibraryDbHelper (schema + sample books), LibraryRepository (all SQL and rules),
          Book, Reservation, ReservationException
  util/   Validator (form rules), DateText (date formatting)
app/src/main/res/layout/       one XML layout per screen and per list row
app/src/test/.../ValidatorTest.java   unit tests
docs/                          diagrams, wireframes and report
```

## How to run

1. Firebase: create a project at console.firebase.google.com, add an Android app with package
   `ug.ac.usjm.smartlibrary`, download `google-services.json` into the `app/` folder, and enable
   **Authentication → Email/Password**. (The file is not in this repository on purpose.)
2. Open the project in Android Studio and wait for Gradle sync to finish.
3. Start an emulator (Device Manager) or connect an Android phone with USB debugging on.
4. Press **Run ▶**, then create an account on the Sign in screen.

Run the unit tests: right-click `app/src/test/java/ug.ac.usjm.smartlibrary/util/ValidatorTest` → **Run**.

The registration number format accepted is `2023/BIT/0457` (or `USJM/23/BIT/045`). Change the
`REG_NUMBER` pattern in `util/Validator.java` if your format differs.

## Planned for the final submission

- Sync the catalogue and reservations with the Smart Library web system (REST API), keeping SQLite as an offline cache
- Pickup reminder notifications
- Scan a book's QR label to open its details
- Full test plan and a signed release APK

## Acknowledgements

- Sample book titles are real published works, used only as catalogue data.
- Android developer documentation: https://developer.android.com
- Parts of this code were developed with help from an AI assistant (Claude, by Anthropic); I have
  reviewed, tested and can explain all of it.
