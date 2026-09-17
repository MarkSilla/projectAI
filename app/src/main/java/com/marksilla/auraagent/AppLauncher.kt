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

data class AppMatchCandidate(
    val app: InstalledApp,
    val score: Float
)

fun findApp(
    apps: List<InstalledApp>,
    requestedName: String
): InstalledApp? {
    val matches = findAppMatches(apps, requestedName, limit = 3)
    if (matches.isEmpty()) {
        return null
    }

    val top = matches.first()
    val next = matches.getOrNull(1)
    return if (top.score >= 0.72f && (next == null || top.score - next.score >= 0.10f)) {
        top.app
    } else {
        null
    }
}

fun findAppMatches(
    apps: List<InstalledApp>,
    requestedName: String,
    limit: Int = 3
): List<AppMatchCandidate> {
    val query = normalizeAppText(requestedName)
    if (query.spaced.isBlank()) {
        return emptyList()
    }

    val queryKeys =
        listOf(query) + appAliases(query.spaced)
            .map(::normalizeAppText)

    val exactMatches = mutableListOf<AppMatchCandidate>()
    val prefixMatches = mutableListOf<AppMatchCandidate>()
    val containsMatches = mutableListOf<AppMatchCandidate>()
    val fuzzyMatches = mutableListOf<AppMatchCandidate>()

    for (app in apps) {
        val appName = normalizeAppText(app.name)
        for (key in queryKeys) {
            val score = when {
                appName.spaced == key.spaced || appName.compact == key.compact -> 1.0f
                appName.spaced.startsWith(key.spaced) || appName.compact.startsWith(key.compact) -> 0.95f
                appName.spaced.contains(key.spaced) || appName.compact.contains(key.compact) -> 0.9f
                else -> fuzzyAppMatchScore(key, appName)
            }

            if (score <= 0.0f) continue
            val candidate = AppMatchCandidate(app, score)
            when {
                score >= 0.95f -> exactMatches += candidate
                score >= 0.90f -> prefixMatches += candidate
                score >= 0.85f -> containsMatches += candidate
                score > 0.0f -> fuzzyMatches += candidate
            }
        }
    }

    return (exactMatches + prefixMatches + containsMatches + fuzzyMatches)
        .distinctBy { it.app.packageName }
        .sortedByDescending { it.score }
        .take(limit)
}

private fun fuzzyAppMatchScore(
    query: AppSearchText,
    appName: AppSearchText
): Float {
    if (query.spaced.isBlank() || appName.spaced.isBlank()) {
        return 0.0f
    }

    if (query.compact == appName.compact) {
        return 1.0f
    }

    val compactQuery = query.compact
    val compactApp = appName.compact

    if (compactApp.startsWith(compactQuery) || compactQuery.startsWith(compactApp)) {
        return 0.9f
    }

    if (compactApp.contains(compactQuery) || compactQuery.contains(compactApp)) {
        return 0.85f
    }

    val levenshtein = levenshteinDistance(compactQuery, compactApp)
    val maxLength = maxOf(compactQuery.length, compactApp.length)
    if (maxLength == 0) {
        return 0.0f
    }

    val distanceRatio = 1.0f - (levenshtein.toFloat() / maxLength.toFloat())
    val prefixBonus = if (compactApp.firstOrNull() == compactQuery.firstOrNull()) 0.12f else 0.0f
    val finalScore = distanceRatio + prefixBonus

    return if (finalScore >= 0.72f) finalScore else 0.0f
}

private fun levenshteinDistance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length

    val previous = IntArray(right.length + 1)
    val current = IntArray(right.length + 1)

    for (index in 0..right.length) {
        previous[index] = index
    }

    for (i in left.indices) {
        current[0] = i + 1
        for (j in right.indices) {
            val cost = if (left[i] == right[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }

        for (j in 0..right.length) {
            previous[j] = current[j]
        }
    }

    return current[right.length]
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
