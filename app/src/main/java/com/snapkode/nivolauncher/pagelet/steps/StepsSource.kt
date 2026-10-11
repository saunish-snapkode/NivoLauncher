package com.snapkode.nivolauncher.pagelet.steps

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/** What Health Connect says about today's steps. */
sealed interface StepsResult {
    data class Ready(val count: Long) : StepsResult

    /** Health Connect is there but the user hasn't allowed reading steps. */
    data object NeedsPermission : StepsResult

    /** Android 13 and older without the Health Connect app. */
    data object NotInstalled : StepsResult

    /** Android 7 and older, or a device that can't run Health Connect at all. */
    data object Unsupported : StepsResult

    data class Failed(val reason: String) : StepsResult
}

/** Reads today's step total from Health Connect, read-only. Every call is safe on any API level. */
class StepsSource(private val context: Context) {
    companion object {
        /** Android 14+ ships Health Connect; before that it is a separate app. */
        const val MIN_SDK = 26
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val permissions: Set<String> get() = setOf(HealthPermission.getReadPermission(StepsRecord::class))

        /** The contract the UI launches to ask for step access. */
        fun requestContract() = PermissionController.createRequestPermissionResultContract()
    }

    fun status(): Int = if (Build.VERSION.SDK_INT < MIN_SDK) HealthConnectClient.SDK_UNAVAILABLE else HealthConnectClient.getSdkStatus(context)

    suspend fun hasPermission(): Boolean {
        if (status() != HealthConnectClient.SDK_AVAILABLE) return false
        return runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions) }.getOrDefault(false)
    }

    suspend fun today(startOfDayMs: Long, nowMs: Long): StepsResult {
        if (Build.VERSION.SDK_INT < MIN_SDK) return StepsResult.Unsupported
        when (status()) {
            HealthConnectClient.SDK_AVAILABLE -> Unit
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> return StepsResult.NotInstalled
            else -> return if (Build.VERSION.SDK_INT >= 34) StepsResult.Unsupported else StepsResult.NotInstalled
        }
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions().containsAll(permissions)) return StepsResult.NeedsPermission
            val agg = client.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(Instant.ofEpochMilli(startOfDayMs), Instant.ofEpochMilli(nowMs))),
            )
            StepsResult.Ready(agg[StepsRecord.COUNT_TOTAL] ?: 0L)
        } catch (e: SecurityException) {
            StepsResult.NeedsPermission
        } catch (e: Exception) {
            StepsResult.Failed(e.javaClass.simpleName)
        }
    }
}
