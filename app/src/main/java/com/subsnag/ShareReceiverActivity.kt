package com.subsnag

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.subsnag.util.YouTubeUrlParser

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    handleSharedText(intent)
                } else {
                    showToastAndFinish("Unsupported content type")
                }
            }
            else -> {
                finish()
            }
        }
    }

    private fun handleSharedText(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText == null) {
            showToastAndFinish("No text found")
            return
        }

        val videoId = YouTubeUrlParser.extractVideoId(sharedText)

        if (videoId == null) {
            showToastAndFinish("Not a valid YouTube link")
            return
        }

        // Launch SubtitleActivity
        val subtitleIntent = Intent(this, SubtitleActivity::class.java).apply {
            putExtra("videoId", videoId)
            putExtra("url", sharedText)
        }
        startActivity(subtitleIntent)
        finish()
    }

    private fun showToastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
