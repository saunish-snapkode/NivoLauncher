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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.pagelet.PageletTokens
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.pressFeedback
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val White = Color.White

/** Tabular figures: the clock, the temperature and every counting number keep their width as digits change. */
private const val TNUM = "tnum"

/**
 * A soft, wide shadow under the text that sits on the sky. The scrim keeps contrast at 4.5:1; this only lifts the glyph
 * edges off a bright cloud drifting behind them, so it stays almost invisible on a dark sky.
 */
private val SkyTextShadow = Shadow(color = Color.Black.copy(alpha = 0.32f), offset = Offset(0f, 1f), blurRadius = 10f)

/** Arc labels, built once: the arc redraws every frame, the styles never change. */
private val TickLabelStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight(500), color = White.copy(alpha = 0.72f), fontFeatureSettings = TNUM)
private val NoonLabelStyle = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight(600), letterSpacing = 2.sp, color = White.copy(alpha = 0.5f))
private val NowLabelStyle = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = White.copy(alpha = 0.85f))

/** "28°", with a true minus sign below zero ("−3°") so the hero number reads as typography, not as a hyphen. */
private fun degrees(c: Double): String {
    val v = c.roundToInt()
    return if (v < 0) "\u2212${-v}°" else "$v°"
}

/** What the Sky says to a screen reader: one sentence instead of every number and the decorative arc. */
internal fun skySummary(data: SkyData, config: PageletConfig, condition: String?): String = buildString {
    append(timeText(data.nowMin, true)).append(". ")
    if (config.has(SkySection.WEATHER)) {
        val w = data.weather
        if (w != null) {
            append(w.city).append(", ")
            if (!condition.isNullOrEmpty()) append(condition).append(", ")
            append(w.tempC.roundToInt()).append(" degrees, feels like ").append(w.feelsC.roundToInt())
                .append(", high ").append(w.highC.roundToInt())
                .append(", low ").append(w.lowC.roundToInt()).append(". ")
            if (data.weatherStatus == WeatherStatus.Stale) append("Last known weather; it hasn't updated recently. ")
        } else when (data.weatherStatus) {
            WeatherStatus.NeedsLocation -> append("Set a location to see the weather. ")
            WeatherStatus.Unavailable -> append("Weather unavailable. ")
            else -> Unit
        }
    }
    data.wellbeing?.takeIf { config.has(SkySection.WELLBEING) }?.let {
        val total = it.totalMin
        append("Screen time ").append(spoken(total))
        if (config.goalMinutes > 0) {
            append(", ").append((100f * total / config.goalMinutes).roundToInt()).append(" percent of your ")
                .append(spoken(config.goalMinutes)).append(" goal")
        }
        append(". ").append(it.unlocks).append(" unlocks.")
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
        val defaultRadius = (wPx - with(density) { (2 * PageletTokens.ARC_DESIGN_SIDE_MARGIN_DP).dp.toPx() }) / 2f
        // On a tall-text or big-display phone the clock and weather block grow; the arc and the clock give way to fit
        // instead of colliding. The clock only ever shrinks (never oscillates) and stays above 55% of its size.
        var clockScale by remember { mutableFloatStateOf(1f) }
        val groundTop = hPx * SkyScenes.GROUND_START
        val groundHeightPx = (hPx - groundTop - with(density) { bottomInset.toPx() }).coerceAtLeast(0f)

        // The decorative parts (arc, clock, numbers) are one summary for a screen reader; the buttons on the ground
        // (Allow…, Activity log, the MET credit) stay reachable on their own.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = skySummary(data, config, condition) }) {
                SkyArcLayer(ctl, data, look, { clock.longValue / 1e9f }, reducedMotion, lowLoad, is24)
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
                            lineHeight = (106 * clockScale).sp, color = White, fontFeatureSettings = TNUM, shadow = SkyTextShadow,
                        ),
                    )
                }
                val w = data.weather
                if (w != null && config.has(SkySection.WEATHER)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = NivoSpace.xs).fillMaxWidth().clearAndSetSemantics { }) {
                        Text(
                            degrees(w.tempC),
                            style = TextStyle(
                                fontSize = (52 * clockScale.coerceAtLeast(0.7f)).sp, fontWeight = FontWeight(250),
                                letterSpacing = (-1.5).sp, color = White, fontFeatureSettings = TNUM, shadow = SkyTextShadow,
                            ),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                (condition ?: "") + if (look.phaseKind == TimePhase.Day || look.phaseKind == TimePhase.Golden || look.phaseKind == TimePhase.Dawn) " today" else "",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White, shadow = SkyTextShadow),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            // Two short lines, so the glyph on the right never makes one of them wrap.
                            Text(
                                "H ${degrees(w.highC)}  ·  L ${degrees(w.lowC)}",
                                style = TextStyle(
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White.copy(alpha = 0.86f),
                                    fontFeatureSettings = TNUM, shadow = SkyTextShadow,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                "Feels like ${degrees(w.feelsC)}" + if (data.weatherStatus == WeatherStatus.Stale) "  ·  Last known" else "",
                                style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = NivoAlpha.medium), fontFeatureSettings = TNUM, shadow = SkyTextShadow),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // The weather as a small illustration, in the space the text leaves on the right.
                        // Only when there is room: on a small screen, or with large text, the numbers matter more than the picture.
                        if (clockScale >= 0.99f && density.fontScale <= 1.3f) {
                            Spacer(Modifier.weight(1f))
                            WeatherGlyph(
                                glyphFor(SkyDebug.layer?.let(SkyScenes::symbolFor) ?: w.symbolCode, look.phaseKind == TimePhase.Dusk || look.phaseKind == TimePhase.Night),
                                ph.sunC, 48.dp, Modifier.padding(start = NivoSpace.s),
                            )
                        }
                    }
                }
                if (config.has(SkySection.CALENDAR)) CalendarLine(data, ph.accent, is24, onAllow)
            }

            // Ground: everything below the horizon sits directly on the near-black ground, no cards. It shows as many rows as
            // fit, most important first, so a small screen or large text loses a detail instead of overlapping the footer.
            GroundColumn(
                ctl = ctl, data = data, config = config, accent = ph.accent, nowHour = data.nowMin / 60, is24 = is24, iconFor = iconFor,
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
                // City and condition read as one heading, so TalkBack users can jump straight to the weather.
                Row(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NearMe, null, Modifier.size(15.dp), tint = White)
                Spacer(Modifier.width(6.dp))
                Text(
                    data.weather?.city ?: "Weather", style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = White, shadow = SkyTextShadow),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(NivoSpace.s))
                Box(Modifier.size(3.dp).background(White.copy(alpha = NivoAlpha.medium), CircleShape).clearAndSetSemantics { })
                Spacer(Modifier.width(NivoSpace.s))
                // With no forecast the page still shows the right time-of-day sky; the words say why there is no weather.
                val words = condition ?: when (data.weatherStatus) {
                    WeatherStatus.NeedsLocation -> "Set a location"
                    WeatherStatus.Unavailable -> "Weather unavailable"
                    else -> ""
                }
                Text(
                    words,
                    style = TextStyle(
                        fontSize = 13.5.sp, fontWeight = FontWeight(500), shadow = SkyTextShadow,
                        // "Set a location" is a link: full white and underlined, so it reads as tappable.
                        color = White.copy(alpha = if (needsPlace) NivoAlpha.high else 0.86f),
                        textDecoration = if (needsPlace) TextDecoration.Underline else null,
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).then(
                        if (needsPlace) Modifier.heightIn(min = NivoTouch.min).clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Set a location", onClick = onOptions)
                            .wrapContentHeight().padding(horizontal = NivoSpace.xs)
                        else Modifier
                    ),
                )
                }
                Box(
                    Modifier.size(NivoTouch.min).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Pagelet options", onClick = onOptions)
                        .semantics { contentDescription = "Pagelet options" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MoreVert, null, tint = White) }
            }
        } else {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = topInset + 8.dp).size(NivoTouch.min).clip(CircleShape)
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
                style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = White.copy(alpha = 0.86f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 54.dp)
                    .graphicsLayer { alpha = ctl.rise.value }
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.14f))
                    .border(1.dp, White.copy(alpha = NivoAlpha.hairline), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    // Read as words, not as an all-caps abbreviation.
                    .clearAndSetSemantics {
                        contentDescription = if (sampleLabel == "SAMPLE DATA") "Sample data: these numbers are examples" else "Sample screen time: these numbers are examples"
                    },
            )
        }

    }
}

