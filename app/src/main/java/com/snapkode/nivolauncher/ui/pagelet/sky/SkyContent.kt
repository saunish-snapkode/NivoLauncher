package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.calendar.NextUp
import com.snapkode.nivolauncher.pagelet.SkySection
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val White = Color.White

/** What the Sky says to a screen reader: one sentence instead of every number and the decorative arc. */
internal fun skySummary(data: SkyData, config: PageletConfig, condition: String?): String = buildString {
    append(timeText(data.nowMin, true)).append(". ")
    data.weather?.takeIf { config.has(SkySection.WEATHER) }?.let {
        append(it.city).append(", ").append(condition ?: "").append(", ")
            .append(it.tempC.roundToInt()).append(" degrees, high ").append(it.highC.roundToInt())
            .append(", low ").append(it.lowC.roundToInt()).append(". ")
    }
    data.wellbeing?.takeIf { config.has(SkySection.WELLBEING) }?.let {
        val total = it.totalMin
        append("Screen time ").append(spoken(total)).append(", ")
            .append((100f * total / config.goalMinutes).roundToInt()).append(" percent of your ")
            .append(spoken(config.goalMinutes)).append(" goal. ")
            .append(it.unlocks).append(" unlocks.")
    }
}

private fun spoken(min: Int): String {
    val h = min / 60; val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h hour${if (h == 1) "" else "s"} $m minute${if (m == 1) "" else "s"}"
        h > 0 -> "$h hour${if (h == 1) "" else "s"}"
        else -> "$m minute${if (m == 1) "" else "s"}"
    }
}

/** "13:27" on a 24-hour clock, "1:27" on a 12-hour one (no AM/PM: the page is a clock face, not a label). */
internal fun timeText(nowMin: Int, is24: Boolean = true): String {
    val h = nowMin / 60 % 24
    val shown = if (is24) h else (h % 12).let { if (it == 0) 12 else it }
    return if (is24) "%02d:%02d".format(shown, nowMin % 60) else "%d:%02d".format(shown, nowMin % 60)
}
private fun hm(min: Int) = "${min / 60}:%02d".format(min % 60)

/**
 * The Sky's foreground: header, clock, the 24-hour arc with screen-time segments and the sun or moon on it, and the
 * wellbeing numbers sitting directly on the dark ground. Everything animates in once, via [SkyController].
 */
