package com.datafloat.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datafloat.app.AppSettings
import com.datafloat.app.DataUnit
import com.datafloat.app.DisplayStyle
import com.datafloat.app.Fmt
import com.datafloat.app.LimitType
import com.datafloat.app.MainViewModel
import com.datafloat.app.Perms
import com.datafloat.app.Pos
import com.datafloat.app.Snapshot
import com.datafloat.app.TrackingMode
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

// ───────────────────────────── Root + navigation ─────────────────────────────

@Composable
fun AppRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val perms = vm.perms
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var skipped by rememberSaveable { mutableStateOf(false) }
    val s = settings

    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val allGranted = perms.usageAccess && perms.overlay
    // First time both required permissions are in place: switch tracking + overlay on.
    LaunchedEffect(allGranted, s.onboarded) {
        if (allGranted && !s.onboarded) vm.completeOnboarding()
    }

    if (!allGranted && !skipped) {
        Surface(Modifier.fillMaxSize()) { OnboardingScreen(vm, perms) { skipped = true } }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Dashboard") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        ) { pad ->
            if (tab == 0) DashboardScreen(vm, s, snap, perms, pad) else SettingsScreen(vm, s, snap, pad)
        }
    }
}

// ───────────────────────────── Onboarding ─────────────────────────────

@Composable
fun OnboardingScreen(vm: MainViewModel, perms: Perms, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Welcome to DataFloat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "DataFloat shows how much mobile data you have used, as a running total in KB / MB / GB " +
                "(not your internet speed), in a small counter that floats over other apps. " +
                "Everything stays on your phone: no account, no cloud.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PermCard(
            "1. Usage access",
            "Android only lets apps read data-usage statistics (NetworkStatsManager) after you allow " +
                "\"Usage access\". Used for daily and monthly totals, history and the per-app breakdown. " +
                "Wi-Fi is never counted.",
            perms.usageAccess, "Open settings",
        ) { openUsageAccess(ctx) }
        PermCard(
            "2. Display over other apps",
            "Needed to draw the floating counter on top of games, browsers and other apps. " +
                "You can hide it at any time without stopping data tracking.",
            perms.overlay, "Open settings",
        ) { openOverlaySettings(ctx) }
        if (Build.VERSION.SDK_INT >= 33) {
            PermCard(
                "3. Notifications (optional)",
                "Only used for data-limit warnings (80%, 90%, 100%) and one silent status notification " +
                    "Android requires while tracking runs in the background.",
                perms.notifications, "Allow",
            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        Text(
            "Tip: after you allow a permission, press Back to return here. Tracking and the counter start " +
                "automatically once steps 1 and 2 are done.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onSkip) { Text("Skip for now (limited features)") }
    }
}

@Composable
private fun PermCard(title: String, body: String, granted: Boolean, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (granted) Text("✓ Granted", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            else Button(onClick = onClick) { Text(button) }
        }
    }
}

// ───────────────────────────── Dashboard ─────────────────────────────

