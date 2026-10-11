package com.snapkode.nivolauncher.ui.pagelet

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.pagelet.wellbeing.ActivityEntry
import com.snapkode.nivolauncher.pagelet.wellbeing.ActivityFilter
import com.snapkode.nivolauncher.pagelet.wellbeing.ActivityLog
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageStatsReader
import com.snapkode.nivolauncher.pagelet.wellbeing.WellbeingDay
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/** Android keeps roughly a week of usage events; the day switcher stops there. */
private const val DAYS_BACK = 7

/**
 * The Activity log: what the phone did each day, hour by hour. Computed on the spot from Android's own usage log;
 * nothing is stored or uploaded. CSV export goes wherever the user points the system file picker.
 */
@Composable
fun ActivityLogScreen(onBack: () -> Unit, iconFor: (String?) -> ImageBitmap?) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val reader = remember { UsageStatsReader(context.applicationContext) }
    val scheme = MaterialTheme.colorScheme
    var daysAgo by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(ActivityFilter.All) }
    var day by remember { mutableStateOf<WellbeingDay?>(null) }
    val labels = remember { HashMap<String, String>() }
    fun label(pkg: String) = labels.getOrPut(pkg) { reader.label(pkg) }
    val now = remember { System.currentTimeMillis() }

    LaunchedEffect(daysAgo) {
        day = null
        day = withContext(Dispatchers.IO) {
            val start = Calendar.getInstance().apply { timeInMillis = reader.dayStart(now); add(Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
            val end = if (daysAgo == 0) now else Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            reader.day(start, end)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val d = day
        if (uri != null && d != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(ActivityLog.csv(d, ::label).toByteArray()) } }
        }
    }

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Activity log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val d = day
                    if (d != null) export.launch("nivo-activity-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date(d.dayStartMs)) + ".csv")
                }, enabled = day != null) { Icon(Icons.Outlined.FileDownload, "Export as CSV") }
            }
            // Day switcher.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { daysAgo++ }, enabled = daysAgo < DAYS_BACK) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
                Text(
                    ActivityLog.dayTitle(day?.dayStartMs ?: now, now), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(160.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { daysAgo-- }, enabled = daysAgo > 0) { Icon(Icons.Filled.ChevronRight, "Next day") }
            }
            val d = day
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Reading the day…", color = scheme.onSurfaceVariant) }
                return@Column
            }
            // Summary.
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Summary("Screen time", SkyInsight.formatMin(d.totalMinutes).replace(" min", "m"), Modifier.weight(1f))
                Summary("Unlocks", d.unlocks.toString(), Modifier.weight(1f))
                Summary("App sessions", d.appSessions.size.toString(), Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f, onClick = { filter = f }, label = { Text(f.label) },
                        leadingIcon = if (filter == f) {{ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }} else null,
                    )
                }
            }
            val groups = remember(d, filter) { ActivityLog.byHour(ActivityLog.entries(d, filter)) }
            if (groups.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        when (filter) {
                            ActivityFilter.All -> "Nothing recorded for this day."
                            ActivityFilter.Unlocks -> "No unlocks recorded for this day."
                            ActivityFilter.AppUse -> "No app use recorded for this day."
                        },
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                groups.forEach { (hour, entries) ->
                    items(entries.size, key = { "$hour-$it" }) { i ->
                        TimelineRow(entries[i], showHour = i == 0, hour = hour, last = i == entries.lastIndex, label = ::label, iconFor = iconFor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Stored only on this phone. Android keeps about a week of usage.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { export.launch("nivo-activity-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date(d.dayStartMs)) + ".csv") }) { Text("Export CSV") }
            }
        }
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimelineRow(
    e: ActivityEntry, showHour: Boolean, hour: Int, last: Boolean, label: (String) -> String, iconFor: (String?) -> ImageBitmap?,
) {
    val scheme = MaterialTheme.colorScheme
    val time = remember(e.timeMs) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(e.timeMs)) }
    val text = when (e) {
        is ActivityEntry.Unlock -> "Unlocked"
        is ActivityEntry.ScreenOff -> "Screen off"
        is ActivityEntry.AppUse -> label(e.pkg)
    }
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = "$time, $text" },
    ) {
        Text(time, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.width(52.dp).padding(top = 14.dp))
        // The rail and its node.
        Box(Modifier.width(24.dp).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxSize().background(scheme.outlineVariant.copy(alpha = if (last) 0f else 1f)))
            Box(Modifier.padding(top = 18.dp).size(10.dp).background(if (e is ActivityEntry.AppUse) scheme.primary else scheme.outline, CircleShape))
        }
        Row(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            when (e) {
                is ActivityEntry.AppUse -> {
                    val icon = iconFor(e.pkg)
                    if (icon != null) Image(icon, null, Modifier.size(32.dp).clip(CircleShape))
                    else Box(Modifier.size(32.dp).clip(CircleShape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Text(text.take(1), style = MaterialTheme.typography.labelLarge) }
                }
                else -> Box(Modifier.size(32.dp).clip(CircleShape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(if (e is ActivityEntry.Unlock) Icons.Outlined.LockOpen else Icons.Outlined.PowerSettingsNew, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            if (e is ActivityEntry.AppUse) {
                Text(
                    SkyInsight.formatMin(((e.durationMs + 59_999L) / 60_000L).toInt()), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(CircleShape).background(scheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
