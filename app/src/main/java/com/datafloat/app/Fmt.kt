package com.datafloat.app

import java.util.Locale

/** Decimal units (1 MB = 1,000,000 bytes), matching Android's own data-usage screen and carriers. */
object Fmt {
    fun bytes(b: Long, unit: DataUnit): String {
        val u = if (unit != DataUnit.AUTO) unit else when {
            b < 1_000_000L -> DataUnit.KB
            b < 1_000_000_000L -> DataUnit.MB
            else -> DataUnit.GB
        }
        val l = Locale.getDefault()
        return when (u) {
            DataUnit.KB -> String.format(l, "%.0f KB", b / 1_000.0)
            DataUnit.MB -> String.format(l, "%.1f MB", b / 1_000_000.0)
            else -> String.format(l, "%.2f GB", b / 1_000_000_000.0)
        }
    }

    fun bytesOrNa(b: Long?, unit: DataUnit): String = if (b == null) "N/A" else bytes(b, unit)

    fun modeLabel(mode: TrackingMode): String = when (mode) {
        TrackingMode.DAILY -> "Today"
        TrackingMode.SESSION -> "Session"
        TrackingMode.MONTHLY -> "Month"
    }

    fun overlayText(style: DisplayStyle, mode: TrackingMode, bytes: Long?, unit: DataUnit): String {
        val v = bytesOrNa(bytes, unit)
        return when (style) {
            DisplayStyle.MINIMAL -> v
            DisplayStyle.LABELED -> "Mobile Data: $v"
            DisplayStyle.PERIOD_LABEL -> "${modeLabel(mode)}: $v"
        }
    }
}
