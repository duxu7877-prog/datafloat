package com.datafloat.app

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "datafloat_settings")

private object K {
    val onboarded = booleanPreferencesKey("onboarded")
    val mode = stringPreferencesKey("mode")
    val trackingEnabled = booleanPreferencesKey("tracking_enabled")
    val overlayVisible = booleanPreferencesKey("overlay_visible")
    val monthlyResetDay = intPreferencesKey("monthly_reset_day")
    val style = stringPreferencesKey("style")
    val unit = stringPreferencesKey("unit")
    val interval = intPreferencesKey("interval_sec")
    val textSize = intPreferencesKey("text_size")
    val textColor = intPreferencesKey("text_color")
    val bgColor = intPreferencesKey("bg_color")
    val opacity = intPreferencesKey("opacity")
    val padding = intPreferencesKey("padding")
    val radius = intPreferencesKey("radius")
    val showBg = booleanPreferencesKey("show_bg")
    val x = intPreferencesKey("x")
    val y = intPreferencesKey("y")
    val persistentNotif = booleanPreferencesKey("persistent_notif")
    val limitType = stringPreferencesKey("limit_type")
    val limitMb = longPreferencesKey("limit_mb")
    val warnState = stringPreferencesKey("warn_state")
    val sessionStart = longPreferencesKey("session_start")
    val sessionBytes = longPreferencesKey("session_bytes")
    val lastTraffic = longPreferencesKey("last_traffic")
}

private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, def: E): E =
    this[key]?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        onboarded = this[K.onboarded] ?: d.onboarded,
        mode = enumOr(K.mode, d.mode),
        trackingEnabled = this[K.trackingEnabled] ?: d.trackingEnabled,
        overlayVisible = this[K.overlayVisible] ?: d.overlayVisible,
        monthlyResetDay = this[K.monthlyResetDay] ?: d.monthlyResetDay,
        style = enumOr(K.style, d.style),
        unit = enumOr(K.unit, d.unit),
        updateIntervalSec = this[K.interval] ?: d.updateIntervalSec,
        textSizeSp = this[K.textSize] ?: d.textSizeSp,
        textColor = this[K.textColor] ?: d.textColor,
        bgColor = this[K.bgColor] ?: d.bgColor,
        opacity = this[K.opacity] ?: d.opacity,
        bubblePaddingDp = this[K.padding] ?: d.bubblePaddingDp,
        cornerRadiusDp = this[K.radius] ?: d.cornerRadiusDp,
        showBackground = this[K.showBg] ?: d.showBackground,
        overlayX = this[K.x] ?: d.overlayX,
        overlayY = this[K.y] ?: d.overlayY,
        persistentNotification = this[K.persistentNotif] ?: d.persistentNotification,
        limitType = enumOr(K.limitType, d.limitType),
        limitMb = this[K.limitMb] ?: d.limitMb,
        warnState = this[K.warnState] ?: d.warnState,
        sessionStartMs = this[K.sessionStart] ?: d.sessionStartMs,
        sessionBytes = this[K.sessionBytes] ?: d.sessionBytes,
        lastTrafficReading = this[K.lastTraffic] ?: d.lastTrafficReading,
    )
}

private fun AppSettings.writeTo(p: MutablePreferences) {
    p[K.onboarded] = onboarded
    p[K.mode] = mode.name
    p[K.trackingEnabled] = trackingEnabled
    p[K.overlayVisible] = overlayVisible
    p[K.monthlyResetDay] = monthlyResetDay
    p[K.style] = style.name
    p[K.unit] = unit.name
    p[K.interval] = updateIntervalSec
    p[K.textSize] = textSizeSp
    p[K.textColor] = textColor
    p[K.bgColor] = bgColor
    p[K.opacity] = opacity
    p[K.padding] = bubblePaddingDp
    p[K.radius] = cornerRadiusDp
    p[K.showBg] = showBackground
    p[K.x] = overlayX
    p[K.y] = overlayY
    p[K.persistentNotif] = persistentNotification
    p[K.limitType] = limitType.name
    p[K.limitMb] = limitMb
    p[K.warnState] = warnState
    p[K.sessionStart] = sessionStartMs
    p[K.sessionBytes] = sessionBytes
    p[K.lastTraffic] = lastTrafficReading
}

class SettingsRepo(private val ctx: Context) {
    val flow: Flow<AppSettings> = ctx.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    /** Atomic read-modify-write; safe to call from the UI, the service and the engine concurrently. */
    suspend fun update(block: (AppSettings) -> AppSettings) {
        ctx.dataStore.edit { p -> block(p.toSettings()).writeTo(p) }
    }
}
