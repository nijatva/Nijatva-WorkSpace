# Nijatva Work Space

**A GPLv3 fork of Shelter, with ~45 features added.**

An offline-first Android work-profile manager — part of the Nijatva suite.

**Package** `com.nijatva.workspace` · **Version** 1.0 · **minSdk** 24 · **targetSdk** 35
**Language** Java + XML · **Licence** GPLv3 (derived from Shelter by PeterCxy)

---

## What it does

**ISOLATE** — creates a real Android work profile. Work apps and data stay apart from your personal space.

**CONTROL**
- Freeze / unfreeze apps, batch freeze, "freeze all others"
- Auto-freeze on screen lock (0 / 1 / 2 / 5 / 15 min), skip foreground apps
- **Timed unfreeze ("leash")** — unfreeze for 15/30/60 min, then it freezes itself again
- Suspend mode (visible but cannot run), uninstall guard, force-stop protection
- Clone apps into the work space, install APKs with a metadata preview
- Search, 5 sort modes, filter chips with live counts, multi-select + batch dock

**PROTECT** (every switch reads its policy back from Android)
- Block screenshots and screen recording
- Hide work notification content on the lock screen
- Block work-data backup, Bluetooth contact sharing
- Cross-profile notification-listener firewall
- Caller-ID isolation, trusted-CA inventory
- Live work-profile status (active / paused / removed)

**TRANSFER**
- File Shuttle bridge through the system Files app, with a 4-condition permission checker
- `Nijatva/Shuttle` staging folder with auto-wipe (15 min / 1 h / 24 h) and manual clean

**INSPECT** (read-only, local)
- Profile integrity check (profile owner / admin active / profile available)
- App inventory: versions, target SDK, signing SHA-256, permission counts
- Permission snapshots and diffs — "Microphone: denied → GRANTED"
- Policy verification report, exported-component inspector

**ECOSYSTEM**
- Nijatva suite registry (14 apps) with a dedicated filter chip and suite screen
- Nijatva Open API — `ACTION_FREEZE` / `UNFREEZE` / `LAUNCH` behind a signature permission
- Quick Settings tile to freeze / unfreeze every work app

---

## Build

```bash
./gradlew assembleDebug        # quick build
./gradlew assembleRelease      # signed build (needs keystore.properties)
./gradlew check                # unit tests + no-GMS guard
```

Version lives in `version.properties` (`VERSION_CODE`, `VERSION_NAME`).

Release signing: copy `keystore.properties.example` → `keystore.properties` and fill it in.

### Guarantees enforced by the build

- `checkNoGms` fails the build if Google Play Services or Firebase ever appears on the classpath.
- The app declares **no `INTERNET` permission** — it cannot phone home.
- Unit tests cover the sorting rules and the Nijatva suite registry.

---

## Privacy

No analytics, no ads, no accounts, no cloud, no crash reporting, no AI APIs. Everything the app
knows stays on the device. See `docs/PRIVACY.md`.

---

## Licence and credit

This project is derived from **Shelter** by PeterCxy (<https://github.com/PeterCxy/Shelter>) and is
licensed under the **GNU General Public License v3**. If you redistribute it, keep the licence, the
source, and this credit.

Made in India.
