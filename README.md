# DataFloat – real-time mobile data counter

Floating, draggable overlay that shows **cumulative mobile data used** (e.g. `125.4 MB`, `Today: 1.42 GB`), never speed.
Kotlin · Jetpack Compose · NetworkStatsManager + TrafficStats · foreground service · DataStore + Room. No account, no cloud.

> Status: written without access to an Android SDK, so it has **not been compiled or run yet**. Expect to fix, at most, a few small
> compile errors on first sync. See "First build" below.

## Requirements
- Android Studio Ladybug (2024.2) or newer, JDK 17 (bundled with Studio), Android SDK 35
- A **real phone with a SIM and mobile data**, Android 10+ (API 29+). Emulators have no real mobile counters.

## Build and install
1. *File > Open* the `DataFloat` folder. Let Gradle sync (it downloads Gradle 8.9 from `gradle-wrapper.properties`).
   If Studio complains about a missing wrapper, run `gradle wrapper --gradle-version 8.9` once, or accept its upgrade prompts.
2. Enable *Developer options > USB debugging* on the phone, connect it, press **Run ▶**.
   Or from a terminal: `./gradlew assembleDebug`, then
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. Release APK: *Build > Generate Signed App Bundle / APK*.

### First build: likely small fixes
Versions are pinned in `build.gradle.kts` / `app/build.gradle.kts` (AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.01,
Room 2.6.1). If Studio suggests newer ones, upgrading is fine. If a version pair is rejected, keep Kotlin and KSP versions matching
(`<kotlin>-<ksp>`).

## Using it
1. Open the app, grant **Usage access** and **Display over other apps** (and Notifications on Android 13+). Press Back after each.
2. Tracking and the floating counter start automatically. Drag the counter; tap it to cycle Daily → Session → Monthly.
3. Dashboard: today, billing cycle, session (with reset), per-app breakdown, 14-day history, limit progress.
4. Settings: mode, monthly reset day, corner presets, style, units, text size, bubble size, corner radius, opacity, colors,
   background on/off, update frequency, daily/monthly limit, notification options.

## Test plan
| Test | Steps | Expected |
|---|---|---|
| Mobile only | Turn Wi-Fi **off**. Note the counter. Download ~25 MB (a speed-test file or video). | Counter rises by about 25 MB. |
| Wi-Fi ignored | Turn Wi-Fi **on**, repeat the download. | Counter does not move. |
| Switch networks | Download while toggling Wi-Fi/mobile. | Only mobile portion counted; no jumps. |
| Over other apps | Open a browser or game with the counter visible. | Counter stays on top and keeps updating. |
| Hide ≠ stop | Turn "Show floating counter" off, use data, turn it on. | Total includes the data used while hidden. |
| Session | Reset session, use data. Close the app from recents. Reopen. | Session value preserved and still growing. |
| Reboot | Restart the phone. | Tracking resumes (if it was on); session continues from the last saved value. |
| Midnight | Leave running across midnight (or change the clock). | "Today" resets; yesterday appears in history. |
| Monthly | Set reset day to today. | Monthly total counts from today 00:00. |
| Limits | Set daily limit to a small value (e.g. 20 MB) and use data. | One notification each at 80 %, 90 %, 100 %. |
| Cross-check | Compare Android *Settings > Network > Data usage* with the dashboard for the same period. | Within a few percent (see LIMITATIONS). |
| Permission loss | Revoke Usage access or Overlay in system settings. | Overlay hides or shows N/A; app shows a warning, no crash. |

## Project layout
```
app/src/main/java/com/datafloat/app/
  TrackerEngine.kt      core accounting (session, daily, monthly, never-decreasing totals)
  UsageSource.kt        NetworkStatsManager + TrafficStats wrappers, per-app breakdown
  TrackerService.kt     foreground service: tick loop, limits, notification
  OverlayController.kt  draggable overlay window (TYPE_APPLICATION_OVERLAY)
  SettingsRepo.kt       DataStore settings + session state     Db.kt  Room daily history
  BootReceiver.kt       restart after reboot/update            Notifs.kt  channels + limit alerts
  MainViewModel.kt, MainActivity.kt, ui/  (Compose dashboard, settings, onboarding)
docs/LIMITATIONS.md     how Android limits data tracking
```

## Permissions and why
| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` (special access) | Read mobile usage from NetworkStatsManager |
| `SYSTEM_ALERT_WINDOW` (special access) | Draw the floating counter |
| `FOREGROUND_SERVICE`, `..._SPECIAL_USE` | Keep tracking/overlay alive |
| `POST_NOTIFICATIONS` | Limit warnings, status notification (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Resume after restart |

No `READ_PHONE_STATE` or `QUERY_ALL_PACKAGES` is needed (minSdk 29 allows a null subscriber id; app names come from a launcher `<queries>` entry).

## Get an APK without Android Studio (GitHub Actions)
1. Create a GitHub repository and upload the **contents** of this folder (so `.github/workflows/build-apk.yml` is at the repo root).
2. Open the repo's *Actions* tab > *Build APK* > *Run workflow* (it also runs on every push).
3. When it finishes, open the run and download the **DataFloat-debug-apk** artifact (a zip containing `app-debug.apk`), then install it on your phone.
If the run fails, open the failed step: the error shows which Kotlin line to fix.
