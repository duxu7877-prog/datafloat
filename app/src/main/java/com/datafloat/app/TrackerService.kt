package com.datafloat.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service: owns the tick loop and the overlay window. Ticks every N seconds while the
 * screen is on and once a minute while it is off (counters keep counting; we just catch up later).
 */
class TrackerService : Service() {
    companion object {
        const val ACTION_STOP = "com.datafloat.app.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private lateinit var app: App
    private lateinit var overlay: OverlayController
    private var settings: AppSettings? = null
    private var screenOn = true
    private var started = false
    private var lastNotifMs = 0L
    private var lastNotifText = ""
    private var lastHandledWarn = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            screenOn = i?.action == Intent.ACTION_SCREEN_ON
            wake.trySend(Unit)
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as App
        Notifs.ensureChannels(this)
        overlay = OverlayController(
            ctx = this,
            onMoved = { x, y -> scope.launch { app.settingsRepo.update { it.copy(overlayX = x, overlayY = y) } } },
            onTap = {
                scope.launch {
                    app.settingsRepo.update {
                        it.copy(mode = TrackingMode.entries[(it.mode.ordinal + 1) % TrackingMode.entries.size])
                    }
                }
            },
        )
        screenOn = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CoroutineScope(Dispatchers.IO).launch {
                app.settingsRepo.update { it.copy(trackingEnabled = false) }
            }
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!started) {
            started = true
            scope.launch {
                settings = app.settingsRepo.current()
                launch { app.settingsRepo.flow.collect { settings = it; wake.trySend(Unit) } }
                loop()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.reposition()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        overlay.destroy()
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch { app.engine.flush() }
        super.onDestroy()
    }

    private fun startInForeground() {
        val n = Notifs.tracking(this, "Counting mobile data in the background")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifs.ID_TRACK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifs.ID_TRACK, n)
        }
    }

    private suspend fun loop() {
        app.engine.tick(forceNs = true)
        render()
        while (currentCoroutineContext().isActive) {
            val waitMs = if (screenOn) (settings?.updateIntervalSec ?: 5) * 1000L else 60_000L
            withTimeoutOrNull(waitMs) { wake.receive() }
            app.engine.tick()
            render()
        }
    }

    private fun render() {
        val s = settings ?: return
        val snap = app.engine.state.value
        if (screenOn) {
            overlay.update(s, Fmt.overlayText(s.style, s.mode, snap.valueFor(s.mode), s.unit))
        }
        updateNotification(s, snap)
        checkLimit(s, snap)
    }

    private fun updateNotification(s: AppSettings, snap: Snapshot) {
        val text = if (s.persistentNotification) {
            "Today ${Fmt.bytesOrNa(snap.dayBytes, s.unit)}  ·  Session ${Fmt.bytes(snap.sessionBytes, s.unit)}"
        } else {
            "Counting mobile data in the background"
        }
        val now = System.currentTimeMillis()
        if (text != lastNotifText && now - lastNotifMs >= 15_000L) {
            lastNotifText = text
            lastNotifMs = now
            Notifs.updateTracking(this, text)
        }
    }

    /** Warn once each at 80 / 90 / 100 % of the configured daily or monthly limit. */
    private fun checkLimit(s: AppSettings, snap: Snapshot) {
        if (s.limitType == LimitType.NONE || s.limitMb <= 0L) return
        val (used, key, name) = when (s.limitType) {
            LimitType.DAILY -> Triple(snap.dayBytes, "D${snap.dayKey}", "daily")
            LimitType.MONTHLY -> Triple(snap.monthBytes, "M${snap.monthKey}", "monthly")
            LimitType.NONE -> return
        }
        if (used == null) return
        val limit = s.limitMb * 1_000_000L
        val pct = used * 100 / limit
        val level = when {
            pct >= 100 -> 100
            pct >= 90 -> 90
            pct >= 80 -> 80
            else -> 0
        }
        val prevLevel = if (s.warnState.startsWith("$key|")) s.warnState.substringAfter('|').toIntOrNull() ?: 0 else 0
        val marker = "$key|$level"
        if (level == prevLevel || marker == lastHandledWarn) return
        lastHandledWarn = marker
        scope.launch { app.settingsRepo.update { it.copy(warnState = marker) } }
        if (level > prevLevel) {
            val text = "${Fmt.bytes(used, s.unit)} of ${Fmt.bytes(limit, s.unit)} used"
            Notifs.limit(this, "You've used $level% of your $name data limit", text)
        }
    }
}
