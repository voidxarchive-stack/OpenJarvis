package com.openjarvis.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

internal enum class AppAccessLevel(val label: String) {
    ALLOW_ALL("Allow all"),
    READ_ONLY("Read only"),
    DENY("Deny"),
}

internal data class InstalledJarvisApp(
    val label: String,
    val packageName: String,
)

/** Jarvis-side policy only; this never grants Android permissions or Accessibility access. */
internal class AppAccessStore(context: Context) {
    private val preferences = context.getSharedPreferences("jarvis_app_access", Context.MODE_PRIVATE)

    fun get(packageName: String, label: String): AppAccessLevel {
        val saved = preferences.getString(packageName, null)
        return saved?.let { value -> AppAccessLevel.values().firstOrNull { it.name == value } }
            ?: defaultLevel(packageName, label)
    }

    fun set(packageName: String, level: AppAccessLevel) {
        preferences.edit().putString(packageName, level.name).apply()
    }

    private fun defaultLevel(packageName: String, label: String): AppAccessLevel {
        val key = (packageName + " " + label).lowercase()
        val sensitiveMarkers = listOf(
            "gmail", "google.android.apps.nbu.paisa", "googlepay", "phonepe", "paytm",
            "bank", "banking", "upi", "wallet", "cred", "mobikwik", "amazonpay",
            "bhim", "yono", "sbi", "icici", "hdfc", "kotak", "axisbank",
            "imobile", "bobworld", "canara", "unionbank", "idfc", "federalbank",
        )
        return if (sensitiveMarkers.any(key::contains)) AppAccessLevel.DENY else AppAccessLevel.READ_ONLY
    }
}

internal fun queryInstalledLauncherApps(context: Context): List<InstalledJarvisApp> {
    val packageManager = context.packageManager
    val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherQuery, 0)
        .mapNotNull { resolveInfo: ResolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isBlank()) null else InstalledJarvisApp(label, activity.packageName)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
