package com.snapkode.nivolauncher.pagelet.weather

import com.snapkode.nivolauncher.pagelet.PageletDataProvider
import com.snapkode.nivolauncher.pagelet.PageletUiState
import com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyData
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyWellbeing
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyWeather
import com.snapkode.nivolauncher.ui.pagelet.sky.WeatherStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.TimeZone

/**
 * The Sky's data: real weather from MET Norway (cached, refreshed only while the Sky is on screen) and, until the
 * Usage-access provider exists, sample wellbeing numbers that the page labels as such.
 */
class LiveSkyProvider(
    private val repo: SkyWeatherRepository,
    private val wellbeing: com.snapkode.nivolauncher.pagelet.wellbeing.WellbeingSource? = null,
    private val calendar: (nowMs: Long) -> Pair<SectionStatus, List<com.snapkode.nivolauncher.pagelet.calendar.CalEvent>> = { SectionStatus.Idle to emptyList() },
    private val steps: suspend (startOfDayMs: Long, nowMs: Long) -> Pair<SectionStatus, Long?> = { _, _ -> SectionStatus.Idle to null },
    /** Which sections any Sky currently shows: nothing is read for a section that is switched off. */
    private val wanted: () -> Set<String> = { emptySet() },
    /** The full lookup (may geocode): only ever called off the main thread. */
    private val location: () -> WeatherLocation?,
    /** The instant lookup, never blocking: used for the first frame. */
    private val quickLocation: () -> WeatherLocation? = location,
    private val clock: () -> Calendar = { Calendar.getInstance() },
) : PageletDataProvider<SkyData> {
    private val kicks = Channel<Unit>(Channel.CONFLATED)

    /** For the options sheet: when the forecast was last fetched. */
    val weatherState get() = repo.state

    /** Re-check now: the place changed, permission was granted, or the launcher came back to the front. */
    fun kick() { kicks.trySend(Unit) }

    // The last thing read for each section, so the first frame after a restart isn't empty and a slow read never blanks the page.
    @Volatile private var lastWellbeing: Pair<SectionStatus, SkyWellbeing?> = SectionStatus.Idle to null
    @Volatile private var lastCalendar: Pair<SectionStatus, List<com.snapkode.nivolauncher.pagelet.calendar.CalEvent>> = SectionStatus.Idle to emptyList()
    @Volatile private var lastSteps: Pair<SectionStatus, Long?> = SectionStatus.Idle to null

    /** What can be drawn instantly, with no network: the cache (or the reason there isn't one). */
    fun snapshot(): SkyData = quickLocation().let { repo.showCached(it); build(it) }

    override val state: Flow<PageletUiState<SkyData>> = flow {
        while (true) {
            val loc = location()
            repo.showCached(loc)
            emit(PageletUiState.Ready(build(loc), System.currentTimeMillis()))
            readSections()
            emit(PageletUiState.Ready(build(loc), System.currentTimeMillis()))
            repo.refreshIfDue(loc)            // network, off the main thread inside; honours Expires and back-off
            emit(PageletUiState.Ready(build(loc), System.currentTimeMillis()))
            // Wake at the top of every minute for the clock, or sooner when kicked.
            withTimeoutOrNull(60_000L) { kicks.receive() }
        }
    }.flowOn(Dispatchers.IO)

    /** Reads only the sections some Sky has switched on. */
    private suspend fun readSections() {
        val want = wanted()
        val now = System.currentTimeMillis()
        lastWellbeing = if ("wellbeing" in want && wellbeing != null) wellbeing.read(now) else SectionStatus.Idle to null
        lastCalendar = if ("calendar" in want) calendar(now) else SectionStatus.Idle to emptyList()
        val start = com.snapkode.nivolauncher.pagelet.calendar.CalendarToday.dayBounds(now).first
        lastSteps = if ("steps" in want) steps(start, now) else SectionStatus.Idle to null
    }

    override suspend fun refresh() = repo.refreshIfDue(location())

    private fun build(loc: WeatherLocation?): SkyData {
        val c = clock()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val sun = loc?.let {
            SolarTimes.forDate(it.lat, it.lon, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), TimeZone.getDefault())
        }
        // Without a place (or in polar day/night) fall back to ordinary hours so the sky still has a time of day.
        val sunrise = sun?.sunriseMin ?: (6 * 60 + 30)
        val sunset = sun?.sunsetMin ?: (18 * 60 + 30)
        val (weather, status) = when (val s = repo.state.value) {
            is SkyWeatherState.Ready -> SkyWeather(
                city = s.place, symbolCode = s.forecast.symbolCode, tempC = s.forecast.tempC, feelsC = s.forecast.feelsLikeC,
                highC = s.forecast.todayMaxC, lowC = s.forecast.todayMinC,
            ) to (if (s.stale) WeatherStatus.Stale else WeatherStatus.Live)
            SkyWeatherState.NeedsLocation -> null to WeatherStatus.NeedsLocation
            is SkyWeatherState.Unavailable -> null to WeatherStatus.Unavailable
        }
        return SkyData(
            now, sunrise, sunset, weather, lastWellbeing.second, weatherStatus = status,
            wellbeingStatus = lastWellbeing.first, calendar = lastCalendar.second, calendarStatus = lastCalendar.first,
            steps = lastSteps.second, stepsStatus = lastSteps.first,
        )
    }
}
