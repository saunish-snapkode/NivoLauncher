package com.snapkode.nivolauncher.pagelet

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/** The "Usage access" special permission that Wellbeing needs to read screen time on this phone. */
object UsageAccess {
    fun isGranted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The Usage access screen; on Android 10+ it opens straight at this app's switch. */
    fun settingsIntent(context: Context? = null): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            if (context != null && Build.VERSION.SDK_INT >= 29) data = android.net.Uri.fromParts("package", context.packageName, null)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts [settingsIntent], falling back to the plain list if the direct link isn't supported on this phone. */
    fun open(context: Context) {
        try { context.startActivity(settingsIntent(context)) }
        catch (e: Exception) { runCatching { context.startActivity(settingsIntent()) } }
    }
}
