# StreamX Windows v0.2 — live-catalogue interface milestone

This is a Windows 10/11 WPF + WebView2 application for x64 and ARM64. It is a **work-in-progress port**, not full feature parity with StreamX TV yet.

## What v0.2 changes from the failed v0.1 prototype

- Uses the original StreamX logo-mark asset and bundled Inter/Poppins fonts from the Android TV source.
- Recreates the TV layout rules more closely: 64 px icon rail that expands to 260 px, large featured deck with neighbouring card peeks, eight-second idle deck advancement, and 120×180 → 360×180 poster expansion over 200 ms.
- Removes the made-up catalogue titles and gradient-only poster placeholders.
- Loads catalogue metadata/artwork from the default Cinemeta addon and other user-added Stremio-compatible addon manifests.
- Adds addon installation/removal, live search against addon search catalogs, title metadata requests, and stream-source discovery.
- Persists addon URLs and local My List/profile name in Windows local app data/WebView local storage.
- Builds portable self-contained artifacts for `win-arm64` and `win-x64` through GitHub Actions.

## Build on GitHub

The workflow lives at `.github/workflows/windows-build.yml`. On every push to `main` or manual `workflow_dispatch`, it restores, builds and publishes the project separately for `win-arm64` and `win-x64`.

Artifacts are versioned ZIPs containing the published app folder. For a Snapdragon PC, download the ARM64 artifact. For Intel/AMD, download x64.

## Run locally

Install the .NET 8 SDK for your architecture and Microsoft Edge WebView2 Runtime if not already installed. From this directory:

```powershell
dotnet restore .\StreamX.Windows.csproj
dotnet run --project .\StreamX.Windows.csproj
```

## Important feature status

- **Implemented in this milestone:** live catalogue metadata and artwork through Cinemeta/Stremio-compatible APIs; addon manifest management; addon catalogue/search/meta/stream requests; title details; local My List and profile name; a separate native Windows playback window using LibVLCSharp/LibVLC; play/pause, seek, ±10-second skip, volume, fullscreen, and audio/subtitle track selection where the stream exposes tracks.
- **Direct stream support:** HTTP/HTTPS URLs are passed to LibVLC. Common `User-Agent`, `Referer`, and `Cookie` headers from compatible stream objects are forwarded where recognized. Playback depends on the source format, codec, server rules, headers, and whether the addon supplies a playable URL.
- **Still not at Android TV parity:** torrent/infoHash sources, addon-specific JavaScript/Rhino execution, specialized resolvers, arbitrary custom HTTP headers, DRM-protected sources, subtitle download/search workflows, skip intro/recap, voice controls, watch-together, downloads, account/cloud sync, full history/resume synchronization, and the Android player’s specialized recovery behavior remain unported.
- The native player is a real Windows playback engine foundation rather than the previous browser `<video>` mockup, but it still requires real-source testing on both architectures and feature-by-feature porting before claiming parity.

## Local data

Addon URLs: `%LOCALAPPDATA%\StreamX\addons.json`
My List and profile name: WebView local storage for the StreamX origin.

## Playback engine licensing

The Windows player uses LibVLCSharp and VideoLAN LibVLC Windows. Review the libraries' LGPL-2.1-or-later licensing and redistribution requirements before publishing public installers. See https://www.nuget.org/packages/LibVLCSharp.WPF and https://www.nuget.org/packages/VideoLAN.LibVLC.Windows.

## Original Android TV source

The original Android TV archive was inspected during development but is intentionally not included in this Windows package: it contains Android-only build/signing material. `Reference/ANDROID-TV-PORTING-NOTES.md` records the source mapping and the behaviors this port aims to preserve. The original Android repository should remain separate and unchanged.