@Composable
fun SkyContent(
    ctl: SkyController,
    config: PageletConfig,
    active: Boolean,
    reducedMotion: Boolean,
    lowLoad: Boolean,
    onOptions: () -> Unit,
    /** Null hides the link: the Activity log doesn't exist until the Wellbeing data does. */
    onActivityLog: (() -> Unit)?,
    onAttribution: () -> Unit,
    /** Asks for the permission a section needs, right where its prompt is shown. */
    onAllow: (SkySection) -> Unit = {},
    /** The launcher's own icon for an app (by package), or null to show an initial. */
    iconFor: (String?) -> ImageBitmap? = { null },
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val data = ctl.data ?: return
    val look = ctl.to
    val ph = look.phase
    // A forced debug weather also forces the words, so the label never contradicts the picture.
    val condition = data.weather?.let { SkyScenes.conditionLabel(SkyDebug.layer?.let(SkyScenes::symbolFor) ?: it.symbolCode, look.phaseKind) }
    val density = LocalDensity.current
    val is24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)

    // One frame clock for the marker's breathing; stopped when the page is away or motion is reduced.
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active && !reducedMotion && !lowLoad) {
        if (!(active && !reducedMotion && !lowLoad)) return@LaunchedEffect
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            kotlinx.coroutines.delay(SKY_FRAME_MS)
            androidx.compose.runtime.withFrameNanos { clock.longValue = it - start }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val horizonY = hPx * SkyScenes.HORIZON
        val defaultRadius = (wPx - with(density) { 60.dp.toPx() }) / 2f
        // On a tall-text or big-display phone the clock and weather block grow; the arc and the clock give way to fit
        // instead of colliding. The clock only ever shrinks (never oscillates) and stays above 55% of its size.
        var clockScale by remember { mutableFloatStateOf(1f) }
        val groundTop = hPx * SkyScenes.GROUND_START
        val groundHeightPx = (hPx - groundTop - with(density) { bottomInset.toPx() }).coerceAtLeast(0f)

        // The decorative parts (arc, clock, numbers) are one summary for a screen reader; the buttons on the ground
        // (Allow…, Activity log, the MET credit) stay reachable on their own.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = skySummary(data, config, condition) }) {
                SkyArcLayer(ctl, data, look, { clock.longValue / 1e9f }, reducedMotion, lowLoad)
            }

            // Header + clock rise into place first.
            Column(
                Modifier
                    .padding(horizontal = 22.dp)
                    .padding(top = topInset + 8.dp)
                    .testTag("sky-top")
                    .onGloballyPositioned {
                        val bottom = it.positionInParent().y + it.size.height
                        val room = with(density) { 46.dp.toPx() }               // NOON / NOW labels above the arc's top
                        val fits = horizonY - bottom - room
                        val minRadius = defaultRadius * 0.5f
                        val radius = fits.coerceIn(minRadius, defaultRadius)
                        if (kotlin.math.abs(ctl.arcRadiusPx - radius) > 1f) ctl.arcRadiusPx = radius
                        if (fits < minRadius && clockScale > 0.55f) clockScale = (clockScale * 0.93f).coerceAtLeast(0.55f)
                    }
                    .graphicsLayer {
                        val r = ctl.rise.value
                        alpha = r
                        translationY = (1f - r) * 26.dp.toPx()
                    }
            ) {
                if (config.has(SkySection.WEATHER)) Spacer(Modifier.height(54.dp)) else Spacer(Modifier.height(4.dp))
                if (config.has(SkySection.TIME)) {
                    Text(
                        timeText(data.nowMin, is24),
                        modifier = Modifier.clearAndSetSemantics { },   // part of the page summary
                        style = TextStyle(
                            fontSize = (118 * clockScale).sp, fontWeight = FontWeight(180), letterSpacing = (-6 * clockScale).sp,
                            lineHeight = (106 * clockScale).sp, color = White,
                        ),
                    )
                }
                val w = data.weather
                if (w != null && config.has(SkySection.WEATHER)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp).fillMaxWidth().clearAndSetSemantics { }) {
                        Text("${w.tempC.roundToInt()}°", style = TextStyle(fontSize = (52 * clockScale.coerceAtLeast(0.7f)).sp, fontWeight = FontWeight(250), color = White))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                (condition ?: "") + if (look.phaseKind == TimePhase.Day || look.phaseKind == TimePhase.Golden || look.phaseKind == TimePhase.Dawn) " today" else "",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White),
                            )
                            // Two short lines, so the glyph on the right never makes one of them wrap.
                            Text(
                                "H ${w.highC.roundToInt()}°  ·  L ${w.lowC.roundToInt()}°",
                                style = TextStyle(fontSize = 13.sp, color = White.copy(alpha = 0.75f)), maxLines = 1,
                            )
                            Text(
                                "Feels like ${w.feelsC.roundToInt()}°",
                                style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = 0.6f)), maxLines = 1,
                            )
                        }
                        // The weather as a small illustration, in the space the text leaves on the right.
                        // Only when there is room: on a small screen, or with large text, the numbers matter more than the picture.
                        if (clockScale >= 0.99f && density.fontScale <= 1.3f) {
                            Spacer(Modifier.weight(1f))
                            WeatherGlyph(
                                glyphFor(SkyDebug.layer?.let(SkyScenes::symbolFor) ?: w.symbolCode, look.phaseKind == TimePhase.Dusk || look.phaseKind == TimePhase.Night),
                                ph.sunC, 48.dp, Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                if (config.has(SkySection.CALENDAR)) CalendarLine(data, ph.accent, is24, onAllow)
            }

            // Ground: everything below the horizon sits directly on the near-black ground, no cards. It shows as many rows as
            // fit, most important first, so a small screen or large text loses a detail instead of overlapping the footer.
            GroundColumn(
                ctl = ctl, data = data, config = config, accent = ph.accent, nowHour = data.nowMin / 60, iconFor = iconFor,
                onAllow = onAllow, onActivityLog = onActivityLog, onAttribution = onAttribution,
                modifier = Modifier
                    .offset(y = with(density) { groundTop.toDp() })
                    .padding(horizontal = 22.dp)
                    .height(with(density) { groundHeightPx.toDp() })
                    .fillMaxWidth()
                    .testTag("sky-ground")
                    .graphicsLayer {
                        val g = ctl.ground.value
                        alpha = g
                        translationY = (1f - g) * 22.dp.toPx()
                    },
            )
        }

        // Header row (interactive) sits above the decorative layer so it keeps its own semantics.
        if (config.has(SkySection.WEATHER)) {
            val needsPlace = data.weather == null && data.weatherStatus == WeatherStatus.NeedsLocation
            Row(
                Modifier
                    .padding(start = 22.dp, end = 6.dp)
                    .padding(top = topInset + 8.dp)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = ctl.rise.value },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NearMe, null, Modifier.size(15.dp), tint = White)
                Spacer(Modifier.width(6.dp))
                Text(
                    data.weather?.city ?: "Weather", style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = White),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(3.dp).background(White.copy(alpha = 0.7f), CircleShape))
                Spacer(Modifier.width(8.dp))
                // With no forecast the page still shows the right time-of-day sky; the words say why there is no weather.
                val words = condition ?: when (data.weatherStatus) {
                    WeatherStatus.NeedsLocation -> "Set a location"
                    WeatherStatus.Unavailable -> "Weather unavailable"
                    else -> ""
                }
                Text(
                    words,
                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight(500), color = White.copy(alpha = 0.85f)),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).then(if (needsPlace) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onOptions).padding(vertical = 14.dp) else Modifier),
                )
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Pagelet options", onClick = onOptions)
                        .semantics { contentDescription = "Pagelet options" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MoreVert, null, tint = White) }
            }
        } else {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = topInset + 8.dp).size(48.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Pagelet options", onClick = onOptions)
                    .semantics { contentDescription = "Pagelet options" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreVert, null, tint = White) }
        }

        // Anything made up is labelled as such.
        val sampleLabel = when {
            data.weatherIsSample && data.wellbeingIsSample -> "SAMPLE DATA"
            data.wellbeingIsSample && config.has(SkySection.WELLBEING) -> "SAMPLE WELLBEING"
            else -> null
        }
        if (sampleLabel != null) {
            Text(
                sampleLabel,
                style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = White.copy(alpha = 0.8f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 54.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }

    }
}

