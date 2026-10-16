# Upload StreamX Windows v0.2 to GitHub (website only)

1. Download `StreamX-Windows-v0.2-source.zip` and extract it on Windows.
2. Open the existing private `StreamX-Windows` repository in your browser.
3. Choose **Add file → Upload files**.
4. From the extracted v0.2 folder, select all files and folders inside it, including the hidden `.github` folder if File Explorer shows it. Do not upload the ZIP itself.
5. Drag the selected files into GitHub's upload area. Upload in batches if GitHub reports its per-batch limit; keep the same directory paths (especially `.github/workflows`, `Assets/Fonts`, `UI`, and `Reference`).
6. GitHub may show existing paths as replacements/changes. Verify that `StreamX.Windows.csproj`, `MainWindow.xaml.cs`, `StreamXBackend.cs`, `StreamXPlayerWindow.xaml`, and `StreamXPlayerWindow.xaml.cs` are in the repository root.
7. Commit directly to `main` with message `Upgrade StreamX Windows to v0.2 native player foundation`.
8. Open **Actions → StreamX Windows Build**, wait for both ARM64 and x64 jobs, and inspect any failed step.
9. Download `StreamX-Windows-v0.2-ARM64.zip` for Snapdragon or `StreamX-Windows-v0.2-x64.zip` for Intel/AMD. Extract the ZIP and run `StreamX.exe` in the extracted folder. Keep all files together.

Do not delete the original Android repository. Android files accidentally uploaded into this Windows repository are not compiled by the Windows `.csproj`; leave cleanup until this new version successfully builds.

## What this build does and doesn't mean

This milestone adds a native Windows LibVLC playback window for direct HTTP/HTTPS stream URLs plus live addon/catalogue integration. It does not yet implement every Android TV feature. Addon JavaScript runtimes, torrent/resolver sources, arbitrary headers, DRM, advanced subtitle flows, watch-together, and specialized playback automation need further porting and testing.
