package com.snapkode.nivolauncher.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.NewsFormat
import com.snapkode.nivolauncher.data.NewsItem
import com.snapkode.nivolauncher.data.NewsState
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.data.news.NewsCatalog
import com.snapkode.nivolauncher.data.news.NewsStyle
import com.snapkode.nivolauncher.ui.design.edgeFade
import com.snapkode.nivolauncher.ui.design.nivoCard
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.pressFeedback
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

/**
 * The Headlines Pagelet: topic chips and a quiet stack of headlines from the sources the user chose, on a page of its own.
 * Tap a story to read it in the browser; the tune button opens the Pagelet's settings, a long press on the title the page menu.
 */
@Composable
fun HeadlinesPage(
    viewModel: LauncherViewModel,
    id: String,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configs by viewModel.pagelets.collectAsState()
    val states by viewModel.newsStates.collectAsState()
    val config = configs[id] ?: PageletConfig.defaultsFor(PageletType.HEADLINES)
    val state = states[id] ?: NewsState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val prefs = config.news
    val topic = viewModel.effectiveNewsTopic(config)
    val topics = remember(prefs.providers, prefs.custom) { NewsCatalog.topicsFor(prefs) }
    LaunchedEffect(id, config.newsTopic, prefs.providers, prefs.custom) { viewModel.loadNews(id) }

    fun open(item: NewsItem) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Headlines", style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                Text(
                    if (state.loading && state.items.isEmpty()) "Fetching headlines…" else "${topic.label} · ${NewsCatalog.summary(prefs)}",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // While a refresh runs the button becomes its own progress indicator, so a tap always gets an answer.
            IconButton(onClick = { viewModel.loadNews(id, force = true) }, enabled = !state.loading) {
                if (state.loading) CircularProgressIndicator(
                    Modifier.size(20.dp).semantics { contentDescription = "Refreshing headlines" }, strokeWidth = 2.dp, color = scheme.primary,
                )
                else Icon(Icons.Default.Refresh, "Refresh headlines", tint = scheme.onSurfaceVariant)
            }
            IconButton(onClick = onOptions) { Icon(Icons.Outlined.Tune, "Customise headlines", tint = scheme.onSurfaceVariant) }
        }
        LazyRow(
            modifier = Modifier.edgeFade(),
            contentPadding = PaddingValues(horizontal = NivoSpace.l),
            horizontalArrangement = Arrangement.spacedBy(NivoSpace.s),
        ) {
            items(topics) { t ->
                val selected = t == topic
                val shape = RoundedCornerShape(14.dp)
                val chipBg by animateColorAsState(if (selected) scheme.primaryContainer else Color.Transparent, label = "topicBg")
                val chipText by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurface, label = "topicText")
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipText,
                    maxLines = 1,
                    modifier = Modifier
                        // Draws 40 dp tall but answers to a full 48 dp touch.
                        .minimumInteractiveComponentSize()
                        .background(chipBg, shape)
                        .border(1.dp, if (selected) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant, shape)
                        .clip(shape)
                        .selectable(selected = selected, role = Role.Tab) { viewModel.setNewsTopic(id, t) }
                        .heightIn(min = 40.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                NewsCatalog.plan(prefs, topic).isEmpty() -> NewsMessage(
                    Icons.AutoMirrored.Outlined.Article,
                    if (prefs.providers.isEmpty() && prefs.custom.isEmpty()) "Choose your news sources" else "Your sources don't cover ${topic.label}",
                    "Pick from the list, or add a feed of your own.",
                    "Choose sources", onOptions, Modifier.align(Alignment.Center),
                )
                state.items.isEmpty() && state.loading -> NewsSkeleton(prefs.style, prefs.images)
                state.items.isEmpty() -> NewsMessage(
                    Icons.Outlined.CloudOff,
                    "Couldn't load headlines",
                    "Check your connection and try again.",
                    "Try again", { viewModel.loadNews(id, force = true) }, Modifier.align(Alignment.Center),
                )
                else -> {
                    val now = System.currentTimeMillis()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = NivoSpace.l, end = NivoSpace.l, top = NivoSpace.m, bottom = NivoSpace.xl),
                        verticalArrangement = Arrangement.spacedBy(if (prefs.style == NewsStyle.Headlines) 0.dp else NivoSpace.m),
                    ) {
                        // Some chosen sources couldn't be reached: say which, once, quietly.
                        if (state.failedSources.isNotEmpty() && !state.loading) item(key = "failed") {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceContainerLow)
                                    .clickable(role = Role.Button, onClickLabel = "Try again") { viewModel.loadNews(id, force = true) }
                                    .padding(horizontal = NivoSpace.m, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                                Spacer(Modifier.width(NivoSpace.s))
                                Text(
                                    "Couldn't reach " + state.failedSources.take(2).joinToString(", ") + if (state.failedSources.size > 2) " +${state.failedSources.size - 2}" else "",
                                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                Text("Retry", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(start = NivoSpace.s))
                            }
                        }
                        items(state.items.size, key = { state.items[it].link }) { i ->
                            val item = state.items[i]
                            when (prefs.style) {
                                NewsStyle.Cards -> NewsCard(item, featured = i == 0, now = now, showImage = prefs.images) { open(item) }
                                NewsStyle.Compact -> CompactRow(item, now, prefs.images) { open(item) }
                                NewsStyle.Headlines -> HeadlineRow(item, now, last = i == state.items.lastIndex) { open(item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Picture on the left, headline on the right: more stories per screen. */
@Composable
private fun CompactRow(item: NewsItem, now: Long, showImage: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val image = if (showImage) rememberRemoteImage(item.imageUrl, widthDp = 84) else null
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressFeedback(press).clip(shape).background(scheme.surfaceContainer).border(1.dp, scheme.outlineVariant, shape)
            .clickable(interactionSource = press, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showImage) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Crossfade(image, animationSpec = tween(LocalMotion.current.duration(300)), label = "compactImage") { bmp ->
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // No picture (yet): a quiet glyph instead of an empty grey square.
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.Article, null, Modifier.size(28.dp), tint = scheme.onSurfaceVariant.copy(alpha = 0.35f))
                    }
                }
            }
            Spacer(Modifier.width(NivoSpace.m))
        }
        Column(Modifier.weight(1f).padding(vertical = NivoSpace.xxs)) {
            SourceAge(item, now, scheme.onSurfaceVariant)
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Text only, divided by hairlines: the quietest way to read. */
@Composable
private fun HeadlineRow(item: NewsItem, now: Long, last: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(top = NivoSpace.m, start = NivoSpace.xs, end = NivoSpace.xs)) {
        SourceAge(item, now, scheme.onSurfaceVariant)
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (!last) androidx.compose.material3.HorizontalDivider(Modifier.padding(top = NivoSpace.m), color = scheme.outlineVariant)
        else Spacer(Modifier.height(NivoSpace.m))
    }
}

@Composable
private fun SourceAge(item: NewsItem, now: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.source.ifBlank { "News" }.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        val ago = NewsFormat.ago(now, item.publishedAt)
        if (ago.isNotEmpty()) Text("  ·  $ago", style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** An icon on a tinted disc, a headline, a line and one action: the page's empty and error states. */
@Composable
private fun NewsMessage(icon: ImageVector, title: String, line: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = NivoSpace.xxl, vertical = NivoSpace.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(32.dp), tint = scheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = NivoSpace.l))
        Text(
            line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = NivoSpace.xs),
        )
        FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = NivoSpace.l)) { Text(action) }
    }
}

