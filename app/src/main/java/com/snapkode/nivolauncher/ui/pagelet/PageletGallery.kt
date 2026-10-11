package com.snapkode.nivolauncher.ui.pagelet

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.pressFeedback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.SkySection
import com.snapkode.nivolauncher.pagelet.UsageAccess
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyPalettes

/**
 * "Add a Pagelet": a bottom sheet with a carousel of templates, the sections to show, the use-the-whole-page switch, a
 * permission card that follows the real permission state, and the Cancel / Add buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageletGallerySheet(
    onAdd: (PageletType, PageletConfig) -> Unit,
    onDismiss: () -> Unit,
    checkFeed: suspend (String) -> Pair<String, Int>? = { null },
    /** Kinds already on home: shown as "Added" and not selectable. */
    taken: Set<PageletType> = emptySet(),
    /** The template to start on; the first one not yet added if null. */
    initial: PageletType? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = initial?.takeIf { it.available && it !in taken }
        ?: PageletType.entries.firstOrNull { it.available && it !in taken } ?: PageletType.SKY
    var selected by remember { mutableStateOf(start) }
    var config by remember { mutableStateOf(PageletConfig.defaultsFor(start)) }
    val drafts = remember { HashMap<PageletType, PageletConfig>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SheetTitle("Add a Pagelet")
            Text(
                "A Pagelet is a widget that fills a whole home page. It updates live and swipes like any other page.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
            )
            LazyRow(
                // Extra room on top for the selected card's lift, so it never clips.
                modifier = Modifier.selectableGroup(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = NivoSpace.xl, end = NivoSpace.xl, top = 20.dp, bottom = NivoSpace.l),
                horizontalArrangement = Arrangement.spacedBy(NivoSpace.m),
            ) {
                items(PageletType.entries.filter { it != PageletType.FOCUS }) { type ->
                    TemplateCard(type, selected = type == selected, taken = type in taken) {
                        // Switching back and forth keeps what was already chosen for each template.
                        if (type.available && type !in taken && type != selected) {
                            drafts[selected] = config
                            selected = type
                            config = drafts[type] ?: PageletConfig.defaultsFor(type)
                        }
                    }
                }
            }

            if (selected == PageletType.SKY) SkyOptions(config, onChange = { config = it })
            if (selected == PageletType.HEADLINES) HeadlinesPageletOptions(config, onChange = { config = it }, checkFeed)
            else if (selected.available && selected != PageletType.SKY) SimplePageletOptions(selected, config, onChange = { config = it })

            Row(
                Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xl, vertical = NivoSpace.m),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = NivoTouch.min)) { Text("Cancel") }
                Spacer(Modifier.width(NivoSpace.s))
                Button(onClick = { onAdd(selected, config) }, enabled = selected.available && selected !in taken, modifier = Modifier.heightIn(min = NivoTouch.min)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(NivoSpace.xs))
                    Text("Add ${selected.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The settings of a Pagelet that already exists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageletOptionsSheet(
    type: PageletType,
    config: PageletConfig,
    onChange: (PageletConfig) -> Unit,
    onDismiss: () -> Unit,
    /** Where the weather comes from; shown while the Weather section is on. */
    weather: @Composable () -> Unit = {},
    debug: @Composable () -> Unit = {},
    checkFeed: suspend (String) -> Pair<String, Int>? = { null },
    /** Offered as a "Remove this Pagelet" button when set. */
    onRemove: (() -> Unit)? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("${type.title} Pagelet")
            if (type == PageletType.SKY) SkyOptions(config, onChange)
            if (type == PageletType.HEADLINES) HeadlinesPageletOptions(config, onChange, checkFeed)
            else if (type.available && type != PageletType.SKY) SimplePageletOptions(type, config, onChange)
            if (type == PageletType.SKY && config.has(SkySection.WEATHER)) weather()
            if (type == PageletType.SKY) debug()   // scene controls only mean something on the sky
            Row(
                // Same 24 dp edges as the rows above, so the text lines up with them and Done with the switches.
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRemove != null) TextButton(
                    onClick = onRemove, modifier = Modifier.heightIn(min = NivoTouch.min),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = NivoSpace.m, top = NivoSpace.s, bottom = NivoSpace.s),
                ) { Text("Remove Pagelet", color = MaterialTheme.colorScheme.error, maxLines = 1) }
                else Spacer(Modifier.width(1.dp))
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = NivoTouch.min)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun TemplateCard(type: PageletType, selected: Boolean, taken: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    // The chosen card rises a little and gains its ring; both ease rather than jump.
    val lift by animateDpAsState(if (selected && !motion.reduced) (-6).dp else 0.dp, motion.spring(1f), label = "templateLift")
    val ring by animateDpAsState(if (selected) 3.dp else 1.dp, motion.spring(1f), label = "templateRing")
    val shape = RoundedCornerShape(NivoRadius.card + 4.dp)
    val enabled = type.available && !taken
    val press = remember { MutableInteractionSource() }
    val onPreview = if (type == PageletType.SKY) Color.White else scheme.onSurface
    Box(
        Modifier
            .offset(y = lift)
            .pressFeedback(press)
            .size(132.dp, 262.dp)
            .clip(shape)
            .border(ring, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .alpha(if (enabled) 1f else 0.55f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = press, indication = LocalIndication.current, onClick = onClick)
            .semantics { contentDescription = type.title + if (taken) ", already added" else if (type.available) ", " + type.tagline else ", coming soon" },
    ) {
        if (type == PageletType.SKY) SkyPreview() else if (type == PageletType.HEADLINES) HeadlinesPreview()
        else if (type == PageletType.AGENDA) AgendaPreview() else if (type == PageletType.NOW_PLAYING) NowPlayingPreview() else if (type == PageletType.MOVE) MovePreview() else Box(Modifier.matchParentSizeCompat().background(scheme.surfaceContainerHigh))
        // A soft scrim under the name, so it reads over any preview.
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(96.dp).background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, if (type == PageletType.SKY) Color.Black.copy(alpha = 0.35f) else scheme.surface.copy(alpha = 0.9f)),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(type.title, color = onPreview, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (taken) "Already added" else if (type.available) type.tagline else "Coming soon",
                color = onPreview.copy(alpha = NivoAlpha.medium), style = MaterialTheme.typography.labelSmall,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = NivoSpace.xxs),
            )
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).background(scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = scheme.onPrimary) }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().height(262.dp))

