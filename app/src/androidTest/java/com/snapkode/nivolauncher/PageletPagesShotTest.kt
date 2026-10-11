package com.snapkode.nivolauncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.calendar.CalEvent
import com.snapkode.nivolauncher.pagelet.move.HeartStats
import com.snapkode.nivolauncher.pagelet.move.MoveData
import com.snapkode.nivolauncher.pagelet.move.MoveResult
import com.snapkode.nivolauncher.ui.pagelet.AgendaBody
import com.snapkode.nivolauncher.ui.pagelet.MoveBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/**
 * Renders the self-drawn Pagelet pages with realistic data and saves a picture of each, so the data states (not just the
 * "allow access" ones a bare emulator shows) can be looked at. Also checks each renders without crashing.
 */
@RunWith(AndroidJUnit4::class)
class PageletPagesShotTest {
    @get:Rule val rule = createComposeRule()

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun at(h: Int, m: Int = 0) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }.timeInMillis

    @Test fun agendaWithEvents() {
        val events = listOf(
            CalEvent(1, "Company holiday", at(0), at(23, 59), true, null, 0xFF34B26F.toInt()),
            CalEvent(2, "Standup", at(9), at(9, 30), false, "Meet", 0xFF4F7CFF.toInt()),
            CalEvent(3, "Design review", at(11), at(12, 30), false, "Room 4", 0xFFE89A3C.toInt()),
            CalEvent(4, "Lunch with Asha", at(13), at(14), false, null, 0xFFB75CE0.toInt()),
            CalEvent(5, "1:1 with Ravi", at(13, 30), at(14, 15), false, null, 0xFF4F7CFF.toInt()),
            CalEvent(6, "Gym", at(18), at(19), false, "Cult.fit", 0xFFE5484D.toInt()),
        )
        val dayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        rule.setContent {
            MaterialTheme { Box(Modifier.size(412.dp, 860.dp)) {
                com.snapkode.nivolauncher.ui.pagelet.SurfaceWash(PageletType.AGENDA)
                AgendaBody(dayStart, at(12, 10), events, isToday = true, showPast = true, allowed = true, refused = false, dayOffset = 0, onDay = {}, onAsk = {}, onOptions = {}, is24 = false)
            } }
        }
        rule.waitForIdle(); save("agenda")
    }

    @Test fun nowPlayingWithCover() {
        val cover = android.graphics.Bitmap.createBitmap(400, 400, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val c = android.graphics.Canvas(this)
            c.drawColor(0xFF2B5876.toInt())
            c.drawCircle(200f, 200f, 140f, android.graphics.Paint().apply { color = 0xFFFFB347.toInt() })
        }
        val track = NowPlaying("Golden Hour", "JVKE", true, "com.spotify.music", "this is what ___ feels like", cover, 209_000, 83_000, System.currentTimeMillis())
        rule.setContent { MaterialTheme { Box(Modifier.size(412.dp, 860.dp)) { com.snapkode.nivolauncher.ui.pagelet.SurfaceWash(PageletType.NOW_PLAYING); com.snapkode.nivolauncher.ui.pagelet.NowPlayingBody(track, true, "Spotify", {}, {}) } } }
        rule.waitForIdle(); save("nowplaying")
    }

    @Test fun moveWithData() {
        val data = MoveData(
            steps = 6420, week = listOf(8200, 10400, 3100, 9800, 12000, 7400, 6420), sleepMin = 432,
            heart = HeartStats(72, 76, 54, 131), canReadSteps = true, canReadSleep = true, canReadHeart = true,
        )
        rule.setContent {
            MaterialTheme { Box(Modifier.size(412.dp, 1100.dp)) {
                com.snapkode.nivolauncher.ui.pagelet.SurfaceWash(PageletType.MOVE)
                MoveBody(PageletConfig.defaultsFor(PageletType.MOVE), MoveResult.Ready(data), {}, {}, {})
            } }
        }
        rule.waitForIdle(); save("move")
    }

    @Test fun emptyStates() {
        rule.setContent {
            MaterialTheme { androidx.compose.foundation.layout.Row {
                Box(Modifier.size(412.dp, 860.dp)) { com.snapkode.nivolauncher.ui.pagelet.NowPlayingBody(null, true, null, {}, {}) }
                Box(Modifier.size(412.dp, 860.dp)) {
                    AgendaBody(System.currentTimeMillis(), System.currentTimeMillis(), emptyList(), true, true, true, false, 0, {}, {}, {}, false)
                }
            } }
        }
        rule.waitForIdle(); save("empty")
    }

    @Test fun moveDarkTheme() {
        val data = MoveData(9300, listOf(8200, 10400, 3100, 9800, 12000, 7400, 9300), null, HeartStats(64, 70, 51, 118), true, true, true)
        rule.setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) { Box(Modifier.size(412.dp, 1100.dp)) {
                MoveBody(PageletConfig.defaultsFor(PageletType.MOVE), MoveResult.Ready(data), {}, {}, {})
            } }
        }
        rule.waitForIdle(); save("move_dark")
    }

    @Test fun loadingScreen() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Row {
                Box(Modifier.size(300.dp, 600.dp)) { com.snapkode.nivolauncher.ui.design.NivoLoadingScreen(dark = false) }
                Box(Modifier.size(300.dp, 600.dp)) { com.snapkode.nivolauncher.ui.design.NivoLoadingScreen(dark = true) }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        save("loading")
    }
}
