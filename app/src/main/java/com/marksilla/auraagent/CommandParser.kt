package com.marksilla.auraagent

/**
 * Extracts the application name from natural "open app" commands.
 *
 * Examples:
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

    // Remove punctuation that commonly appears in voice commands.
    text = text
        .replace("?", " ")
        .replace("!", " ")
        .replace(".", " ")
        .replace(",", " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    // Remove the assistant's name when the user says:
    // "AURA, open Facebook"
    // "Hey AURA, open Facebook"
    val assistantPrefixes = listOf(
        "hey aura ",
        "hi aura ",
        "hello aura ",
        "aura ",
        "hey aura, ",
        "hi aura, ",
        "hello aura, "
    )

    for (prefix in assistantPrefixes) {
        if (text.startsWith(prefix)) {
            text = text.removePrefix(prefix).trim()
            break
        }
    }

    // Remove common polite/conversational phrases.
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
            text = text.removePrefix(prefix).trim()
            break
        }
    }

    /*
     * Longer phrases MUST come before shorter phrases.
     *
     * Example:
     * "buksan mo facebook"
     *
     * If "buksan " comes first, the result becomes:
     * "mo facebook"
     *
     * So "buksan mo " comes first.
     */
    val openPrefixes = listOf(

        // English conversational
        "open up ",
        "open for me ",
        "open it for me ",
        "open the app ",
        "open app ",
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

        // English navigation
        "take me to ",
        "take me into ",
        "take me inside ",
        "bring me to ",
        "bring me into ",
        "go to ",
        "go into ",
        "get me to ",
        "get me into ",
        "get me in ",

        // English conversational
        "i want you to open ",
        "i need you to open ",
        "i want to open ",
        "i need to open ",
        "i'd like to open ",
        "i would like to open ",
        "please open ",
        "please launch ",
        "please start ",
        "please run ",

        // Tagalog
        "buksan mo ang ",
        "buksan mo yung ",
        "buksan mo ang app na ",
        "buksan mo yung app na ",
        "buksan mo ",
        "buksan ang ",
        "buksan yung ",
        "buksan ang app na ",
        "buksan yung app na ",

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

        // "Paki" variations
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

        // Tagalog conversational
        "pwede mo bang buksan ang ",
        "pwede mo bang buksan yung ",
        "pwede mo bang buksan ",
        "pwede bang buksan ang ",
        "pwede bang buksan yung ",
        "pwede bang buksan ",
        "maaari mo bang buksan ang ",
        "maaari mo bang buksan yung ",
        "maaari mo bang buksan ",
        "gusto kong buksan ang ",
        "gusto kong buksan yung ",
        "gusto kong buksan ",
        "puntahan mo ang ",
        "puntahan mo yung ",
        "puntahan mo ",

        // Casual phrases
        "let's open ",
        "lets open ",
        "go ahead and open ",
        "go ahead open ",
        "get ",
        "open up "
    )

    for (prefix in openPrefixes) {

        if (text.startsWith(prefix)) {

            val appName = text
                .removePrefix(prefix)
                .trim()

            if (appName.isNotBlank()) {
                return cleanAppName(appName)
            }

            return null
        }
    }

    /*
     * Handle commands where the user simply says:
     *
     * "Facebook please"
     * "Facebook"
     *
     * We only return a name for these if they look like a
     * simple app request.
     */
    val trailingRequestWords = listOf(
        " please",
        " naman",
        " nga",
        " na",
        " ngayon"
    )

    for (suffix in trailingRequestWords) {
        if (text.endsWith(suffix)) {
            text = text.removeSuffix(suffix).trim()
            break
        }
    }

    // Don't treat random sentences as app names.
    if (text.contains(" ")) {
        return null
    }

    if (text.isBlank()) {
        return null
    }

    return cleanAppName(text)
}


/**
 * Cleans the extracted application name.
 */
private fun cleanAppName(appName: String): String {

    var result = appName
        .trim()
        .replace(Regex("\\s+"), " ")

    // Remove common trailing conversational words.
    val trailingWords = listOf(
        " please",
        " for me",
        " naman",
        " nga",
        " na"
    )

    for (suffix in trailingWords) {
        if (result.endsWith(suffix)) {
            result = result
                .removeSuffix(suffix)
                .trim()
        }
    }

    // Remove "the app" if it was accidentally included.
    result = result
        .removePrefix("the app ")
        .removePrefix("app ")

    return result.trim()
}
