package com.subsnag

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.subsnag.api.YouTubeApi
import com.subsnag.model.SubtitleTrack
import com.subsnag.model.VideoInfo
import com.subsnag.ui.theme.SubSnagTheme
import com.subsnag.util.ClipboardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import android.util.Log

class SubtitleActivity : ComponentActivity() {

    companion object {
        const val TAG = "SubtitleActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: SubtitleActivity started")

        val videoId = intent.getStringExtra("videoId")
        Log.d(TAG, "onCreate: videoId = $videoId")

        if (videoId == null) {
            Log.e(TAG, "onCreate: Invalid video ID received")
            Toast.makeText(this, "Invalid video ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            SubSnagTheme {
                SubtitleDownloadDialog(
                    videoId = videoId,
                    onDismiss = { finish() },
                    onDownloadSuccess = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onError = { error ->
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

@Composable
fun SubtitleDownloadDialog(
    videoId: String,
    onDismiss: () -> Unit,
    onDownloadSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val youtubeApi = remember { YouTubeApi() }
    val scope = rememberCoroutineScope()

    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTrack by remember { mutableStateOf<SubtitleTrack?>(null) }
    var copyToClipboard by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Fetch video info on launch
    LaunchedEffect(videoId) {
        Log.d(TAG, "LaunchedEffect: Starting to fetch video info for videoId: $videoId")
        isLoading = true
        error = null

        val result = withContext(Dispatchers.IO) {
            youtubeApi.getVideoInfo(videoId)
        }

        result.onSuccess { info ->
            Log.d(TAG, "LaunchedEffect: Successfully fetched video info - title: ${info.title}, tracks: ${info.availableTracks.size}")
            videoInfo = info
            // Auto-select English if available, otherwise first track
            selectedTrack = info.availableTracks.find { it.languageCode == "en" }
                ?: info.availableTracks.firstOrNull()
            Log.d(TAG, "LaunchedEffect: Auto-selected track: ${selectedTrack?.displayName} (${selectedTrack?.languageCode})")
            isLoading = false
        }.onFailure { e ->
            Log.e(TAG, "LaunchedEffect: Failed to load video info", e)
            error = e.message ?: "Failed to load video info"
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    isLoading -> {
                        LoadingContent()
                    }
                    error != null -> {
                        ErrorContent(
                            error = error!!,
                            onRetry = {
                                Log.d(TAG, "ErrorContent: Retry button clicked")
                                scope.launch {
                                    isLoading = true
                                    error = null
                                    val result = withContext(Dispatchers.IO) {
                                        youtubeApi.getVideoInfo(videoId)
                                    }
                                    result.onSuccess { info ->
                                        Log.d(TAG, "ErrorContent: Retry successful - title: ${info.title}")
                                        videoInfo = info
                                        selectedTrack = info.availableTracks.find { it.languageCode == "en" }
                                            ?: info.availableTracks.firstOrNull()
                                        isLoading = false
                                    }.onFailure { e ->
                                        Log.e(TAG, "ErrorContent: Retry failed", e)
                                        error = e.message ?: "Failed to load video info"
                                        isLoading = false
                                    }
                                }
                            },
                            onDismiss = onDismiss
                        )
                    }
                    videoInfo != null -> {
                        VideoInfoContent(
                            videoInfo = videoInfo!!,
                            selectedTrack = selectedTrack,
                            copyToClipboard = copyToClipboard,
                            isDownloading = isDownloading,
                            onTrackSelected = { selectedTrack = it },
                            onCopyToClipboardChanged = { copyToClipboard = it },
                            onDownload = {
                                if (selectedTrack == null) {
                                    Log.w(TAG, "VideoInfoContent: Download attempted with no track selected")
                                    onError("Please select a language")
                                    return@VideoInfoContent
                                }

                                Log.d(TAG, "VideoInfoContent: Starting download - track: ${selectedTrack!!.displayName}, copyToClipboard: $copyToClipboard")
                                scope.launch {
                                    isDownloading = true
                                    val result = withContext(Dispatchers.IO) {
                                        downloadSubtitles(
                                            context = context,
                                            youtubeApi = youtubeApi,
                                            videoInfo = videoInfo!!,
                                            track = selectedTrack!!,
                                            copyToClipboard = copyToClipboard
                                        )
                                    }

                                    isDownloading = false

                                    result.onSuccess { message ->
                                        Log.d(TAG, "VideoInfoContent: Download successful - $message")
                                        onDownloadSuccess(message)
                                    }.onFailure { e ->
                                        Log.e(TAG, "VideoInfoContent: Download failed", e)
                                        onError(e.message ?: "Download failed")
                                    }
                                }
                            },
                            onCancel = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading video info...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun VideoInfoContent(
    videoInfo: VideoInfo,
    selectedTrack: SubtitleTrack?,
    copyToClipboard: Boolean,
    isDownloading: Boolean,
    onTrackSelected: (SubtitleTrack) -> Unit,
    onCopyToClipboardChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Thumbnail
        AsyncImage(
            model = videoInfo.thumbnailUrl,
            contentDescription = "Video thumbnail",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = videoInfo.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Language dropdown
        LanguageDropdown(
            tracks = videoInfo.availableTracks,
            selectedTrack = selectedTrack,
            onTrackSelected = onTrackSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Copy to clipboard checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = copyToClipboard,
                onCheckedChange = onCopyToClipboardChanged
            )
            Text(
                text = "Copy to clipboard after save",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Download button
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isDownloading && selectedTrack != null
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("DOWNLOAD SUBS")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cancel button
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDownloading
        ) {
            Text("CANCEL")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    tracks: List<SubtitleTrack>,
    selectedTrack: SubtitleTrack?,
    onTrackSelected: (SubtitleTrack) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedTrack?.displayName ?: "Select language",
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.displayName) },
                    onClick = {
                        onTrackSelected(track)
                        expanded = false
                    }
                )
            }
        }
    }
}

private suspend fun downloadSubtitles(
    context: android.content.Context,
    youtubeApi: YouTubeApi,
    videoInfo: VideoInfo,
    track: SubtitleTrack,
    copyToClipboard: Boolean
): Result<String> {
    return try {
        Log.d(TAG, "downloadSubtitles: Starting download for videoId: ${videoInfo.videoId}, track: ${track.languageCode}")
        
        // Fetch subtitles
        val subtitlesResult = youtubeApi.getSubtitles(videoInfo.videoId, track)

        if (subtitlesResult.isFailure) {
            Log.e(TAG, "downloadSubtitles: Failed to fetch subtitles", subtitlesResult.exceptionOrNull())
            return Result.failure(subtitlesResult.exceptionOrNull()!!)
        }

        val subtitleContent = subtitlesResult.getOrNull()!!
        Log.d(TAG, "downloadSubtitles: Successfully fetched subtitles, size: ${subtitleContent.length} bytes")

        // Create filename
        val sanitizedTitle = videoInfo.title
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
        val filename = "${sanitizedTitle}_${track.languageCode}.srt"
        Log.d(TAG, "downloadSubtitles: Generated filename: $filename")

        // Save to Downloads folder using MediaStore
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SubSnag")
                Log.d(TAG, "downloadSubtitles: Saving to Download/SubSnag folder")
            } else {
                Log.d(TAG, "downloadSubtitles: Saving to Downloads folder (Android < Q)")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Log.e(TAG, "downloadSubtitles: Failed to create file via MediaStore")
            return Result.failure(IOException("Failed to create file"))
        }
        Log.d(TAG, "downloadSubtitles: File URI created: $uri")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(subtitleContent.toByteArray())
            Log.d(TAG, "downloadSubtitles: Successfully wrote ${subtitleContent.length} bytes to file")
        } ?: run {
            Log.e(TAG, "downloadSubtitles: Failed to open output stream for URI: $uri")
            return Result.failure(IOException("Failed to open output stream"))
        }

        // Copy to clipboard if requested
        if (copyToClipboard) {
            Log.d(TAG, "downloadSubtitles: Copying subtitles to clipboard")
            ClipboardHelper.copyToClipboard(context, subtitleContent, "Subtitles")
            Log.d(TAG, "downloadSubtitles: Successfully copied to clipboard")
        }

        val message = if (copyToClipboard) {
            "Subtitles saved and copied to clipboard ✓"
        } else {
            "Subtitles saved to Downloads/SubSnag ✓"
        }
        Log.d(TAG, "downloadSubtitles: Download completed successfully - $message")

        Result.success(message)
    } catch (e: Exception) {
        Log.e(TAG, "downloadSubtitles: Unexpected error during download", e)
        Result.failure(e)
    }
}
