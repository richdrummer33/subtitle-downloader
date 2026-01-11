package com.subsnag.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    /**
     * Gets the current text from clipboard.
     */
    fun getClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            return clipData.getItemAt(0).text?.toString()
        }
        return null
    }

    /**
     * Copies text to clipboard.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "SubSnag") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    /**
     * Gets YouTube URL from clipboard if available.
     */
    fun getYouTubeUrlFromClipboard(context: Context): String? {
        val text = getClipboardText(context) ?: return null
        return if (YouTubeUrlParser.isValidYouTubeUrl(text)) text else null
    }
}
