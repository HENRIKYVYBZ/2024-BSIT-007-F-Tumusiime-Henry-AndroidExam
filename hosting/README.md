# Password-reset page (Firebase Hosting)

Firebase's own reset page has a single password box. These files replace it with our page, where
the new password is typed **twice** and saved only when both match and follow the app's rules.

| File | Purpose |
|---|---|
| `public/action.html` | Reset page (also finishes verify-email and undo-email-change links) |
| `public/.well-known/assetlinks.json` | Proves the app owns the link, so on the phone it opens `ResetPasswordActivity` |
| `firebase.json` | Serves `/auth/action`, keeps `.well-known`, stops the one-time code leaking (no-referrer, no-store) |
| `deploy.bat` / `deploy.ps1` | Writes the app's SHA-256 into assetlinks.json and deploys |

## One-time setup

1. Install Node.js LTS from https://nodejs.org if `node -v` does not work.
2. Double-click `deploy.bat`. Sign in with your Google account when the browser opens.
3. Firebase console → **Authentication → Templates → Password reset → pencil icon →
   Customize action URL** → `https://usjm-smart-library.web.app/auth/action` → **Save**.
   (The setting applies to every template, which is why the page also handles verify-email links.)
4. Run the app again from Android Studio so Android re-checks the link.

## Where the link opens

Firebase's default link (`https://usjm-smart-library.firebaseapp.com/__/auth/action?...`) also opens the app on the phone, because the manifest claims that path and assetlinks.json is served on both domains. So the app screen works even if the console refuses "Customize action URL"; only a PC browser then falls back to Firebase's one-box page.


- Phone with the app installed: `ResetPasswordActivity` (App Link, verified by assetlinks.json).
- Anywhere else (PC browser, a phone without the app): `action.html` on usjm-smart-library.web.app.

Both check the code with `verifyPasswordResetCode` (shows whose account it is), then save with
`confirmPasswordReset`.

## Signed release APK later

The release key has a different fingerprint. Put it (one per line) in `hosting/release-sha256.txt`
and double-click `deploy.bat` again. Get it with `gradlew signingReport` or from Play Console.
