package com.snapkode.nivolauncher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.data.CalendarSource
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.data.NextEvent
import com.snapkode.nivolauncher.data.WeatherFormat
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.pressFeedback

/** "At a glance": today's date, the next calendar event and the battery state, shown above the home pages. */
@Composable
fun AtAGlance(settings: LauncherSettings, viewModel: com.snapkode.nivolauncher.LauncherViewModel, modifier: Modifier = Modifier, showDate: Boolean = true) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var event by remember { mutableStateOf<NextEvent?>(null) }
    var battery by remember { mutableStateOf<String?>(null) }

    val weather by viewModel.weather.collectAsState()
    val nowPlaying by com.snapkode.nivolauncher.NowPlayingState.current.collectAsState()
    LaunchedEffect(settings.smartspaceCalendar, settings.smartspaceBattery, settings.weatherEnabled, settings.weatherPlace) {
        while (true) {
            viewModel.refreshWeatherIfStale()
            now = System.currentTimeMillis()
            event = if (settings.smartspaceCalendar) withContext(Dispatchers.IO) { CalendarSource.nextEvent(context) } else null
            battery = if (settings.smartspaceBattery) batteryText(context) else null
            delay(60_000)
        }
    }

    val textColor = LocalHomeTextColor.current
    val shadow = if (textColor == Color.White) HomeLabelShadow else null
    val date = DateFormat.format("EEEE, MMM d", Date(now)).toString()

    val scheme = MaterialTheme.colorScheme
    val dotColor = if (textColor == Color.White) Color(0xFFFFD54F) else scheme.tertiary
    Column(modifier.fillMaxWidth().padding(horizontal = NivoSpace.xl, vertical = NivoSpace.xs)) {
        if (showDate) {
            Text(
                date,
                style = MaterialTheme.typography.headlineSmall.merge(TextStyle(shadow = shadow)),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClickLabel = "Open calendar", role = Role.Button) { openCalendar(context) }
                    .heightIn(min = NivoTouch.min)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
        // One tidy status row: weather, next event, battery.
        val weatherText = if (settings.weatherEnabled && settings.weatherPlace.isNotEmpty()) weather?.let { w ->
            val (_, label) = WeatherFormat.describe(w.code)
            "${WeatherFormat.temperature(w.celsius, settings.weatherFahrenheit)} · $label"
        } else null
        val eventText = event?.let { e ->
            val time = if (e.allDay) "All day" else DateFormat.getTimeFormat(context).format(Date(e.begin))
            e.title to time
        }
        if (weatherText != null || eventText != null || battery != null) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(top = NivoSpace.xs, start = NivoSpace.xs),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                val rowStyle = MaterialTheme.typography.titleMedium.merge(TextStyle(shadow = shadow))
                if (weatherText != null) {
                    Box(Modifier.size(10.dp).background(dotColor, androidx.compose.foundation.shape.CircleShape))
                    Text(
                        weatherText, style = rowStyle, color = textColor, maxLines = 1,
                        modifier = Modifier.padding(start = NivoSpace.s),
                    )
                }
                if (eventText != null) {
                    if (weatherText != null) Separator(rowStyle, textColor)
                    // The event shrinks (with an ellipsis) before anything else on the line is pushed off screen.
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .weight(1f, fill = false)
                            .clickable(onClickLabel = "Open event", role = Role.Button) { event?.let { openEvent(context, it.id) } }
                            .heightIn(min = NivoTouch.min),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.CalendarToday, null, tint = textColor, modifier = Modifier.size(18.dp))
                        Text(
                            "${eventText.first} · ${eventText.second}",
                            style = rowStyle, color = textColor, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = NivoSpace.s),
                        )
                    }
                }
                battery?.let {
                    if (weatherText != null || eventText != null) Separator(rowStyle, textColor)
                    Text(it, style = rowStyle, color = textColor.copy(alpha = 0.9f), maxLines = 1)
                }
            }
        }
        if (settings.smartspaceMedia) nowPlaying?.let { MediaCard(it) }
    }
}

