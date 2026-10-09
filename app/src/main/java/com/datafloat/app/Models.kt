package com.datafloat.app

enum class TrackingMode { DAILY, SESSION, MONTHLY }
enum class DisplayStyle { MINIMAL, LABELED, PERIOD_LABEL }
enum class DataUnit { AUTO, KB, MB, GB }
enum class LimitType { NONE, DAILY, MONTHLY }

/** Overlay anchor sentinels. Any coordinate >= 0 is an absolute pixel position. */
object Pos {
    const val LEFT = -1
    const val RIGHT = -2
    const val TOP = -1
    const val BOTTOM = -2
}

data class AppSettings(
    val onboarded: Boolean = false,
    val mode: TrackingMode = TrackingMode.DAILY,
    val trackingEnabled: Boolean = false,
    val overlayVisible: Boolean = true,
    val monthlyResetDay: Int = 1,
    val style: DisplayStyle = DisplayStyle.PERIOD_LABEL,
    val unit: DataUnit = DataUnit.AUTO,
    val updateIntervalSec: Int = 2,
    val textSizeSp: Int = 14,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val bgColor: Int = 0xFF000000.toInt(),
    val opacity: Int = 80,
    val bubblePaddingDp: Int = 8,
    val cornerRadiusDp: Int = 14,
    val showBackground: Boolean = true,
    val overlayX: Int = Pos.RIGHT,
    val overlayY: Int = 300,
    val persistentNotification: Boolean = true,
    val limitType: LimitType = LimitType.NONE,
    val limitMb: Long = 1000L,
    val warnState: String = "",
    // Session accounting (persisted so it survives app close / process death / reboot)
    val sessionStartMs: Long = 0L,
    val sessionBytes: Long = 0L,
    val lastTrafficReading: Long = -1L,
)

/** Live numbers produced by [TrackerEngine]. Null = unavailable (e.g. usage access not granted). */
data class Snapshot(
    val dayBytes: Long? = null,
    val monthBytes: Long? = null,
    val sessionBytes: Long = 0L,
    val dayKey: String = "",
    val monthKey: String = "",
    val updatedAtMs: Long = 0L,
    val usageAccess: Boolean = false,
    val mobileSupported: Boolean = true,
)

fun Snapshot.valueFor(mode: TrackingMode): Long? = when (mode) {
    TrackingMode.DAILY -> dayBytes
    TrackingMode.MONTHLY -> monthBytes
    TrackingMode.SESSION -> sessionBytes
}

data class AppUsage(val label: String, val bytes: Long)

data class Perms(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = true,
)
