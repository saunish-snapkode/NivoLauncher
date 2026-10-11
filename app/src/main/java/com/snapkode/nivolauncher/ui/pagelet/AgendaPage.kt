package com.snapkode.nivolauncher.ui.pagelet

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.calendar.AgendaItem
import com.snapkode.nivolauncher.pagelet.calendar.AgendaModel
import com.snapkode.nivolauncher.pagelet.calendar.CalEvent
import com.snapkode.nivolauncher.pagelet.calendar.CalendarToday
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Your day, hour by hour: what's next, a ribbon of the whole day, and a timeline with the gaps between things. */
@Composable
fun AgendaPage(config: PageletConfig, onOptions: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var allowed by remember { mutableStateOf(CalendarToday.hasPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    var dayOffset by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; if (!ok) refused = true }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); allowed = CalendarToday.hasPermission(context); delay(30_000) }
        }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val dayStart = remember(now / 3_600_000L, dayOffset) {
        Calendar.getInstance().apply { timeInMillis = CalendarToday.dayBounds(now).first; add(Calendar.DAY_OF_YEAR, dayOffset) }.timeInMillis
    }
    val events by produceState(emptyList<CalEvent>(), allowed, dayStart, now / 60_000L) {
        value = if (allowed) withContext(Dispatchers.IO) { CalendarToday.eventsOnDay(context, dayStart) } else emptyList()
    }
    AgendaBody(
        dayStartMs = dayStart, nowMs = now, events = events, isToday = dayOffset == 0, showPast = config.showPast,
        allowed = allowed, refused = refused, dayOffset = dayOffset, onDay = { dayOffset = it },
        onAsk = { if (refused) openAppSettings(context) else ask.launch(Manifest.permission.READ_CALENDAR) },
        onOptions = onOptions, is24 = is24,
    )
}

/** The Agenda for given events: the loader above feeds it; the screenshot test feeds it fixed ones. */
@Composable
internal fun AgendaBody(
    dayStartMs: Long, nowMs: Long, events: List<CalEvent>, isToday: Boolean, showPast: Boolean,
    allowed: Boolean, refused: Boolean, dayOffset: Int, onDay: (Int) -> Unit, onAsk: () -> Unit, onOptions: () -> Unit, is24: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val clockFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()) }
    val ampm = remember { SimpleDateFormat("a", Locale.getDefault()) }
    fun clock(ms: Long) = clockFmt.format(Date(ms))
    fun clockFull(ms: Long) = if (is24) clock(ms) else clock(ms) + " " + ampm.format(Date(ms)).lowercase()
    val items = remember(events, nowMs / 60_000L, isToday, showPast) { AgendaModel.withGaps(AgendaModel.build(events, nowMs, isToday, showPast)) }
    val next = remember(events, nowMs / 60_000L, isToday) { if (isToday) AgendaModel.upNext(events, nowMs) else null }
    // Finished events fold into one line until asked for; the day reads forward.
    var showEarlier by remember { mutableStateOf(false) }
    val pastCount = items.count { it is AgendaItem.Timed && it.past }
    val folded = isToday && pastCount >= 2 && !showEarlier
    val shown = if (folded) items.filterNot { it is AgendaItem.Timed && it.past || (it is AgendaItem.Free && it.endMs <= nowMs) } else items

    // No background of its own: the wash behind the pager (SurfaceWash) is painted edge to edge, under the status bar too.
    Column(Modifier.fillMaxSize()) {
        // ---- the masthead: the day in big type ---------------------------------------------------------------------
        val day = Date(dayStartMs)
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (isToday) "TODAY" else "TOMORROW") + " · " + SimpleDateFormat("d MMMM", Locale.getDefault()).format(day).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold), color = scheme.primary,
                )
                Text(
                    SimpleDateFormat("EEEE", Locale.getDefault()).format(day),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
                    color = scheme.onSurface, modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (!allowed) "Your calendar, at a glance" else when (events.size) { 0 -> "Nothing planned"; 1 -> "1 event"; else -> "${events.size} events" },
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOptions) { Icon(Icons.Outlined.Tune, "Customise Agenda", tint = scheme.onSurfaceVariant) }
        }
        DaySwitch(dayOffset, onDay, Modifier.padding(start = 24.dp, top = 16.dp))

        when {
            !allowed -> AskCard(
                "See your day here", "The Agenda reads today's events from the calendars on this phone. It only reads, and nothing leaves your device.",
                if (refused) "Open settings" else "Allow calendar", Modifier.padding(16.dp),
            ) { onAsk() }
            events.isEmpty() -> ClearDay(isToday, Modifier.weight(1f))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
                item(key = "ribbon") { DayRibbon(events, nowMs.takeIf { isToday }, dayStartMs) { ms -> if (is24) clock(ms) else SimpleDateFormat("h a", Locale.getDefault()).format(Date(ms)).lowercase() } }
                if (next != null) item(key = "next") { UpNextCard(next, nowMs, ::clockFull, Modifier.padding(top = 14.dp)) }
                if (folded) item(key = "earlier") { EarlierRow(pastCount) { showEarlier = true } }
                item(key = "gap") { Spacer(Modifier.height(10.dp)) }
                items(shown.size, key = { i -> keyOf(shown[i]) }) { i ->
                    when (val it = shown[i]) {
                        is AgendaItem.AllDay -> AllDayRow(it.event)
                        is AgendaItem.Timed -> TimelineRow(it, clock(it.event.beginMs), if (!is24) ampm.format(Date(it.event.beginMs)).lowercase() else "", AgendaModel.range(it.event, ::clockFull), first = i == 0, last = i == shown.lastIndex)
                        is AgendaItem.NowLine -> NowRow(clockFull(it.atMs))
                        is AgendaItem.Free -> FreeRow(AgendaModel.span(it.endMs - it.startMs))
                    }
                }
            }
        }
    }
}

