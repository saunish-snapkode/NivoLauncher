package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.IconRenderer
import com.snapkode.nivolauncher.data.IconShape
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.data.Tint
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

/** Look of folder tiles, supplied by the root from user settings. */
data class FolderStyle(val shape: IconShape, val tileOpacity: Float, val tint: Tint)

val LocalFolderStyle = androidx.compose.runtime.compositionLocalOf {
    FolderStyle(IconShape.RoundedSquare, 0.28f, Tint.White)
}

internal class MaskShape(private val shape: IconShape) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ) = androidx.compose.ui.graphics.Outline.Generic(
        IconRenderer.maskPath(if (shape == IconShape.Original) IconShape.RoundedSquare else shape, size.width).asComposePath()
    )
}

/** Folder as it appears on the home screen: a rounded tile previewing up to four apps. */
@Composable
fun FolderIconView(
    folder: HomeFolder,
    appsById: Map<String, AppInfo>,
    scale: Float,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onWallpaper: Boolean = true,
) {
    val tile = (56 * scale).dp
    val style = LocalFolderStyle.current
    val tileColor = if (onWallpaper) style.tint.color().copy(alpha = style.tileOpacity)
    else MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val badge = LocalBadgeStyle.current
        val folderCount = folder.appIds.sumOf { id -> appsById[id]?.let(badge::countFor) ?: 0 }
        Box(
            Modifier.size(tile)
                .background(tileColor, MaskShape(style.shape))
                .then(if (onWallpaper) Modifier.border(1.dp, style.tint.color().copy(alpha = 0.22f), MaskShape(style.shape)) else Modifier)
                .padding(tile / 12),
        ) {
            NotificationBadge(folderCount, Modifier.align(Alignment.TopEnd))
            val preview = folder.appIds.mapNotNull(appsById::get).take(4)
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(tile / 16)) {
                preview.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(tile / 16)) {
                        row.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(tile * 0.4f)) }
                    }
                }
            }
        }
        if (showLabel) {
            // Same sizing rules as app labels, so a folder never reads larger or smaller than its neighbours.
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val bigFont = fontScale > 1.3f
            val labelCap = if (fontScale > 1.5f) 1.5f / fontScale else 1f
            val labelSize = LocalLabelSize.current * labelCap
            val homeInk = LocalHomeTextColor.current
            Text(
                folder.name,
                maxLines = if (bigFont) maxOf(2, LocalLabelLines.current) else LocalLabelLines.current,
                overflow = TextOverflow.Ellipsis,
                fontSize = labelSize.sp,
                lineHeight = (labelSize * 1.2f).sp,
                color = if (onWallpaper) homeInk else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    // Only white text over the wallpaper needs the shadow; dark text or a solid surface reads cleaner without.
                    shadow = if (onWallpaper && homeInk == Color.White) HomeLabelShadow else null,
                    fontFamily = LocalAppFont.current,
                ),
                modifier = Modifier.width(((if (bigFont) 80 else 64) * scale).dp.coerceAtLeast(56.dp)),
            )
        }
    }
}