@Composable
private fun GroundColumn(
    ctl: SkyController, data: SkyData, config: PageletConfig, accent: Color, nowHour: Int,
    iconFor: (String?) -> ImageBitmap?, onAllow: (SkySection) -> Unit, onActivityLog: (() -> Unit)?, onAttribution: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = ctl.counts.value
    val wb = data.wellbeing
    PriorityColumn(modifier) {
        // 0 - never dropped: screen time (or the one-line way to allow it).
        if (config.has(SkySection.WELLBEING)) {
            when {
                wb != null && data.wellbeingStatus != SectionStatus.NeedsPermission -> ScreenTimeBlock(ctl, wb, config, accent, counts, Modifier.priority(0))
                data.wellbeingStatus == SectionStatus.NeedsPermission -> PermissionPrompt(
                    "Screen time", "Allow usage access to see how long you use your phone. It is computed on this phone and never leaves it.",
                    "Allow usage access", accent, Modifier.priority(0),
                ) { onAllow(SkySection.WELLBEING) }
            }
        }
        if (wb != null && config.has(SkySection.WELLBEING) && data.wellbeingStatus != SectionStatus.NeedsPermission) {
            // 1: the three numbers.
            Row(Modifier.priority(1).padding(top = 14.dp).fillMaxWidth().height(IntrinsicSizeMinHeight)) {
                Stat("Unlocks", (wb.unlocks * counts).roundToInt().toString(), Modifier.weight(1f))
                Hairline()
                Stat("Up since", wb.upSinceHour?.let { "%d:%02d".format(it.toInt(), ((it % 1f) * 60).roundToInt()) } ?: "–", Modifier.weight(1f))
                Hairline()
                Stat("Longest session", SkyInsight.formatMin(wb.longestSessionMin).replace(" min", "m"), Modifier.weight(1f))
            }
        }
        // 2: steps.
        if (config.has(SkySection.STEPS)) StepsRow(ctl, data, config, accent, counts, Modifier.priority(2), onAllow)
        // 3: the sentence.
        if (wb != null && config.has(SkySection.WELLBEING) && data.wellbeingStatus != SectionStatus.NeedsPermission) {
            Text(
                SkyInsight.text(wb.totalMin, wb.yesterdayTotalMin, wb.hourlyMinutes, nowHour),
                style = TextStyle(fontSize = 13.sp, color = White.copy(alpha = 0.75f)),
                modifier = Modifier.priority(3).padding(top = 12.dp),
            )
        }
        // 4: the busiest apps, with their real icons.
        if (wb != null && wb.topApps.isNotEmpty() && config.has(SkySection.WELLBEING) && data.wellbeingStatus != SectionStatus.NeedsPermission) {
            Row(Modifier.priority(4).padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                wb.topApps.take(3).forEach { app ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        val icon = iconFor(app.packageName)
                        if (icon != null) Image(icon, null, Modifier.size(28.dp).clip(CircleShape))
                        else Box(Modifier.size(28.dp).clip(CircleShape).background(White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Text(app.label.take(1), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = White))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(app.label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight(500), color = White), maxLines = 1)
                            Text(SkyInsight.formatMin(app.minutes), style = TextStyle(fontSize = 11.sp, color = White.copy(alpha = 0.6f)))
                        }
                    }
                }
            }
        }
        // 0 - never dropped: the footer, with the credit MET Norway's terms require.
        Row(
            Modifier.priority(0).padding(top = 4.dp).fillMaxWidth().testTag("sky-footer"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (config.has(SkySection.WELLBEING) && onActivityLog != null && data.wellbeingStatus == SectionStatus.Ready) {
                Box(
                    Modifier.height(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onActivityLog).padding(end = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text("Activity log →", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)) }
            } else Spacer(Modifier.size(1.dp))
            if (config.has(SkySection.WEATHER) && data.weather != null && !data.weatherIsSample) {
                Box(
                    Modifier.height(48.dp).clickable(role = Role.Button, onClickLabel = "Open weather data source", onClick = onAttribution),
                    contentAlignment = Alignment.CenterEnd,
                ) { Text("Weather data: MET Norway", style = TextStyle(fontSize = 10.sp, color = White.copy(alpha = 0.45f))) }
            }
        }
    }
}

