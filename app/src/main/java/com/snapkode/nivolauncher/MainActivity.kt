package com.snapkode.nivolauncher

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import android.widget.Toast
import com.snapkode.nivolauncher.ui.LauncherRoot
import com.snapkode.nivolauncher.ui.NivoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    /** A widget that has been allocated an id but isn't on the home screen yet. */
    private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo)

    private var pending: PendingWidget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        viewModel.launchOptions = { bounds ->
            bounds?.let {
                ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
            }
        }
        lifecycleScope.launch {
            viewModel.settings.collect { s ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = s.darkStatusIcons
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (s.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
                else controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        lifecycleScope.launch {
            viewModel.widgetRequests.collect { startWidgetFlow(it) }
        }
        setContent {
            NivoTheme(viewModel) { LauncherRoot(viewModel) }
        }
    }

    override fun onDestroy() {
        viewModel.launchOptions = null // don't keep the activity alive through the view model
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        viewModel.syncDynamicWallpaper()
        viewModel.widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        viewModel.flushSettings()
        runCatching { viewModel.widgetHost.stopListening() }
    }

    private fun startWidgetFlow(info: AppWidgetProviderInfo) {
        val id = viewModel.widgetHost.allocateAppWidgetId()
        pending = PendingWidget(id, info)
        val work = viewModel.isWorkWidget(info) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
        val bound = if (work) {
            viewModel.widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } else {
            viewModel.widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        }
        if (bound) {
            afterBind()
        } else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            startActivityForResult(bind, REQUEST_BIND)
        }
    }

    private fun afterBind() {
        val request = pending ?: return
        if (request.info.configure != null) {
            viewModel.widgetHost.startAppWidgetConfigureActivityForResult(this, request.id, 0, REQUEST_CONFIGURE, null)
        } else {
            finishWidget(request)
        }
    }

    private fun finishWidget(request: PendingWidget) {
        pending = null
        val metrics = resources.displayMetrics
        val s = viewModel.settings.value
        val cellW = metrics.widthPixels / metrics.density / s.columns
        val cellH = metrics.heightPixels * 0.7f / metrics.density / s.rows
        val spanX = ceil(request.info.minWidth / metrics.density / cellW).toInt()
        val spanY = ceil(request.info.minHeight / metrics.density / cellH).toInt()
        if (!viewModel.placeWidget(request.id, spanX, spanY)) {
            viewModel.discardWidget(request.id)
            Toast.makeText(this, "No room on the home screen", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Widget bind/configure results arrive here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val request = pending ?: return
        when (requestCode) {
            REQUEST_BIND ->
                if (resultCode == RESULT_OK) afterBind() else { viewModel.discardWidget(request.id); pending = null }
            REQUEST_CONFIGURE ->
                if (resultCode == RESULT_OK) finishWidget(request) else { viewModel.discardWidget(request.id); pending = null }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Themed icons follow light/dark, so re-render them when the system theme flips.
        if (viewModel.settings.value.themedIcons) viewModel.refreshIcons()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we're already in front: go back to the first page.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }

    private companion object {
        const val REQUEST_BIND = 41
        const val REQUEST_CONFIGURE = 42
    }
}
