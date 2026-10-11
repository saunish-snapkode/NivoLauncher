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
                Text("Headlines", style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
                Text(
                    if (state.loading && state.items.isEmpty()) "Fetching headlines…" else "${topic.label} · ${NewsCatalog.summary(prefs)}",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { viewModel.loadNews(id, force = true) }) { Icon(Icons.Default.Refresh, "Refresh headlines", tint = scheme.onSurfaceVariant) }
            IconButton(onClick = onOptions) { Icon(Icons.Outlined.Tune, "Customise headlines", tint = scheme.onSurfaceVariant) }
        }
        LazyRow(
            modifier = Modifier.edgeFade(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(topics) { t ->
                val selected = t == topic
                val shape = RoundedCornerShape(14.dp)
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.primary else scheme.onSurface,
                    modifier = Modifier
                        .background(if (selected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, shape)
                        .border(1.dp, if (selected) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant, shape)
                        .clip(shape)
                        .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab) { viewModel.setNewsTopic(id, t) }
                        .heightIn(min = 40.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                NewsCatalog.plan(prefs, topic).isEmpty() -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (prefs.providers.isEmpty() && prefs.custom.isEmpty()) "Choose your news sources" else "Your sources don't cover ${topic.label}", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Pick from the list, or add a feed of your own.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = onOptions) { Text("Choose sources") }
                }
                state.items.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                state.items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't load headlines", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Check your connection and try again.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { viewModel.loadNews(id, force = true) }) { Text("Retry") }
                }
                else -> {
                    val now = System.currentTimeMillis()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(if (prefs.style == NewsStyle.Headlines) 0.dp else 12.dp),
                    ) {
                        // Some chosen sources couldn't be reached: say which, once, quietly.
                        if (state.failedSources.isNotEmpty() && !state.loading) item(key = "failed") {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { viewModel.loadNews(id, force = true) }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Couldn't reach " + state.failedSources.take(2).joinToString(", ") + if (state.failedSources.size > 2) " +${state.failedSources.size - 2}" else "",
                                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                                )
                                Text("Retry", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
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
    Row(
        Modifier.fillMaxWidth().clip(shape).background(scheme.surfaceContainer).border(1.dp, scheme.outlineVariant, shape).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showImage) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh)) {
                Crossfade(image, animationSpec = tween(300), label = "compactImage") { bmp ->
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            SourceAge(item, now, scheme.onSurfaceVariant)
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Text only, divided by hairlines: the quietest way to read. */
@Composable
private fun HeadlineRow(item: NewsItem, now: Long, last: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        SourceAge(item, now, scheme.onSurfaceVariant)
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (!last) androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 12.dp), color = scheme.outlineVariant)
    }
}

@Composable
private fun SourceAge(item: NewsItem, now: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.source.ifBlank { "News" }.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        val ago = NewsFormat.ago(now, item.publishedAt)
        if (ago.isNotEmpty()) Text("  ·  $ago", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** A story as a picture card: the photo fills the card, a scrim lifts the text, source and age sit above the headline. */
@Composable
private fun NewsCard(item: NewsItem, featured: Boolean, now: Long, showImage: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val image = if (showImage) rememberRemoteImage(item.imageUrl, widthDp = 400) else null
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (!showImage) (if (featured) 150.dp else 112.dp) else if (featured) 260.dp else 176.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(scheme.surfaceContainerHigh, lerp(scheme.surfaceContainerHigh, scheme.primary, 0.28f))))
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Crossfade(image, animationSpec = tween(350), label = "newsImage") { bitmap ->
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        // Scrim: clear at the top so the photo reads, dark at the bottom so the headline always does.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.10f),
                    0.45f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.88f),
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.source.ifBlank { "News" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val ago = NewsFormat.ago(now, item.publishedAt)
                if (ago.isNotEmpty()) Text("  ·  $ago", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f))
            }
            Text(
                item.title,
                style = if (featured) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = if (featured) 3 else 3,
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
