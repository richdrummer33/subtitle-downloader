package com.subsnag.util

object YouTubeUrlParser {
    private val patterns = listOf(
        Regex("(?:v=|/)([a-zA-Z0-9_-]{11})(?:[&?]|$)"),  // Standard watch URL
        Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),          // Short URL
        Regex("embed/([a-zA-Z0-9_-]{11})"),               // Embed URL
        Regex("shorts/([a-zA-Z0-9_-]{11})")               // Shorts URL
    )

    /**
     * Extracts video ID from various YouTube URL formats.
     * Returns null if no valid video ID is found.
     */
    fun extractVideoId(url: String): String? {
        for (pattern in patterns) {
            val matchResult = pattern.find(url)
            if (matchResult != null && matchResult.groupValues.size > 1) {
                return matchResult.groupValues[1]
            }
        }
        return null
    }

    /**
     * Validates if the given string is a valid YouTube URL.
     */
    fun isValidYouTubeUrl(url: String): Boolean {
        return extractVideoId(url) != null
    }
}
