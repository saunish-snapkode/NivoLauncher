package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.DrawerSort
import com.snapkode.nivolauncher.data.GestureAction
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow

private const val VIRTUAL_W = 360
private const val VIRTUAL_H = 640

/**
 * Draws [content] on a 360×640dp virtual phone and scales it down to [height], so previews use the
 * real launcher composables and update live as settings change.
 */
@Composable
fun PhonePreview(height: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val width = height * VIRTUAL_W / VIRTUAL_H
    val scale = height.value / VIRTUAL_H
    // Corner radius follows the preview's size so small previews keep a phone's proportions.
    val bezel = RoundedCornerShape(height * 0.11f)
    Box(
        modifier
            .size(width, height)
            // A picture, not a control: screen readers hear one label instead of every mock icon inside.
            .clearAndSetSemantics { contentDescription = "Preview" }
            .clip(bezel)
            .border(3.dp, MaterialTheme.colorScheme.outlineVariant, bezel)
            .clipToBounds()
    ) {
        Box(
            Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(VIRTUAL_W.dp.roundToPx(), VIRTUAL_H.dp.roundToPx()))
                    layout((VIRTUAL_W * scale * density).toInt(), (VIRTUAL_H * scale * density).toInt()) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            content = content,
        )
    }
}

private val WallpaperBrush = Brush.verticalGradient(listOf(Color(0xFF16253F), Color(0xFF0A0D12), Color(0xFF0A0D12), Color(0xFF241B30)))

@Composable
private fun Wallpaper() = Box(Modifier.fillMaxSize().background(WallpaperBrush))

@Composable
private fun SearchPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Color.White)
        Text(text, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(start = NivoSpace.m), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mini home screen: grid, padding, labels, dock, dots and optional extras, all from [settings]. */
@Composable
fun HomePreview(settings: LauncherSettings, apps: List<AppInfo>, folder: HomeFolder? = null, height: Dp = 300.dp) {
    PhonePreview(height) {
        Wallpaper()
        Column(Modifier.fillMaxSize()) {
            if (!settings.hideStatusBar) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("9:41", color = Color.White, fontSize = 12.sp)
                }
            } else Box(Modifier.height(12.dp))
            NivoClock(settings)
            if (settings.homeSearchBar) SearchPill("Search apps, contacts, web")
            Column(Modifier.weight(1f).padding(horizontal = settings.homePaddingH.dp, vertical = settings.homePaddingV.dp)) {
                repeat(settings.rows) { r ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(settings.columns) { c ->
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val index = r * settings.columns + c
                                if (folder != null && index == 1) {
                                    FolderIconView(folder, apps.associateBy { it.id }, settings.iconScale, settings.showLabels)
                                } else apps.getOrNull(index)?.let {
                                    AppIconView(it, settings.iconScale, settings.showLabels, onWallpaper = true)
                                }
                            }
                        }
                    }
                }
            }
            if (settings.showPageDots) PreviewDots()
            if (settings.showDock && settings.dockSearchBar) SearchPill("Search apps, contacts, web")
            if (settings.showDock) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + settings.dockBottomSpace.dp)
                        .background(settings.dockTint.color().copy(alpha = settings.dockOpacity), RoundedCornerShape(settings.dockRadius.dp))
                        .padding(vertical = 8.dp)
                ) {
                    repeat(settings.dockSlots) { i ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            apps.getOrNull(apps.size - 1 - i)?.let { AppIconView(it, settings.iconScale, settings.dockLabels, onWallpaper = true) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewDots() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        repeat(3) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == 0) 8.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (i == 0) 0.95f else 0.45f), CircleShape)
            )
        }
    }
}

