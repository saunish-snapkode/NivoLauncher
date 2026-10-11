package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.news.NewsCatalog
import com.snapkode.nivolauncher.data.news.NewsCategory
import com.snapkode.nivolauncher.data.news.NewsCustomFeed
import com.snapkode.nivolauncher.data.news.NewsFeedUrl
import com.snapkode.nivolauncher.data.news.NewsOrder
import com.snapkode.nivolauncher.data.news.NewsPrefs
import com.snapkode.nivolauncher.data.news.NewsProvider
import com.snapkode.nivolauncher.data.news.NewsStyle
import kotlinx.coroutines.launch

/**
 * The Headlines Pagelet's choices: pick the sources from the list (or add your own feed), then how the page looks. Used inside the
 * Pagelet gallery and the Pagelet options sheet; every change applies at once, so the page behind the sheet is the preview.
 */
@Composable
fun HeadlinesOptions(
    prefs: NewsPrefs,
    onChange: ((NewsPrefs) -> NewsPrefs) -> Unit,
    checkFeed: suspend (String) -> Pair<String, Int>?,
) {
    var adding by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Column {
        Text(
            "Choose where your headlines come from and how they look.",
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
            // ---- sources --------------------------------------------------------------------------------------
            val chosen = prefs.providers.count { NewsCatalog.find(it) != null } + prefs.custom.size
            SectionTitle("Sources", if (chosen == 0) "None chosen" else "$chosen chosen")
            NewsCategory.entries.forEach { cat ->
                val list = NewsCatalog.providers.filter { it.category == cat }
                if (list.isNotEmpty()) {
                    Text(cat.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 2.dp))
                    list.forEach { p ->
                        SourceRow(p, p.id in prefs.providers) {
                            onChange { it.copy(providers = if (p.id in it.providers) it.providers - p.id else it.providers + p.id) }
                        }
                    }
                }
            }

            // ---- your own feeds -------------------------------------------------------------------------------
            SectionTitle("Your own feeds", if (prefs.custom.isEmpty()) null else "${prefs.custom.size}")
            prefs.custom.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.url, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onChange { it.copy(custom = it.custom - f) } }) { Icon(Icons.Filled.Close, "Remove ${f.name}") }
                }
            }
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add a feed address")
            }
            Text(
                "Any website's RSS or Atom link works. Your feeds appear under Top.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp),
            )

            // ---- look -----------------------------------------------------------------------------------------
            SectionTitle("Look", null)
            Text("Style", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Segments(
                NewsStyle.entries, prefs.style, { when (it) { NewsStyle.Cards -> "Cards"; NewsStyle.Compact -> "Compact"; NewsStyle.Headlines -> "Headlines" } },
            ) { st -> onChange { it.copy(style = st) } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Pictures", style = MaterialTheme.typography.titleSmall)
                    Text("Turn off to save data and keep the page quieter", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Switch(checked = prefs.images, onCheckedChange = { v -> onChange { it.copy(images = v) } })
            }
            Text("Stories from each source", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Segments(NewsPrefs.PerSourceChoices, prefs.perSource, { "$it" }) { n -> onChange { it.copy(perSource = n) } }
            Text("Order", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))
            Segments(NewsOrder.entries, prefs.order, { it.label }) { o -> onChange { it.copy(order = o) } }
            Text(prefs.order.about, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))

            // Contents start at the same 24 dp edge as everything above.
            TextButton(
                onClick = { onChange { NewsPrefs() } }, modifier = Modifier.padding(start = 24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp, top = 8.dp, bottom = 8.dp),
            ) { Text("Reset sources and look") }
    }
    if (adding) AddFeedDialog(checkFeed, prefs, onChange, onDone = { adding = false })
}

@Composable
private fun SectionTitle(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourceRow(p: NewsProvider, selected: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val topics = remember(p) { p.topics.sortedBy { it.ordinal }.joinToString(" · ") { it.label } }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClick = onToggle).padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${p.name}. ${p.about}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Text(p.name.removePrefix("The ").take(1), style = MaterialTheme.typography.titleMedium, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall)
            Text(p.about, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(topics, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

/** Asks for a feed address, checks it really is a feed (and reads its name), then adds it. */
@Composable
private fun AddFeedDialog(checkFeed: suspend (String) -> Pair<String, Int>?, prefs: NewsPrefs, onChange: ((NewsPrefs) -> NewsPrefs) -> Unit, onDone: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun submit() {
        val clean = NewsFeedUrl.normalise(url)
        when {
            clean == null -> error = "That doesn't look like a web address."
            prefs.custom.any { it.url == clean } || NewsCatalog.providers.any { p -> clean in p.feeds.values } -> error = "That feed is already in your list."
            else -> { busy = true; error = null
                scope.launch {
                    val found = checkFeed(clean)
                    busy = false
                    if (found == null || found.second == 0) error = "Couldn't read stories from that address. Check it's an RSS or Atom feed and that you're online."
                    else {
                        val shown = name.trim().ifBlank { found.first }.take(28)
                        onChange { it.copy(custom = it.custom + NewsCustomFeed(shown, clean)) }
                        onDone()
                    }
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text("Add a feed") },
        text = {
            Column {
                OutlinedTextField(url, { url = it; error = null }, singleLine = true, label = { Text("Feed address") }, placeholder = { Text("example.com/rss") }, enabled = !busy)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") }, enabled = !busy)
                if (busy) Text("Checking the feed…", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = !busy && url.isNotBlank()) { Text("Check and add") } },
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text("Cancel") } },
    )
}

/** A row of equal-width choices on the same 24 dp edges as the rest of the sheet; one is selected. */
@Composable
private fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected, onClick = { onSelect(option) },
                label = { Text(label(option), Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
