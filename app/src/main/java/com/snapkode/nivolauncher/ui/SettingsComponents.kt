package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.ui.design.edgeFade
import com.snapkode.nivolauncher.ui.design.nivoCard
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import com.snapkode.nivolauncher.data.FontChoice
import com.snapkode.nivolauncher.data.IconRenderer
import com.snapkode.nivolauncher.data.IconShape
import com.snapkode.nivolauncher.data.ThemeMode
import kotlin.math.roundToInt

/** A titled, rounded group of setting rows. */
@Composable
fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) SectionLabel(title, Modifier.padding(start = 8.dp, bottom = 8.dp, top = 4.dp))
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth().nivoCard(shape, MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.outlineVariant).padding(vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        // One switch control for assistive tech, not a row plus a separate switch.
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked, onCheckedChange = null,
            thumbContent = if (checked) {
                { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, null, Modifier.size(16.dp)) }
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).semantics(mergeDescendants = true) {
        contentDescription = title
        stateDescription = valueText
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
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
    Box {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = androidx.compose.ui.semantics.Role.DropdownList) { open = true }
                .semantics { stateDescription = label(selected) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun PickerTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
}

/** Tiles showing each icon / folder shape; the chosen one is outlined. */
@Composable
fun ShapePicker(title: String, selected: IconShape, onSelect: (IconShape) -> Unit) {
    PickerTitle(title)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconShape.entries.forEach { shape ->
            val isSelected = shape == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelect(shape) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(34.dp).background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            MaskShape(shape),
                        )
                    )
                }
                Text(
                    shape.label.substringBefore(' ').take(9), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Box(Modifier.padding(bottom = 8.dp))
}

/** A row of colour dots; the chosen one gets a ring. */
@Composable
fun <T> SwatchPicker(title: String, options: List<Pair<T, Color>>, selected: T, onSelect: (T) -> Unit) {
    PickerTitle(title)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        options.forEach { (value, color) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .size(42.dp)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .padding(if (isSelected) 5.dp else 0.dp)
                    .background(color, CircleShape)
                    .clickable { onSelect(value) },
            )
        }
    }
    Box(Modifier.padding(bottom = 8.dp))
}

@Composable
fun FontPicker(selected: FontChoice, onSelect: (FontChoice) -> Unit) {
    PickerTitle("Font")
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FontChoice.entries.forEach { choice ->
            val family = when (choice) {
                FontChoice.System, FontChoice.Custom -> FontFamily.Default
                FontChoice.Serif -> FontFamily.Serif
                FontChoice.Mono -> FontFamily.Monospace
                FontChoice.Cursive -> FontFamily.Cursive
            }
            val isSelected = choice == selected
            Column(
                Modifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(18.dp),
                    )
                    .clickable { onSelect(choice) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aa", fontFamily = family, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.headlineSmall)
                Text(choice.label.substringBefore(' '), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Box(Modifier.padding(bottom = 8.dp))
}

@Composable
fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    PickerTitle("Theme")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val (bg, fg) = when (mode) {
                ThemeMode.Light -> Color(0xFFF4F1FA) to Color(0xFF1D1B20)
                ThemeMode.Dark -> Color(0xFF1D1B20) to Color(0xFFE6E0E9)
                ThemeMode.System -> Color(0xFFF4F1FA) to Color(0xFF1D1B20)
                ThemeMode.Amoled -> Color(0xFF000000) to Color(0xFFE6E0E9)
            }
            Column(
                Modifier
                    .weight(1f)
                    .border(
                        if (isSelected) 3.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(18.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().size(width = 10.dp, height = 56.dp).background(bg, RoundedCornerShape(12.dp))) {
                    if (mode == ThemeMode.System) {
                        Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f).size(height = 56.dp, width = 10.dp).background(Color(0xFF1D1B20), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)))
                    }
                    Box(Modifier.align(Alignment.Center).size(width = 36.dp, height = 8.dp).background(fg.copy(alpha = 0.7f), RoundedCornerShape(4.dp)))
                }
                Text(mode.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    Box(Modifier.padding(bottom = 8.dp))
}

/** Horizontally scrolling single-select chips, for short lists such as sort modes. */
@Composable
fun <T> ChipPicker(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    PickerTitle(title)
    Row(
        Modifier.edgeFade().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            androidx.compose.material3.FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                shape = RoundedCornerShape(16.dp),
                leadingIcon = if (option == selected) {
                    { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
    Box(Modifier.padding(bottom = 8.dp))
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
        Modifier.edgeFade().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        com.snapkode.nivolauncher.data.AccentColor.entries.forEach { accent ->
            val scheme = remember(accent) { com.snapkode.nivolauncher.ui.design.nivoColorScheme(dark, Color(accent.argb)) }
            val isSelected = accent == selected
            Box(
                Modifier
                    .size(68.dp)
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, RoundedCornerShape(24.dp))
                    .clickable { onSelect(accent) }
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
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
    Box(Modifier.padding(bottom = 8.dp))
}

/** Small caps-style heading that sits above a card. Quiet by design: the cards carry the weight. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
