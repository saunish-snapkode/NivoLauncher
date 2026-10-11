package com.snapkode.nivolauncher.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import com.snapkode.nivolauncher.data.ThemeSchedule
import com.snapkode.nivolauncher.data.ThemeScheduleDefaults
import com.snapkode.nivolauncher.data.ThemeScheduler
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.snapkode.nivolauncher.data.ColorStyle
import com.snapkode.nivolauncher.data.NivoPalettes
import com.snapkode.nivolauncher.ui.design.LocalHapticsEnabled
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.nivoColorScheme
import com.snapkode.nivolauncher.ui.design.nivoTypography
import com.snapkode.nivolauncher.ui.design.rememberMotion
import com.snapkode.nivolauncher.data.FontChoice
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.ThemeMode
import com.snapkode.nivolauncher.data.Tint

private val FallbackLight = lightColorScheme(primary = Color(0xFF3F51B5), secondary = Color(0xFF009688))
private val FallbackDark = darkColorScheme(primary = Color(0xFF9FA8DA), secondary = Color(0xFF80CBC4))

/** Font applied to labels that aren't drawn through Material text styles. */
val LocalAppFont = androidx.compose.runtime.compositionLocalOf<FontFamily?> { null }

@Composable
fun NivoTheme(viewModel: LauncherViewModel, content: @Composable () -> Unit) {
    val settings = viewModel.settings.collectAsState().value
    val systemDark = isSystemInDarkTheme()
    val scheduledDark = rememberScheduledDark(settings)
    val dark = when {
        // A schedule decides light vs dark; the chosen mode still picks the dark flavour (AMOLED or regular).
        scheduledDark != null -> scheduledDark
        settings.themeMode == ThemeMode.System -> systemDark
        settings.themeMode == ThemeMode.Light -> false
        else -> true
    }
    val amoled = settings.themeMode == ThemeMode.Amoled && dark
    val context = LocalContext.current
    val scheme = when (settings.colorStyle) {
        ColorStyle.Nivo -> nivoColorScheme(
            when {
                amoled -> NivoPalettes.Amoled
                dark -> NivoPalettes.Dark
                else -> NivoPalettes.Light
            },
            Color(settings.accent.argb),
        )
        ColorStyle.Custom -> accentScheme(Color(settings.accent.argb), dark)
        ColorStyle.Dynamic -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> FallbackDark
            else -> FallbackLight
        }
    }.let { if (amoled && settings.colorStyle != ColorStyle.Nivo) it.copy(background = Color.Black, surface = Color.Black) else it }

    val family = remember(settings.fontChoice, settings.customFontPath) {
        when (settings.fontChoice) {
            FontChoice.System -> null
            FontChoice.Serif -> FontFamily.Serif
            FontChoice.Mono -> FontFamily.Monospace
            FontChoice.Cursive -> FontFamily.Cursive
            FontChoice.Custom -> runCatching { FontFamily(Font(java.io.File(settings.customFontPath))) }.getOrNull()
        }
    }
    val typography = remember(family) { nivoTypography(family) }
    val motion = rememberMotion(settings.motionStyle)
    CompositionLocalProvider(
        LocalAppFont provides family,
        LocalMotion provides motion,
        LocalHapticsEnabled provides settings.hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = scheme.animated(motion),
            typography = typography,
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(com.snapkode.nivolauncher.ui.design.NivoRadius.inner),
                small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(com.snapkode.nivolauncher.ui.design.NivoRadius.control),
                large = androidx.compose.foundation.shape.RoundedCornerShape(com.snapkode.nivolauncher.ui.design.NivoRadius.card),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(com.snapkode.nivolauncher.ui.design.NivoRadius.sheet),
            ),
            content = content,
        )
    }
}

/** Null when no schedule is active; otherwise whether the dark theme applies right now. Re-evaluates at each boundary. */
@Composable
private fun rememberScheduledDark(settings: com.snapkode.nivolauncher.data.LauncherSettings): Boolean? {
    if (settings.themeSchedule == ThemeSchedule.Off) return null
    val (start, end) = when (settings.themeSchedule) {
        ThemeSchedule.Evening -> ThemeScheduleDefaults.EVENING_START to ThemeScheduleDefaults.MORNING_END
        else -> settings.scheduleStart to settings.scheduleEnd
    }
    fun now(): Int = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
    var dark by androidx.compose.runtime.remember(start, end) { androidx.compose.runtime.mutableStateOf(ThemeScheduler.isDark(now(), start, end)) }
    androidx.compose.runtime.LaunchedEffect(start, end) {
        while (true) {
            dark = ThemeScheduler.isDark(now(), start, end)
            val minutes = ThemeScheduler.minutesUntilChange(now(), start, end)
            kotlinx.coroutines.delay(minutes * 60_000L + 500L)
        }
    }
    return dark
}

/** Cross-fades the colours that make up most of the UI when the scheme changes, instead of snapping. */
@Composable
private fun ColorScheme.animated(motion: com.snapkode.nivolauncher.ui.design.NivoMotion): ColorScheme {
    val spec = if (motion.reduced) androidx.compose.animation.core.snap<Color>() else androidx.compose.animation.core.tween<Color>(420)
    @Composable fun a(c: Color) = androidx.compose.animation.animateColorAsState(c, spec, label = "scheme").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondary = a(secondary), secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        tertiary = a(tertiary), tertiaryContainer = a(tertiaryContainer), onTertiaryContainer = a(onTertiaryContainer),
        background = a(background), onBackground = a(onBackground),
        surface = a(surface), onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant), onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainerLow = a(surfaceContainerLow), surfaceContainer = a(surfaceContainer),
        surfaceContainerHigh = a(surfaceContainerHigh), surfaceContainerHighest = a(surfaceContainerHighest),
        outline = a(outline), outlineVariant = a(outlineVariant),
    )
}

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

/** Builds a full colour scheme around one accent colour, for light or dark mode. */
private fun accentScheme(accent: Color, dark: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val primary = if (dark) lerp(accent, Color.White, 0.4f) else accent
    fun tinted(c: Color, amount: Float) = lerp(c, accent, amount)
    return base.copy(
        primary = primary,
        onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = tinted(base.surface, if (dark) 0.4f else 0.2f),
        onPrimaryContainer = if (dark) Color.White else lerp(accent, Color.Black, 0.55f),
        secondary = primary,
        surface = tinted(base.surface, 0.05f),
        background = tinted(base.background, 0.05f),
        surfaceVariant = tinted(base.surfaceVariant, 0.1f),
        onSurfaceVariant = tinted(base.onSurfaceVariant, 0.1f),
        surfaceContainer = tinted(base.surfaceContainer, 0.08f),
        surfaceContainerHigh = tinted(base.surfaceContainerHigh, 0.1f),
        surfaceContainerHighest = tinted(base.surfaceContainerHighest, 0.12f),
    )
}

/** Resolves a user-chosen [Tint] to a concrete colour in the current theme. */
@Composable
fun Tint.color(): Color = when (this) {
    Tint.Black -> Color.Black
    Tint.White -> Color.White
    Tint.Accent -> MaterialTheme.colorScheme.primary
    Tint.Red -> Color(0xFFE53935)
}
