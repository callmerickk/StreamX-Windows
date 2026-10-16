# StreamX
(project folder is still called StreamNest so the GitHub build keeps working)
Disney+/Hotstar-inspired Android app (Kotlin, Compose, Media3 ExoPlayer).

- Start: "Who's watching?" profile picker (per-profile history & watchlist; Kids profile shows animated/family titles)
- Navigation: Home / Search / My Space. All settings live behind My Space -> Help & Settings
- Customize: Help & Settings -> Advanced -> Developer's Corner (password protected)
- Icons: 3 launcher icons (Red Glow default, Ribbon X, Halo Play) = activity-aliases in AndroidManifest.xml; art in res/drawable/ic_bg_*/ic_fg_*/logo_*.xml + res/mipmap-*; switching logic in AppIcons.kt
- Name under the icon: res/values/strings.xml
- Signing: app/streamnest.keystore is used for every build so updates install over the old app. Never delete it.
- Metadata: Cinemeta (built-in, default addon)
- Addons: Stremio addon protocol (catalog / meta / stream / subtitles)
- Player: Media3 ExoPlayer (Apache-2.0), CC button for subtitle tracks
- Only direct-URL streams (HTTP/HLS/DASH) play. Torrent/infoHash streams are not supported.

## Build
1. Open this folder in Android Studio (Koala or newer). It generates the Gradle wrapper.
2. Sync, then Run on a device/emulator (minSdk 24).

- Voice assistant: AI button next to the capsule. Mood -> genre rules live in Voice.kt (offline, no API key)
- Changelog: Changelog.kt (shown in Help & Settings > Update Log). Add an entry every version.
- Popups: always use PremiumAlert / PremiumDialog / PremiumSheet (Premium.kt), never plain AlertDialog
- Streams: tags, Fusion badges, filters and regex fallbacks in Streams.kt; sources page in SourcesScreen.kt
- Startup: Startup.kt (splash + Who's watching). Audio: Jellyfin FFmpeg decoder (media3-ffmpeg-decoder must match the Media3 version).
- AI: Ai.kt (OpenAI-compatible client, memory). Votes + recommendations: Vote.kt. Quick Start + TMDB/MDBList: Quick.kt. Detail page: Detail.kt.
- Engines: Engine.kt (StreamHeaders for addon request headers, VlcEngine). Playback > Player engine (auto/exo/vlc).
- Size: debug build is shrunk by R8 (shrink only, no obfuscation) + arm64-v8a only + compressed jniLibs. VLC is the default engine.
- Version: v0.9.3 (versionCode 12). Every build: versionCode +1, versionName 0.<code>