/** Mini drawer sheet over the wallpaper. */
@Composable
fun DrawerPreview(settings: LauncherSettings, apps: List<AppInfo>, height: Dp = 300.dp) {
    PhonePreview(height) {
        Wallpaper()
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 40.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = settings.drawerOpacity), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 40.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
            Box(Modifier.padding(top = settings.drawerTopPadding.dp))
            if (!settings.hideDrawerSearch) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null)
                    Text("Search apps", modifier = Modifier.padding(start = NivoSpace.m), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLabelSize provides settings.drawerLabelSize) {
                    Column(Modifier.fillMaxSize().padding(horizontal = settings.drawerSideMargin.dp)) {
                        if (settings.predictions) {
                            Text("Suggested", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                        }
                        when (settings.drawerMode) {
                            com.snapkode.nivolauncher.data.DrawerMode.List -> repeat(if (settings.predictions) 6 else 7) { r ->
                                apps.getOrNull(r)?.let {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = settings.drawerCellPadding.dp / 2), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.foundation.Image(it.icon, null, Modifier.size((40 * settings.drawerIconScale).dp))
                                        Text(it.label, modifier = Modifier.padding(start = 14.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            com.snapkode.nivolauncher.data.DrawerMode.Categories -> repeat(2) { section ->
                                Text(if (section == 0) "Productivity · 4" else "Communication · 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                                Row(Modifier.fillMaxWidth().padding(vertical = settings.drawerCellPadding.dp / 2)) {
                                    repeat(settings.drawerColumns) { c ->
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            apps.getOrNull(section * settings.drawerColumns + c)?.let { AppIconView(it, settings.drawerIconScale, settings.showDrawerLabels, onWallpaper = false) }
                                        }
                                    }
                                }
                            }
                            else -> repeat(if (settings.predictions) 4 else 5) { r ->
                                Row(Modifier.fillMaxWidth().padding(vertical = settings.drawerCellPadding.dp)) {
                                    repeat(settings.drawerColumns) { c ->
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            apps.getOrNull(r * settings.drawerColumns + c)?.let {
                                                AppIconView(it, settings.drawerIconScale, settings.showDrawerLabels, onWallpaper = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (settings.drawerScroller && settings.drawerSort == DrawerSort.AToZ) {
                    Column(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J").forEach {
                            Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Closed folder tiles (home + drawer look) and the open folder card. */
@Composable
fun FolderPreview(settings: LauncherSettings, apps: List<AppInfo>, folder: HomeFolder, height: Dp = 300.dp) {
    PhonePreview(height) {
        Wallpaper()
        val byId = apps.associateBy { it.id }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FolderIconView(folder, byId, settings.iconScale, settings.showLabels)
                apps.getOrNull(0)?.let { AppIconView(it, settings.iconScale, settings.showLabels, onWallpaper = true) }
                apps.getOrNull(1)?.let { AppIconView(it, settings.iconScale, settings.showLabels, onWallpaper = true) }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(NivoRadius.hero), color = MaterialTheme.colorScheme.surface.copy(alpha = settings.folderBgOpacity)) {
                    Column(Modifier.padding(NivoSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = NivoSpace.s))
                        val shown = folder.appIds.mapNotNull(byId::get)
                        shown.chunked(settings.folderColumns).take(2).forEach { row ->
                            Row {
                                row.forEach {
                                    Box(Modifier.width(72.dp).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        AppIconView(it, settings.iconScale, settings.showFolderLabels, onWallpaper = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Icons as the launcher draws them: current shape, pack, theming and label style. */
@Composable
fun IconsPreview(settings: LauncherSettings, apps: List<AppInfo>) {
    PreviewCard {
        Column(Modifier.padding(NivoSpace.l), verticalArrangement = Arrangement.spacedBy(NivoSpace.m)) {
            repeat(2) { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(5) { c ->
                        apps.getOrNull(r * 5 + c)?.let { AppIconView(it, 0.9f, true, onWallpaper = false) }
                    }
                }
            }
        }
    }
}

/** Sample controls drawn with the active colour scheme and font. */
@Composable
fun ThemePreview() {
    PreviewCard {
        Column(Modifier.padding(NivoSpace.l), verticalArrangement = Arrangement.spacedBy(NivoSpace.m)) {
            Text("Nivo Launcher", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("The quick brown fox jumps over the lazy dog", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(NivoSpace.s), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}) { Text("Primary", maxLines = 1) }
                OutlinedButton(onClick = {}) { Text("Outline", maxLines = 1) }
                Switch(checked = true, onCheckedChange = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                FilterChip(selected = true, onClick = {}, label = { Text("Selected", maxLines = 1) })
                FilterChip(selected = false, onClick = {}, label = { Text("Chip", maxLines = 1) })
            }
        }
    }
}

/** Where each gesture is configured to go. */
@Composable
fun GesturePreview(settings: LauncherSettings) {
    PreviewCard {
        Column(Modifier.padding(NivoSpace.l), verticalArrangement = Arrangement.spacedBy(NivoSpace.m)) {
            GestureLine(Icons.Filled.ArrowDownward, "Swipe down", settings.swipeDown)
            GestureLine(Icons.Filled.ArrowUpward, "Swipe up", settings.swipeUp)
            GestureLine(Icons.Filled.TouchApp, "Double tap", settings.doubleTap)
            GestureLine(Icons.Filled.Home, "Home button", settings.homePress)
        }
    }
}

@Composable
private fun GestureLine(icon: ImageVector, name: String, action: GestureAction) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = NivoSpace.m).weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(action.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Search as it will look while typing "wifi". */
@Composable
fun SearchPreview(settings: LauncherSettings, apps: List<AppInfo>) {
    PreviewCard {
        Column(Modifier.padding(NivoSpace.l), verticalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
            Row(
                Modifier.fillMaxWidth().height(NivoSpace.xxxl).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape).padding(horizontal = NivoSpace.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("wifi", modifier = Modifier.padding(start = NivoSpace.m))
            }
            if (settings.settingsSearch) PreviewResult(Icons.Filled.Settings, "Wi-Fi", "Settings")
            if (settings.contactsSearch) PreviewResult(Icons.Filled.Person, "Will Fischer", "Contacts")
            if (settings.webSuggestions && settings.webSearch) PreviewResult(Icons.Default.Search, "wifi password", "Suggestion")
            if (settings.webSearch) PreviewResult(Icons.Filled.Public, "Search ${settings.searchProvider.label} for “wifi”", "")
        }
    }
}

@Composable
private fun PreviewResult(badge: ImageVector, title: String, sub: String) {
    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(badge, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = NivoSpace.m).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Icons with sample badges using the chosen style. */
@Composable
fun BadgePreview(settings: LauncherSettings, apps: List<AppInfo>) {
    val sample = apps.take(4)
    val counts = sample.withIndex().associate { (i, app) ->
        com.snapkode.nivolauncher.NotificationState.badgeKey(app.packageName, app.user.hashCode()) to (i * 3 + 1)
    }
    PreviewCard {
        CompositionLocalProvider(
            LocalBadgeStyle provides BadgeStyle(settings.notificationDots, settings.notificationCount, settings.badgeColor.color(), counts)
        ) {
            Row(Modifier.fillMaxWidth().padding(NivoSpace.l), horizontalArrangement = Arrangement.SpaceEvenly) {
                sample.forEach { AppIconView(it, 1f, true, onWallpaper = false) }
            }
        }
    }
}

/** A widget card showing the corner setting. */
@Composable
fun WidgetPreview(settings: LauncherSettings) {
    PreviewCard {
        Box(Modifier.fillMaxWidth().padding(NivoSpace.l), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth(0.8f).height(90.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(if (settings.roundedWidgets) NivoRadius.card else 0.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("10:08", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}

@Composable
fun PreviewCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(NivoRadius.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = NivoSpace.l, vertical = NivoSpace.s),
    ) { content() }
}

/** Category cards as they look in the App Library. */
@Composable
fun LibraryPreview(groups: List<com.snapkode.nivolauncher.data.LibraryGroup>) {
    PreviewCard {
        Column(Modifier.padding(NivoSpace.m), verticalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
            groups.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                    row.forEach { g ->
                        Column(
                            Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(NivoRadius.control)).padding(10.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                g.apps.take(3).forEach { androidx.compose.foundation.Image(it.icon, null, Modifier.size(28.dp)) }
                            }
                            Text(g.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Tap to replay: a ball moves with the active motion preset so the difference can be felt. */
@Composable
fun MotionPreview() {
    var right by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val motion = com.snapkode.nivolauncher.ui.design.LocalMotion.current
    // 0..1 progress; the travel distance is the track's real width, so the ball never runs off a narrow card.
    val t by androidx.compose.animation.core.animateFloatAsState(if (right) 1f else 0f, motion.spring(1f), label = "ball")
    PreviewCard {
        Column(Modifier.clickable(role = Role.Button, onClickLabel = "Replay") { right = !right }.padding(NivoSpace.l)) {
            Text("Tap to replay", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(top = NivoSpace.m).height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            ) {
                val travel = (maxWidth - 56.dp).coerceAtLeast(0.dp)
                Box(
                    Modifier.graphicsLayer { translationX = travel.toPx() * t }
                        .size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}
