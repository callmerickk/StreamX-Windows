# Android TV source reference notes

The original Android TV ZIP supplied by the project owner remains the source of truth for parity work. It is not compiled into the Windows app.

Confirmed reference behavior:

- `app/src/main/java/com/example/streamnest/TvStart.kt`: branded intro stages (background glow; logo-mark pop; “Stream” slides in; red X pops then scales away).
- `TvSidebar.kt`: 64 dp permanent icon rail; expands to 260 dp, 200 ms; order Search, Home, Library, My Space, Settings; profile at bottom.
- `TvHome.kt`: hero deck with two neighbouring cards peeking on the right; advances every 8 seconds when idle and the home list is at its top; neighbouring artwork is prefetched in Android.
- `TvHome.kt` / `TvCommon.kt`: `TvExpandCard` dimensions 120×180 dp normal and 360×180 dp focused, 200 ms expansion; expanded state uses backdrop art and title metadata/description.
- `Theme.kt`: Poppins Medium/Bold for wordmark/headings.
- `PlayerScreen.kt`: Android Media3/ExoPlayer, Jellyfin FFmpeg decoder and LibVLC fallback. This implementation depends on Android APIs and is not copied into the Windows player.
- `Plugins.kt`, `Streams.kt`, `Engine.kt`, `Api.kt`, `Models.kt`, `AppViewModel.kt`: source/catalog/addon behavior reference. Windows v0.2 ports part of the standard addon manifest/catalog/meta/stream HTTP protocol in C#; custom Android Rhino plugin scripts are not yet ported.

Use the original Android repository/archive to inspect these files. Do not move Android Kotlin files to the Windows project root. Do not put signing keystores or their passwords in a public repository.
