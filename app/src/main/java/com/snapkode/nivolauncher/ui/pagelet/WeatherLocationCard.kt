package com.snapkode.nivolauncher.ui.pagelet

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
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
        Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xl, vertical = NivoSpace.xs),
        shape = RoundedCornerShape(NivoRadius.card), color = scheme.surfaceContainerHigh, border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(NivoSpace.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Box(
                    Modifier.size(40.dp).background(if (usingCity || granted) scheme.primaryContainer else scheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (usingCity) Icons.Outlined.LocationCity else if (granted) Icons.Outlined.MyLocation else Icons.Outlined.LocationOff, null,
                        Modifier.size(20.dp),
                        tint = if (usingCity || granted) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                    )
                }
                Column(Modifier.weight(1f).padding(start = NivoSpace.m)) {
                    Text("Weather location", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(
                        when {
                            usingCity -> settings.weatherPlace
                            granted -> "Your approximate location"
                            else -> "Not set"
                        },
                        style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (usingCity || granted) "Only the place, rounded to about 1 km, is sent to MET Norway."
                else "Pick a city, or allow your approximate location. Only the place, rounded to about 1 km, is sent to MET Norway.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = NivoSpace.m),
            )
            if (updatedAtMs != null) {
                val mins = ((System.currentTimeMillis() - updatedAtMs) / 60_000L).coerceAtLeast(0)
                Text(
                    "Updated " + if (mins < 1) "just now" else if (mins < 60) "$mins min ago" else "${mins / 60} h ago",
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = NivoSpace.xs),
                )
            }
            Spacer(Modifier.height(NivoSpace.m))
            // Equal-width buttons that never wrap their labels, whatever the text size or screen width.
            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = NivoSpace.m, vertical = NivoSpace.s)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                OutlinedButton(onClick = { choosing = true }, modifier = Modifier.weight(1f).heightIn(min = NivoTouch.min), contentPadding = pad) {
                    Text("Choose a city", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (usingCity || !granted) {
                    // The location button is the suggested path when nothing is set yet, so it carries the fill.
                    FilledTonalButton(
                        onClick = { if (granted) viewModel.clearWeatherPlace() else ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        modifier = Modifier.weight(1f).heightIn(min = NivoTouch.min), contentPadding = pad,
                    ) { Text("Use my location", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    if (choosing) PlaceSearchDialog(viewModel, onDone = { choosing = false }, onPick = { viewModel.setWeatherPlace(it) })
}
