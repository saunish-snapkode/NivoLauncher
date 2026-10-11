package com.snapkode.nivolauncher.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loads every launchable activity and keeps the list fresh as packages change. */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadLock = Mutex()
    @Volatile private var style = IconStyle()
    private val overridePacks = HashMap<String, IconPack?>()
    @Volatile private var pack: IconPack? = null
    private val iconPx = (ICON_DP * appContext.resources.displayMetrics.density).toInt()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    data class IconStyle(
        val shape: IconShape = IconShape.Original,
        val pack: String = "",
        val themed: Boolean = false,
        val wrapLegacy: Boolean = false,
        val overrides: Map<String, String> = emptyMap(),
    )

    fun setStyle(newStyle: IconStyle) {
        if (newStyle == style) return
        if (newStyle.pack != style.pack) pack = if (newStyle.pack.isEmpty()) null else IconPack.load(appContext, newStyle.pack)
        style = newStyle
        reload()
    }

    /** Loads a user-chosen icon: "file:<name>" from app storage or "pack:<package>/<drawable>". */
    private fun overrideIcon(spec: String?): android.graphics.Bitmap? {
        spec ?: return null
        return when {
            spec.startsWith("file:") -> android.graphics.BitmapFactory.decodeFile(
                java.io.File(java.io.File(appContext.filesDir, "icons"), spec.removePrefix("file:")).path
            )?.let { android.graphics.Bitmap.createScaledBitmap(it, iconPx, iconPx, true) }
            spec.startsWith("pack:") -> {
                val (pkg, name) = spec.removePrefix("pack:").split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val loaded = overridePacks.getOrPut(pkg) { IconPack.load(appContext, pkg) }
                loaded?.drawableByName(name)?.toBitmap(iconPx, iconPx)
            }
            else -> null
        }
    }

    private fun themeColors(): IconRenderer.ThemeColors? {
        if (!style.themed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val night = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return IconRenderer.ThemeColors(
            background = appContext.getColor(if (night) android.R.color.system_accent1_800 else android.R.color.system_accent1_100),
            foreground = appContext.getColor(if (night) android.R.color.system_accent1_100 else android.R.color.system_accent1_700),
        )
    }

    fun reload() {
        overridePacks.clear()
        scope.launch {
            loadLock.withLock {
                _apps.value = queryAll()
                _loaded.value = true
            }
        }
    }

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    private fun queryAll(): List<AppInfo> {
        val result = ArrayList<AppInfo>()
        for (user in userManager.userProfiles) {
            val serial = serialOf(user)
            for (activity in launcherApps.getActivityList(null, user)) {
                val key = AppKey(activity.componentName, serial)
                val icon = runCatching {
                    overrideIcon(style.overrides[key.encode()])
                        ?: pack?.drawableFor(activity.componentName)?.toBitmap(iconPx, iconPx)
                        ?: IconRenderer.render(activity.getBadgedIcon(0), iconPx, style.shape, themeColors(), style.wrapLegacy)
                }
                    .onFailure { android.util.Log.w("NivoApps", "icon failed for ${activity.componentName}", it) }
                    .getOrNull() ?: continue
                result += AppInfo(
                    key = key,
                    label = activity.label?.toString().orEmpty(),
                    user = user,
                    icon = icon.asImageBitmap(),
                    installTime = activity.firstInstallTime,
                )
            }
        }
        return result.sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val ICON_DP = 72
    }
}
