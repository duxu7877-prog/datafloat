package com.datafloat.app

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Process

/**
 * Thin wrapper over the two Android sources of mobile-data numbers:
 *  - NetworkStatsManager: authoritative, per-period, per-app, survives reboots (needs "Usage access").
 *    Android stores it in coarse time buckets and updates it lazily, so it can lag behind reality.
 *  - TrafficStats: a near-real-time since-boot counter of all bytes on mobile interfaces (no permission).
 *    Both count mobile interfaces only, so Wi-Fi traffic is never included.
 */
class UsageSource(private val ctx: Context) {
    private val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun hasUsageAccess(): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Total rx+tx on mobile networks (all SIMs) between the two instants, or null if unavailable. */
    @Suppress("DEPRECATION")
    fun mobileBytes(startMs: Long, endMs: Long): Long? {
        if (endMs <= startMs) return 0L
        return try {
            // Android 10+: subscriberId = null means "all mobile subscriptions".
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            null
        }
    }

    /** Since-boot mobile byte counter (rx+tx), or -1 if the device does not expose one. */
    fun mobileCounter(): Long {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        return if (rx < 0 || tx < 0) -1L else rx + tx
    }

    /** Mobile bytes per app (by UID, merged by label), largest first. */
    @Suppress("DEPRECATION")
    fun appBreakdown(startMs: Long, endMs: Long): List<AppUsage> {
        if (endMs <= startMs) return emptyList()
        val perUid = HashMap<Int, Long>()
        try {
            val stats = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
            }
            stats.close()
        } catch (e: Exception) {
            return emptyList()
        }
        val perLabel = HashMap<String, Long>()
        for ((uid, bytes) in perUid) {
            if (bytes <= 0L) continue
            val label = labelFor(uid)
            perLabel[label] = (perLabel[label] ?: 0L) + bytes
        }
        return perLabel.map { AppUsage(it.key, it.value) }.sortedByDescending { it.bytes }
    }

    private fun labelFor(uid: Int): String {
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED -> return "Removed apps"
            NetworkStats.Bucket.UID_TETHERING -> return "Hotspot / tethering"
            0 -> return "Android OS (root)"
            1000 -> return "Android System"
        }
        val pm = ctx.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return "System (uid $uid)"
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}