/** A small painted preview of the Sky: gradient, horizon, an arc and the sun. */
@Composable
private fun SkyPreview() {
    val p = SkyPalettes.Golden
    Canvas(Modifier.size(132.dp, 262.dp)) {
        drawRect(Brush.verticalGradient(0f to p.sky0, 0.36f to p.sky1, 0.547f to p.sky2, 0.61f to p.ground, 1f to p.groundEnd))
        val cx = size.width / 2f; val hy = size.height * 0.547f; val r = size.width * 0.42f
        drawArc(Color.White.copy(alpha = 0.3f), 180f, 180f, false, Offset(cx - r, hy - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        drawCircle(p.sunC, 6.dp.toPx(), Offset(cx + r * 0.7f, hy - r * 0.7f))
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, hy), Offset(size.width, hy), 1f)
    }
}

@Composable
private fun SkyOptions(config: PageletConfig, onChange: (PageletConfig) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SectionLabel("Show on this Pagelet")
    FlowRow(Modifier.padding(horizontal = NivoSpace.xl), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
        SkySection.entries.forEach { section ->
            FilterChip(
                selected = config.has(section),
                onClick = {
                    val next = if (config.has(section)) config.sections - section.key else config.sections + section.key
                    onChange(config.copy(sections = next))
                },
                label = { Text(section.label) },
                leadingIcon = if (config.has(section)) {{ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }} else null,
            )
        }
    }

    WholePageRow(config, onChange)

    if (config.has(SkySection.WELLBEING)) {
        UsageAccessCard()
        GoalStepper(
            "Daily screen-time goal", com.snapkode.nivolauncher.ui.pagelet.sky.SkyInsight.formatMin(config.goalMinutes),
            onMinus = { onChange(config.copy(goalMinutes = (config.goalMinutes - 15).coerceAtLeast(15))) },
            onPlus = { onChange(config.copy(goalMinutes = (config.goalMinutes + 15).coerceAtMost(24 * 60))) },
        )
    }
    if (config.has(SkySection.CALENDAR)) CalendarAccessCard()
    if (config.has(SkySection.STEPS)) {
        StepsAccessCard()
        GoalStepper(
            "Daily step goal", "%,d steps".format(config.stepGoal),
            onMinus = { onChange(config.copy(stepGoal = (config.stepGoal - 500).coerceAtLeast(500))) },
            onPlus = { onChange(config.copy(stepGoal = (config.stepGoal + 500).coerceAtMost(100_000))) },
        )
    }

}