@Composable
private fun GroundColumn(
    ctl: SkyController, data: SkyData, config: PageletConfig, accent: Color, nowHour: Int, is24: Boolean,
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
            val upSince = wb.upSinceHour?.let { timeText((it * 60f).roundToInt(), is24) }
            Row(Modifier.priority(1).padding(top = 14.dp).fillMaxWidth().height(IntrinsicSizeMinHeight)) {
                Stat("Unlocks", (wb.unlocks * counts).roundToInt().toString(), "${wb.unlocks} unlocks", Modifier.weight(1f))
                Hairline()
                Stat("Up since", upSince ?: "–", if (upSince != null) "Up since $upSince" else "No unlock yet today", Modifier.weight(1f))
                Hairline()
                Stat(
                    "Longest session", SkyInsight.formatMin(wb.longestSessionMin).replace(" min", "m"),
                    "Longest session ${spoken(wb.longestSessionMin)}", Modifier.weight(1f),
                )
            }
        }
        // 2: steps.
        if (config.has(SkySection.STEPS)) StepsRow(ctl, data, config, accent, counts, Modifier.priority(2), onAllow)
        // 3: the sentence.
        if (wb != null && config.has(SkySection.WELLBEING) && data.wellbeingStatus != SectionStatus.NeedsPermission) {
            Text(
                SkyInsight.text(wb.totalMin, wb.yesterdayTotalMin, wb.hourlyMinutes, nowHour),
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = White.copy(alpha = NivoAlpha.medium)),
                modifier = Modifier.priority(3).padding(top = NivoSpace.m),
            )
        }
        // 4: the busiest apps, with their real icons.
        if (wb != null && wb.topApps.isNotEmpty() && config.has(SkySection.WELLBEING) && data.wellbeingStatus != SectionStatus.NeedsPermission) {
            Row(Modifier.priority(4).padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                wb.topApps.take(3).forEach { app ->
                    Row(
                        Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${app.label}, ${spoken(app.minutes)}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = iconFor(app.packageName)
                        if (icon != null) Image(icon, null, Modifier.size(28.dp).clip(CircleShape))
                        else Box(Modifier.size(28.dp).clip(CircleShape).background(White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Text(app.label.take(1).uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = White))
                        }
                        Spacer(Modifier.width(NivoSpace.s))
                        Column {
                            Text(
                                app.label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight(500), color = White),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                SkyInsight.formatMin(app.minutes),
                                style = TextStyle(fontSize = 11.sp, color = White.copy(alpha = NivoAlpha.low), fontFeatureSettings = TNUM), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        // 0 - never dropped: the footer, with the credit MET Norway's terms require.
        Row(
            Modifier.priority(0).padding(top = NivoSpace.xs).fillMaxWidth().testTag("sky-footer"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (config.has(SkySection.WELLBEING) && onActivityLog != null && data.wellbeingStatus == SectionStatus.Ready) {
                Row(
                    Modifier.height(NivoTouch.min).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Open activity log", onClick = onActivityLog).padding(end = NivoSpace.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Activity log", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent))
                    // The arrow is decoration: TalkBack says "Activity log", not "right arrow".
                    Text(" →", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent), modifier = Modifier.clearAndSetSemantics { })
                }
            } else Spacer(Modifier.size(1.dp))
            if (config.has(SkySection.WEATHER) && data.weather != null && !data.weatherIsSample) {
                Box(
                    Modifier.height(NivoTouch.min).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Open weather data source", onClick = onAttribution)
                        .padding(start = NivoSpace.m),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        "Weather data: MET Norway",
                        style = TextStyle(fontSize = 11.sp, letterSpacing = 0.2.sp, color = White.copy(alpha = NivoAlpha.low)),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeBlock(ctl: SkyController, wb: SkyWellbeing, config: PageletConfig, accent: Color, counts: Float, modifier: Modifier) {
    val total = wb.totalMin
    val shown = (total * counts).roundToInt()
    val pct = if (config.goalMinutes > 0) (100f * total / config.goalMinutes) else 0f
    // One sentence with the real (not mid-count-up) numbers: "Screen time today, 3 hours 12 minutes, 80 percent of 4 hours goal".
    val spokenBlock = buildString {
        append("Screen time today, ").append(spoken(total))
        if (config.goalMinutes > 0) append(", ").append(pct.roundToInt()).append(" percent of ").append(spoken(config.goalMinutes)).append(" goal")
    }
    Column(modifier.clearAndSetSemantics { contentDescription = spokenBlock }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("SCREEN TIME TODAY", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, color = accent))
                Text(
                    buildValue(shown),
                    style = TextStyle(fontSize = 58.sp, fontWeight = FontWeight(220), letterSpacing = (-1).sp, color = White, fontFeatureSettings = TNUM),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${(pct * counts).roundToInt()}%",
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight(250), color = White, fontFeatureSettings = TNUM),
                )
                Text(
                    "of ${SkyInsight.formatMin(config.goalMinutes).replace(" min", "m")} goal",
                    style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = NivoAlpha.medium)),
                )
            }
        }
        GoalBar(min(1f, pct / 100f) * ctl.goalBar.value, accent, Modifier.padding(top = 10.dp))
    }
}

