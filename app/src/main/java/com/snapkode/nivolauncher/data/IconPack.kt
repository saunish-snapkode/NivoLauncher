package com.snapkode.nivolauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/** An installed icon pack (ADW / Nova style `appfilter.xml`) mapping activities to drawables. */
class IconPack private constructor(
    private val resources: Resources,
    private val packageName: String,
    private val mapping: Map<String, String>,
) {
    /** Every distinct icon name this pack maps to, for the icon picker. */
    fun drawableNames(): List<String> = mapping.values.distinct().sorted()

    fun drawableByName(name: String): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return runCatching { ResourcesCompat.getDrawable(resources, id, null) }.getOrNull()
    }

    fun drawableFor(component: ComponentName): Drawable? {
        val name = mapping["ComponentInfo{${component.packageName}/${component.className}}"] ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return runCatching { ResourcesCompat.getDrawable(resources, id, null) }.getOrNull()
    }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "app.lawnchair.icons.THEMED_ICON",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return ACTIONS.flatMap { action ->
                pm.queryIntentActivities(Intent(action), 0).map {
                    IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString())
                }
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }

        fun load(context: Context, packageName: String): IconPack? = runCatching {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val parser = openAppFilter(resources, packageName) ?: return null
            val map = HashMap<String, String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) map.putIfAbsent(component, drawable)
                }
                event = parser.next()
            }
            IconPack(resources, packageName, map)
        }.getOrNull()

        private fun openAppFilter(resources: Resources, pkg: String): XmlPullParser? {
            val xmlId = resources.getIdentifier("appfilter", "xml", pkg)
            if (xmlId != 0) return resources.getXml(xmlId)
            val stream = runCatching { resources.assets.open("appfilter.xml") }.getOrNull() ?: return null
            return XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, null) }
        }
    }
}
