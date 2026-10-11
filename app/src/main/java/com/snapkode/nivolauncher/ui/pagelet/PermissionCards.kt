package com.snapkode.nivolauncher.ui.pagelet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snapkode.nivolauncher.pagelet.UsageAccess
import com.snapkode.nivolauncher.pagelet.calendar.CalendarToday
import com.snapkode.nivolauncher.pagelet.steps.StepsSource

/** Opens this app's own page in system Settings: the only way back once a runtime permission has been refused for good. */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Where Health Connect lives on Play, for Android 13 and older. */
fun openHealthConnectStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${StepsSource.PROVIDER_PACKAGE}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(market) } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${StepsSource.PROVIDER_PACKAGE}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
private fun rememberResumeCounter(): Int {
    val owner = LocalLifecycleOwner.current
    var n by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) n++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    return n
}

@Composable
private fun PermissionCard(title: String, body: String, granted: Boolean, grantedBody: String, actionLabel: String, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh, border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(if (granted) grantedBody else body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (!granted) {
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            } else Icon(Icons.Filled.Check, null, tint = scheme.primary)
        }
    }
}

/** Usage access: the page's own entry in Settings > Special app access > Usage access, opened straight at this app. */
@Composable
fun UsageAccessCard() {
    val context = LocalContext.current
    val resumes = rememberResumeCounter()
    val granted = remember(resumes) { UsageAccess.isGranted(context) }
    PermissionCard(
        if (granted) "Usage access allowed" else "Usage access needed",
        "Wellbeing reads screen time on this phone. Nothing leaves your device. In Settings, find Nivo Launcher and switch it on.",
        granted, "Wellbeing can read screen time on this phone. Nothing leaves your device.", "Allow",
    ) { UsageAccess.open(context) }
}

@Composable
fun CalendarAccessCard(onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val resumes = rememberResumeCounter()
    var granted by remember(resumes) { mutableStateOf(CalendarToday.hasPermission(context)) }
    var refusedOnce by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; if (!ok) refusedOnce = true; if (ok) onChanged()
    }
    PermissionCard(
        if (granted) "Calendar allowed" else "Calendar access needed",
        "The Today page shows what is next in your calendars. It only reads, and nothing leaves your device.",
        granted, "Today shows what is next in your calendars. Read-only.", if (refusedOnce) "Open settings" else "Allow",
    ) { if (refusedOnce) openAppSettings(context) else ask.launch(Manifest.permission.READ_CALENDAR) }
}

@Composable
fun StepsAccessCard(onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val source = remember { StepsSource(context.applicationContext) }
    val resumes = rememberResumeCounter()
    var granted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(HealthConnectClient.SDK_UNAVAILABLE) }
    LaunchedEffect(resumes) { status = source.status(); granted = source.hasPermission() }
    val ask = rememberLauncherForActivityResult(StepsSource.requestContract()) { result ->
        granted = result.containsAll(StepsSource.permissions); if (granted) onChanged()
    }
    val unsupported = status == HealthConnectClient.SDK_UNAVAILABLE && android.os.Build.VERSION.SDK_INT >= 34
    when {
        unsupported -> PermissionCard("Steps unavailable", "This phone can't read steps from Health Connect.", false, "", "") {}
        status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED || (status == HealthConnectClient.SDK_UNAVAILABLE) ->
            PermissionCard("Health Connect needed", "Steps come from Health Connect, which isn't installed on this phone.", false, "", "Get it") { openHealthConnectStore(context) }
        else -> PermissionCard(
            if (granted) "Steps allowed" else "Steps access needed",
            "The Today page shows your steps from Health Connect. Read-only; nothing leaves your device.",
            granted, "Today shows your steps from Health Connect. Read-only.", "Allow",
        ) { ask.launch(StepsSource.permissions) }
    }
}

/** A "− value +" row for a goal. */
@Composable
fun GoalStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val size = Modifier.size(width = 64.dp, height = 48.dp)
            val pad = androidx.compose.foundation.layout.PaddingValues(0.dp)
            OutlinedButton(onClick = onMinus, modifier = size.semantics { contentDescription = "Decrease $label" }, contentPadding = pad) { Text("−", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = onPlus, modifier = size.semantics { contentDescription = "Increase $label" }, contentPadding = pad) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