/** Placeholder shapes in the chosen style while the first headlines arrive, gently pulsing (still under reduced motion). */
@Composable
private fun NewsSkeleton(style: NewsStyle, images: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val pulse by rememberInfiniteTransition(label = "newsSkeleton").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "newsSkeletonPulse",
    )
    val fill = scheme.surfaceContainerHigh
    val line = scheme.surfaceContainerHighest
    Column(
        Modifier.fillMaxSize().padding(start = NivoSpace.l, end = NivoSpace.l, top = NivoSpace.m)
            .alpha(if (reduced) 0.8f else pulse)
            .semantics(mergeDescendants = true) { contentDescription = "Loading headlines" },
        verticalArrangement = Arrangement.spacedBy(NivoSpace.m),
    ) {
        repeat(4) { i ->
            when (style) {
                NewsStyle.Cards -> Box(
                    Modifier.fillMaxWidth().height(if (i == 0) (if (images) 260.dp else 150.dp) else (if (images) 176.dp else 112.dp))
                        .background(fill, RoundedCornerShape(22.dp)),
                ) {
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Box(Modifier.width(72.dp).height(8.dp).background(line, CircleShape))
                        Box(Modifier.padding(top = NivoSpace.s).fillMaxWidth(0.8f).height(14.dp).background(line, CircleShape))
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(14.dp).background(line, CircleShape))
                    }
                }
                else -> Row(
                    Modifier.fillMaxWidth().background(if (style == NewsStyle.Compact) fill else Color.Transparent, RoundedCornerShape(16.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (style == NewsStyle.Compact && images) {
                        Box(Modifier.size(84.dp).background(line, RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(NivoSpace.m))
                    }
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.width(64.dp).height(8.dp).background(line, CircleShape))
                        Box(Modifier.padding(top = NivoSpace.s).fillMaxWidth(0.9f).height(12.dp).background(line, CircleShape))
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(12.dp).background(line, CircleShape))
                    }
                }
            }
        }
    }
}