/** A hairline track with the accent filling it; pill-ended so it reads as a meter, not a rule. */
@Composable
private fun GoalBar(fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(White.copy(alpha = NivoAlpha.hairline))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).clip(CircleShape).background(accent))
    }
}

@Composable
private fun StepsRow(ctl: SkyController, data: SkyData, config: PageletConfig, accent: Color, counts: Float, modifier: Modifier, onAllow: (SkySection) -> Unit) {
    when (data.stepsStatus) {
        SectionStatus.Ready, SectionStatus.Sample -> {
            val steps = data.steps ?: 0L
            val goal = config.stepGoal.coerceAtLeast(1)
            Column(
                modifier.padding(top = 14.dp).fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = "%,d steps of %,d goal".format(steps, goal) },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        // Number and unit share a baseline at any font scale.
                        Text(
                            "%,d".format((steps * counts).toLong()),
                            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight(250), color = White, fontFeatureSettings = TNUM),
                            modifier = Modifier.alignByBaseline(),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("steps", style = TextStyle(fontSize = 11.5.sp, color = White.copy(alpha = NivoAlpha.low)), modifier = Modifier.alignByBaseline())
                    }
                    Text("of %,d".format(goal), style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = NivoAlpha.low), fontFeatureSettings = TNUM))
                }
                GoalBar(min(1f, steps.toFloat() / goal) * ctl.goalBar.value, accent, Modifier.padding(top = 6.dp))
            }
        }
        SectionStatus.NeedsPermission -> PermissionPrompt("Steps", "Allow Health Connect to count today's steps. Read-only; it stays on this phone.", "Allow steps", accent, modifier) { onAllow(SkySection.STEPS) }
        SectionStatus.NotInstalled -> PermissionPrompt("Steps", "Steps come from Health Connect, which isn't installed on this phone.", "Get Health Connect", accent, modifier) { onAllow(SkySection.STEPS) }
        SectionStatus.Unavailable -> Text("Steps are unavailable right now.", style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = NivoAlpha.low)), modifier = modifier.padding(top = 14.dp))
        SectionStatus.Unsupported, SectionStatus.Idle -> Unit
    }
}