@Composable
private fun ScreenTimeBlock(ctl: SkyController, wb: SkyWellbeing, config: PageletConfig, accent: Color, counts: Float, modifier: Modifier) {
    val total = wb.totalMin
    val shown = (total * counts).roundToInt()
    val pct = if (config.goalMinutes > 0) (100f * total / config.goalMinutes) else 0f
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("SCREEN TIME TODAY", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, color = accent))
                Text(buildValue(shown), style = TextStyle(fontSize = 58.sp, fontWeight = FontWeight(220), color = White))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${(pct * counts).roundToInt()}%", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight(250), color = White))
                Text("of ${SkyInsight.formatMin(config.goalMinutes).replace(" min", "m")} goal", style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = 0.65f)))
            }
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(2.dp).background(White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(min(1f, pct / 100f) * ctl.goalBar.value).height(2.dp).background(accent))
        }
    }
}

@Composable
private fun StepsRow(ctl: SkyController, data: SkyData, config: PageletConfig, accent: Color, counts: Float, modifier: Modifier, onAllow: (SkySection) -> Unit) {
    when (data.stepsStatus) {
        SectionStatus.Ready, SectionStatus.Sample -> {
            val steps = data.steps ?: 0L
            val goal = config.stepGoal.coerceAtLeast(1)
            Column(modifier.padding(top = 14.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%,d".format((steps * counts).toLong()), style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight(250), color = White))
                        Spacer(Modifier.width(6.dp))
                        Text("steps", style = TextStyle(fontSize = 11.5.sp, color = White.copy(alpha = 0.6f)), modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text("of %,d".format(goal), style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = 0.6f)))
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).background(White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(min(1f, steps.toFloat() / goal) * ctl.goalBar.value).height(2.dp).background(accent))
                }
            }
        }
        SectionStatus.NeedsPermission -> PermissionPrompt("Steps", "Allow Health Connect to count today's steps. Read-only; it stays on this phone.", "Allow steps", accent, modifier) { onAllow(SkySection.STEPS) }
        SectionStatus.NotInstalled -> PermissionPrompt("Steps", "Steps come from Health Connect, which isn't installed on this phone.", "Get Health Connect", accent, modifier) { onAllow(SkySection.STEPS) }
        SectionStatus.Unavailable -> Text("Steps are unavailable right now.", style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = 0.6f)), modifier = modifier.padding(top = 14.dp))
        SectionStatus.Unsupported, SectionStatus.Idle -> Unit
    }
}

