# Changelog

## 0.2.0 — live catalogue and TV interaction port baseline
- Replaced the fictional demo catalogue with live addon/Cinemeta metadata.
- Added a C# WebView2 message bridge and addon manifest/catalog/meta/stream client.
- Bundled an SVG translation of the original StreamX logo mark and the original Inter/Poppins font resources.
- Implemented TV rail expand/collapse, deck peek cards, auto-advance, card expansion, search, title details, source discovery and local list/profile settings.
- Updated GitHub Actions to current Node 24-capable action major versions.
- Clearly identified full playback feature parity as incomplete.

## 0.1.0 — first prototype
- Initial WPF/WebView2 shell and static HTML preview.

## v0.2.0 — Native player foundation

- Added a dedicated WPF playback window powered by LibVLCSharp and the native LibVLC Windows runtime.
- Added play/pause, seek bar, ±10-second skip, volume, fullscreen, and available audio/subtitle track selection.
- Added basic hand-off for direct HTTP/HTTPS stream URLs and common User-Agent, Referer, and Cookie headers where supplied by a compatible addon.
- Kept the TV-style interface in WebView2 and added a C# bridge to open the native player.
- This is the first native playback foundation, not yet feature parity with Android TV: torrent/hash sources, resolver-specific addons, arbitrary request headers, DRM-protected media, skip-intro/recap automation, voice controls, watch-together, and complete playback recovery remain unported.