@Composable
fun DashboardScreen(vm: MainViewModel, s: AppSettings, snap: Snapshot, perms: Perms, pad: PaddingValues) {
    val history by vm.history.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val usedForLimit = when (s.limitType) {
        LimitType.DAILY -> snap.dayBytes
        LimitType.MONTHLY -> snap.monthBytes
        LimitType.NONE -> null
    }

    LazyColumn(
        Modifier.padding(pad).statusBarsPadding().fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("DataFloat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Mobile data used (running total, not speed)", style = MaterialTheme.typography.bodyMedium)
        }

        if (!perms.usageAccess) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usage access is off. Daily and monthly totals, history and per-app data are unavailable; the session counter still works.")
                    Button(onClick = { openUsageAccess(ctx) }) { Text("Grant usage access") }
                }
            }
        }
        if (!snap.mobileSupported) item {
            Text("This device does not expose a mobile data counter, so totals may stay at zero.", color = MaterialTheme.colorScheme.error)
        }

        item { UsageCard("Today", snap.dayBytes, s.unit, "Resets automatically at midnight") }
        item { UsageCard("This billing cycle", snap.monthBytes, s.unit, "Resets on day ${s.monthlyResetDay} of each month") }
        item {
            UsageCard(
                "Session", snap.sessionBytes, s.unit,
                "Since " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.sessionStartMs)),
            ) {
                OutlinedButton(onClick = { vm.resetSession() }) { Text("Reset session") }
            }
        }

        if (s.limitType != LimitType.NONE && usedForLimit != null && s.limitMb > 0) item {
            val limit = s.limitMb * 1_000_000L
            val frac = (usedForLimit.toFloat() / limit).coerceIn(0f, 1f)
            Section("${if (s.limitType == LimitType.DAILY) "Daily" else "Monthly"} limit") {
                LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth())
                Text("${Fmt.bytes(usedForLimit, s.unit)} of ${Fmt.bytes(limit, s.unit)} (${(usedForLimit * 100 / limit)}%)")
            }
        }

        item {
            Section("Floating counter") {
                SwitchRow("Track mobile data in the background", s.trackingEnabled) { vm.setTracking(it) }
                SwitchRow("Show floating counter", s.overlayVisible, enabled = perms.overlay) { vm.setOverlay(it) }
                if (!perms.overlay) {
                    TextButton(onClick = { openOverlaySettings(ctx) }) { Text("Grant overlay permission") }
                }
                Text("Counter shows:", style = MaterialTheme.typography.bodyMedium)
                ChipChoice(
                    listOf("Daily" to TrackingMode.DAILY, "Session" to TrackingMode.SESSION, "Monthly" to TrackingMode.MONTHLY),
                    s.mode,
                ) { m -> vm.update { it.copy(mode = m) } }
                Text("Hiding the counter does not stop tracking. Tap the counter to cycle modes, drag it to move.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Section("Data by app") {
                ChipChoice(listOf("Today" to false, "This cycle" to true), vm.appsMonth) { vm.setAppsPeriod(it) }
                if (!perms.usageAccess) Text("Needs usage access.")
                else if (vm.apps.isEmpty()) Text("No mobile data recorded for this period yet.")
                else {
                    val max = vm.apps.first().bytes.coerceAtLeast(1L)
                    vm.apps.take(15).forEach { app ->
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Text(app.label, Modifier.weight(1f), maxLines = 1)
                                Text(Fmt.bytes(app.bytes, s.unit), fontWeight = FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(progress = { app.bytes.toFloat() / max }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        item {
            val map = history.associate { it.date to it.bytes }
            val today = LocalDate.now()
            val days = (0..13).map { today.minusDays(it.toLong()) }
            val values = days.map { d ->
                if (d == today) (snap.dayBytes ?: map[d.toString()] ?: 0L) else (map[d.toString()] ?: 0L)
            }
            val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
            Section("Last 14 days") {
                days.forEachIndexed { i, d ->
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(d.format(fmt), Modifier.weight(1f))
                            Text(Fmt.bytes(values[i], s.unit), fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(progress = { values[i].toFloat() / max }, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Text(
                "Updated " + DateFormat.getTimeInstance().format(Date(snap.updatedAtMs)) +
                    ". Android refreshes its own data statistics in batches, so very recent traffic can appear a few seconds to a minute late. See Settings > About accuracy.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun UsageCard(title: String, bytes: Long?, unit: DataUnit, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(Fmt.bytesOrNa(bytes, unit), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

// ───────────────────────────── Settings ─────────────────────────────

@Composable
fun SettingsScreen(vm: MainViewModel, s: AppSettings, snap: Snapshot, pad: PaddingValues) {
    Column(
        Modifier.padding(pad).statusBarsPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Section("What the counter tracks") {
            ChipChoice(
                listOf("Daily" to TrackingMode.DAILY, "Session" to TrackingMode.SESSION, "Monthly" to TrackingMode.MONTHLY),
                s.mode,
            ) { m -> vm.update { it.copy(mode = m) } }
            IntSlider("Monthly reset day", s.monthlyResetDay, 1..31) { d -> vm.update { it.copy(monthlyResetDay = d) } }
            Text("Months with fewer days reset on their last day.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.resetSession() }) { Text("Reset session counter") }
        }

        Section("Position") {
            Text("Drag the counter anywhere, or jump to a corner:", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.update { it.copy(overlayX = Pos.LEFT, overlayY = Pos.TOP) } }) { Text("Top left") }
                OutlinedButton(onClick = { vm.update { it.copy(overlayX = Pos.RIGHT, overlayY = Pos.TOP) } }) { Text("Top right") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.update { it.copy(overlayX = Pos.LEFT, overlayY = Pos.BOTTOM) } }) { Text("Bottom left") }
                OutlinedButton(onClick = { vm.update { it.copy(overlayX = Pos.RIGHT, overlayY = Pos.BOTTOM) } }) { Text("Bottom right") }
            }
        }

        Section("Appearance") {
            CounterPreview(s, snap)
            Text("Style", style = MaterialTheme.typography.bodyMedium)
            ChipChoice(
                listOf("125.4 MB" to DisplayStyle.MINIMAL, "Mobile Data: …" to DisplayStyle.LABELED, "Today: / Session: / Month:" to DisplayStyle.PERIOD_LABEL),
                s.style,
            ) { st -> vm.update { it.copy(style = st) } }
            Text("Units", style = MaterialTheme.typography.bodyMedium)
            ChipChoice(
                listOf("Auto" to DataUnit.AUTO, "KB" to DataUnit.KB, "MB" to DataUnit.MB, "GB" to DataUnit.GB),
                s.unit,
            ) { u -> vm.update { it.copy(unit = u) } }
            IntSlider("Text size", s.textSizeSp, 10..32, " sp") { v -> vm.update { it.copy(textSizeSp = v) } }
            IntSlider("Bubble size (padding)", s.bubblePaddingDp, 2..24, " dp") { v -> vm.update { it.copy(bubblePaddingDp = v) } }
            IntSlider("Corner radius", s.cornerRadiusDp, 0..32, " dp") { v -> vm.update { it.copy(cornerRadiusDp = v) } }
            IntSlider("Opacity", s.opacity, 15..100, " %") { v -> vm.update { it.copy(opacity = v) } }
            SwitchRow("Show background", s.showBackground) { b -> vm.update { it.copy(showBackground = b) } }
            ColorRow("Text color", s.textColor) { c -> vm.update { it.copy(textColor = c) } }
            ColorRow("Background color", s.bgColor) { c -> vm.update { it.copy(bgColor = c) } }
        }

        Section("Update frequency") {
            ChipChoice(
                listOf("1 s" to 1, "2 s" to 2, "5 s" to 5, "10 s" to 10, "30 s" to 30),
                s.updateIntervalSec,
            ) { v -> vm.update { it.copy(updateIntervalSec = v) } }
            Text(
                "How often the counter is redrawn while the screen is on (once a minute while it is off). " +
                    "Shorter is smoother but uses a little more battery. Android itself may only publish exact totals every so often.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Data limit") {
            ChipChoice(
                listOf("None" to LimitType.NONE, "Daily" to LimitType.DAILY, "Monthly" to LimitType.MONTHLY),
                s.limitType,
            ) { t -> vm.update { it.copy(limitType = t) } }
            if (s.limitType != LimitType.NONE) {
                var text by remember(s.limitMb) { mutableStateOf(s.limitMb.toString()) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        text = t.filter(Char::isDigit).take(7)
                        text.toLongOrNull()?.takeIf { it > 0 }?.let { v -> vm.update { it.copy(limitMb = v) } }
                    },
                    label = { Text("Limit in MB (1000 MB = 1 GB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipChoice(
                    listOf("500 MB" to 500L, "1 GB" to 1000L, "2 GB" to 2000L, "5 GB" to 5000L, "10 GB" to 10000L),
                    s.limitMb,
                ) { v -> vm.update { it.copy(limitMb = v) } }
                Text("You get one quiet notification each at 80%, 90% and 100% per period, nothing more.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Section("Notification") {
            SwitchRow("Show usage in the status notification", s.persistentNotification) { b ->
                vm.update { it.copy(persistentNotification = b) }
            }
            Text(
                "Android requires one notification while a background service runs. It is silent and low priority; this switch only decides whether it shows your totals.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("About accuracy") {
            Text(
                "• Only mobile data is counted; Wi-Fi traffic is never included, so switching networks cannot double-count.\n" +
                    "• Session = exact counter deltas. Daily/monthly = Android's usage statistics plus live counter deltas.\n" +
                    "• Android stores usage in coarse time slices and publishes it in batches, so totals can be briefly behind. They catch up and never go backwards.\n" +
                    "• Your carrier's bill can differ slightly (overhead, rounding, roaming, free-zone traffic).\n" +
                    "• Some phones stop background apps aggressively; exclude DataFloat from battery optimization if the counter freezes.\n" +
                    "• Full details: docs/LIMITATIONS.md in the project.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
