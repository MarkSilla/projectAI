package com.marksilla.auraagent

import android.content.Context
import android.content.Intent

data class InstalledApp(
    val name: String,
    val packageName: String
)

fun getInstalledApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager

    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    return packageManager
        .queryIntentActivities(launcherIntent, 0)
        .map {
            InstalledApp(
                name = it.loadLabel(packageManager).toString(),
                packageName = it.activityInfo.packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

fun findApp(
    apps: List<InstalledApp>,
    requestedName: String
): InstalledApp? {

    val query = requestedName
        .trim()
        .lowercase()

    if (query.isBlank()) {
        return null
    }

    // Exact match first
    apps.firstOrNull {
        it.name.lowercase() == query
    }?.let {
        return it
    }

    // Then starts-with match
    apps.firstOrNull {
        it.name.lowercase().startsWith(query)
    }?.let {
        return it
    }

    // Finally contains match
    return apps.firstOrNull {
        it.name.lowercase().contains(query)
    }
}

fun extractOpenCommand(command: String): String? {

    val cleaned = command
        .trim()
        .lowercase()

    val prefixes = listOf(
        "open ",
        "launch ",
        "start ",
        "run "
    )

    for (prefix in prefixes) {
        if (cleaned.startsWith(prefix)) {
            return command
                .trim()
                .substring(prefix.length)
                .trim()
        }
    }

    return null
}

fun openApp(
    context: Context,
    app: InstalledApp
): Boolean {

    val intent =
        context.packageManager
            .getLaunchIntentForPackage(app.packageName)
            ?: return false

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    context.startActivity(intent)

    return true
}