/** A calm one-line ask in the page's own voice, with a real button: no popups, no sample numbers standing in for data. */
@Composable
private fun PermissionPrompt(title: String, why: String, action: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Column(modifier.padding(top = 14.dp).fillMaxWidth()) {
        Text(
            title.uppercase(), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, color = accent),
            modifier = Modifier.semantics { contentDescription = title; heading() },
        )
        Text(why, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = White.copy(alpha = NivoAlpha.medium)), modifier = Modifier.padding(top = NivoSpace.xs))
        Box(
            Modifier.padding(top = NivoSpace.s).heightIn(min = NivoTouch.min).pressFeedback(press).clip(CircleShape)
                .background(White.copy(alpha = 0.14f))
                .border(1.dp, White.copy(alpha = NivoAlpha.hairline), CircleShape)
                .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text(action, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = White)) }
    }
}

/** "NEXT · in 40 min   Design review" under the weather; or the way to allow the calendar. */
@Composable
private fun CalendarLine(data: SkyData, accent: Color, is24: Boolean, onAllow: (SkySection) -> Unit) {
    when (data.calendarStatus) {
        SectionStatus.NeedsPermission -> Box(
            Modifier.padding(top = NivoSpace.xxs).height(NivoTouch.min).clip(CircleShape)
                .clickable(role = Role.Button, onClick = { onAllow(SkySection.CALENDAR) }).padding(end = NivoSpace.m),
            contentAlignment = Alignment.CenterStart,
        ) { Text("Allow calendar to see what's next", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent, shadow = SkyTextShadow)) }
        SectionStatus.Ready -> {
            val next = data.calendar.firstOrNull()
            val nowMs = System.currentTimeMillis()
            if (next == null) {
                Text(
                    "Nothing else today",
                    style = TextStyle(fontSize = 13.sp, color = White.copy(alpha = NivoAlpha.medium), shadow = SkyTextShadow),
                    modifier = Modifier.padding(top = NivoSpace.s),
                )
            } else {
                val whenText = NextUp.when_(next, nowMs) { ms -> timeText(java.util.Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }, is24) }
                val more = data.calendar.size - 1
                val spokenLine = "Next: ${next.title}, $whenText" + if (more > 0) ", and $more more today" else ""
                Row(
                    Modifier.padding(top = NivoSpace.s).fillMaxWidth().clearAndSetSemantics { contentDescription = spokenLine },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(NivoSpace.s).background(if (next.color != 0) Color(next.color) else accent, CircleShape))
                    Spacer(Modifier.width(NivoSpace.s))
                    Text(
                        whenText.uppercase(),
                        style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = accent, fontFeatureSettings = TNUM, shadow = SkyTextShadow),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(NivoSpace.s))
                    Text(
                        next.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White, shadow = SkyTextShadow), modifier = Modifier.weight(1f, fill = false),
                    )
                    if (more > 0) Text("  +$more", style = TextStyle(fontSize = 12.sp, color = White.copy(alpha = NivoAlpha.medium), shadow = SkyTextShadow))
                }
            }
        }
        else -> Unit
    }
}

