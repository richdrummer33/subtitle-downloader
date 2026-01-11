# SubSnag - YouTube Subtitle Downloader

A minimal, elegant Android app for downloading YouTube video subtitles with just a few taps.

## Features

- **Quick Settings Tile**: Copy a YouTube link, swipe down, tap the tile - done!
- **Share Target Integration**: Share YouTube links directly to SubSnag from any app
- **Material 3 Design**: Beautiful, modern UI with dynamic colors and dark mode support
- **Multiple Languages**: Download subtitles in any available language
- **Auto-Generated Support**: Works with both manual and auto-generated captions
- **Clipboard Integration**: Optionally copy subtitles to clipboard after download
- **No API Keys Required**: Uses YouTube's public endpoints
- **Privacy Focused**: No tracking, no analytics, no permissions besides internet

## How to Use

### Method 1: Quick Settings Tile
1. Copy a YouTube video link
2. Swipe down to access Quick Settings
3. Tap the "Get Subs" tile
4. Select language and download

### Method 2: Share Target
1. Long-press a YouTube link or tap Share in the YouTube app
2. Select "SubSnag" from the share sheet
3. Select language and download

## File Storage

Subtitles are saved to `Downloads/SubSnag/` in SRT format with the naming pattern:
```
{video_title}_{language_code}.srt
```

## Technical Details

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Networking**: Ktor Client
- **Image Loading**: Coil
- **HTML Parsing**: JSoup
- **Architecture**: Single-activity with Compose dialogs

### Requirements
- Android 8.0 (API 26) or higher
- Internet connection

### Permissions
- `INTERNET`: Required for fetching video information and subtitles

### How It Works
1. Extracts video ID from YouTube URL using regex patterns
2. Fetches video metadata using YouTube's oEmbed API
3. Scrapes available caption tracks from the watch page
4. Downloads subtitles using YouTube's timedtext API
5. Converts to SRT format and saves using MediaStore API

## Building

```bash
./gradlew assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/`

## Project Structure

```
app/src/main/
├── java/com/subsnag/
│   ├── MainActivity.kt              # Main app screen with instructions
│   ├── SubtitleActivity.kt          # Download dialog UI
│   ├── SubSnagTileService.kt        # Quick Settings tile
│   ├── ShareReceiverActivity.kt     # Share intent handler
│   ├── api/
│   │   └── YouTubeApi.kt            # YouTube data fetching
│   ├── model/
│   │   └── VideoInfo.kt             # Data models
│   ├── util/
│   │   ├── YouTubeUrlParser.kt      # URL parsing utilities
│   │   └── ClipboardHelper.kt       # Clipboard operations
│   └── ui/theme/                    # Compose theme
└── res/
    ├── drawable/
    │   └── ic_subtitles.xml         # App icon
    └── values/
        ├── strings.xml
        └── themes.xml
```

## Supported YouTube URL Formats

- `https://www.youtube.com/watch?v={videoId}`
- `https://youtu.be/{videoId}`
- `https://www.youtube.com/embed/{videoId}`
- `https://www.youtube.com/shorts/{videoId}`

## Known Limitations

- Only supports YouTube videos
- Requires videos to have available captions
- Cannot download from private or age-restricted videos
- No support for playlists (yet)

## Future Enhancements

- Download history
- Batch download from playlists
- Custom subtitle formatting options
- Support for other video platforms
- Subtitle editing capabilities

## License

See [LICENSE](LICENSE) file for details.

## Contributing

This is an open-source project. Contributions are welcome!
