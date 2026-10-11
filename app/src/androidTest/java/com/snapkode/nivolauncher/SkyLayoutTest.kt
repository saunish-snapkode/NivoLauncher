package com.snapkode.nivolauncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.ui.pagelet.sky.FakeSkyProvider
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyContent
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyController
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyScenes
import com.snapkode.nivolauncher.ui.pagelet.sky.skyLookFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Sky's foreground at the screen sizes and text sizes people really have. The page the user reported on had a bigger
 * display scale than the emulator: the weather block ran into the arc and the credit sat on the apps row. These render the
 * real composable and measure it.
 */
@RunWith(AndroidJUnit4::class)
class SkyLayoutTest {
    @get:Rule val rule = createComposeRule()

    private class Case(val name: String, val wDp: Int, val hDp: Int, val density: Float, val fontScale: Float)

    private val cases = listOf(
        Case("360x800 @1.0", 360, 800, 2.625f, 1.0f),
        Case("360x800 @1.3 font", 360, 800, 2.625f, 1.3f),
        Case("411x891 @1.15", 411, 891, 2.625f, 1.15f),
        Case("393x851 @1.3", 393, 851, 2.75f, 1.3f),
        Case("360x740 @1.3", 360, 740, 3.0f, 1.3f),
        Case("320x640 @1.0", 320, 640, 2.0f, 1.0f),
        Case("360x800 @2.0 font", 360, 800, 2.625f, 2.0f),
    )

    private fun check(c: Case) {
        val problems = ArrayList<String>()
        var ctlRef: SkyController? = null
        rule.setContent {
            val scope = rememberCoroutineScope()
            val ctl = androidx.compose.runtime.remember { SkyController(scope, reducedMotion = true).also { ctlRef = it } }
            val data = androidx.compose.runtime.remember { FakeSkyProvider().snapshot() }
            androidx.compose.runtime.LaunchedEffect(Unit) { ctl.update(data, skyLookFor(data)) }
            CompositionLocalProvider(LocalDensity provides Density(c.density, c.fontScale)) {
                Box(Modifier.size(c.wDp.dp, c.hDp.dp)) {
                    SkyContent(
                        ctl, PageletConfig(), active = false, reducedMotion = true, lowLoad = true,
                        onOptions = {}, onActivityLog = {}, onAttribution = {},
                        topInset = 28.dp, bottomInset = 56.dp,
                    )
                }
            }
        }
        rule.waitForIdle(); rule.waitForIdle()
        val top = rule.onNodeWithTag("sky-top", useUnmergedTree = true).getBoundsInRoot()
        val ground = rule.onNodeWithTag("sky-ground", useUnmergedTree = true).getBoundsInRoot()
        val footer = rule.onNodeWithTag("sky-footer", useUnmergedTree = true).getBoundsInRoot()
        val d = c.density
        val horizon = c.hDp * d * SkyScenes.HORIZON
        val arcTopDp = (horizon - ctlRef!!.arcRadiusPx) / d
        // 1. The clock and weather block ends above the top of the arc (with room for the NOON/NOW labels).
        if (top.bottom.value > arcTopDp - 20f) problems += "${c.name}: weather block bottom ${top.bottom.value}dp reaches the arc top ${arcTopDp}dp"
        // 2. The footer (the MET credit) is inside the ground area, which itself ends above the system bar.
        if (footer.bottom.value > ground.bottom.value + 0.5f) problems += "${c.name}: footer bottom ${footer.bottom.value} spills past the ground ${ground.bottom.value}"
        if (ground.bottom.value > c.hDp - 56f + 0.5f) problems += "${c.name}: ground reaches into the navigation bar"
        // 3. The ground starts at the horizon band, not above it.
        if (ground.top.value < (horizon / d) - 1f) problems += "${c.name}: ground starts above the horizon"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun case0_360x800__1_0() = check(cases[0])
    @Test fun case1_360x800__1_3_font() = check(cases[1])
    @Test fun case2_411x891__1_15() = check(cases[2])
    @Test fun case3_393x851__1_3() = check(cases[3])
    @Test fun case4_360x740__1_3() = check(cases[4])
    @Test fun case5_320x640__1_0() = check(cases[5])
    @Test fun case6_360x800__2_0_font() = check(cases[6])
}