private val IntrinsicSizeMinHeight = 52.dp

@Composable
private fun Hairline() = Box(Modifier.padding(horizontal = 10.dp).width(1.dp).fillMaxSize().background(White.copy(alpha = NivoAlpha.hairline)))

/** One of the three numbers: the figure over its label, read as one phrase ([spokenText]) with the final value. */
@Composable
private fun Stat(label: String, value: String, spokenText: String, modifier: Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = spokenText }) {
        Text(value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight(250), color = White, fontFeatureSettings = TNUM), maxLines = 1)
        Text(label, style = TextStyle(fontSize = 11.5.sp, color = White.copy(alpha = NivoAlpha.low)), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun buildValue(minutes: Int): AnnotatedString = buildAnnotatedString {
    val h = minutes / 60
    val m = minutes % 60
    val unit = SpanStyle(fontSize = 22.sp, letterSpacing = 0.sp, color = White.copy(alpha = NivoAlpha.low))
    if (h > 0) { append(h.toString()); withStyle(unit) { append("h ") } }
    append(m.toString()); withStyle(unit) { append("m") }
}

// ---- the arc ---------------------------------------------------------------------------------------------------

/** Strokes for the arc, built once per density instead of on every frame of the breathing marker. */
private class ArcStrokes(dp: Float) {
    val dotted = Stroke(1.2f * dp, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * dp, 6f * dp)))
    val daylight = Stroke(8f * dp, cap = StrokeCap.Round)
    val segGlow = Stroke(9f * dp, cap = StrokeCap.Round)
    val seg = Stroke(5f * dp, cap = StrokeCap.Round)
}

private fun DrawScope.drawSunTick(
    hour: Float, label: String, cx: Float, hy: Float, r: Float, dp: Float, measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val (x1, y1) = SkyArc.point(hour, cx, hy, r - 5f * dp)
    val (x2, y2) = SkyArc.point(hour, cx, hy, r + 5f * dp)
    drawLine(White.copy(alpha = 0.6f), Offset(x1, y1), Offset(x2, y2), 1.5f * dp, cap = StrokeCap.Round)
    val layout = measurer.measure(label, TickLabelStyle)
    val (lx, ly) = SkyArc.point(hour, cx, hy, r + 14f * dp)
    drawText(layout, topLeft = Offset(lx - layout.size.width / 2f, ly - layout.size.height / 2f))
}