/** "Use the whole page": hide the dock and search bar while this Pagelet is showing. */
@Composable
private fun WholePageRow(config: PageletConfig, onChange: (PageletConfig) -> Unit) {
    // The whole row toggles, so the label is part of the target and is read with the switch.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = config.hideDock, role = Role.Switch, onValueChange = { onChange(config.copy(hideDock = it)) })
            .padding(horizontal = NivoSpace.xl, vertical = NivoSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = NivoSpace.l)) {
            Text("Use the whole page", style = MaterialTheme.typography.titleSmall)
            Text(
                "Hide the dock and search bar on this page",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = config.hideDock, onCheckedChange = null)
    }
}

@Composable
private fun HeadlinesPageletOptions(config: PageletConfig, onChange: (PageletConfig) -> Unit, checkFeed: suspend (String) -> Pair<String, Int>?) {
    WholePageRow(config, onChange)
    com.snapkode.nivolauncher.ui.HeadlinesOptions(config.news, onChange = { t -> onChange(config.copy(news = t(config.news))) }, checkFeed = checkFeed)
}

/** A small painted preview of Headlines: a title, a few chips and cards. */
@Composable
private fun HeadlinesPreview() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(132.dp, 262.dp)) {
        drawRect(scheme.surface)
        val pad = 12.dp.toPx()
        drawRoundRect(scheme.onSurface.copy(alpha = 0.8f), Offset(pad, 28.dp.toPx()), androidx.compose.ui.geometry.Size(56.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        repeat(3) { i ->
            drawRoundRect(if (i == 0) scheme.primaryContainer else scheme.surfaceContainerHigh, Offset(pad + i * 34.dp.toPx(), 46.dp.toPx()), androidx.compose.ui.geometry.Size(30.dp.toPx(), 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        }
        var y = 72.dp.toPx()
        repeat(2) { i ->
            val h = if (i == 0) 72.dp.toPx() else 40.dp.toPx()
            drawRoundRect(scheme.surfaceContainer, Offset(pad, y), androidx.compose.ui.geometry.Size(size.width - 2 * pad, h), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            drawRoundRect(scheme.onSurface.copy(alpha = 0.25f), Offset(pad + 8.dp.toPx(), y + h - 16.dp.toPx()), androidx.compose.ui.geometry.Size(size.width * 0.5f, 5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            y += h + 8.dp.toPx()
        }
    }
}

/** A small heading over a group of options, with room above it to separate it from what came before. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = NivoSpace.xl, end = NivoSpace.xl, top = NivoSpace.s, bottom = NivoSpace.xs).semantics { heading() },
    )
}

/** The same title block on every Pagelet sheet. */
@Composable
private fun SheetTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = NivoSpace.xl, end = NivoSpace.xl, top = NivoSpace.s, bottom = NivoSpace.xs).semantics { heading() },
    )
}

/** Options for the kinds that need only a few choices: the whole-page switch and what is particular to them. */
@Composable
private fun SimplePageletOptions(type: PageletType, config: PageletConfig, onChange: (PageletConfig) -> Unit) {
    WholePageRow(config, onChange)
    when (type) {
        PageletType.AGENDA -> SwitchRow("Show finished events", "Keep today's events that are over in the list, dimmed", config.showPast) { onChange(config.copy(showPast = it)) }
        PageletType.MOVE -> {
            SectionLabel("Show on this Pagelet")
            FlowRow(Modifier.padding(horizontal = NivoSpace.xl), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                com.snapkode.nivolauncher.pagelet.MoveSection.entries.forEach { section ->
                    FilterChip(
                        selected = config.has(section),
                        onClick = { onChange(config.copy(sections = if (config.has(section)) config.sections - section.key else config.sections + section.key)) },
                        label = { Text(section.label) },
                        leadingIcon = if (config.has(section)) {{ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }} else null,
                    )
                }
            }
            GoalStepper(
                "Daily step goal", "%,d steps".format(config.stepGoal),
                onMinus = { onChange(config.copy(stepGoal = (config.stepGoal - 500).coerceAtLeast(500))) },
                onPlus = { onChange(config.copy(stepGoal = (config.stepGoal + 500).coerceAtMost(100_000))) },
            )
            GoalStepper(
                "Sleep goal", com.snapkode.nivolauncher.pagelet.move.MoveFormat.duration(config.sleepGoalMin),
                onMinus = { onChange(config.copy(sleepGoalMin = (config.sleepGoalMin - 15).coerceAtLeast(60))) },
                onPlus = { onChange(config.copy(sleepGoalMin = (config.sleepGoalMin + 15).coerceAtMost(16 * 60))) },
            )
        }
        else -> Unit
    }
}

@Composable
private fun SwitchRow(title: String, line: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = NivoSpace.xl, vertical = NivoSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = NivoSpace.l)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Small painted previews for the gallery cards. */
@Composable
private fun AgendaPreview() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(132.dp, 262.dp)) {
        drawRect(scheme.surface)
        val pad = 12.dp.toPx()
        drawRoundRect(scheme.onSurface.copy(alpha = 0.8f), Offset(pad, 28.dp.toPx()), androidx.compose.ui.geometry.Size(52.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        val colors = listOf(Color(0xFF4F7CFF), Color(0xFF34B26F), Color(0xFFE89A3C))
        var y = 56.dp.toPx()
        colors.forEachIndexed { i, c ->
            drawRoundRect(scheme.onSurface.copy(alpha = 0.25f), Offset(pad, y + 6.dp.toPx()), androidx.compose.ui.geometry.Size(16.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            val h = (30 + i * 6).dp.toPx()
            drawRoundRect(scheme.surfaceContainer, Offset(pad + 24.dp.toPx(), y), androidx.compose.ui.geometry.Size(size.width - 2 * pad - 24.dp.toPx(), h), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            drawRoundRect(c, Offset(pad + 30.dp.toPx(), y + 6.dp.toPx()), androidx.compose.ui.geometry.Size(3.dp.toPx(), h - 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            y += h + 8.dp.toPx()
            if (i == 0) {
                drawCircle(scheme.primary, 3.dp.toPx(), Offset(pad + 22.dp.toPx(), y - 4.dp.toPx()))
                drawLine(scheme.primary, Offset(pad + 26.dp.toPx(), y - 4.dp.toPx()), Offset(size.width - pad, y - 4.dp.toPx()), 1.dp.toPx())
                y += 6.dp.toPx()
            }
        }
    }
}

@Composable
private fun NowPlayingPreview() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(132.dp, 262.dp)) {
        drawRect(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surface)))
        val pad = 18.dp.toPx()
        val side = size.width - 2 * pad
        drawRoundRect(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)), Offset(pad, 34.dp.toPx()), androidx.compose.ui.geometry.Size(side, side), androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
        drawRoundRect(scheme.onSurface.copy(alpha = 0.75f), Offset(pad, 34.dp.toPx() + side + 12.dp.toPx()), androidx.compose.ui.geometry.Size(side * 0.7f, 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawRoundRect(scheme.onSurface.copy(alpha = 0.3f), Offset(pad, 34.dp.toPx() + side + 24.dp.toPx()), androidx.compose.ui.geometry.Size(side * 0.45f, 5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        val cy = 34.dp.toPx() + side + 44.dp.toPx()
        drawLine(scheme.onSurface.copy(alpha = 0.2f), Offset(pad, cy), Offset(size.width - pad, cy), 2.dp.toPx())
        drawLine(scheme.primary, Offset(pad, cy), Offset(pad + side * 0.4f, cy), 2.dp.toPx())
        drawCircle(scheme.primary, 14.dp.toPx(), Offset(size.width / 2f, cy + 22.dp.toPx()))
    }
}

@Composable
private fun MovePreview() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(132.dp, 262.dp)) {
        drawRect(scheme.surface)
        val pad = 12.dp.toPx()
        drawRoundRect(scheme.onSurface.copy(alpha = 0.8f), Offset(pad, 28.dp.toPx()), androidx.compose.ui.geometry.Size(36.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        val cardW = size.width - 2 * pad
        drawRoundRect(scheme.surfaceContainer, Offset(pad, 50.dp.toPx()), androidx.compose.ui.geometry.Size(cardW, 70.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        drawArc(scheme.primary.copy(alpha = 0.2f), 0f, 360f, false, Offset(pad + 8.dp.toPx(), 58.dp.toPx()), androidx.compose.ui.geometry.Size(54.dp.toPx(), 54.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx()))
        drawArc(scheme.primary, -90f, 230f, false, Offset(pad + 8.dp.toPx(), 58.dp.toPx()), androidx.compose.ui.geometry.Size(54.dp.toPx(), 54.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawRoundRect(scheme.surfaceContainer, Offset(pad, 128.dp.toPx()), androidx.compose.ui.geometry.Size(cardW, 38.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        drawRoundRect(scheme.tertiary, Offset(pad + 8.dp.toPx(), 154.dp.toPx()), androidx.compose.ui.geometry.Size(cardW * 0.6f, 5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawRoundRect(scheme.surfaceContainer, Offset(pad, 174.dp.toPx()), androidx.compose.ui.geometry.Size(cardW, 38.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        drawCircle(Color(0xFFE5484D), 6.dp.toPx(), Offset(pad + 16.dp.toPx(), 193.dp.toPx()))
    }
}
