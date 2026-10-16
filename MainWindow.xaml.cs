using Microsoft.Web.WebView2.Core;
using System.IO;
using System.Windows;

namespace StreamX.Windows;
public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Loaded += MainWindow_Loaded;
        PreviewKeyDown += (_, e) => { if (e.Key == System.Windows.Input.Key.F11) { WindowStyle = WindowStyle.None; WindowState = WindowState.Maximized; } else if (e.Key == System.Windows.Input.Key.Escape && WindowStyle == WindowStyle.None) { WindowStyle = WindowStyle.SingleBorderWindow; WindowState = WindowState.Normal; } };
    }
    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            await Browser.EnsureCoreWebView2Async();
            Browser.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            Browser.CoreWebView2.Settings.AreDevToolsEnabled = false;
            Browser.CoreWebView2.Settings.IsStatusBarEnabled = false;
            var ui = Path.Combine(AppContext.BaseDirectory, "UI");
            Browser.CoreWebView2.SetVirtualHostNameToFolderMapping("streamx.local", ui, CoreWebView2HostResourceAccessKind.Allow);
            Browser.Source = new Uri("https://streamx.local/index.html");
        }
        catch (Exception ex)
        {
            MessageBox.Show("StreamX needs Microsoft Edge WebView2 Runtime. Install the Evergreen Runtime, then reopen StreamX.\n\n" + ex.Message, "StreamX v0.1", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }
}
