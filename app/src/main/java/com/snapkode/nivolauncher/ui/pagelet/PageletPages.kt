package com.snapkode.nivolauncher.ui.pagelet

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.calendar.AgendaItem
import com.snapkode.nivolauncher.pagelet.calendar.AgendaModel
import com.snapkode.nivolauncher.pagelet.calendar.CalendarToday
import com.snapkode.nivolauncher.ui.HeadlinesPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The page a Pagelet draws inside the pager, by kind. (Today is drawn by the layers around the pager instead.) */
@Composable
fun PageletPage(viewModel: LauncherViewModel, id: String, type: PageletType, onOptions: () -> Unit) {
    val configs by viewModel.pagelets.collectAsState()
    val config = configs[id] ?: PageletConfig.defaultsFor(type)
    when (type) {
        PageletType.HEADLINES -> HeadlinesPage(viewModel, id, onOptions = onOptions)
        PageletType.AGENDA -> AgendaPage(config, onOptions)
        PageletType.NOW_PLAYING -> NowPlayingPage(viewModel, onOptions)
        PageletType.MOVE -> MovePage(config, onOptions)
        else -> Box(Modifier.fillMaxSize())
    }
}

/** The shared top of the self-drawn pages: title, a line under it, optional actions, and the tune button. */
@Composable
internal fun PageletHeader(title: String, subtitle: String?, onOptions: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(start = 20.dp, end = NivoSpace.xs, top = NivoSpace.m, bottom = NivoSpace.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
        IconButton(onClick = onOptions) { Icon(Icons.Outlined.Tune, "Customise $title", tint = scheme.onSurfaceVariant) }
    }
}

/** A calm card asking for one permission, with its button. [icon], when given, sits in a tonal badge above the title. */
@Composable
internal fun AskCard(
    title: String, why: String, action: String, modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NivoRadius.hero)
    Column(
        modifier.fillMaxWidth()
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(20.dp),
    ) {
        if (icon != null) {
            Box(Modifier.padding(bottom = NivoSpace.m).size(44.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = scheme.onPrimaryContainer)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
        Text(why, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = NivoSpace.xs))
        Button(onClick = onClick, modifier = Modifier.padding(top = NivoSpace.l).heightIn(min = NivoTouch.min)) { Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** An icon, a headline and a line, centred: the page's empty and error states. */
@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, line: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(NivoSpace.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
        }
        Text(
            title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = NivoSpace.l).semantics { heading() },
        )
        Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = NivoSpace.xs))
    }
}
