package com.datafloat.app

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Computes the cumulative numbers shown in the overlay and dashboard.
 *
 * SESSION  = sum of deltas of the since-boot TrafficStats mobile counter, persisted. Exact to the
 *            kernel counter and independent of Android's coarse stats buckets. Reboots are detected
 *            when the counter goes backwards.
 * DAILY / MONTHLY = NetworkStatsManager total for the period (authoritative, covers time the app was
 *            not running) re-read every [NS_REFRESH_MS], plus the TrafficStats delta since that read so
 *            the number moves in near real time. After each re-read we keep max(NetworkStats, our
 *            estimate) so the counter never goes backwards when Android's stats lag.
 */
class TrackerEngine(private val app: App) {
    companion object {
        private const val NS_REFRESH_MS = 30_000L
        private const val PERSIST_MS = 20_000L
    }

    private val repo get() = app.settingsRepo
    private val source get() = app.usageSource
    private val mutex = Mutex()
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private var loaded = false
    private var sessionBytes = 0L
    private var lastTs = -1L
    private var lastPersistMs = 0L

    private var baseDay: Long? = null
    private var baseMonth: Long? = null
    private var baseTs = -1L
    private var baseAtMs = 0L
    private var dayKey = ""
    private var monthKey = ""

    suspend fun tick(forceNs: Boolean = false) {
        mutex.withLock { withContext(Dispatchers.IO) { tickLocked(forceNs) } }
    }

    suspend fun resetSession() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                ensureLoaded(repo.current())
                val counter = source.mobileCounter()
                val now = System.currentTimeMillis()
                sessionBytes = 0L
                lastTs = counter
                repo.update { it.copy(sessionBytes = 0L, lastTrafficReading = counter, sessionStartMs = now) }
                _state.value = _state.value.copy(sessionBytes = 0L)
            }
        }
    }

    /** Persist everything now (called when the service stops). */
    suspend fun flush() {
        mutex.withLock { withContext(Dispatchers.IO) { if (loaded) persist() } }
    }

    private suspend fun ensureLoaded(s: AppSettings) {
        if (loaded) return
        loaded = true
        sessionBytes = s.sessionBytes
        lastTs = s.lastTrafficReading
        if (s.sessionStartMs == 0L) {
            repo.update { it.copy(sessionStartMs = System.currentTimeMillis()) }
        }
    }

    /** Bytes counted by TrafficStats since [baseTs]; handles the counter resetting on reboot. */
    private fun liveDelta(ts: Long): Long = when {
        ts < 0 || baseTs < 0 -> 0L
        ts >= baseTs -> ts - baseTs
        else -> ts
    }

    private suspend fun tickLocked(forceNs: Boolean) {
        val s = repo.current()
        ensureLoaded(s)
        val now = System.currentTimeMillis()
        val ts = source.mobileCounter()

        // ---- Session: accumulate counter deltas (survives restarts: lastTs is persisted) ----
        if (ts >= 0) {
            if (lastTs >= 0) sessionBytes += if (ts >= lastTs) ts - lastTs else ts
            lastTs = ts
        }

        // ---- Period keys (midnight / billing-cycle rollover happens here automatically) ----
        val today = LocalDate.now()
        val cycle = Periods.cycleStart(today, s.monthlyResetDay)
        val newDayKey = today.toString()
        val newMonthKey = cycle.toString()
        val dayChanged = newDayKey != dayKey
        val monthChanged = newMonthKey != monthKey
        val prev = _state.value
        if (dayChanged && dayKey.isNotEmpty()) {
            prev.dayBytes?.let { app.db.dao().upsertMax(dayKey, it) } // close out yesterday
        }
        dayKey = newDayKey
        monthKey = newMonthKey

        // ---- Daily / monthly baselines from NetworkStatsManager ----
        val access = source.hasUsageAccess()
        if (!access) {
            baseDay = null
            baseMonth = null
            baseTs = -1L
        } else {
            val backwards = ts >= 0 && baseTs >= 0 && ts < baseTs
            val stale = now - baseAtMs >= NS_REFRESH_MS
            if (forceNs || dayChanged || monthChanged || backwards || stale || baseDay == null || baseMonth == null) {
                refreshBaselines(today, cycle, now, dayChanged, monthChanged)
            }
        }

        val live = liveDelta(ts)
        var day = baseDay?.plus(live)
        var month = baseMonth?.plus(live)
        if (prev.dayKey == dayKey) day = day?.let { maxOf(it, prev.dayBytes ?: 0L) }
        if (prev.monthKey == monthKey) month = month?.let { maxOf(it, prev.monthBytes ?: 0L) }

        _state.value = Snapshot(
            dayBytes = day,
            monthBytes = month,
            sessionBytes = sessionBytes,
            dayKey = dayKey,
            monthKey = monthKey,
            updatedAtMs = now,
            usageAccess = access,
            mobileSupported = ts >= 0,
        )

        if (now - lastPersistMs >= PERSIST_MS) persist(now)
    }

    private fun refreshBaselines(
        today: LocalDate, cycle: LocalDate, now: Long, dayChanged: Boolean, monthChanged: Boolean,
    ) {
        val tsBefore = source.mobileCounter() // read BEFORE the query so nothing falls in a gap
        val d = source.mobileBytes(Periods.startOfDayMs(today), now) ?: return
        val m = source.mobileBytes(Periods.startOfDayMs(cycle), now) ?: return
        val estDay = if (!dayChanged) baseDay?.let { it + liveDelta(tsBefore) } else null
        val estMonth = if (!monthChanged) baseMonth?.let { it + liveDelta(tsBefore) } else null
        baseDay = maxOf(d, estDay ?: 0L)
        baseMonth = maxOf(m, estMonth ?: 0L)
        baseTs = tsBefore
        baseAtMs = now
    }

    private suspend fun persist(now: Long = System.currentTimeMillis()) {
        lastPersistMs = now
        val sb = sessionBytes
        val lt = lastTs
        repo.update { it.copy(sessionBytes = sb, lastTrafficReading = lt) }
        _state.value.dayBytes?.let { app.db.dao().upsertMax(dayKey, it) }
    }
}
