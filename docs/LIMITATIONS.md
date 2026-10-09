# Android limitations that affect DataFloat

## Where the numbers come from
1. **NetworkStatsManager** (usage statistics). Authoritative, per period and per app, survives reboots, but:
   - Android keeps it in time **buckets (typically 2 hours)**. A query starting or ending inside a bucket gets a *proportional share*
     of that bucket. So "since midnight" and "this cycle" can be off by a small amount at the period boundary.
   - The system **publishes new traffic to these stats in batches** (seconds to a couple of minutes late).
   - Needs the special **Usage access** permission; without it only the session counter works.
2. **TrafficStats mobile counter**. Near-real-time since-boot byte counter for all mobile interfaces, no permission, but it resets at
   reboot and has no per-app/period history.

## How DataFloat combines them
- **Session** = persisted sum of TrafficStats deltas. Exact and immediate; reboot detected when the counter goes backwards.
  Traffic between the last save (≤ 20 s) and a power-off is lost; traffic while the service was not running is still included until
  the next reboot because the kernel counter keeps counting.
- **Daily / Monthly** = NetworkStats total (re-read every 30 s) + TrafficStats delta since that read. After each re-read the larger of
  the two is kept, so the displayed value **never decreases** and does not stall while Android's stats lag.
- **Wi-Fi is never counted**: both sources are mobile-only, so switching networks cannot double-count.

## Update delays you cannot avoid
- Redraw rate is configurable (1–30 s), but the exact total from Android may lag. The live estimate bridges most of the gap.
- With the screen off, DataFloat wakes once a minute (battery); it catches up instantly when the screen turns on.
- Doze / OEM battery managers (Xiaomi, Huawei, Samsung, OnePlus…) may kill background services. Exempt DataFloat from battery optimization and
  allow auto-start if the counter freezes or the overlay disappears.

## Accuracy vs your carrier
Device-side counts differ from the bill: protocol overhead below the counted layer, rounding, roaming, zero-rated/free-zone traffic,
VoLTE/IMS, and tethering rules. Expect a few percent difference. DataFloat uses decimal units (1 MB = 1,000,000 bytes) like Android Settings.

## Other platform limits
- **Per-app breakdown**: attributed by UID; apps sharing a UID are merged, removed apps appear as "Removed apps", tethering as its own row.
  App names are only resolved for launchable apps (system services show as "System (uid)").
- **History**: Android only keeps limited history (roughly 90 days, shorter on some phones). DataFloat copies daily totals into its own
  database, but days before install are only as complete as Android's stats.
- **Multi-SIM**: totals combine all mobile subscriptions.
- **Overlay**: Android hides overlays on some system screens (permission dialogs, lock screen, some banking/secure windows). Android 12+ may
  block touches through overlays on some surfaces; the counter itself still draws normally.
- **Foreground service rules** (Android 14/15): service uses type `specialUse`, started only from the app UI or at boot.
  Android 15 prevents some types from starting at boot; `specialUse` is allowed.
- **Permissions can vanish**: the user can revoke Usage access/Overlay at any time. The app re-checks on every resume, shows N/A instead of wrong numbers,
  and the overlay hides itself without crashing.
- **Device support**: if a phone exposes no mobile counter (`TrafficStats.UNSUPPORTED`), the app shows a warning.