/** A story as a picture card: the photo fills the card, a scrim lifts the text, source and age sit above the headline. */
@Composable
private fun NewsCard(item: NewsItem, featured: Boolean, now: Long, showImage: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val image = if (showImage) rememberRemoteImage(item.imageUrl, widthDp = 400) else null
    val press = remember { MutableInteractionSource() }
    // Without pictures the card is a calm tinted panel with theme-coloured text; with them, a photo under a scrim and white text.
    val onCard = if (showImage) Color.White else scheme.onSurface
    val onCardQuiet = if (showImage) Color.White.copy(alpha = 0.78f) else scheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (!showImage) (if (featured) 150.dp else 112.dp) else if (featured) 260.dp else 176.dp)
            .pressFeedback(press)
            .clip(shape)
            .background(
                if (showImage) Brush.linearGradient(listOf(scheme.surfaceContainerHigh, lerp(scheme.surfaceContainerHigh, scheme.primary, 0.28f)))
                else Brush.linearGradient(listOf(scheme.surfaceContainer, lerp(scheme.surfaceContainer, scheme.primaryContainer, 0.9f)))
            )
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(interactionSource = press, indication = ripple(), role = Role.Button, onClick = onClick),
    ) {
        if (showImage) Crossfade(image, animationSpec = tween(LocalMotion.current.duration(350)), label = "newsImage") { bitmap ->
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        // Scrim: clear at the top so the photo reads, dark at the bottom so the headline always does.
        if (showImage) Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.10f),
                    0.45f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.88f),
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = NivoSpace.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.source.ifBlank { "News" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardQuiet,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val ago = NewsFormat.ago(now, item.publishedAt)
                if (ago.isNotEmpty()) Text("  ·  $ago", style = MaterialTheme.typography.labelSmall, color = onCardQuiet, maxLines = 1)
            }
            Text(
                item.title,
                style = if (featured) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = onCard,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private val imageCache = object : android.util.LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtLeast(1 shl 20)) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Loads a picture off the main thread, sampled down to screen width, and remembers it for the session. */
@Composable
private fun rememberRemoteImage(url: String?, widthDp: Int): ImageBitmap? {
    // Decoded no larger than it is drawn: a full-size photo for an 84 dp thumbnail cost memory and a slow texture upload.
    val targetPx = with(androidx.compose.ui.platform.LocalDensity.current) { widthDp.dp.roundToPx() }
    val key = url?.let { "$it@$targetPx" }
    val state = produceState<ImageBitmap?>(initialValue = key?.let { imageCache.get(it) }, key) {
        if (url == null || key == null) { value = null; return@produceState }
        value = imageCache.get(key) ?: loadImage(url, key, targetPx)
    }
    return state.value
}

private val imageScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

/** One download per picture even when several rows ask for it at once. */
private val inFlight = HashMap<String, kotlinx.coroutines.Deferred<ImageBitmap?>>()

private suspend fun loadImage(url: String, key: String, targetPx: Int): ImageBitmap? {
    val job = synchronized(inFlight) {
        inFlight.getOrPut(key) {
            imageScope.async {
                try { downloadImage(url, targetPx)?.also { imageCache.put(key, it) } }
                finally { synchronized(inFlight) { inFlight.remove(key) } }
            }
        }
    }
    return job.await()
}

private fun downloadImage(url: String, targetPx: Int): ImageBitmap? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 5000; c.readTimeout = 8000
    c.setRequestProperty("User-Agent", "NivoLauncher")
    val bytes = try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
    bitmap.prepareToDraw()   // upload to the GPU now, off the main thread, not on the first frame it scrolls into view
    bitmap.asImageBitmap()
}.getOrNull()
