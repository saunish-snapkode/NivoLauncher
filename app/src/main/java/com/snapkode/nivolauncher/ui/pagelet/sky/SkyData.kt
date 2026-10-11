package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snapkode.nivolauncher.pagelet.PageletDataProvider
import com.snapkode.nivolauncher.pagelet.PageletUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar

data class SkyWeather(
    val city: String,
    /** MET Norway `symbol_code`, e.g. `partlycloudy_day`. */
    val symbolCode: String,
    val tempC: Double, val feelsC: Double, val highC: Double, val lowC: Double,
)

/** One app's time today. [packageName] lets the page show the real icon from the launcher's own list. */
data class SkyAppUsage(val packageName: String?, val label: String, val minutes: Int)

data class SkyWellbeing(
    /** Minutes of screen time per local hour, always 24 entries. */
    val hourlyMinutes: List<Int>,
    val unlocks: Int,
    /** Hour of day (e.g. 6.5) of the first unlock after 4 AM, or null. */
    val upSinceHour: Float?,
    val longestSessionMin: Int,
    val topApps: List<SkyAppUsage>,
    val yesterdayTotalMin: Int?,
) {
    val totalMin: Int get() = hourlyMinutes.sum()
}

/** Why [SkyData.weather] is what it is. */
enum class WeatherStatus {
    /** Real forecast, current. */
    Live,

    /** Real forecast, but the last refresh failed or is overdue. */
    Stale,

    /** No place chosen and no permission to use the phone's position. */
    NeedsLocation,

    /** Nothing cached and nothing could be fetched. */
    Unavailable,

    /** Made-up numbers (the fake provider). */
    Sample,
}

/** The state of one of the other sections (wellbeing, calendar, steps). */
enum class SectionStatus {
    /** Not read yet (first frame) or switched off. */
    Idle,
    Ready,

    /** The user hasn't allowed it. The page says so, in words, and offers the button. */
    NeedsPermission,

    /** Health Connect isn't installed (Android 13 and older). */
    NotInstalled,

    /** This phone can't do it. */
    Unsupported,
    Unavailable,

    /** Made-up numbers (the fake provider). */
    Sample,
}

data class SkyData(
    /** Minutes since local midnight. */
    val nowMin: Int,
    val sunriseMin: Int,
    val sunsetMin: Int,
    val weather: SkyWeather?,
    val wellbeing: SkyWellbeing?,
    val weatherStatus: WeatherStatus = WeatherStatus.Sample,
    val wellbeingStatus: SectionStatus = SectionStatus.Sample,
    /** Today's events still to come or in progress, soonest first. */
    val calendar: List<com.snapkode.nivolauncher.pagelet.calendar.CalEvent> = emptyList(),
    val calendarStatus: SectionStatus = SectionStatus.Idle,
    /** Steps so far today. */
    val steps: Long? = null,
    val stepsStatus: SectionStatus = SectionStatus.Idle,
) {
    val weatherIsSample get() = weatherStatus == WeatherStatus.Sample
    val wellbeingIsSample get() = wellbeingStatus == SectionStatus.Sample
}

/** Debug-only override of the scene, so every phase × weather can be looked at without waiting for it. Null = real. */
object SkyDebug {
    var phase by mutableStateOf<TimePhase?>(null)
    var layer by mutableStateOf<WeatherLayer?>(null)

    fun nextPhase() { phase = TimePhase.entries.let { e -> e[((phase?.ordinal ?: -1) + 1) % e.size] } }
    fun nextLayer() { layer = WeatherLayer.entries.let { e -> e[((layer?.ordinal ?: -1) + 1) % e.size] } }
    fun clear() { phase = null; layer = null }
}

/** A stand-in data source with believable numbers, used until the real weather and wellbeing providers exist. */
class FakeSkyProvider(private val clock: () -> Calendar = { Calendar.getInstance() }) : PageletDataProvider<SkyData> {
    fun snapshot(): SkyData {
        val c = clock()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val nowHour = now / 60
        // A plausible day: quiet night, a morning peak, a lull, an afternoon bump.
        val pattern = intArrayOf(0, 0, 0, 0, 0, 0, 6, 22, 34, 26, 18, 31, 12, 9, 14, 21, 17, 13, 24, 19, 11, 6, 0, 0)
        val hourly = List(24) { h ->
            when {
                h < nowHour -> pattern[h]
                h == nowHour -> (pattern[h] * (now % 60) / 60f).toInt()
                else -> 0
            }
        }
        return SkyData(
            nowMin = now,
            sunriseMin = 6 * 60 + 28,
            sunsetMin = 18 * 60 + 21,
            weather = SkyWeather("Mumbai", "partlycloudy_day", 28.0, 31.0, 31.0, 24.0),
            wellbeing = SkyWellbeing(
                hourlyMinutes = hourly, unlocks = 38, upSinceHour = 6.5f, longestSessionMin = 42,
                topApps = listOf(SkyAppUsage(null, "Chrome", 54), SkyAppUsage(null, "Messages", 38), SkyAppUsage(null, "Maps", 21)),
                yesterdayTotalMin = (hourly.sum() + 18).coerceAtLeast(30),
            ),
            calendar = listOf(
                com.snapkode.nivolauncher.pagelet.calendar.CalEvent(1, "Design review", System.currentTimeMillis() + 40 * 60_000L, System.currentTimeMillis() + 100 * 60_000L, false, null, 0),
                com.snapkode.nivolauncher.pagelet.calendar.CalEvent(2, "Lunch with Asha", System.currentTimeMillis() + 3 * 3_600_000L, System.currentTimeMillis() + 4 * 3_600_000L, false, null, 0),
            ),
            calendarStatus = SectionStatus.Ready, steps = 6420, stepsStatus = SectionStatus.Sample,
        )
    }

    override val state: Flow<PageletUiState<SkyData>> = flow {
        while (true) {
            emit(PageletUiState.Ready(snapshot(), System.currentTimeMillis()))
            delay(60_000)
        }
    }

    override suspend fun refresh() = Unit
}

/** The look for [data] right now: the debug override if one is set, otherwise real time of day and real weather. */
fun skyLookFor(data: SkyData): SkyLook {
    val phase = SkyDebug.phase ?: SkyScenes.phaseAt(data.nowMin, data.sunriseMin, data.sunsetMin)
    val layer = SkyDebug.layer ?: data.weather?.let { SkyScenes.layerFor(it.symbolCode) } ?: WeatherLayer.Clear
    return SkyLook(phase, layer)
}
