package com.snapkode.nivolauncher.pagelet.move

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** Heart rate over the last day, in beats per minute. */
data class HeartStats(val latest: Int?, val average: Int?, val min: Int?, val max: Int?)

/** What the Move page shows. A field is null when its permission wasn't given or Health Connect had nothing. */
data class MoveData(
    val steps: Long?,
    /** Steps on each of the last seven days, oldest first, today last. */
    val week: List<Long>?,
    /** Last night's sleep, in minutes. */
    val sleepMin: Int?,
    val heart: HeartStats?,
    /** Which of the three the user has allowed. */
    val canReadSteps: Boolean, val canReadSleep: Boolean, val canReadHeart: Boolean,
)

sealed interface MoveResult {
    data class Ready(val data: MoveData) : MoveResult
    data object NotInstalled : MoveResult
    data object Unsupported : MoveResult
    data class Failed(val reason: String) : MoveResult
}

/** Reads steps, sleep and heart rate from Health Connect, read-only. Safe to call on any API level. */
class MoveSource(private val context: Context) {
    companion object {
        const val MIN_SDK = 26
        val stepsPermission: String get() = HealthPermission.getReadPermission(StepsRecord::class)
        val sleepPermission: String get() = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val heartPermission: String get() = HealthPermission.getReadPermission(HeartRateRecord::class)
        val permissions: Set<String> get() = setOf(stepsPermission, sleepPermission, heartPermission)

        fun requestContract() = PermissionController.createRequestPermissionResultContract()
    }

    fun status(): Int = if (Build.VERSION.SDK_INT < MIN_SDK) HealthConnectClient.SDK_UNAVAILABLE else HealthConnectClient.getSdkStatus(context)

    suspend fun read(nowMs: Long): MoveResult {
        if (Build.VERSION.SDK_INT < MIN_SDK) return MoveResult.Unsupported
        when (status()) {
            HealthConnectClient.SDK_AVAILABLE -> Unit
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> return MoveResult.NotInstalled
            else -> return if (Build.VERSION.SDK_INT >= 34) MoveResult.Unsupported else MoveResult.NotInstalled
        }
        return readFromHealthConnect(nowMs)
    }

    // Everything that needs java.time lives here, so a phone older than Android 8 never loads it.
    @androidx.annotation.RequiresApi(26)
    private suspend fun readFromHealthConnect(nowMs: Long): MoveResult {
        val zone = ZoneId.systemDefault()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val canSteps = stepsPermission in granted
            val canSleep = sleepPermission in granted
            val canHeart = heartPermission in granted
            val now = Instant.ofEpochMilli(nowMs)
            val today = now.atZone(zone).toLocalDate()

            var steps: Long? = null
            var week: List<Long>? = null
            if (canSteps) runCatching {
                val startOfDay = today.atStartOfDay(zone).toInstant()
                steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startOfDay, now)))[StepsRecord.COUNT_TOTAL] ?: 0L
                val from = LocalDateTime.of(today.minusDays(6), java.time.LocalTime.MIDNIGHT)
                val groups = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, from.plusDays(7)), Period.ofDays(1)),
                )
                val byDay = groups.associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
                week = List(7) { i -> byDay[today.minusDays(6L - i)] ?: 0L }
            }

            var sleep: Int? = null
            if (canSleep) runCatching {
                // Last night: from 6 PM yesterday to now.
                val from = today.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
                val d = client.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(from, now)))[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                sleep = d?.toMinutes()?.toInt()?.takeIf { it > 0 }
            }

            var heart: HeartStats? = null
            if (canHeart) runCatching {
                val from = now.minus(Duration.ofHours(24))
                val agg = client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX), TimeRangeFilter.between(from, now)))
                val latest = client.readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(6)), now), ascendingOrder = false, pageSize = 1),
                ).records.firstOrNull()?.samples?.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
                val avg = agg[HeartRateRecord.BPM_AVG]?.toInt()
                if (latest != null || avg != null) heart = HeartStats(latest, avg, agg[HeartRateRecord.BPM_MIN]?.toInt(), agg[HeartRateRecord.BPM_MAX]?.toInt())
            }
            MoveResult.Ready(MoveData(steps, week, sleep, heart, canSteps, canSleep, canHeart))
        } catch (e: SecurityException) {
            MoveResult.Ready(MoveData(null, null, null, null, false, false, false))
        } catch (e: Exception) {
            MoveResult.Failed(e.javaClass.simpleName)
        }
    }
}

/** The words and numbers on the Move page. Pure. */
object MoveFormat {
    /** "7h 12m", "45m", "8h". */
    fun duration(min: Int): String = when {
        min >= 60 && min % 60 == 0 -> "${min / 60}h"
        min >= 60 -> "${min / 60}h ${min % 60}m"
        else -> "${min}m"
    }

    /** 0..1 progress towards a goal; a goal of 0 never divides. */
    fun progress(value: Long, goal: Long): Float = if (goal <= 0) 0f else (value.toFloat() / goal).coerceIn(0f, 1f)

    /** Bar heights 0..1 for a week of steps, scaled to the larger of the best day and [goal], so a quiet week still looks quiet. */
    fun bars(week: List<Long>, goal: Long): List<Float> {
        val top = maxOf(week.maxOrNull() ?: 0L, goal, 1L).toFloat()
        return week.map { (it / top).coerceIn(0f, 1f) }
    }

    /** Rough distance walked, from steps (an average stride of 0.76 m). Labelled as an estimate wherever it is shown. */
    fun distanceKm(steps: Long): Double = steps * 0.000762

    /** Rough energy for those steps (about 0.04 kcal a step). An estimate too. */
    fun kcal(steps: Long): Int = (steps * 0.04).toInt()

    /** Average over the days that have any steps; null if none do. */
    fun average(week: List<Long>): Long? = week.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

    /** Where [value] sits between [min] and [max], 0..1; the middle when the range is empty. */
    fun position(value: Int, min: Int, max: Int): Float = if (max <= min) 0.5f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)

    /** A one-line judgement of the night, relative to the goal. */
    fun sleepNote(min: Int, goalMin: Int): String = when {
        min >= goalMin -> "You reached your goal."
        goalMin - min <= 30 -> "Just short of your goal."
        else -> "${duration(goalMin - min)} short of your goal."
    }
}
