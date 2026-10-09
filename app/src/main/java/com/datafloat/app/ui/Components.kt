package com.datafloat.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datafloat.app.AppSettings
import com.datafloat.app.Fmt
import com.datafloat.app.Snapshot
import com.datafloat.app.valueFor
import kotlin.math.roundToInt

fun openUsageAccess(ctx: Context) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(flags))
    } catch (e: Exception) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(flags)) }
    }
}

fun openOverlaySettings(ctx: Context) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(flags))
    } catch (e: Exception) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(flags)) }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun <T> ChipChoice(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Slider that only writes to storage when the finger lifts. */
@Composable
fun IntSlider(label: String, value: Int, range: IntRange, suffix: String = "", onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${local.roundToInt()}$suffix", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

private val Swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF00E676, 0xFFFFD600, 0xFF29B6F6, 0xFFFF5252, 0xFFE040FB, 0xFF616161,
).map { it.toInt() }

@Composable
fun ColorRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatches.forEach { c ->
                val isSel = c == selected
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { onSelect(c) }
                )
            }
        }
    }
}

/** On-screen mock of the floating counter using the current style settings. */
@Composable
fun CounterPreview(s: AppSettings, snap: Snapshot) {
    val text = Fmt.overlayText(s.style, s.mode, snap.valueFor(s.mode), s.unit)
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF455A64), RoundedCornerShape(12.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        var m = Modifier.alpha((s.opacity / 100f).coerceIn(0.15f, 1f))
        if (s.showBackground) m = m.background(Color(s.bgColor), RoundedCornerShape(s.cornerRadiusDp.dp))
        Text(
            text,
            color = Color(s.textColor),
            fontSize = s.textSizeSp.sp,
            fontWeight = FontWeight.Bold,
            modifier = m.padding(horizontal = (s.bubblePaddingDp * 1.5f).dp, vertical = s.bubblePaddingDp.dp),
        )
    }
}
