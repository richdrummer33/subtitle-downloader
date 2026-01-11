package com.subsnag.api

import android.util.Log
import com.subsnag.model.SubtitleTrack
import com.subsnag.model.VideoInfo
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.net.URLDecoder

class YouTubeApi {
    private val client = HttpClient(Android) {
        engine {
            connectTimeout = 30_000
            socketTimeout = 30_000
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val TAG = "YouTubeApi"
    }

    /**
     * Fetches video information including title, thumbnail, and available caption tracks.
     * Uses InnerTube API with ANDROID client to get fresh auth parameters.
     */
    suspend fun getVideoInfo(videoId: String): Result<VideoInfo> {
        return try {
            Log.d(TAG, "Fetching video info for: $videoId")

            // Step 1: Get basic info from oEmbed API
            val oEmbedUrl = "https://www.youtube.com/oembed?url=https://youtube.com/watch?v=$videoId&format=json"
            Log.d(TAG, "Fetching oEmbed: $oEmbedUrl")
            val oEmbedResponse = client.get(oEmbedUrl).bodyAsText()
            val oEmbedJson = json.parseToJsonElement(oEmbedResponse).jsonObject

            val title = oEmbedJson["title"]?.jsonPrimitive?.content ?: "Unknown Title"
            val thumbnailUrl = oEmbedJson["thumbnail_url"]?.jsonPrimitive?.content
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            Log.d(TAG, "Video title: $title")

            // Step 2: Fetch the watch page to extract INNERTUBE_API_KEY
            val watchPageUrl = "https://www.youtube.com/watch?v=$videoId"
            Log.d(TAG, "Fetching watch page: $watchPageUrl")
            val watchPageHtml = client.get(watchPageUrl).bodyAsText()

            val apiKey = extractInnerTubeApiKey(watchPageHtml)
            if (apiKey == null) {
                Log.e(TAG, "Failed to extract INNERTUBE_API_KEY from watch page")
                return Result.failure(Exception("Failed to extract YouTube API key"))
            }
            Log.d(TAG, "Extracted INNERTUBE_API_KEY: ${apiKey.take(20)}...")

            // Step 3: Call InnerTube player API with ANDROID client to get fresh caption tracks
            val captionTracks = getCaptionTracksFromInnerTube(videoId, apiKey)
            Log.d(TAG, "Found ${captionTracks.size} caption tracks from InnerTube API")

            if (captionTracks.isEmpty()) {
                Log.w(TAG, "No subtitles available")
                return Result.failure(Exception("No subtitles available for this video"))
            }

            Result.success(
                VideoInfo(
                    videoId = videoId,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    availableTracks = captionTracks
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch video info", e)
            Result.failure(Exception("Failed to fetch video info: ${e.message}", e))
        }
    }

    /**
     * Extracts the INNERTUBE_API_KEY from YouTube watch page HTML.
     */
    private fun extractInnerTubeApiKey(html: String): String? {
        return try {
            val pattern = Regex(""""INNERTUBE_API_KEY":"([^"]+)"""")
            val matchResult = pattern.find(html)
            matchResult?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract INNERTUBE_API_KEY", e)
            null
        }
    }

    /**
     * Calls YouTube's InnerTube player API with ANDROID client to get fresh caption tracks.
     * This bypasses the PoToken anti-bot protection by using the official Android client.
     */
    private suspend fun getCaptionTracksFromInnerTube(videoId: String, apiKey: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()

        return try {
            val innerTubeUrl = "https://www.youtube.com/youtubei/v1/player?key=$apiKey"
            Log.d(TAG, "Calling InnerTube API: $innerTubeUrl")

            // Create request body with ANDROID client context
            val requestBody = buildJsonObject {
                put("videoId", videoId)
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", "30")
                        put("hl", "en")
                        put("gl", "US")
                    }
                }
            }

            Log.d(TAG, "Request body: ${requestBody.toString().take(200)}...")

            // POST to InnerTube API
            val response = client.post(innerTubeUrl) {
                header("Content-Type", "application/json")
                header("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 11)")
                header("X-YouTube-Client-Name", "3") // 3 = ANDROID
                header("X-YouTube-Client-Version", "20.10.38")
                setBody(requestBody.toString())
            }

            val responseText = response.bodyAsText()
            Log.d(TAG, "InnerTube response length: ${responseText.length} bytes")

            val playerResponse = json.parseToJsonElement(responseText).jsonObject

            // Extract caption tracks from response
            val captions = playerResponse["captions"]?.jsonObject
            val playerCaptionRenderer = captions?.get("playerCaptionsTracklistRenderer")?.jsonObject
            val captionTracks = playerCaptionRenderer?.get("captionTracks")?.jsonArray

            if (captionTracks == null) {
                Log.w(TAG, "No captionTracks found in InnerTube response")
                return emptyList()
            }

            Log.d(TAG, "Found ${captionTracks.size} caption tracks in InnerTube response")

            captionTracks.forEach { trackElement ->
                val trackObj = trackElement.jsonObject
                val baseUrl = trackObj["baseUrl"]?.jsonPrimitive?.content ?: return@forEach
                val languageCode = trackObj["languageCode"]?.jsonPrimitive?.content ?: "en"

                val name = trackObj["name"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                    ?: trackObj["name"]?.jsonObject?.get("runs")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: languageCode

                val isAutoGenerated = trackObj["kind"]?.jsonPrimitive?.content == "asr"

                Log.d(TAG, "Track: lang=$languageCode, name=$name, auto=$isAutoGenerated, baseUrl=${baseUrl.take(80)}...")

                tracks.add(
                    SubtitleTrack(
                        languageCode = languageCode,
                        languageName = name,
                        isAutoGenerated = isAutoGenerated,
                        baseUrl = baseUrl  // Fresh auth params from InnerTube!
                    )
                )
            }

            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get caption tracks from InnerTube API", e)
            emptyList()
        }
    }

    /**
     * Extracts caption track information from the YouTube watch page HTML.
     * DEPRECATED: This extracts expired auth params. Use getCaptionTracksFromInnerTube() instead.
     */
    private fun extractCaptionTracks(html: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()

        try {
            // Look for ytInitialPlayerResponse in the HTML
            val playerResponsePattern = Regex(""""captionTracks":\[(.*?)\]""")
            val matchResult = playerResponsePattern.find(html)

            if (matchResult != null) {
                val captionTracksJson = "[${matchResult.groupValues[1]}]"
                val tracksArray = json.parseToJsonElement(captionTracksJson).jsonArray

                for (trackElement in tracksArray) {
                    val trackObj = trackElement.jsonObject
                    val baseUrl = trackObj["baseUrl"]?.jsonPrimitive?.content ?: continue
                    val languageCode = trackObj["languageCode"]?.jsonPrimitive?.content ?: "en"

                    val name = trackObj["name"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                        ?: trackObj["name"]?.jsonObject?.get("runs")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: languageCode

                    val isAutoGenerated = trackObj["kind"]?.jsonPrimitive?.content == "asr"

                    tracks.add(
                        SubtitleTrack(
                            languageCode = languageCode,
                            languageName = name,
                            isAutoGenerated = isAutoGenerated,
                            baseUrl = baseUrl  // This contains all auth params!
                        )
                    )
                }
            }

            // Fallback: try alternative pattern
            if (tracks.isEmpty()) {
                val altPattern = Regex("""var ytInitialPlayerResponse = (\{.*?\});""")
                val altMatch = altPattern.find(html)
                if (altMatch != null) {
                    val playerResponse = json.parseToJsonElement(altMatch.groupValues[1]).jsonObject
                    val captions = playerResponse["captions"]?.jsonObject
                    val playerCaptionRenderer = captions?.get("playerCaptionsTracklistRenderer")?.jsonObject
                    val captionTracks = playerCaptionRenderer?.get("captionTracks")?.jsonArray

                    captionTracks?.forEach { trackElement ->
                        val trackObj = trackElement.jsonObject
                        val baseUrl = trackObj["baseUrl"]?.jsonPrimitive?.content ?: return@forEach
                        val languageCode = trackObj["languageCode"]?.jsonPrimitive?.content ?: "en"
                        val name = trackObj["name"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content ?: languageCode
                        val isAutoGenerated = trackObj["kind"]?.jsonPrimitive?.content == "asr"

                        tracks.add(
                            SubtitleTrack(
                                languageCode = languageCode,
                                languageName = name,
                                isAutoGenerated = isAutoGenerated,
                                baseUrl = baseUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract caption tracks from HTML", e)
        }

        return tracks
    }

    /**
     * Fetches subtitle content in SRT format.
     * Uses the fresh baseUrl from InnerTube API which contains valid auth parameters.
     */
    suspend fun getSubtitles(videoId: String, track: SubtitleTrack): Result<String> {
        return try {
            // CRITICAL: Use the baseUrl from track which contains FRESH auth params from InnerTube API
            // This includes valid signature, expire timestamp, and bypasses PoToken anti-bot protection
            val subtitleUrl = if (track.baseUrl != null && track.baseUrl.isNotBlank()) {
                Log.d(TAG, "Using fresh baseUrl from InnerTube API (contains valid auth params)")
                track.baseUrl
            } else {
                // Fallback: construct simple URL (likely won't work due to PoToken protection)
                Log.w(TAG, "No baseUrl in track, using simple URL (may fail)")
                "https://www.youtube.com/api/timedtext?v=$videoId&lang=${track.languageCode}"
            }

            Log.d(TAG, "Fetching subtitles from: ${subtitleUrl.take(100)}...")  // Don't log full URL (too long)

            val httpResponse = client.get(subtitleUrl) {
                // Add headers to mimic YouTube's Android app
                header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11)")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Referer", "https://www.youtube.com/watch?v=$videoId")
                header("Origin", "https://www.youtube.com")
            }

            Log.d(TAG, "HTTP Status: ${httpResponse.status}")
            Log.d(TAG, "HTTP Status Code: ${httpResponse.status.value}")

            val response = httpResponse.bodyAsText()
            Log.d(TAG, "Subtitle response length: ${response.length} bytes")

            if (response.length > 0) {
                Log.d(TAG, "Response preview: ${response.take(200)}")
            }

            if (response.isBlank()) {
                Log.e(TAG, "Empty subtitle response - HTTP ${httpResponse.status.value}")
                Log.e(TAG, "baseUrl present: ${track.baseUrl != null}")
                return Result.failure(Exception("Empty subtitle response from YouTube (HTTP ${httpResponse.status.value})"))
            }

            // Detect format and convert to SRT
            val srtContent = when {
                response.trim().startsWith("<?xml") || response.trim().startsWith("<transcript") -> {
                    Log.d(TAG, "Detected XML format")
                    convertXMLToSRT(response)
                }
                response.trim().startsWith("{") -> {
                    Log.d(TAG, "Detected JSON format")
                    convertToSRT(response)
                }
                else -> {
                    Log.w(TAG, "Unknown format (starts with: ${response.take(20)}), trying XML parser")
                    convertXMLToSRT(response)
                }
            }

            Log.d(TAG, "Converted SRT length: ${srtContent.length} bytes")
            if (srtContent.isBlank()) {
                Log.e(TAG, "Conversion resulted in empty SRT")
                return Result.failure(Exception("Failed to convert subtitles to SRT format"))
            }

            Result.success(srtContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch subtitles", e)
            Result.failure(Exception("Failed to fetch subtitles: ${e.message}", e))
        }
    }

    /**
     * Converts YouTube's JSON3 subtitle format to SRT.
     */
    private fun convertToSRT(jsonContent: String): String {
        val srtBuilder = StringBuilder()
        var counter = 1

        try {
            val jsonObject = json.parseToJsonElement(jsonContent).jsonObject
            val events = jsonObject["events"]?.jsonArray ?: return ""

            for (event in events) {
                val eventObj = event.jsonObject
                val startTime = eventObj["tStartMs"]?.jsonPrimitive?.long ?: continue
                val duration = eventObj["dDurationMs"]?.jsonPrimitive?.long ?: continue
                val segs = eventObj["segs"]?.jsonArray

                if (segs != null && segs.isNotEmpty()) {
                    val text = segs.joinToString("") { seg ->
                        seg.jsonObject["utf8"]?.jsonPrimitive?.content ?: ""
                    }.trim()

                    if (text.isNotEmpty()) {
                        val endTime = startTime + duration
                        srtBuilder.append("$counter\n")
                        srtBuilder.append("${formatTime(startTime)} --> ${formatTime(endTime)}\n")
                        srtBuilder.append("$text\n\n")
                        counter++
                    }
                }
            }
        } catch (e: Exception) {
            // If JSON parsing fails, try XML format
            return convertXMLToSRT(jsonContent)
        }

        return srtBuilder.toString()
    }

    /**
     * Converts YouTube's SRV3 (XML) subtitle format to SRT.
     */
    private fun convertSRV3ToSRT(xmlContent: String): String {
        return convertXMLToSRT(xmlContent)
    }

    /**
     * Converts XML subtitle format to SRT.
     */
    private fun convertXMLToSRT(xmlContent: String): String {
        val srtBuilder = StringBuilder()
        var counter = 1

        try {
            Log.d(TAG, "Parsing XML content...")
            val doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser())
            val textElements = doc.select("text")
            Log.d(TAG, "Found ${textElements.size} text elements")

            if (textElements.isEmpty()) {
                Log.w(TAG, "No <text> elements found in XML")
            }

            for (element in textElements) {
                val startStr = element.attr("start")
                val durStr = element.attr("dur")
                val rawText = element.text()

                Log.v(TAG, "Element $counter: start=$startStr, dur=$durStr, text=${rawText.take(50)}")

                val start = startStr.toDoubleOrNull()?.times(1000)?.toLong()
                if (start == null) {
                    Log.w(TAG, "Invalid start time: $startStr")
                    continue
                }

                val duration = durStr.toDoubleOrNull()?.times(1000)?.toLong() ?: 3000

                // Use Jsoup's built-in HTML entity decoding
                val text = Jsoup.parse(rawText).text().trim()

                if (text.isNotEmpty()) {
                    val end = start + duration
                    srtBuilder.append("$counter\n")
                    srtBuilder.append("${formatTime(start)} --> ${formatTime(end)}\n")
                    srtBuilder.append("$text\n\n")
                    counter++
                }
            }

            Log.d(TAG, "Converted ${counter - 1} subtitle entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XML", e)
            throw Exception("Failed to parse subtitle XML: ${e.message}")
        }

        return srtBuilder.toString()
    }

    /**
     * Formats milliseconds to SRT timestamp format (HH:MM:SS,mmm).
     */
    private fun formatTime(milliseconds: Long): String {
        val hours = milliseconds / 3600000
        val minutes = (milliseconds % 3600000) / 60000
        val seconds = (milliseconds % 60000) / 1000
        val millis = milliseconds % 1000

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    /**
     * Decodes HTML entities.
     */
    private fun decodeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }

    fun close() {
        client.close()
    }
}
