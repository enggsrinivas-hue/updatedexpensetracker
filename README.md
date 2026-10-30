# Expense Tracker (SMS)

Offline Android app that reads bank / card / UPI alert SMS notifications, parses them on-device and stores them in Room.

## Get the APK with GitHub (no Android Studio needed)
1. Create a new empty GitHub repo.
2. Upload everything in this folder (keep the `.github` folder).
3. Open the **Actions** tab, run **Build APK** (it also runs on every push).
4. When it is green, open the run and download **expense-tracker-debug-apk** from Artifacts. Unzip it and install `app-debug.apk`.

Android may warn about installing from unknown sources and about SMS permission. Allow both.

## After installing
Open the app, tap **Open settings** and turn on notification access for Expense Tracker.
If the switch is greyed out: App info -> three dots -> **Allow restricted settings**, then try again.
