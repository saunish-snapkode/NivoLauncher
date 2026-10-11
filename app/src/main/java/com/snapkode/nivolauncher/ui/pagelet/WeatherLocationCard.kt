package com.snapkode.nivolauncher.ui.pagelet

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.pagelet.weather.WeatherLocationResolver
import com.snapkode.nivolauncher.ui.PlaceSearchDialog

/**
 * Where the Today Pagelet's weather comes from: a city the user picks, or the phone's approximate position if they allow it.
 * Location is only asked for here, when the user taps the button, with the reason on the card.
 */
@Composable
fun WeatherLocationCard(viewModel: LauncherViewModel, updatedAtMs: Long? = null) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val resolver = remember { WeatherLocationResolver(context.applicationContext) }
    var granted by remember { mutableStateOf(resolver.hasCoarsePermission()) }
    var choosing by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = resolver.hasCoarsePermission() }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) viewModel.clearWeatherPlace()
    }
    val scheme = MaterialTheme.colorScheme
    val usingCity = settings.weatherPlace.isNotEmpty()
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh, border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Weather location", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    usingCity -> settings.weatherPlace
                    granted -> "Your approximate location"
                    else -> "Not set"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (usingCity || granted) "Only the place, rounded to about 1 km, is sent to MET Norway."
                else "Pick a city, or allow your approximate location. Only the place, rounded to about 1 km, is sent to MET Norway.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            if (updatedAtMs != null) {
                val mins = ((System.currentTimeMillis() - updatedAtMs) / 60_000L).coerceAtLeast(0)
                Text(
                    "Updated " + if (mins < 1) "just now" else if (mins < 60) "$mins min ago" else "${mins / 60} h ago",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            // Equal-width buttons that never wrap their labels, whatever the text size or screen width.
            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { choosing = true }, modifier = Modifier.weight(1f), contentPadding = pad) {
                    Text("Choose a city", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (usingCity || !granted) {
                    OutlinedButton(
                        onClick = { if (granted) viewModel.clearWeatherPlace() else ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        modifier = Modifier.weight(1f), contentPadding = pad,
                    ) { Text("Use my location", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }
        }
    }
    if (choosing) PlaceSearchDialog(viewModel, onDone = { choosing = false }, onPick = { viewModel.setWeatherPlace(it) })
}