/** A calm one-line ask in the page's own voice, with a real button: no popups, no sample numbers standing in for data. */
@Composable
private fun PermissionPrompt(title: String, why: String, action: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.padding(top = 14.dp).fillMaxWidth()) {
        Text(title.uppercase(), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, color = accent))
        Text(why, style = TextStyle(fontSize = 13.sp, color = White.copy(alpha = 0.75f)), modifier = Modifier.padding(top = 4.dp))
        Box(
            Modifier.padding(top = 8.dp).height(48.dp).clip(CircleShape).background(White.copy(alpha = 0.14f))
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text(action, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = White)) }
    }
}

/** "NEXT · in 40 min   Design review" under the weather; or the way to allow the calendar. */
@Composable
private fun CalendarLine(data: SkyData, accent: Color, is24: Boolean, onAllow: (SkySection) -> Unit) {
    when (data.calendarStatus) {
        SectionStatus.NeedsPermission -> Box(
            Modifier.padding(top = 6.dp).height(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = { onAllow(SkySection.CALENDAR) }).padding(end = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text("Allow calendar to see what's next", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)) }
        SectionStatus.Ready -> {
            val next = data.calendar.firstOrNull()
            val nowMs = System.currentTimeMillis()
            if (next == null) {
                Text("Nothing else today", style = TextStyle(fontSize = 13.sp, color = White.copy(alpha = 0.65f)), modifier = Modifier.padding(top = 8.dp))
            } else {
                val whenText = NextUp.when_(next, nowMs) { ms -> timeText(java.util.Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }, is24) }
                val more = data.calendar.size - 1
                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (next.color != 0) Color(next.color) else accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(whenText.uppercase(), style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = accent))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        next.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White), modifier = Modifier.weight(1f, fill = false),
                    )
                    if (more > 0) Text("  +$more", style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = 0.6f)))
                }
            }
        }
        else -> Unit
    }
}

private val IntrinsicSizeMinHeight = 52.dp

@Composable
private fun Hairline() = Box(Modifier.padding(horizontal = 10.dp).width(1.dp).fillMaxSize().background(White.copy(alpha = 0.12f)))

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight(250), color = White), maxLines = 1)
        Text(label, style = TextStyle(fontSize = 11.5.sp, color = White.copy(alpha = 0.6f)), maxLines = 1)
    }
}

private fun buildValue(minutes: Int): AnnotatedString = buildAnnotatedString {
    val h = minutes / 60
    val m = minutes % 60
    val unit = SpanStyle(fontSize = 22.sp, color = White.copy(alpha = 0.6f))
    if (h > 0) { append(h.toString()); withStyle(unit) { append("h ") } }
    append(m.toString()); withStyle(unit) { append("m") }
}

// ---- the arc ---------------------------------------------------------------------------------------------------

