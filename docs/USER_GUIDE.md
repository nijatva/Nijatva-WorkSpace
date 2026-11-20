# Nijatva Work Space — User Guide

Work profile manager for Android · Version 1.0 · Package `com.nijatva.workspace`

## What Nijatva Work Space does

Nijatva Work Space creates a separate work profile on your phone. Apps and files in the work profile stay
apart from your personal apps. Nijatva Work Space adds the controls Android leaves out: freeze work apps,
move files between the two spaces, and choose which work apps may interact with your personal
space. The app works fully offline and has no internet permission.

## First-time setup

1. Install Nijatva Work Space and open it.
2. Tap **Create work profile** and complete the Android setup wizard.
3. Wait for the app to show two tabs: **Personal** and **Work**.
4. Install or clone your work apps into the Work tab.

Removing the work profile deletes the apps and data inside it — back up first.

## The app list

| Action | What it does |
|---|---|
| Freeze / Unfreeze | Hides the app and stops it running; its data is kept |
| Launch | Opens the app (unfreezing first if needed) |
| Clone to main profile | Installs the same app in the other profile |
| Allow Widgets in Main Profile | Lets the app's widgets appear on your personal launcher |
| Allow Cross-Profile Interaction | Lets this app exchange data with the other profile |

Long-press an app for the menu; long-press also starts multi-select for batch freeze.
The magnifier searches by name or package; the sort icon offers Name A–Z, Name Z–A,
Frozen first and Active first.

## Settings, row by row

| Setting | What it does |
|---|---|
| File Shuttle | Move files between profiles through the system Files app |
| Allow all apps cross-profile | Every work app may interact with your personal space |
| Per-app cross-profile access | Pick exactly which work apps may interact |
| Block contacts searching | Hides work contacts from apps in your personal space |
| Auto-Freeze | Freezes work apps automatically when the screen locks |
| Auto-Freeze delay | Immediately, 1, 2 or 5 minutes after locking |
| Skip foreground apps | Leaves apps you are using unfrozen (music players) |
| Support the developer | Optional UPI QR; the app is free and offline |
| Send feedback | Opens your email app with the developer's address |

## File Shuttle

File Shuttle adds a **Nijatva Work Space** entry to the system Files app. Turn it on in Settings; Nijatva Work Space then
checks four permissions and shows OK or MISSING for each:

- All files access — Personal
- All files access — Work
- Display over other apps — Personal
- Display over other apps — Work

Turn on everything marked MISSING, then flip the switch again. Open Files, choose Nijatva Work Space, then
pick Personal or Work.

## Cross-profile access

Android blocks app-to-app actions between profiles by default; when an app tries one you see
*Access blocked by your IT admin*. In Nijatva Work Space you are the admin:

- **Settings → Allow all apps cross-profile** — allow everything at once
- **Settings → Per-app cross-profile access** — tick only the apps you want
- **Work tab → long-press an app → Allow Cross-Profile Interaction** — one app

If the work profile is managed by a company instead of Nijatva Work Space, that policy applies.

## Troubleshooting

| Problem | What to do |
|---|---|
| Work profile will not connect | Reopen the app (three retries, then a diagnostic dialog); set battery use to Unrestricted |
| Documents missing from the picker | Turn on File Shuttle and grant all four permissions |
| Apps do not freeze | Check Auto-Freeze is on and Skip foreground apps is not excluding them |
| Access blocked by IT admin | Settings → Allow all apps cross-profile |
| Old icon after updating | Remove and re-add the shortcut, or restart the launcher |

## Screenshots to capture

Save as PNG in `docs/screenshots`, numbered in this order: 01 app list (Personal), 02 app list
(Work, frozen app), 03 settings, 04 File Shuttle dialog, 05 per-app cross-profile dialog,
06 About section.
