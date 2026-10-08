# USJM Smart Library Mobile

An Android app (Java) that lets University of Saint Joseph Mbarara (USJM) students and staff browse the
library catalogue on their phone, check whether a copy is on the shelf, and reserve it for pickup at an
express counter, so they spend less time queueing at the main circulation desk. Librarians and
administrators use the same app for their own work (see User roles).

Course unit: Android Programming (Year 3). Mobile companion to my final-year project, the USJM Smart
Library Management System.

## Features

| Screen | What it does |
|---|---|
| Sign in | Email and password (Firebase Authentication), Show/Hide password, links to Forgot password and Create account; skipped when already signed in |
| Create account | I am a student / teaching staff / non-teaching staff, full name, registration number or staff ID, email, password and confirmation, all validated; sends a verification email |
| Reset password | Sends a password-reset link to the student's email |
| Catalogue | Greets the signed-in person with a colour-coded role badge; lists every book from the phone's SQLite copy of the shared catalogue (kept live from Cloud Firestore); live search by title, author or subject; shows how many copies are available |
| Book details | Shelf location, subject, year, description and availability; Reserve button (disabled when no copies are left) |
| Reserve | Form with full name, registration number or staff ID and pickup date (calendar); validates every field; saves the reservation to Cloud Firestore in one transaction, so the last copy can never go to two people |
| My reservations | Live list of holds and loans (Active / On loan with due date / Overdue / Returned / Cancelled / Not collected / Expired); cancel with confirmation, which puts the copy back on the shelf |
| Circulation desk (librarians) | Tabs for Pickups, On loan, Overdue and Stock; mark a hold **Collected** (starts a loan with a due date) or **No-show**, and a loan **Returned**; scan a book's QR label to see only that book's holds and loans; publish the shared catalogue (sample books, or import from the web system's REST API) |
| Manage users (administrators) | Approve staff accounts, reject a staff request, change anyone's role (e.g. make a librarian), suspend or restore an account; search by name, email or ID |

## User roles

| Role | How you get it | Reservations at a time | Book ahead |
|---|---|---|---|
| Student | Choose it when signing up (approved at once) | 3 | 7 days |
| Teaching staff | Choose it when signing up, then an administrator approves | 10 | 14 days |
| Non-teaching staff | Choose it when signing up, then an administrator approves | 5 | 7 days |
| Librarian | Given by an administrator | 5 | 14 days |
| Administrator | Given by another administrator (the first one is set in the Firebase console) | 5 | 14 days |

Loans last 14 days for students and 30 days for staff. Staff waiting for approval can use the app with student limits. Roles are stored in Cloud Firestore
(`users/{uid}`) and protected by `firestore.rules`: nobody can give themselves a role, approve
themselves or lift their own suspension, even with a modified app.

Library rules enforced by the app:
- a book can be reserved only while a copy is available;
- a student cannot reserve the same book twice;
- at most 3 active reservations for a student (more for staff, see User roles);
- the pickup date must be from today up to 7 days ahead (14 for lecturers), and not a Sunday;
- reservations not collected by their pickup date expire automatically and the copy returns to the shelf.

## Technology

- Java, Android SDK (min SDK 24), AppCompat
- Firebase Authentication (email and password accounts, password-reset email)
- Cloud Firestore (user profiles and roles, the shared catalogue, reservations and loans) with live listeners
  and transactions, protected by security rules in `firestore.rules`
- SQLite (`SQLiteOpenHelper`) as the phone's offline copy of the catalogue for fast search
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
2. Cloud Firestore: in the Firebase console open **Firestore Database → Create database**, then open
   the **Rules** tab, paste the contents of `firestore.rules` and click **Publish**.
3. First administrator: create an account in the app, then in **Firestore Database → Data → users**
   open your document and set `role` to `admin` and `approved` to `true`. From then on, administrators
   manage everyone else from the app.
4. Open the project in Android Studio and wait for Gradle sync to finish.
5. Start an emulator (Device Manager) or connect an Android phone with USB debugging on.
6. Press **Run ▶**, then create an account on the Sign in screen.
7. Make an account a librarian (Manage users → Change role → Librarian), sign in with it, open the
   **Circulation desk** and publish the catalogue (Catalogue → Publish the sample books, or Import
   from the web system). Until then readers see sample books and can't reserve.

Run the unit tests: right-click `app/src/test/java/ug.ac.usjm.smartlibrary/util/ValidatorTest` → **Run**.

The registration number format accepted is `2023/BIT/0457` (or `USJM/23/BIT/045`). Change the
`REG_NUMBER` pattern in `util/Validator.java` if your format differs.

## Planned for the final submission

- Circulation desk extras: walk-in issue, add and edit books, overdue reminders
- Teaching staff: book recommendations and course reading lists; administrator statistics
- Full test plan and a signed release APK

## Acknowledgements

- Sample book titles are real published works, used only as catalogue data.
- Android developer documentation: https://developer.android.com
- Parts of this code were developed with help from an AI assistant (Claude, by Anthropic); I have
  reviewed, tested and can explain all of it.