@Composable
internal fun FolderPopup(
    folder: HomeFolder,
    appsById: Map<String, AppInfo>,
    viewModel: LauncherViewModel,
    drag: DragState,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
) {
    val scale = settings.iconScale
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    // Save the title when the popup closes, however it closes.
    val latestName by rememberUpdatedState(name)
    DisposableEffect(folder.id) { onDispose { viewModel.renameFolder(folder.id, latestName.ifBlank { "Folder" }) } }
    // While an icon is dragged out the popup stays composed (so the gesture survives). It stays visible while the
    // icon is still over the folder and only fades away once the icon leaves it, so the home screen can be dropped on.
    val draggingOut = drag.lifted && drag.fromFolder == folder.id
    val motion = com.snapkode.nivolauncher.ui.design.LocalMotion.current
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val enter = progress.value.coerceIn(0f, 1f)
    var surfaceBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { progress.animateTo(1f, motion.spring(1.1f)) }
    DisposableEffect(Unit) { onDispose { drag.folderBounds = null } }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsOn = com.snapkode.nivolauncher.ui.design.LocalHapticsEnabled.current
    var wasInside by remember { mutableStateOf(true) }
    val insideCard = surfaceBounds?.contains(drag.pos) == true
    val visible by animateFloatAsState(if (draggingOut && !insideCard) 0f else 1f, androidx.compose.animation.core.tween(motion.duration(150)), label = "folderVisible")
    LaunchedEffect(insideCard, draggingOut) {
        if (draggingOut && insideCard != wasInside && hapticsOn) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
        wasInside = if (draggingOut) insideCard else true
    }
    // Closing runs the opening in reverse, back into the folder's tile.
    fun close() {
        scope.launch {
            progress.animateTo(0f, motion.spring(1.3f))
            onDismiss()
        }
    }
    BackHandler(onBack = ::close)
    val titleFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .alpha(visible)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f * enter))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::close),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = {},
            modifier = Modifier
                .padding(NivoSpace.xl)
                .onGloballyPositioned { surfaceBounds = it.boundsInRoot(); drag.folderBounds = surfaceBounds }
                .graphicsLayer {
                    val target = surfaceBounds
                    val origin = drag.openFolderOrigin
                    if (target != null && origin != null) {
                        // Grow from the tapped tile: start small, centred on it, and glide to the middle.
                        val startScale = (origin.width / target.width).coerceIn(0.1f, 0.6f)
                        val f = startScale + (1f - startScale) * enter
                        scaleX = f; scaleY = f
                        translationX = (origin.center.x - target.center.x) * (1f - enter)
                        translationY = (origin.center.y - target.center.y) * (1f - enter)
                        alpha = (enter * 2.5f).coerceIn(0f, 1f)
                    } else {
                        val f = 0.85f + 0.15f * enter
                        scaleX = f; scaleY = f; alpha = enter
                    }
                    // Letting go of the card as an icon leaves it: it eases back a little while it fades.
                    val release = 0.94f + 0.06f * visible
                    scaleX *= release; scaleY *= release
                },
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = settings.folderBgOpacity),
        ) {
            Column(Modifier.padding(NivoSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
                // The title sits in a tonal pill with a pencil, hinting that it can be renamed in place. The whole pill
                // (pencil included) puts the cursor in the name, not just the text itself.
                val titleStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Row(
                    Modifier
                        .padding(bottom = NivoSpace.l)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClickLabel = "Rename folder") { titleFocus.requestFocus() }
                        .heightIn(min = NivoTouch.min)
                        .padding(horizontal = NivoSpace.xl, vertical = NivoSpace.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        singleLine = true,
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { close() }),
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 220.dp)
                            .focusRequester(titleFocus)
                            .semantics { contentDescription = "Folder name" },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                // A cleared name shows what it will fall back to, instead of an empty pill.
                                if (name.isEmpty()) {
                                    Text("Folder", style = titleStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 1)
                                }
                                inner()
                            }
                        },
                    )
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Edit, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = NivoSpace.m).size(20.dp),
                    )
                }
                val folderApps = folder.appIds.mapNotNull(appsById::get)
                if (folderApps.isEmpty()) {
                    // Every app in it was uninstalled (or is still loading): say so rather than showing a blank card.
                    Text(
                        "No apps in this folder yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = NivoSpace.xl, vertical = NivoSpace.xxl),
                    )
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.folderColumns),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    itemsIndexed(folderApps, key = { _, app -> app.id }) { index, app ->
                        FolderCell(app, folder, viewModel, drag, scale, settings.showFolderLabels, index)
                    }
                }
            }
        }
    }
    // The dragged icon floats above the card, never underneath it.
    if (draggingOut) {
        val half = with(androidx.compose.ui.platform.LocalDensity.current) { (32 * scale).dp.toPx() }
        val lift by animateFloatAsState(1.18f, motion.spring(1.2f), label = "folderLift")
        appsById[drag.id]?.let {
            AppIconView(
                it, scale * lift, showLabel = false, onWallpaper = true,
                modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((drag.pos.x - half).toInt(), (drag.pos.y - half).toInt()) },
            )
        }
    }
    }
}

@Composable
private fun FolderCell(
    app: AppInfo,
    folder: HomeFolder,
    viewModel: LauncherViewModel,
    drag: DragState,
    scale: Float,
    showLabel: Boolean,
    index: Int,
) {
    val motion = com.snapkode.nivolauncher.ui.design.LocalMotion.current
    val appear = remember { androidx.compose.animation.core.Animatable(if (motion.stagger) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion.stagger) {
            kotlinx.coroutines.delay(90L + index * 35L)
            appear.animateTo(1f, motion.spring(0.8f))
        }
    }
    DragCell(
        id = app.id,
        drag = drag,
        fromFolder = folder.id,
        description = app.label,
        modifier = Modifier.fillMaxWidth().padding(vertical = NivoSpace.s).graphicsLayer {
            val a = appear.value
            alpha = a.coerceIn(0f, 1f)
            val sc = 0.6f + 0.4f * a
            scaleX = sc; scaleY = sc
        },
        onTap = { bounds -> viewModel.launch(app, bounds) },
        menu = { expanded, dismiss ->
            AppMenu(
                expanded = expanded,
                app = app,
                viewModel = viewModel,
                extraActions = listOf(MenuAction("Move out of folder") { viewModel.removeFromFolder(folder.id, app.id) }),
                onDismiss = dismiss,
            )
        },
    ) { AppIconView(app, scale, showLabel = showLabel, onWallpaper = false, modifier = Modifier.springVisible(!(drag.lifted && drag.id == app.id))) }
}
