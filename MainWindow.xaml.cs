using Microsoft.Web.WebView2.Core;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Input;

namespace StreamX.Windows;

public partial class MainWindow : Window
{
    private readonly StreamXBackend _backend = new();
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public MainWindow()
    {
        InitializeComponent();
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.F11)
            {
                if (WindowStyle == WindowStyle.None) { WindowStyle = WindowStyle.SingleBorderWindow; WindowState = WindowState.Normal; }
                else { WindowStyle = WindowStyle.None; WindowState = WindowState.Maximized; }
                e.Handled = true;
            }
        };
        Loaded += MainWindow_Loaded;
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            await Browser.EnsureCoreWebView2Async();
            Browser.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            Browser.CoreWebView2.Settings.AreDevToolsEnabled = false;
            Browser.CoreWebView2.Settings.IsStatusBarEnabled = false;
            Browser.CoreWebView2.Settings.AreBrowserAcceleratorKeysEnabled = true;
            Browser.CoreWebView2.WebMessageReceived += Browser_WebMessageReceived;
            // Map the app root so UI and original logo/font assets remain same-origin in WebView2.
            Browser.CoreWebView2.SetVirtualHostNameToFolderMapping("streamx.local", AppContext.BaseDirectory, CoreWebView2HostResourceAccessKind.Allow);
            Browser.Source = new Uri("https://streamx.local/UI/index.html");
        }
        catch (Exception ex)
        {
            MessageBox.Show("StreamX needs Microsoft Edge WebView2 Runtime. Install the Evergreen Runtime, then reopen StreamX.\n\n" + ex.Message,
                "StreamX v0.2", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private async void Browser_WebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        string requestId = "";
        try
        {
            using var doc = JsonDocument.Parse(e.WebMessageAsJson);
            var root = doc.RootElement;
            requestId = root.TryGetProperty("id", out var idEl) ? idEl.GetString() ?? "" : "";
            var action = root.TryGetProperty("action", out var actionEl) ? actionEl.GetString() ?? "" : "";
            var payload = root.TryGetProperty("payload", out var payloadEl) ? payloadEl.Clone() : default;
            if (action == "playStream")
            {
                var url = GetString(payload, "url");
                var title = GetString(payload, "title");
                var headers = GetStringMap(payload, "headers");
                var player = new StreamXPlayerWindow(url, title, headers) { Owner = this };
                player.Show();
                Browser.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new { id = requestId, ok = true, data = new { opened = true } }, JsonOptions));
                return;
            }
            var result = await _backend.HandleAsync(action, payload);
            Browser.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new { id = requestId, ok = true, data = result }, JsonOptions));
        }
        catch (Exception ex)
        {
            Browser.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new { id = requestId, ok = false, error = ex.Message }, JsonOptions));
        }
    }

    private static string GetString(JsonElement payload, string name)
        => payload.ValueKind == JsonValueKind.Object && payload.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : "";

    private static Dictionary<string, string> GetStringMap(JsonElement payload, string name)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (payload.ValueKind != JsonValueKind.Object || !payload.TryGetProperty(name, out var obj) || obj.ValueKind != JsonValueKind.Object) return result;
        foreach (var p in obj.EnumerateObject()) if (p.Value.ValueKind == JsonValueKind.String) result[p.Name] = p.Value.GetString() ?? "";
        return result;
    }
}
