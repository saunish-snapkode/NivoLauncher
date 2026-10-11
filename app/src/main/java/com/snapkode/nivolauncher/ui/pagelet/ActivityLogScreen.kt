package com.snapkode.nivolauncher.ui.pagelet

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
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
                Text(
                    "Activity log", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
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
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // Announced when the day changes, so stepping through days with a screen reader says where you are.
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 160.dp).padding(horizontal = NivoSpace.s).semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { daysAgo-- }, enabled = daysAgo > 0) { Icon(Icons.Filled.ChevronRight, "Next day") }
            }
            val d = day
            if (d == null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    Text(
                        "Reading the day…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = NivoSpace.l),
                    )
                }
                return@Column
            }
            // Summary.
            Row(Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xl, vertical = NivoSpace.m), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                Summary("Screen time", SkyInsight.formatMin(d.totalMinutes).replace(" min", "m"), Modifier.weight(1f))
                Summary("Unlocks", d.unlocks.toString(), Modifier.weight(1f))
                Summary("App sessions", d.appSessions.size.toString(), Modifier.weight(1f))
            }
            // Scrolls sideways rather than squeezing the chips when the text is large.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NivoSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(NivoSpace.s),
            ) {
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
                    EmptyState(
                        Icons.Outlined.History, "A quiet day",
                        when (filter) {
                            ActivityFilter.All -> "Nothing recorded for this day."
                            ActivityFilter.Unlocks -> "No unlocks recorded for this day."
                            ActivityFilter.AppUse -> "No app use recorded for this day."
                        },
                    )
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = NivoSpace.xl, vertical = NivoSpace.m)) {
                groups.forEach { (hour, entries) ->
                    items(entries.size, key = { "$hour-$it" }) { i ->
                        TimelineRow(entries[i], showHour = i == 0, hour = hour, last = i == entries.lastIndex, label = ::label, iconFor = iconFor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = NivoSpace.xl, end = NivoSpace.m, top = NivoSpace.s, bottom = NivoSpace.s), verticalAlignment = Alignment.CenterVertically) {
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
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NivoRadius.control)
    Column(
        modifier.clip(shape).background(scheme.surfaceContainer).border(1.dp, scheme.outlineVariant, shape)
            .semantics(mergeDescendants = true) {}.padding(NivoSpace.m),
    ) {
        Text(
            value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val minutes = if (e is ActivityEntry.AppUse) SkyInsight.formatMin(((e.durationMs + 59_999L) / 60_000L).toInt()) else null
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clearAndSetSemantics { contentDescription = "$time, $text" + (minutes?.let { ", $it" } ?: "") },
    ) {
        Text(
            time, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = scheme.onSurfaceVariant,
            maxLines = 1, modifier = Modifier.width(52.dp).padding(top = 14.dp),
        )
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
            Spacer(Modifier.width(NivoSpace.m))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (minutes != null) {
                Spacer(Modifier.width(NivoSpace.s))
                Text(
                    minutes, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = scheme.onSecondaryContainer, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(scheme.secondaryContainer).padding(horizontal = 10.dp, vertical = NivoSpace.xs),
                )
            }
        }
    }
}