@Composable
/** [time] is read while drawing, so the marker's breathing redraws the arc without recomposing anything. */
private fun SkyArcLayer(ctl: SkyController, data: SkyData, look: SkyLook, time: () -> Float, reduced: Boolean, lowLoad: Boolean) {
    val measurer = rememberTextMeasurer()
    val ph = look.phase
    val moon = ph.marker == Marker.Moon
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val dp = density
        val w = size.width; val h = size.height
        val cx = w / 2f
        val hy = h * SkyScenes.HORIZON
        val r = ctl.arcRadiusPx.takeIf { it > 0f } ?: ((w - 60f * dp) / 2f)
        val topLeft = Offset(cx - r, hy - r)
        val box = Size(2f * r, 2f * r)
        val draw = ctl.arcDraw.value
        val rise = ctl.rise.value

        // Full arc: dotted.
        drawArc(
            White.copy(alpha = 0.28f * draw), 180f, 180f * draw, false, topLeft, box,
            style = Stroke(1.2f * dp, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * dp, 6f * dp))),
        )
        // Daylight span.
        val srH = data.sunriseMin / 60f; val ssH = data.sunsetMin / 60f
        val a0 = SkyArc.composeAngleDeg(srH)
        val spanSweep = (SkyArc.composeAngleDeg(ssH) - a0)
        drawArc(White.copy(alpha = 0.12f * draw), a0, spanSweep * draw, false, topLeft, box, style = Stroke(8f * dp, cap = StrokeCap.Round))

        // Sunrise / sunset ticks and labels.
        if (draw > 0.9f) {
            for ((hr, label, leftSide) in listOf(Triple(srH, "↑ ${hm(data.sunriseMin)}", true), Triple(ssH, "${hm(data.sunsetMin)} ↓", false))) {
                val (x1, y1) = SkyArc.point(hr, cx, hy, r - 5f * dp)
                val (x2, y2) = SkyArc.point(hr, cx, hy, r + 5f * dp)
                drawLine(White.copy(alpha = 0.6f), Offset(x1, y1), Offset(x2, y2), 1.5f * dp)
                val layout = measurer.measure(label, TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight(500), color = White.copy(alpha = 0.7f)))
                val (lx, ly) = SkyArc.point(hr, cx, hy, r + 14f * dp)
                drawText(layout, topLeft = Offset(if (leftSide) lx - layout.size.width / 2f else lx - layout.size.width / 2f, ly - layout.size.height / 2f))
            }
            val noon = measurer.measure("NOON", TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight(600), letterSpacing = 2.sp, color = White.copy(alpha = 0.45f)))
            drawText(noon, topLeft = Offset(cx - noon.size.width / 2f, hy - r - 24f * dp))
        }

        // Screen-time segments, revealed one after another.
        val nowH = data.nowMin / 60
        val frac = (data.nowMin % 60) / 60f
        val used = data.wellbeing?.hourlyMinutes?.withIndex()?.filter { it.value > 0 } ?: emptyList()
        used.forEachIndexed { i, (hr, minutes) ->
            val seg = SkyArc.segment(hr, minutes.toFloat(), if (hr == nowH) frac else 1f) ?: return@forEachIndexed
            val reveal = ((ctl.segments.value * (used.size + 2)) - i).coerceIn(0f, 1f)
            if (reveal <= 0f) return@forEachIndexed
            val start = SkyArc.composeAngleDeg(seg.first)
            val sweep = (SkyArc.composeAngleDeg(seg.second) - start) * reveal
            drawArc(ph.segGlow.copy(alpha = ph.segGlow.alpha * 0.45f), start, sweep, false, topLeft, box, style = Stroke(9f * dp, cap = StrokeCap.Round))
            drawArc(ph.seg, start, sweep, false, topLeft, box, style = Stroke(5f * dp, cap = StrokeCap.Round))
        }

        // The sun or moon at the current time.
        val hourNow = data.nowMin / 60f * ctl.travel.value
        val (mx, my) = SkyArc.point(hourNow, cx, hy, r)
        val sunOp = look.weather.sunOpacity
        val breathe = if (reduced || lowLoad) 1f else 1f + 0.1f * (0.5f + 0.5f * sin(t / 5f * 2f * PI.toFloat()))
        if (!moon) {
            val glowR = 80f * dp * breathe
            drawCircle(
                Brush.radialGradient(0f to ph.glow, 1f to Color.Transparent, center = Offset(mx, my), radius = glowR),
                radius = glowR, center = Offset(mx, my), alpha = sunOp,
            )
            drawCircle(ph.glow, 15f * dp, Offset(mx, my), alpha = 0.5f * sunOp)
            drawCircle(
                Brush.radialGradient(0f to Color.White, 1f to ph.sunC, center = Offset(mx, my), radius = 11f * dp),
                radius = 11f * dp, center = Offset(mx, my), alpha = sunOp.coerceAtLeast(0.35f),
            )
        } else {
            drawCircle(
                Brush.radialGradient(0f to ph.glow, 1f to Color.Transparent, center = Offset(mx, my), radius = 56f * dp),
                radius = 56f * dp, center = Offset(mx, my),
            )
            drawCircle(ph.sunC, 11f * dp, Offset(mx, my))
            // The crescent: bite the disc with the sky colour.
            drawCircle(ph.sky1.copy(alpha = 0.95f), 9f * dp, Offset(mx + 4.5f * dp, my - 3.5f * dp))
        }
        val now = measurer.measure("NOW", TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = White.copy(alpha = 0.85f * rise)))
        drawText(now, topLeft = Offset(mx - now.size.width / 2f, my - 34f * dp - now.size.height / 2f))
    }
}
