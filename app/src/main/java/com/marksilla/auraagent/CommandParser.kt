package com.marksilla.auraagent

/**
 * Converts natural language into an application name.
 *
 * Examples:
 *
 * "Open Facebook"                  -> "facebook"
 * "Please open Facebook"           -> "facebook"
 * "Can you open Facebook?"         -> "facebook"
 * "Could you please launch Chrome" -> "chrome"
 * "Buksan mo Facebook"             -> "facebook"
 * "Paki buksan Messenger"          -> "messenger"
 * "Take me to YouTube"             -> "youtube"
 * "Hey AURA, open TikTok"          -> "tiktok"
 */
fun extractOpenCommand(command: String): String? {

    var text = command
        .trim()
        .lowercase()

    if (text.isBlank()) {
        return null
    }

    // Normalize punctuation.
    text = text
        .replace("?", " ")
        .replace("!", " ")
        .replace(".", " ")
        .replace(",", " ")
        .replace(";", " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    // Remove AURA's name when included.
    val assistantPrefixes = listOf(
        "hey aura ",
        "hi aura ",
        "hello aura ",
        "aura "
    )

    for (prefix in assistantPrefixes) {
        if (text.startsWith(prefix)) {
            text = text
                .removePrefix(prefix)
                .trim()
            break
        }
    }

    // Remove polite phrases.
    val politePrefixes = listOf(
        "could you please ",
        "can you please ",
        "would you please ",
        "will you please ",
        "could you ",
        "can you ",
        "would you ",
        "will you ",
        "please ",
        "kindly ",
        "pakiusap ",
        "paki "
    )

    for (prefix in politePrefixes) {
        if (text.startsWith(prefix)) {
            text = text
                .removePrefix(prefix)
                .trim()
            break
        }
    }

    /*
     * Longer phrases come first.
     *
     * This prevents:
     *
     * "buksan mo Facebook"
     *
     * from becoming:
     *
     * "mo Facebook"
     */
    val openPrefixes = listOf(

        // English
        "open up ",
        "open the app ",
        "open app ",
        "open for me ",
        "open it for me ",
        "open ang ",
        "open yung ",
        "open ",
        "launch the app ",
        "launch app ",
        "launch ",
        "start the app ",
        "start app ",
        "start ",
        "run the app ",
        "run app ",
        "run ",

        // Navigation
        "take me to ",
        "take me into ",
        "bring me to ",
        "bring me into ",
        "go to ",
        "go into ",
        "get me to ",
        "get me into ",

        // Conversational English
        "i want you to open ",
        "i need you to open ",
        "i want to open ",
        "i need to open ",
        "i'd like to open ",
        "i would like to open ",
        "go ahead and open ",

        // Tagalog
        "buksan mo ang app na ",
        "buksan mo yung app na ",
        "buksan mo ang ",
        "buksan mo yung ",
        "buksan mo ",
        "buksan ang app na ",
        "buksan yung app na ",
        "buksan ang ",
        "buksan yung ",
        "buksan ",

        // Taglish
        "i-open mo ang ",
        "i-open mo yung ",
        "i-open mo ",
        "open mo ang ",
        "open mo yung ",
        "open mo ",
        "launch mo ang ",
        "launch mo yung ",
        "launch mo ",
        "start mo ang ",
        "start mo yung ",
        "start mo ",
        "run mo ang ",
        "run mo yung ",
        "run mo ",

        // Paki variations
        "paki-buksan ang ",
        "paki-buksan yung ",
        "paki-buksan ",
        "paki buksan ang ",
        "paki buksan yung ",
        "paki buksan ",
        "paki-open ang ",
        "paki-open yung ",
        "paki-open ",
        "paki open ang ",
        "paki open yung ",
        "paki open ",

        // Tagalog questions
        "pwede mo bang buksan ang ",
        "pwede mo bang buksan yung ",
        "pwede mo bang buksan ",
        "pwede bang buksan ang ",
        "pwede bang buksan yung ",
        "pwede bang buksan ",
        "maaari mo bang buksan ang ",
        "maaari mo bang buksan yung ",
        "maaari mo bang buksan ",

        // Other natural phrases
        "gusto kong buksan ang ",
        "gusto kong buksan yung ",
        "gusto kong buksan ",
        "puntahan mo ang ",
        "puntahan mo yung ",
        "puntahan mo ",
        "let's open ",
        "lets open "
    )

    for (prefix in openPrefixes) {

        if (text.startsWith(prefix)) {

            val appName = text
                .removePrefix(prefix)
                .trim()

            if (appName.isBlank()) {
                return null
            }

            return cleanAppName(appName)
        }
    }

    /*
     * Simple requests:
     *
     * "Facebook please"
     * "Facebook"
     *
     * We only accept a single word here so that
     * random sentences aren't interpreted as apps.
     */
    val trailingWords = listOf(
        " please",
        " naman",
        " nga",
        " na",
        " ngayon"
    )

    for (suffix in trailingWords) {
        if (text.endsWith(suffix)) {
            text = text
                .removeSuffix(suffix)
                .trim()
            break
        }
    }

    if (text.isBlank()) {
        return null
    }

    if (text.contains(" ")) {
        return null
    }

    return cleanAppName(text)
}

/**
 * Cleans the application name before matching it.
 */
private fun cleanAppName(appName: String): String {

    var result = appName
        .trim()
        .replace(Regex("\\s+"), " ")

    val trailingWords = listOf(
        " application",
        " app",
        " please",
        " for me",
        " naman",
        " nga",
        " na",
        " now"
    )

    for (suffix in trailingWords) {
        if (result.endsWith(suffix)) {
            result = result
                .removeSuffix(suffix)
                .trim()
        }
    }

    result = result
        .removePrefix("the app ")
        .removePrefix("app ")

    return result.trim()
}
