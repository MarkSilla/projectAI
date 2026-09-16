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

    val query = normalizeAppText(requestedName)

    if (query.spaced.isBlank()) {
        return null
    }

    val queryKeys =
        listOf(query) + appAliases(query.spaced)
            .map(::normalizeAppText)

    // Exact match
    apps.firstOrNull {
        val appName = normalizeAppText(it.name)
        queryKeys.any { key ->
            appName.spaced == key.spaced ||
                appName.compact == key.compact
        }
    }?.let {
        return it
    }

    // Starts with
    apps.firstOrNull {
        val appName = normalizeAppText(it.name)
        queryKeys.any { key ->
            appName.spaced.startsWith(key.spaced) ||
                appName.compact.startsWith(key.compact)
        }
    }?.let {
        return it
    }

    // Contains
    return apps.firstOrNull {
        val appName = normalizeAppText(it.name)
        queryKeys.any { key ->
            appName.spaced.contains(key.spaced) ||
                appName.compact.contains(key.compact)
        }
    }
}

fun findAppCandidates(
    apps: List<InstalledApp>,
    command: String,
    limit: Int = 5
): List<InstalledApp> {
    val normalized =
        command
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(
                Regex("\\b(open|launch|start|run|buksan|paki|please|app|application|the|yung|ang)\\b"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()

    val categoryAliases =
        when {
            normalized.contains("chat") || normalized.contains("message") ->
                setOf("messenger", "whatsapp", "discord", "telegram", "teams", "slack")

            normalized.contains("video") ->
                setOf("youtube", "tiktok", "netflix")

            else -> emptySet()
        }

    return apps
        .map { app ->
            val name = app.name.lowercase()
            val words = normalized.split(" ").filter { it.length > 1 }
            val score =
                when {
                    categoryAliases.any { alias -> name.contains(alias) } -> 3
                    words.any { word -> name.contains(word) } -> 2
                    else -> 0
                }
            app to score
        }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<InstalledApp, Int>> { it.second }
                .thenBy { it.first.name.lowercase() }
        )
        .map { it.first }
        .distinctBy { it.packageName }
        .take(limit)
}

private data class AppSearchText(
    val spaced: String,
    val compact: String
)

private fun normalizeAppText(text: String): AppSearchText {
    val spaced =
        text
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    return AppSearchText(
        spaced = spaced,
        compact = spaced.replace(" ", "")
    )
}

private fun appAliases(query: String): List<String> =
    when (query) {
        "fb",
        "face book" -> listOf("facebook")

        "ig",
        "insta" -> listOf("instagram")

        "yt",
        "you tube" -> listOf("youtube")

        "msgs",
        "messenger app" -> listOf("messenger")

        else -> emptyList()
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

    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
