package com.snapkode.nivolauncher.ui.pagelet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.pagelet.PageletType

/** A Pagelet that is on home right now, and the page it is on (0-based). */
class PlacedPagelet(val id: String, val type: PageletType, val page: Int)

/**
 * Manage Pagelets from the home menu: each kind appears once, as either an Add button (it isn't on home) or Customise and
 * Remove (it is). Kinds that aren't built yet are listed as coming soon so the list reads as the whole set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageletManagerSheet(
    placed: List<PlacedPagelet>,
    locked: Boolean,
    onAdd: (PageletType) -> Unit,
    onCustomise: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Pagelets", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
            )
            Text(
                if (locked) "Unlock the home layout to add or remove Pagelets."
                else "Each Pagelet fills a home page of its own and can be on home once.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            PageletType.entries.filter { it != PageletType.FOCUS }.forEach { type ->
                val here = placed.firstOrNull { it.type == type }
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(type.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        here != null -> "On page ${here.page + 1}"
                                        type.available -> type.tagline
                                        else -> "Coming soon"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                )
                            }
                            if (here == null && type.available) {
                                Button(onClick = { onAdd(type) }, enabled = !locked) { Text("Add") }
                            }
                        }
                        if (here != null) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCustomise(here.id) }, modifier = Modifier.weight(1f)) { Text("Customise", maxLines = 1) }
                            OutlinedButton(onClick = { onRemove(here.id) }, enabled = !locked, modifier = Modifier.weight(1f)) {
                                Text("Remove", maxLines = 1, color = if (locked) scheme.onSurface.copy(alpha = 0.38f) else scheme.error)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
