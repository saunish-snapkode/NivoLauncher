package com.snapkode.nivolauncher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.data.FontChoice
import com.snapkode.nivolauncher.data.IconShape
import com.snapkode.nivolauncher.data.ThemeMode
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoShapes
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.edgeFade
import com.snapkode.nivolauncher.ui.design.nivoCard
import com.snapkode.nivolauncher.ui.design.pressFeedback
import kotlin.math.roundToInt

/*
 * Row anatomy shared by every settings row: 20 dp side inset (the card's 16 dp gutter plus a hair, so text sits clear of the
 * rounded corners), a 56 dp minimum height, a medium-weight title over a quieter one-to-two line subtitle, and the trailing
 * control centred on the row.
 */
private val RowInset = 20.dp
private val RowMinHeight = 56.dp
private val LeadingIconBox = 40.dp

@Composable
private fun rowTitleStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = rowTitleStyle(), color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = NivoSpace.xxs),
            )
        }
    }
}

/** The tinted disc that holds a row's leading icon, so every icon sits on the same footprint. */
@Composable
private fun LeadingIcon(icon: ImageVector) {
    Box(
        Modifier.padding(end = NivoSpace.l).size(LeadingIconBox).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** A titled, rounded group of setting rows. */
@Composable
fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = NivoSpace.l, vertical = NivoSpace.s)) {
        if (title != null) SectionLabel(title, Modifier.padding(start = NivoSpace.s, bottom = NivoSpace.s, top = NivoSpace.s))
        Column(
            Modifier.fillMaxWidth()
                .nivoCard(NivoShapes.Card, MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.outlineVariant)
                .padding(vertical = NivoSpace.s),
            content = content,
        )
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        // One switch control for assistive tech, not a row plus a separate switch.
        Modifier.fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = RowInset, vertical = NivoSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f).padding(end = NivoSpace.l))
        Switch(
            checked = checked, onCheckedChange = null,
            thumbContent = if (checked) {
                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
            } else null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                checkedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RowInset, vertical = NivoSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LeadingIcon(icon)
        RowText(title, subtitle, Modifier.weight(1f))
    }
}

/**
 * A row that only explains something: same anatomy as [ActionRow], but not tappable, so it doesn't ripple or announce
 * itself as a button that does nothing.
 */
@Composable
fun InfoRow(title: String, subtitle: String? = null, icon: ImageVector? = null) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = RowInset, vertical = NivoSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LeadingIcon(icon)
        RowText(title, subtitle, Modifier.weight(1f))
    }
}

/** A slider that reports every change, so previews update while you drag. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(start = RowInset, end = RowInset, top = NivoSpace.s, bottom = NivoSpace.xs).semantics(mergeDescendants = true) {
        contentDescription = title
        stateDescription = valueText
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = rowTitleStyle(), modifier = Modifier.weight(1f))
            // The value sits in a quiet pill so it reads as a readout, not as another label.
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, NivoShapes.Pill)
                    .padding(horizontal = NivoSpace.s + NivoSpace.xxs, vertical = NivoSpace.xxs),
            )
        }
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps,
            interactionSource = interaction,
            // A round thumb on a slim rounded track reads as one calm control, not a bar with a notch.
            thumb = {
                androidx.compose.material3.SliderDefaults.Thumb(
                    interactionSource = interaction,
                    thumbSize = androidx.compose.ui.unit.DpSize(22.dp, 22.dp),
                )
            },
            track = { state ->
                androidx.compose.material3.SliderDefaults.Track(
                    sliderState = state,
                    modifier = Modifier.padding(vertical = 0.dp),
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 5.dp,
                    drawStopIndicator = null,
                )
            },
        )
    }
}

@Composable
fun IntSliderRow(title: String, value: Int, range: IntRange, unit: String = "", onChange: (Int) -> Unit) =
    SliderRow(
        title, "$value$unit", value.toFloat(), range.first.toFloat()..range.last.toFloat(),
        // Tick marks only help on short ranges; long ones just look noisy.
        steps = if (range.last - range.first <= 8) range.last - range.first - 1 else 0,
    ) { onChange(it.roundToInt()) }

@Composable
fun PercentSliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) =
    SliderRow(title, "${(value * 100).roundToInt()}%", value, range, onChange = onChange)

/** A row showing the current choice that opens a menu of options. */
@Composable
fun <T> OptionRow(title: String, selected: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .clickable(role = Role.DropdownList) { open = true }
                .semantics { stateDescription = label(selected) }
                .padding(start = RowInset, end = RowInset - NivoSpace.xs, top = NivoSpace.m, bottom = NivoSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = rowTitleStyle(), modifier = Modifier.weight(1f).padding(end = NivoSpace.m))
            Text(
                label(selected), style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            // A small caret says "this opens a list" before anyone taps.
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(24.dp), tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            color = if (isSelected) scheme.primary else scheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Medium else null,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = scheme.primary) }
                    } else null,
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun PickerTitle(text: String) {
    Text(
        text, style = rowTitleStyle(),
        modifier = Modifier.padding(start = RowInset, end = RowInset, top = NivoSpace.m, bottom = NivoSpace.s).semantics { heading() },
    )
}