/** A quiet middle dot between items on the status line; decorative, so screen readers skip it. */
@Composable
private fun Separator(style: TextStyle, color: Color) {
    Text(
        "•", style = style, color = color.copy(alpha = NivoAlpha.low),
        modifier = Modifier.padding(horizontal = NivoSpace.m).clearAndSetSemantics { },
    )
}

/** Only worth showing while charging or low; otherwise the line stays hidden. */
private fun batteryText(context: Context): String? {
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    if (level < 0) return null
    val percent = level * 100 / scale
    val charging = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    return when {
        charging -> "Charging · $percent%"
        percent <= 20 -> "Battery low · $percent%"
        else -> null
    }
}

private fun openCalendar(context: Context) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openEvent(context: Context, id: Long) {
    val uri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, id.toString())
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Now playing: tonal card, a gradient art tile, and a filled play/pause disc. */
@Composable
private fun MediaCard(media: com.snapkode.nivolauncher.NowPlaying) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier
            .padding(top = NivoSpace.m)
            .fillMaxWidth()
            .background(scheme.surfaceContainer.copy(alpha = 0.94f), androidx.compose.foundation.shape.RoundedCornerShape(NivoRadius.hero))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(NivoRadius.hero))
            .padding(NivoSpace.m),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                androidx.compose.foundation.shape.RoundedCornerShape(NivoRadius.card),
            ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            // A note on the art tile, so the card reads as music even before its title is noticed.
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Default.MusicNote, contentDescription = null,
                tint = scheme.onPrimary.copy(alpha = NivoAlpha.medium), modifier = Modifier.size(28.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = NivoSpace.l)) {
            Text(media.title, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (media.artist.isNotBlank()) {
                Text(media.artist, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // 48dp targets (the glyphs stay small); pulled in by the touch padding so the row still lines up with the text.
            androidx.compose.foundation.layout.Row(Modifier.offset(x = -NivoSpace.m)) {
                MediaButton(androidx.compose.material.icons.Icons.Default.SkipPrevious, "Previous track", 48) { com.snapkode.nivolauncher.NowPlayingState.previous() }
                MediaButton(androidx.compose.material.icons.Icons.Default.SkipNext, "Next track", 48) { com.snapkode.nivolauncher.NowPlayingState.next() }
            }
        }
        val playSource = remember { MutableInteractionSource() }
        Box(
            Modifier.size(56.dp)
                .pressFeedback(playSource)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(scheme.primary)
                .clickable(interactionSource = playSource, indication = ripple(), role = Role.Button) {
                    com.snapkode.nivolauncher.NowPlayingState.playPause()
                },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                if (media.playing) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                contentDescription = if (media.playing) "Pause" else "Play",
                tint = scheme.onPrimary,
            )
        }
    }
}

@Composable
private fun MediaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, size: Int, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(size.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** A cookie-shaped weather tile: condition icon, temperature and a short description. */
@Composable
fun WeatherTile(settings: LauncherSettings, viewModel: com.snapkode.nivolauncher.LauncherViewModel, modifier: Modifier = Modifier) {
    if (!settings.weatherEnabled || settings.weatherPlace.isEmpty()) return
    val weather by viewModel.weather.collectAsState()
    val w = weather ?: return
    val scheme = MaterialTheme.colorScheme
    val (symbol, label) = WeatherFormat.describe(w.code)
    Column(
        modifier
            .size(116.dp)
            .clip(com.snapkode.nivolauncher.ui.design.NivoShapes.Cookie9)
            .background(scheme.primaryContainer)
            .clickable(onClickLabel = "Refresh weather", role = Role.Button) { viewModel.refreshWeatherIfStale() },
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
        Text(
            WeatherFormat.temperature(w.celsius, settings.weatherFahrenheit),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light),
            color = scheme.onPrimaryContainer,
        )
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = scheme.primary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = NivoSpace.m),
        )
    }
}
