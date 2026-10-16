using LibVLCSharp.Shared;
using LibVLCSharp.Shared.Structures;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;

namespace StreamX.Windows;

public partial class StreamXPlayerWindow : Window
{
    private readonly LibVLC _libVlc;
    private readonly MediaPlayer _player;
    private readonly DispatcherTimer _timer;
    private readonly Uri _streamUri;
    private readonly Dictionary<string, string> _headers;
    private bool _draggingSeek;
    private bool _loadingTracks;
    private bool _isFullscreen;
    private WindowStyle _previousWindowStyle;
    private WindowState _previousWindowState;
    private ResizeMode _previousResizeMode;

    public StreamXPlayerWindow(string url, string title, Dictionary<string, string>? headers = null)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps))
            throw new ArgumentException("Only direct HTTP or HTTPS streams can be opened in this player.", nameof(url));
        _streamUri = uri;
        _headers = headers ?? new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        // Initialize LibVLCSharp before WPF instantiates its VideoView control.
        Core.Initialize();
        InitializeComponent();
        TitleText.Text = string.IsNullOrWhiteSpace(title) ? uri.Host : title;
        _libVlc = new LibVLC(false, true, "--network-caching=1500", "--file-caching=1000", "--quiet");
        _player = new MediaPlayer(_libVlc);
        VideoView.MediaPlayer = _player;
        _player.Playing += Player_Playing;
        _player.EncounteredError += Player_EncounteredError;
        _player.EndReached += Player_EndReached;
        _player.LengthChanged += Player_LengthChanged;
        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(400) };
        _timer.Tick += (_, _) => UpdatePlaybackUi();
        Loaded += PlayerWindow_Loaded;
    }

    private void PlayerWindow_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            using var media = new Media(_libVlc, _streamUri);
            media.AddOption(":network-caching=1500");
            media.AddOption(":http-reconnect=true");
            AddHeaderOptions(media, _headers);
            StatusText.Text = "Connecting to source…";
            if (!_player.Play(media)) StatusText.Text = "The video engine could not start this source.";
            _timer.Start();
        }
        catch (Exception ex)
        {
            StatusText.Text = "Unable to open this stream.";
            MessageBox.Show(this, ex.Message, "StreamX playback error", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private static void AddHeaderOptions(Media media, Dictionary<string, string> headers)
    {
        foreach (var item in headers)
        {
            // LibVLC exposes dedicated options for these common HTTP headers. Unsupported addon-specific
            // headers are deliberately ignored instead of passing untrusted command-line data to VLC.
            if (item.Key.Equals("User-Agent", StringComparison.OrdinalIgnoreCase)) media.AddOption(":http-user-agent=" + item.Value);
            else if (item.Key.Equals("Referer", StringComparison.OrdinalIgnoreCase) || item.Key.Equals("Referrer", StringComparison.OrdinalIgnoreCase)) media.AddOption(":http-referrer=" + item.Value);
            else if (item.Key.Equals("Cookie", StringComparison.OrdinalIgnoreCase)) media.AddOption(":http-cookie=" + item.Value);
        }
    }

    private void Player_Playing(object? sender, EventArgs e) => Dispatcher.InvokeAsync(() =>
    {
        StatusText.Text = "Playing · " + _streamUri.Host;
        PlayPauseButton.Content = "Ⅱ  Pause";
        RefreshTracks();
    });

    private void Player_EncounteredError(object? sender, EventArgs e) => Dispatcher.InvokeAsync(() =>
    {
        StatusText.Text = "Playback failed. This source may require an unsupported resolver, DRM, or custom HTTP headers.";
    });

    private void Player_EndReached(object? sender, EventArgs e) => Dispatcher.InvokeAsync(() =>
    {
        StatusText.Text = "Playback finished.";
        PlayPauseButton.Content = "▶  Play";
    });

    private void Player_LengthChanged(object? sender, MediaPlayerLengthChangedEventArgs e) => Dispatcher.InvokeAsync(UpdatePlaybackUi);

    private void RefreshTracks()
    {
        try
        {
            _loadingTracks = true;
            var audio = _player.AudioTrackDescription ?? Array.Empty<TrackDescription>();
            AudioTracksCombo.ItemsSource = audio.Select(t => new TrackChoice(t.Id, t.Name ?? $"Audio {t.Id}")).ToList();
            AudioTracksCombo.DisplayMemberPath = nameof(TrackChoice.Name);
            AudioTracksCombo.SelectedValuePath = nameof(TrackChoice.Id);
            if (_player.AudioTrack >= 0) AudioTracksCombo.SelectedValue = _player.AudioTrack;

            var subtitles = new List<TrackChoice> { new(-1, "Off") };
            subtitles.AddRange((_player.SpuDescription ?? Array.Empty<TrackDescription>()).Select(t => new TrackChoice(t.Id, t.Name ?? $"Subtitle {t.Id}")));
            SubtitlesCombo.ItemsSource = subtitles;
            SubtitlesCombo.DisplayMemberPath = nameof(TrackChoice.Name);
            SubtitlesCombo.SelectedValuePath = nameof(TrackChoice.Id);
            SubtitlesCombo.SelectedValue = _player.Spu;
        }
        catch { /* Some stream types do not expose their tracks until after the first keyframe. */ }
        finally { _loadingTracks = false; }
    }

    private void UpdatePlaybackUi()
    {
        try
        {
            var duration = _player.Length;
            var current = _player.Time;
            if (!_draggingSeek && duration > 0) SeekSlider.Value = Math.Clamp((double)Math.Max(0, current) / duration * 1000, 0, 1000);
            CurrentTimeText.Text = FormatTime(current);
            DurationText.Text = FormatTime(duration);
            if (_player.IsPlaying) PlayPauseButton.Content = "Ⅱ  Pause";
            else if (_player.State == VLCState.Paused || _player.State == VLCState.Stopped) PlayPauseButton.Content = "▶  Play";
        }
        catch { }
    }

    private static string FormatTime(long milliseconds)
    {
        if (milliseconds < 0) return "--:--";
        var t = TimeSpan.FromMilliseconds(milliseconds);
        return t.TotalHours >= 1 ? $"{(int)t.TotalHours:00}:{t.Minutes:00}:{t.Seconds:00}" : $"{t.Minutes:00}:{t.Seconds:00}";
    }

    private void PlayPauseButton_Click(object sender, RoutedEventArgs e)
    {
        if (_player.IsPlaying) { _player.Pause(); PlayPauseButton.Content = "▶  Play"; }
        else { _player.SetPause(false); PlayPauseButton.Content = "Ⅱ  Pause"; }
    }
    private void BackButton_Click(object sender, RoutedEventArgs e) => SeekBy(-10);
    private void ForwardButton_Click(object sender, RoutedEventArgs e) => SeekBy(10);
    private void SeekBy(int seconds)
    {
        try { if (_player.IsSeekable && _player.Time >= 0) _player.Time = Math.Max(0, Math.Min(_player.Length > 0 ? _player.Length : long.MaxValue, _player.Time + seconds * 1000L)); }
        catch { }
    }
    private void SeekSlider_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e) => _draggingSeek = true;
    private void SeekSlider_PreviewMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        _draggingSeek = false;
        try { if (_player.IsSeekable && _player.Length > 0) _player.Time = (long)(SeekSlider.Value / 1000 * _player.Length); }
        catch { }
    }
    private void SeekSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_draggingSeek && _player.Length > 0) CurrentTimeText.Text = FormatTime((long)(SeekSlider.Value / 1000 * _player.Length));
    }
    private void VolumeSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_player is not null) { try { _player.Volume = (int)VolumeSlider.Value; } catch { } }
    }
    private void AudioTracksCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_loadingTracks && AudioTracksCombo.SelectedValue is int id) { try { _player.SetAudioTrack(id); } catch { } }
    }
    private void SubtitlesCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_loadingTracks && SubtitlesCombo.SelectedValue is int id) { try { _player.SetSpu(id); } catch { } }
    }
    private void FullscreenButton_Click(object sender, RoutedEventArgs e) => ToggleFullscreen();
    private void ToggleFullscreen()
    {
        if (!_isFullscreen)
        {
            _previousWindowStyle = WindowStyle; _previousWindowState = WindowState; _previousResizeMode = ResizeMode;
            WindowStyle = WindowStyle.None; ResizeMode = ResizeMode.NoResize; WindowState = WindowState.Maximized;
            _isFullscreen = true; FullscreenButton.Content = "Windowed";
        }
        else
        {
            WindowStyle = _previousWindowStyle; ResizeMode = _previousResizeMode; WindowState = _previousWindowState;
            _isFullscreen = false; FullscreenButton.Content = "Fullscreen";
        }
    }
    private void CloseButton_Click(object sender, RoutedEventArgs e) => Close();
    private void Window_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Space) { PlayPauseButton_Click(this, new RoutedEventArgs()); e.Handled = true; }
        else if (e.Key == Key.Left) { SeekBy(-10); e.Handled = true; }
        else if (e.Key == Key.Right) { SeekBy(10); e.Handled = true; }
        else if (e.Key == Key.F) { ToggleFullscreen(); e.Handled = true; }
        else if (e.Key == Key.Escape) { if (_isFullscreen) ToggleFullscreen(); else Close(); e.Handled = true; }
    }
    private void Window_Closing(object? sender, CancelEventArgs e)
    {
        _timer.Stop();
        try { _player.Stop(); } catch { }
        VideoView.MediaPlayer = null;
        _player.Dispose();
        _libVlc.Dispose();
    }
    private sealed record TrackChoice(int Id, string Name);
}