/** Tiles showing each icon / folder shape; the chosen one is filled with the accent. */
@Composable
fun ShapePicker(title: String, selected: IconShape, onSelect: (IconShape) -> Unit) {
    PickerTitle(title)
    Row(
        Modifier.edgeFade().horizontalScroll(rememberScrollState()).padding(horizontal = NivoSpace.l, vertical = NivoSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.m),
    ) {
        IconShape.entries.forEach { shape ->
            val isSelected = shape == selected
            val press = remember { MutableInteractionSource() }
            val tileShape = RoundedCornerShape(18.dp)
            val tile by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "shapeTile",
            )
            val glyph by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                label = "shapeGlyph",
            )
            Column(
                Modifier.selectable(selected = isSelected, interactionSource = press, indication = null, role = Role.RadioButton) { onSelect(shape) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .pressFeedback(press)
                        .size(56.dp)
                        .clip(tileShape)
                        .background(tile)
                        .indication(press, ripple()),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(34.dp).background(glyph, MaskShape(shape)))
                }
                Text(
                    shape.label.substringBefore(' ').take(9), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = NivoSpace.xs),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    Spacer(Modifier.height(NivoSpace.s))
}

/** A row of colour dots; the chosen one gets a ring. */
@Composable
fun <T> SwatchPicker(title: String, options: List<Pair<T, Color>>, selected: T, onSelect: (T) -> Unit) {
    PickerTitle(title)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = NivoSpace.m, vertical = NivoSpace.xxs),
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.xs),
    ) {
        options.forEach { (value, color) ->
            val isSelected = value == selected
            val press = remember { MutableInteractionSource() }
            // A full 48 dp touch target around a 40 dp dot.
            Box(
                Modifier
                    .size(NivoTouch.min)
                    .pressFeedback(press)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, interactionSource = press, indication = ripple(), role = Role.RadioButton) { onSelect(value) }
                    .semantics { contentDescription = value.toString() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(5.dp)
                            else Modifier
                        )
                        .background(color, CircleShape)
                        // A hairline keeps white and black dots visible on cards of the same colour.
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
                )
            }
        }
    }
    Spacer(Modifier.height(NivoSpace.s))
}