private fun keyOf(it: AgendaItem): String = when (it) {
    is AgendaItem.AllDay -> "a${it.event.id}-${it.event.beginMs}"
    is AgendaItem.Timed -> "t${it.event.id}-${it.event.beginMs}"
    is AgendaItem.NowLine -> "now"
    is AgendaItem.Free -> "f${it.startMs}"
}

private fun eventColor(e: CalEvent, fallback: Color) = if (e.color != 0) Color(e.color).copy(alpha = 1f) else fallback

/** Today / Tomorrow as one pill with a sliding highlight. */
@Composable
private fun DaySwitch(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val w = 108.dp
    val x by animateDpAsState(w * selected, LocalMotion.current.spring(1f), label = "daySwitch")
    Box(modifier.height(40.dp).width(w * 2).background(scheme.surfaceContainerHigh, CircleShape).padding(3.dp)) {
        Box(Modifier.offset(x = x).width(w - 3.dp).fillMaxHeight().background(scheme.primary, CircleShape))
        Row(Modifier.fillMaxSize()) {
            listOf("Today", "Tomorrow").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).selectable(selected == i, role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected == i) scheme.onPrimary else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The whole day as one ribbon: every event as a block on its hours, and a marker where now is. */
@Composable
private fun DayRibbon(events: List<CalEvent>, nowMs: Long?, dayStartMs: Long, clock: (Long) -> String) {
    val scheme = MaterialTheme.colorScheme
    val timed = events.filter { !it.allDay }
    val hourMs = 3_600_000L
    // The ribbon spans the working day, widened to fit the earliest and latest events.
    val firstH = ((timed.minOfOrNull { it.beginMs } ?: (dayStartMs + 8 * hourMs)) - dayStartMs).floorDiv(hourMs).coerceIn(0, 8)
    val lastH = ((timed.maxOfOrNull { it.endMs } ?: (dayStartMs + 20 * hourMs)) - dayStartMs + hourMs - 1).floorDiv(hourMs).coerceIn(20, 24)
    val from = dayStartMs + firstH * hourMs
    val to = dayStartMs + lastH * hourMs
    val reduced = LocalMotion.current.reduced
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(700)) }
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, RoundedCornerShape(24.dp)).border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(16.dp)) {
        val track = scheme.surfaceContainerHighest; val nowC = scheme.primary; val primary = scheme.primary; val tick = scheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val w = size.width; val h = size.height
            val r = CornerRadius(10.dp.toPx())
            drawRoundRect(track, Offset(0f, 6.dp.toPx()), Size(w, h - 12.dp.toPx()), r)
            fun xOf(ms: Long) = ((ms - from).toFloat() / (to - from)).coerceIn(0f, 1f) * w
            // Hour ticks every three hours.
            var hh = firstH
            while (hh <= lastH) { val x = xOf(dayStartMs + hh * hourMs); drawLine(tick, Offset(x, h - 4.dp.toPx()), Offset(x, h), 1.dp.toPx()); hh += 3 }
            timed.forEach { e ->
                val x0 = xOf(e.beginMs); val x1 = maxOf(xOf(e.endMs), x0 + 4.dp.toPx())
                val c = eventColor(e, primary)
                drawRoundRect(c.copy(alpha = 0.9f), Offset(x0, 6.dp.toPx()), Size((x1 - x0) * reveal.value, h - 12.dp.toPx()), CornerRadius(6.dp.toPx()))
            }
            if (nowMs != null && nowMs in from..to) {
                val x = xOf(nowMs)
                drawLine(nowC, Offset(x, 0f), Offset(x, h), 2.dp.toPx())
                drawCircle(nowC, 4.dp.toPx(), Offset(x, 2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(firstH, (firstH + lastH) / 2, lastH).forEach { hh ->
                Text(clock(dayStartMs + hh * hourMs), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** What's on now or next, large: the countdown first, because that is the question. */
@Composable
private fun UpNextCard(e: CalEvent, nowMs: Long, clock: (Long) -> String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val c = eventColor(e, scheme.primary)
    val on = e.beginMs <= nowMs
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(lerp(scheme.surfaceContainerHigh, c, 0.28f), scheme.surfaceContainerHigh)))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(c, on)
                Text(
                    if (on) "HAPPENING NOW" else "UP NEXT", Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = c,
                )
            }
            Text(
                AgendaModel.countdown(e, nowMs), Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = scheme.onSurface,
            )
            Text(e.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("${clock(e.beginMs)} – ${clock(e.endMs)}")
                e.location?.let { Spacer(Modifier.width(8.dp)); Chip(it, Icons.Outlined.LocationOn) }
            }
            if (on) {
                // How far through it we are.
                val p = ((nowMs - e.beginMs).toFloat() / (e.endMs - e.beginMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp).background(c.copy(alpha = 0.18f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(p).height(4.dp).background(c, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.background(scheme.surface.copy(alpha = 0.7f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp).padding(end = 2.dp), tint = scheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PulseDot(color: Color, pulsing: Boolean) {
    val reduced = LocalMotion.current.reduced
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulseT")
    Canvas(Modifier.size(12.dp)) {
        if (pulsing && !reduced) drawCircle(color.copy(alpha = 0.35f * (1f - t)), size.minDimension / 2f * (0.6f + 0.6f * t))
        drawCircle(color, size.minDimension / 4f)
    }
}

@Composable
private fun EarlierRow(count: Int, onShow: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onShow).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Earlier today", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("$count done · show", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
    }
}

@Composable
private fun AllDayRow(e: CalEvent) {
    val scheme = MaterialTheme.colorScheme
    val c = eventColor(e, scheme.primary)
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().background(c.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(c, CircleShape))
        Text(e.title, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("ALL DAY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = c)
    }
}

private val TimeCol = 54.dp
private val RailCol = 22.dp

/** One event on the timeline: the time, a dot on the rail, and the card. */
@Composable
private fun TimelineRow(item: AgendaItem.Timed, time: String, meridiem: String, range: String, first: Boolean, last: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val c = eventColor(item.event, scheme.primary)
    val rail = scheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(if (item.past) 0.45f else 1f)) {
        Column(Modifier.width(TimeCol).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
            if (item.hourStart) {
                Text(time, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
                if (meridiem.isNotEmpty()) Text(meridiem, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        // The rail: a hairline through the day, with this event's dot on it.
        Box(Modifier.width(RailCol).fillMaxHeight().drawBehind {
            val x = size.width / 2f
            drawLine(rail, Offset(x, if (first) 22.dp.toPx() else 0f), Offset(x, if (last) 22.dp.toPx() else size.height), 1.5.dp.toPx())
            if (item.current) drawCircle(c.copy(alpha = 0.25f), 9.dp.toPx(), Offset(x, 22.dp.toPx()))
            drawCircle(c, 5.dp.toPx(), Offset(x, 22.dp.toPx()))
        })
        Box(
            Modifier.weight(1f).padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp))
                .background(if (item.current) lerp(scheme.surfaceContainerHigh, c, 0.16f) else scheme.surfaceContainerLow)
                .drawBehind { drawRect(c, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column {
                Text(item.event.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(range, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                item.event.location?.let {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                        Text(it, Modifier.padding(start = 3.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** "Free · 1h 30m": room in the day, on a dashed stretch of the rail. */
@Composable
private fun FreeRow(span: String) {
    val scheme = MaterialTheme.colorScheme
    val rail = scheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(TimeCol))
        Box(Modifier.width(RailCol).fillMaxHeight().drawBehind {
            drawLine(rail, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        })
        Text("Free · $span", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

/** Where now is among the day's events. */
@Composable
private fun NowRow(time: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(TimeCol), contentAlignment = Alignment.CenterEnd) {
            Text(time, Modifier.background(scheme.primary, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onPrimary, maxLines = 1)
        }
        Box(Modifier.width(RailCol), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).background(scheme.primary, CircleShape)) }
        Box(Modifier.weight(1f).height(2.dp).background(Brush.horizontalGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0f)))))
    }
}

/** An empty day, said kindly, with a small illustration of a page from a desk calendar. */
@Composable
private fun ClearDay(isToday: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val paper = scheme.surfaceContainerHigh; val ring = scheme.outline; val sun = Color(0xFFFFB547); val head = scheme.primary
        Canvas(Modifier.size(140.dp)) {
            val w = size.width
            drawCircle(sun.copy(alpha = 0.18f), w * 0.46f, Offset(w * 0.68f, w * 0.3f))
            drawCircle(sun, w * 0.12f, Offset(w * 0.72f, w * 0.26f))
            drawRoundRect(paper, Offset(w * 0.14f, w * 0.3f), Size(w * 0.62f, w * 0.6f), CornerRadius(w * 0.08f))
            drawRoundRect(head, Offset(w * 0.14f, w * 0.3f), Size(w * 0.62f, w * 0.16f), CornerRadius(w * 0.08f))
            drawRect(head, Offset(w * 0.14f, w * 0.38f), Size(w * 0.62f, w * 0.08f))
            for (i in 0 until 3) drawRoundRect(ring, Offset(w * (0.26f + i * 0.17f), w * 0.25f), Size(w * 0.04f, w * 0.12f), CornerRadius(w * 0.02f))
            for (r in 0 until 3) drawRoundRect(ring.copy(alpha = 0.35f), Offset(w * 0.22f, w * (0.55f + r * 0.1f)), Size(w * (0.46f - r * 0.1f), w * 0.04f), CornerRadius(w * 0.02f))
        }
        Text(if (isToday) "A clear rest of the day" else "Nothing planned tomorrow", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.padding(top = 16.dp))
        Text("Time that's yours. Enjoy it.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