@Composable
/** [time] is read while drawing, so the marker's breathing redraws the arc without recomposing anything. */
private fun SkyArcLayer(ctl: SkyController, data: SkyData, look: SkyLook, time: () -> Float, reduced: Boolean, lowLoad: Boolean, is24: Boolean) {
    val measurer = rememberTextMeasurer()
    val ph = look.phase
    val moon = ph.marker == Marker.Moon
    val pxPerDp = LocalDensity.current.density
    val strokes = remember(pxPerDp) { ArcStrokes(pxPerDp) }
    val sunriseLabel = remember(data.sunriseMin, is24) { "↑ ${timeText(data.sunriseMin, is24)}" }
    val sunsetLabel = remember(data.sunsetMin, is24) { "${timeText(data.sunsetMin, is24)} ↓" }
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val dp = density
        val w = size.width; val h = size.height
        val cx = w / 2f
        val hy = h * SkyScenes.HORIZON
        val r = ctl.arcRadiusPx.takeIf { it > 0f } ?: ((w - 2f * PageletTokens.ARC_DESIGN_SIDE_MARGIN_DP * dp) / 2f)
        val topLeft = Offset(cx - r, hy - r)
        val box = Size(2f * r, 2f * r)
        val draw = ctl.arcDraw.value
        val rise = ctl.rise.value

        // Full arc: dotted.
        drawArc(White.copy(alpha = 0.28f * draw), 180f, 180f * draw, false, topLeft, box, style = strokes.dotted)
        // Daylight span.
        val srH = data.sunriseMin / 60f; val ssH = data.sunsetMin / 60f
        val a0 = SkyArc.composeAngleDeg(srH)
        val spanSweep = (SkyArc.composeAngleDeg(ssH) - a0)
        drawArc(White.copy(alpha = 0.12f * draw), a0, spanSweep * draw, false, topLeft, box, style = strokes.daylight)

        // Sunrise / sunset ticks and labels.
        if (draw > 0.9f) {
            drawSunTick(srH, sunriseLabel, cx, hy, r, dp, measurer)
            drawSunTick(ssH, sunsetLabel, cx, hy, r, dp, measurer)
            val noon = measurer.measure("NOON", NoonLabelStyle)
            drawText(noon, topLeft = Offset(cx - noon.size.width / 2f, hy - r - 24f * dp))
        }

        // Screen-time segments, revealed one after another (counted in place: no list is built per frame).
        val nowH = data.nowMin / 60
        val frac = (data.nowMin % 60) / 60f
        val hourly = data.wellbeing?.hourlyMinutes
        if (hourly != null) {
            var usedCount = 0
            for (m in hourly) if (m > 0) usedCount++
            var i = 0
            for (hr in hourly.indices) {
                val minutes = hourly[hr]
                if (minutes <= 0) continue
                val idx = i++
                val seg = SkyArc.segment(hr, minutes.toFloat(), if (hr == nowH) frac else 1f) ?: continue
                val reveal = ((ctl.segments.value * (usedCount + 2)) - idx).coerceIn(0f, 1f)
                if (reveal <= 0f) continue
                val start = SkyArc.composeAngleDeg(seg.first)
                val sweep = (SkyArc.composeAngleDeg(seg.second) - start) * reveal
                drawArc(ph.segGlow.copy(alpha = ph.segGlow.alpha * 0.45f), start, sweep, false, topLeft, box, style = strokes.segGlow)
                drawArc(ph.seg, start, sweep, false, topLeft, box, style = strokes.seg)
            }
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
        // Measured with one fixed style (so the measurer's cache hits every frame); the entrance fades it via the colour.
        val now = measurer.measure("NOW", NowLabelStyle)
        drawText(now, color = White.copy(alpha = 0.85f * rise), topLeft = Offset(mx - now.size.width / 2f, my - 34f * dp - now.size.height / 2f))
    }
}