@Composable
fun FontPicker(selected: FontChoice, onSelect: (FontChoice) -> Unit) {
    PickerTitle("Font")
    Row(
        Modifier.edgeFade().horizontalScroll(rememberScrollState()).padding(horizontal = NivoSpace.l, vertical = NivoSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.s + NivoSpace.xxs),
    ) {
        FontChoice.entries.forEach { choice ->
            val family = when (choice) {
                FontChoice.System, FontChoice.Custom -> FontFamily.Default
                FontChoice.Serif -> FontFamily.Serif
                FontChoice.Mono -> FontFamily.Monospace
                FontChoice.Cursive -> FontFamily.Cursive
            }
            val isSelected = choice == selected
            val press = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(18.dp)
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "fontTile",
            )
            Column(
                Modifier
                    .pressFeedback(press)
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, shape)
                    .selectable(selected = isSelected, interactionSource = press, indication = ripple(), role = Role.RadioButton) { onSelect(choice) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 18.dp, vertical = NivoSpace.s + NivoSpace.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Aa", fontFamily = family, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.headlineSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    choice.label.substringBefore(' '), style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(NivoSpace.s))
}

@Composable
fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    PickerTitle("Theme")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NivoSpace.l, vertical = NivoSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.s + NivoSpace.xxs),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            // Fixed preview colours on purpose: each tile shows that theme, whatever theme is on now.
            val (bg, fg) = when (mode) {
                ThemeMode.Light -> Color(0xFFF4F1FA) to Color(0xFF1D1B20)
                ThemeMode.Dark -> Color(0xFF1D1B20) to Color(0xFFE6E0E9)
                ThemeMode.System -> Color(0xFFF4F1FA) to Color(0xFF1D1B20)
                ThemeMode.Amoled -> Color(0xFF000000) to Color(0xFFE6E0E9)
            }
            val press = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(18.dp)
            val ring by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "themeRing",
            )
            Column(
                Modifier
                    .weight(1f)
                    .pressFeedback(press)
                    .clip(shape)
                    .border(if (isSelected) 2.dp else 1.dp, ring, shape)
                    .selectable(selected = isSelected, interactionSource = press, indication = ripple(), role = Role.RadioButton) { onSelect(mode) }
                    .padding(NivoSpace.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val previewShape = RoundedCornerShape(NivoRadius.control - 2.dp)
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(previewShape).background(bg)
                        .border(1.dp, Color.Black.copy(alpha = NivoAlpha.hairline), previewShape),
                ) {
                    if (mode == ThemeMode.System) {
                        Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f).fillMaxHeight().background(Color(0xFF1D1B20)))
                    }
                    Box(Modifier.align(Alignment.Center).size(width = 36.dp, height = 8.dp).background(fg.copy(alpha = 0.7f), NivoShapes.Pill))
                }
                Text(
                    if (mode == ThemeMode.Amoled) "AMOLED" else mode.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(top = NivoSpace.s - NivoSpace.xxs),
                )
            }
        }
    }
    Spacer(Modifier.height(NivoSpace.s))
}

/** Horizontally scrolling single-select chips, for short lists such as sort modes. */
@Composable
fun <T> ChipPicker(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    PickerTitle(title)
    Row(
        Modifier.edgeFade().horizontalScroll(rememberScrollState()).padding(horizontal = NivoSpace.l, vertical = NivoSpace.xxs),
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.s),
    ) {
        options.forEach { option ->
            androidx.compose.material3.FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1) },
                shape = RoundedCornerShape(16.dp),
                leadingIcon = if (option == selected) {
                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
    Spacer(Modifier.height(NivoSpace.s))
}


/**
 * "Wallpaper colours": each swatch is a disc split into the three seed roles (primary, secondary, tertiary)
 * of the scheme that accent would produce, so you see the whole palette, not just one dot.
 */
@Composable
fun AccentPicker(title: String, selected: com.snapkode.nivolauncher.data.AccentColor, onSelect: (com.snapkode.nivolauncher.data.AccentColor) -> Unit) {
    PickerTitle(title)
    val dark = com.snapkode.nivolauncher.data.NivoPalettes.Dark
    Row(
        Modifier.edgeFade().horizontalScroll(rememberScrollState()).padding(horizontal = NivoSpace.l, vertical = NivoSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.s + NivoSpace.xxs),
    ) {
        com.snapkode.nivolauncher.data.AccentColor.entries.forEach { accent ->
            val scheme = remember(accent) { com.snapkode.nivolauncher.ui.design.nivoColorScheme(dark, Color(accent.argb)) }
            val isSelected = accent == selected
            val press = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(24.dp)
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                label = "accentTile",
            )
            Box(
                Modifier
                    .size(68.dp)
                    .pressFeedback(press)
                    .clip(shape)
                    .background(bg)
                    .selectable(selected = isSelected, interactionSource = press, indication = ripple(), role = Role.RadioButton) { onSelect(accent) }
                    .semantics { contentDescription = accent.label },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(48.dp).clip(CircleShape)) {
                    drawRect(scheme.primary, size = androidx.compose.ui.geometry.Size(size.width, size.height / 2f))
                    drawRect(scheme.secondaryContainer.copy(alpha = 1f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), size = androidx.compose.ui.geometry.Size(size.width / 2f, size.height / 2f))
                    drawRect(scheme.tertiary, topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f), size = androidx.compose.ui.geometry.Size(size.width / 2f, size.height / 2f))
                }
                if (isSelected) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
    Spacer(Modifier.height(NivoSpace.s))
}

/** Small caps-style heading that sits above a card. Quiet by design: the cards carry the weight. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}
