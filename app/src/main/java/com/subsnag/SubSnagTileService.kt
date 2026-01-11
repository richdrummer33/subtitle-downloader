package com.subsnag

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.subsnag.util.ClipboardHelper
import com.subsnag.util.YouTubeUrlParser

class SubSnagTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // Get URL from clipboard
        val clipboardText = ClipboardHelper.getClipboardText(this)

        if (clipboardText == null) {
            showToast("Copy a YouTube link first")
            return
        }

        val videoId = YouTubeUrlParser.extractVideoId(clipboardText)

        if (videoId == null) {
            showToast("No valid YouTube link in clipboard")
            return
        }

        // Launch SubtitleActivity
        val intent = Intent(this, SubtitleActivity::class.java).apply {
            putExtra("videoId", videoId)
            putExtra("url", clipboardText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }

    private fun updateTileState() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
