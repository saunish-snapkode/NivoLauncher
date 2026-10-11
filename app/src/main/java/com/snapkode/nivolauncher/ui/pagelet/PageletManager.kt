package com.snapkode.nivolauncher.ui.pagelet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
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
                modifier = Modifier.padding(start = NivoSpace.xl, end = NivoSpace.xl, top = NivoSpace.s, bottom = NivoSpace.xs).semantics { heading() },
            )
            Text(
                if (locked) "Unlock the home layout to add or remove Pagelets."
                else "Each Pagelet fills a home page of its own and can be on home once.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = NivoSpace.xl, end = NivoSpace.xl, bottom = NivoSpace.m),
            )
            PageletType.entries.filter { it != PageletType.FOCUS }.forEach { type ->
                val here = placed.firstOrNull { it.type == type }
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xl, vertical = NivoSpace.xs),
                    shape = RoundedCornerShape(NivoRadius.card), color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Column(Modifier.padding(NivoSpace.l).alpha(if (type.available) 1f else NivoAlpha.medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(if (here != null) scheme.primaryContainer else scheme.surfaceContainerHighest, RoundedCornerShape(NivoRadius.control)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    pageletIcon(type), null, Modifier.size(20.dp),
                                    tint = if (here != null) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = NivoSpace.m, end = NivoSpace.m).semantics(mergeDescendants = true) {}) {
                                Text(type.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        here != null -> "On page ${here.page + 1}"
                                        type.available -> type.tagline
                                        else -> "Coming soon"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (here != null) scheme.primary else scheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (here == null && type.available) {
                                Button(onClick = { onAdd(type) }, enabled = !locked, modifier = Modifier.heightIn(min = NivoTouch.min)) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(NivoSpace.xs))
                                    Text("Add", maxLines = 1)
                                }
                            }
                        }
                        if (here != null) Row(Modifier.fillMaxWidth().padding(top = NivoSpace.m), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                            OutlinedButton(onClick = { onCustomise(here.id) }, modifier = Modifier.weight(1f).heightIn(min = NivoTouch.min)) {
                                Text("Customise", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { onRemove(here.id) }, enabled = !locked, modifier = Modifier.weight(1f).heightIn(min = NivoTouch.min),
                                border = BorderStroke(1.dp, if (locked) scheme.onSurface.copy(alpha = NivoAlpha.hairline) else scheme.error.copy(alpha = 0.5f)),
                            ) {
                                Text("Remove", maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (locked) scheme.onSurface.copy(alpha = NivoAlpha.disabled) else scheme.error)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xl, vertical = NivoSpace.s), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = NivoTouch.min)) { Text("Done") }
            }
        }
    }
}

/** The glyph each kind of Pagelet goes by in lists. */
internal fun pageletIcon(type: PageletType): ImageVector = when (type) {
    PageletType.SKY -> Icons.Outlined.WbSunny
    PageletType.AGENDA -> Icons.Outlined.CalendarMonth
    PageletType.NOW_PLAYING -> Icons.Outlined.MusicNote
    PageletType.HEADLINES -> Icons.AutoMirrored.Outlined.Article
    PageletType.MOVE -> Icons.Outlined.FavoriteBorder
    else -> Icons.Outlined.Widgets
}
