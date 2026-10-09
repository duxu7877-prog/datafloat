package com.datafloat.app

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val a = application as App

    /** null until DataStore has loaded, so the UI never acts on default values. */
    val settings: StateFlow<AppSettings?> =
        a.settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val snapshot: StateFlow<Snapshot> = a.engine.state
    val history: StateFlow<List<DailyUsage>> =
        a.db.dao().recent(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var perms by mutableStateOf(readPerms())
        private set
    var apps by mutableStateOf<List<AppUsage>>(emptyList())
        private set
    var appsMonth by mutableStateOf(false)
        private set

    private var lastBackfillMs = 0L

    private fun readPerms() = Perms(
        usageAccess = a.usageSource.hasUsageAccess(),
        overlay = Settings.canDrawOverlays(a),
        notifications = NotificationManagerCompat.from(a).areNotificationsEnabled(),
    )

    fun refreshPermissions() {
        perms = readPerms()
    }

    fun refresh() {
        refreshPermissions()
        viewModelScope.launch {
            val s = a.settingsRepo.current()
            if (s.trackingEnabled) startService()
            a.engine.tick(forceNs = true)
            backfillHistory()
            loadApps()
        }
    }

    fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { a.settingsRepo.update(block) }
    }

    fun setTracking(on: Boolean) {
        update { it.copy(trackingEnabled = on) }
        if (on) startService() else a.stopService(Intent(a, TrackerService::class.java))
    }

    fun setOverlay(on: Boolean) {
        update { it.copy(overlayVisible = on) }
        if (on && settings.value?.trackingEnabled != true) setTracking(true)
    }

    fun completeOnboarding() {
        update { it.copy(onboarded = true, trackingEnabled = true, overlayVisible = true) }
        startService()
    }

    fun resetSession() {
        viewModelScope.launch { a.engine.resetSession() }
    }

    fun setAppsPeriod(month: Boolean) {
        appsMonth = month
        viewModelScope.launch { loadApps() }
    }

    private fun startService() {
        runCatching { ContextCompat.startForegroundService(a, Intent(a, TrackerService::class.java)) }
    }

    private suspend fun loadApps() {
        if (!perms.usageAccess) {
            apps = emptyList()
            return
        }
        val s = a.settingsRepo.current()
        val today = LocalDate.now()
        val start = Periods.startOfDayMs(if (appsMonth) Periods.cycleStart(today, s.monthlyResetDay) else today)
        apps = withContext(Dispatchers.IO) { a.usageSource.appBreakdown(start, System.currentTimeMillis()) }
    }

    /** Fill the last 30 days from Android's own stats so history is available right after install. */
    private suspend fun backfillHistory() {
        val now = System.currentTimeMillis()
        if (!perms.usageAccess || now - lastBackfillMs < 10 * 60_000L) return
        lastBackfillMs = now
        withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            for (i in 1..30) {
                val d = today.minusDays(i.toLong())
                val b = a.usageSource.mobileBytes(Periods.startOfDayMs(d), Periods.startOfDayMs(d.plusDays(1))) ?: continue
                if (b > 0L) a.db.dao().upsertMax(d.toString(), b)
            }
        }
    }
}
