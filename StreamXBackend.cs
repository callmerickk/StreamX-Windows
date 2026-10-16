using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace StreamX.Windows;

/// <summary>
/// Windows data layer for the public Stremio addon manifest/catalog/meta/stream protocol.
/// It feeds the native Windows player with direct HTTP/HTTPS source URLs and supported common request headers.
/// </summary>
internal sealed class StreamXBackend
{
    private const string DefaultAddon = "https://v3-cinemeta.strem.io";
    private static readonly HttpClient Http = CreateHttp();
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web) { PropertyNameCaseInsensitive = true };
    private readonly string _settingsDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "StreamX");
    private readonly string _addonsFile;
    private readonly object _saveLock = new();
    private readonly ConcurrentDictionary<string, AddonInfo> _manifestCache = new(StringComparer.OrdinalIgnoreCase);
    private List<string> _addonUrls = new();
    private List<CatalogMeta> _homeCache = new();
    private bool _initialized;

    public StreamXBackend()
    {
        _addonsFile = Path.Combine(_settingsDir, "addons.json");
    }

    private static HttpClient CreateHttp()
    {
        var h = new HttpClient { Timeout = TimeSpan.FromSeconds(12) };
        h.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("StreamX-Windows", "0.2.0"));
        h.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        return h;
    }

    public async Task<object?> HandleAsync(string action, JsonElement payload)
    {
        await EnsureInitializedAsync();
        switch (action)
        {
            case "home": return await GetHomeAsync();
            case "search": return await SearchAsync(GetString(payload, "query"));
            case "addons": return await GetAddonsAsync();
            case "addAddon": return await AddAddonAsync(GetString(payload, "url"));
            case "removeAddon": return await RemoveAddonAsync(GetString(payload, "url"));
            case "streams": return await GetStreamsAsync(GetString(payload, "type"), GetString(payload, "id"));
            case "meta": return await GetMetaAsync(GetString(payload, "type"), GetString(payload, "id"));
            case "openExternal": return OpenExternal(GetString(payload, "url"));
            default: throw new InvalidOperationException($"Unknown StreamX action: {action}");
        }
    }

    private async Task EnsureInitializedAsync()
    {
        if (_initialized) return;
        await Task.Run(() =>
        {
            Directory.CreateDirectory(_settingsDir);
            try
            {
                var saved = File.Exists(_addonsFile) ? JsonSerializer.Deserialize<List<string>>(File.ReadAllText(_addonsFile), JsonOptions) : null;
                _addonUrls = (saved ?? new List<string>()).Select(NormalizeBaseUrl).Where(s => s.Length > 0).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            }
            catch { _addonUrls = new List<string>(); }
            if (!_addonUrls.Contains(DefaultAddon, StringComparer.OrdinalIgnoreCase)) _addonUrls.Insert(0, DefaultAddon);
            SaveAddons();
            _initialized = true;
        });
    }

    private void SaveAddons()
    {
        lock (_saveLock) File.WriteAllText(_addonsFile, JsonSerializer.Serialize(_addonUrls, JsonOptions));
    }

    private static string GetString(JsonElement el, string name)
        => el.ValueKind == JsonValueKind.Object && el.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";

    private static string NormalizeBaseUrl(string value)
    {
        var input = (value ?? "").Trim();
        if (input.Length == 0) return "";
        if (!input.StartsWith("https://", StringComparison.OrdinalIgnoreCase) && !input.StartsWith("http://", StringComparison.OrdinalIgnoreCase)) input = "https://" + input;
        if (!Uri.TryCreate(input, UriKind.Absolute, out var uri) || (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp)) throw new InvalidOperationException("Enter a valid http:// or https:// addon URL.");
        var path = uri.AbsolutePath.TrimEnd('/');
        if (path.EndsWith("/manifest.json", StringComparison.OrdinalIgnoreCase)) path = path[..^14].TrimEnd('/');
        return new UriBuilder(uri) { Path = path, Query = "", Fragment = "" }.Uri.ToString().TrimEnd('/');
    }

    private async Task<AddonInfo?> ResolveAddonAsync(string baseUrl)
    {
        if (_manifestCache.TryGetValue(baseUrl, out var cached)) return cached;
        try
        {
            using var res = await Http.GetAsync(baseUrl + "/manifest.json");
            res.EnsureSuccessStatusCode();
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
            var r = doc.RootElement;
            var addon = new AddonInfo { BaseUrl = baseUrl, Name = StringProp(r, "name") ?? new Uri(baseUrl).Host, Version = StringProp(r, "version") ?? "" };
            if (r.TryGetProperty("types", out var types) && types.ValueKind == JsonValueKind.Array)
                addon.Types = types.EnumerateArray().Where(x => x.ValueKind == JsonValueKind.String).Select(x => x.GetString()!).ToList();
            if (r.TryGetProperty("idPrefixes", out var prefixes) && prefixes.ValueKind == JsonValueKind.Array)
                addon.IdPrefixes = prefixes.EnumerateArray().Where(x => x.ValueKind == JsonValueKind.String).Select(x => x.GetString()!).ToList();
            if (r.TryGetProperty("resources", out var resources) && resources.ValueKind == JsonValueKind.Array)
            {
                foreach (var item in resources.EnumerateArray())
                {
                    if (item.ValueKind == JsonValueKind.String) addon.Resources.Add(item.GetString() ?? "");
                    else if (item.ValueKind == JsonValueKind.Object && item.TryGetProperty("name", out var n) && n.ValueKind == JsonValueKind.String) addon.Resources.Add(n.GetString() ?? "");
                }
            }
            if (r.TryGetProperty("catalogs", out var catalogs) && catalogs.ValueKind == JsonValueKind.Array)
            {
                foreach (var item in catalogs.EnumerateArray())
                {
                    if (item.ValueKind != JsonValueKind.Object) continue;
                    var type = StringProp(item, "type") ?? "";
                    var id = StringProp(item, "id") ?? "";
                    if (type.Length == 0 || id.Length == 0) continue;
                    var c = new AddonCatalog { Type = type, Id = id, Name = StringProp(item, "name") ?? id };
                    if (item.TryGetProperty("extra", out var extra) && extra.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var ex in extra.EnumerateArray())
                        {
                            if (ex.ValueKind != JsonValueKind.Object) continue;
                            var en = StringProp(ex, "name") ?? "";
                            var required = ex.TryGetProperty("isRequired", out var isReq) && isReq.ValueKind == JsonValueKind.True;
                            c.Extra.Add(new AddonExtra(en, required));
                        }
                    }
                    addon.Catalogs.Add(c);
                }
            }
            _manifestCache[baseUrl] = addon;
            return addon;
        }
        catch { return null; }
    }

    private static string? StringProp(JsonElement e, string name)
    {
        if (!e.TryGetProperty(name, out var v)) return null;
        return v.ValueKind switch
        {
            JsonValueKind.String => v.GetString(),
            JsonValueKind.Number => v.ToString(),
            JsonValueKind.True => "true",
            JsonValueKind.False => "false",
            _ => null
        };
    }

    private static object OpenExternal(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp))
            throw new InvalidOperationException("Only http:// and https:// links can be opened externally.");
        Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true });
        return new { opened = true };
    }

    private async Task<List<AddonInfo>> ResolveAllAsync()
    {
        var tasks = _addonUrls.ToArray().Select(ResolveAddonAsync);
        var results = await Task.WhenAll(tasks);
        return results.Where(x => x is not null).Cast<AddonInfo>().ToList();
    }

    private async Task<object> GetAddonsAsync()
    {
        var addons = await ResolveAllAsync();
        var rows = addons.Select(a => new { url = a.BaseUrl, name = a.Name, version = a.Version, catalogCount = a.Catalogs.Count, isDefault = a.BaseUrl.Equals(DefaultAddon, StringComparison.OrdinalIgnoreCase) }).ToList();
        return new { addons = rows, errors = _addonUrls.Except(addons.Select(a => a.BaseUrl), StringComparer.OrdinalIgnoreCase).Select(u => new { url = u, error = "Could not read addon manifest" }).ToList() };
    }

    private async Task<object> AddAddonAsync(string urlValue)
    {
        var baseUrl = NormalizeBaseUrl(urlValue);
        if (baseUrl.Length == 0) throw new InvalidOperationException("Paste an addon base URL or its manifest.json URL.");
        _manifestCache.TryRemove(baseUrl, out _);
        var addon = await ResolveAddonAsync(baseUrl);
        if (addon is null) throw new InvalidOperationException("Could not read that addon's manifest. Check the URL and try again.");
        if (!_addonUrls.Contains(baseUrl, StringComparer.OrdinalIgnoreCase)) _addonUrls.Add(baseUrl);
        SaveAddons();
        return await GetAddonsAsync();
    }

    private async Task<object> RemoveAddonAsync(string urlValue)
    {
        var baseUrl = NormalizeBaseUrl(urlValue);
        if (baseUrl.Equals(DefaultAddon, StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("Cinemeta is the default metadata catalogue and cannot be removed.");
        _addonUrls.RemoveAll(x => x.Equals(baseUrl, StringComparison.OrdinalIgnoreCase));
        _manifestCache.TryRemove(baseUrl, out _);
        SaveAddons();
        _homeCache.Clear();
        return await GetAddonsAsync();
    }

    private async Task<object> GetHomeAsync()
    {
        var addons = await ResolveAllAsync();
        var candidates = addons.SelectMany(a => a.Catalogs
                .Where(c => c.Extra.All(e => !e.Required))
                .Select(c => (Addon: a, Catalog: c)))
            .DistinctBy(x => x.Addon.BaseUrl + "|" + x.Catalog.Type + "|" + x.Catalog.Id)
            .Take(14).ToList();

        // A broken/unusual manifest shouldn't leave the home screen empty if the standard metadata catalog is online.
        if (!candidates.Any())
        {
            var cinemeta = await ResolveAddonAsync(DefaultAddon);
            if (cinemeta is not null) candidates = new List<(AddonInfo, AddonCatalog)> {
                (cinemeta, new AddonCatalog { Type = "movie", Id = "top", Name = "Popular Movies" }),
                (cinemeta, new AddonCatalog { Type = "series", Id = "top", Name = "Popular Series" }) };
        }

        var tasks = candidates.Select(async entry =>
        {
            var url = $"{entry.Addon.BaseUrl}/catalog/{entry.Catalog.Type}/{Uri.EscapeDataString(entry.Catalog.Id)}.json";
            var metas = await GetMetaListAsync(url);
            var title = entry.Catalog.Name;
            if (!title.Contains(entry.Catalog.Type, StringComparison.OrdinalIgnoreCase)) title += entry.Catalog.Type.Equals("series", StringComparison.OrdinalIgnoreCase) ? " Series" : " Movies";
            return new CatalogShelf { Id = entry.Addon.BaseUrl + "/" + entry.Catalog.Type + "/" + entry.Catalog.Id, Title = title, Items = metas };
        });
        var shelves = (await Task.WhenAll(tasks)).Where(s => s.Items.Count > 0).ToList();
        // If source addons supply no home catalogs, use Cinemeta's well-known top catalogs as a deterministic fallback.
        if (shelves.Count == 0)
        {
            var fallback = new[] { ("movie", "top", "Popular Movies"), ("series", "top", "Popular Series") };
            var fb = await Task.WhenAll(fallback.Select(async f => new CatalogShelf { Id = f.Item1 + "/" + f.Item2, Title = f.Item3, Items = await GetMetaListAsync($"{DefaultAddon}/catalog/{f.Item1}/{f.Item2}.json") }));
            shelves = fb.Where(s => s.Items.Count > 0).ToList();
        }
        _homeCache = shelves.SelectMany(s => s.Items).DistinctBy(m => m.Type + "/" + m.Id).ToList();
        var featured = new List<CatalogMeta>();
        // Match the TV deck: mix titles across the first two catalog rows instead of showing only one row.
        for (var i = 0; i < 50 && featured.Count < 10; i++)
        {
            foreach (var shelf in shelves.Take(2))
            {
                if (i < shelf.Items.Count)
                {
                    var meta = shelf.Items[i];
                    if ((!string.IsNullOrWhiteSpace(meta.Background) || !string.IsNullOrWhiteSpace(meta.Poster)) && !featured.Any(m => m.Type == meta.Type && m.Id == meta.Id))
                        featured.Add(meta);
                }
                if (featured.Count >= 10) break;
            }
        }
        return new { shelves, featured, addons = addons.Select(a => new { url = a.BaseUrl, name = a.Name, catalogCount = a.Catalogs.Count }).ToList(), loadedAt = DateTimeOffset.Now };
    }

    private async Task<List<CatalogMeta>> GetMetaListAsync(string url)
    {
        try
        {
            using var res = await Http.GetAsync(url);
            if (!res.IsSuccessStatusCode) return new List<CatalogMeta>();
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
            if (!doc.RootElement.TryGetProperty("metas", out var metas) || metas.ValueKind != JsonValueKind.Array) return new List<CatalogMeta>();
            return metas.EnumerateArray().Select(ParseMeta).Where(m => m is not null).Cast<CatalogMeta>().ToList();
        }
        catch { return new List<CatalogMeta>(); }
    }

    private async Task<object> SearchAsync(string query)
    {
        query = (query ?? "").Trim();
        if (query.Length < 2) return new { query, results = Array.Empty<CatalogMeta>(), message = "Enter at least two characters." };
        var addons = await ResolveAllAsync();
        var candidates = addons.SelectMany(a => a.Catalogs.Where(c => c.Extra.Any(e => e.Name.Equals("search", StringComparison.OrdinalIgnoreCase)))
            .Select(c => (Addon: a, Catalog: c))).Take(40).ToList();
        var tasks = candidates.Select(async entry => await GetMetaListAsync($"{entry.Addon.BaseUrl}/catalog/{entry.Catalog.Type}/{Uri.EscapeDataString(entry.Catalog.Id)}/search={Uri.EscapeDataString(query)}.json"));
        var rows = (await Task.WhenAll(tasks)).SelectMany(x => x).ToList();
        // Use the already-loaded live catalogue as a secondary search surface; never inject fictional titles.
        rows.AddRange(_homeCache.Where(m => m.Name.Contains(query, StringComparison.OrdinalIgnoreCase) || (m.Description?.Contains(query, StringComparison.OrdinalIgnoreCase) ?? false)));
        var results = rows.DistinctBy(m => m.Type + "/" + m.Id).Take(100).ToList();
        return new { query, results, message = results.Count == 0 ? "No results returned by the installed addons." : $"{results.Count} results" };
    }

    private async Task<object> GetMetaAsync(string type, string id)
    {
        var addons = await ResolveAllAsync();
        foreach (var a in addons)
        {
            if (!a.Resources.Contains("meta", StringComparer.OrdinalIgnoreCase)) continue;
            if (a.Types.Count > 0 && !a.Types.Contains(type, StringComparer.OrdinalIgnoreCase)) continue;
            if (a.IdPrefixes.Count > 0 && !a.IdPrefixes.Any(p => id.StartsWith(p, StringComparison.OrdinalIgnoreCase))) continue;
            try
            {
                using var res = await Http.GetAsync($"{a.BaseUrl}/meta/{type}/{EncodeId(id)}.json");
                if (!res.IsSuccessStatusCode) continue;
                using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
                if (doc.RootElement.TryGetProperty("meta", out var meta) && meta.ValueKind == JsonValueKind.Object) return new { meta = ParseMeta(meta) };
            }
            catch { }
        }
        var cached = _homeCache.FirstOrDefault(m => m.Id.Equals(id, StringComparison.OrdinalIgnoreCase) && m.Type.Equals(type, StringComparison.OrdinalIgnoreCase));
        return new { meta = cached };
    }

    private async Task<object> GetStreamsAsync(string type, string id)
    {
        var addons = await ResolveAllAsync();
        var candidates = addons.Where(a => a.Resources.Contains("stream", StringComparer.OrdinalIgnoreCase)
            && (a.Types.Count == 0 || a.Types.Contains(type, StringComparer.OrdinalIgnoreCase))
            && (a.IdPrefixes.Count == 0 || a.IdPrefixes.Any(p => id.StartsWith(p, StringComparison.OrdinalIgnoreCase)))).ToList();
        var tasks = candidates.Select(async a =>
        {
            var url = $"{a.BaseUrl}/stream/{type}/{EncodeId(id)}.json";
            try
            {
                using var res = await Http.GetAsync(url);
                if (!res.IsSuccessStatusCode) return new List<StreamSource>();
                using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
                if (!doc.RootElement.TryGetProperty("streams", out var arr) || arr.ValueKind != JsonValueKind.Array) return new List<StreamSource>();
                return arr.EnumerateArray().Select(s => ParseStream(s, a.Name)).Where(s => s is not null).Cast<StreamSource>().ToList();
            }
            catch { return new List<StreamSource>(); }
        });
        var streams = (await Task.WhenAll(tasks)).SelectMany(x => x).Take(100).ToList();
        return new { sources = streams, count = streams.Count, message = streams.Count == 0 ? "No streams were returned by the installed addons for this title." : $"{streams.Count} sources" };
    }

    private static string EncodeId(string id) => Uri.EscapeDataString(id).Replace("%3A", ":", StringComparison.OrdinalIgnoreCase);

    private static CatalogMeta? ParseMeta(JsonElement e)
    {
        var id = StringProp(e, "id"); var name = StringProp(e, "name");
        if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(name)) return null;
        var type = StringProp(e, "type") ?? "movie";
        var genres = new List<string>();
        if (e.TryGetProperty("genres", out var gen) && gen.ValueKind == JsonValueKind.Array) genres = gen.EnumerateArray().Where(g => g.ValueKind == JsonValueKind.String).Select(g => g.GetString()!).ToList();
        return new CatalogMeta
        {
            Id = id, Type = type, Name = name, Poster = StringProp(e, "poster"), Background = StringProp(e, "background"), Logo = StringProp(e, "logo"),
            Description = StringProp(e, "description"), ReleaseInfo = StringProp(e, "releaseInfo") ?? StringProp(e, "year"), ImdbRating = StringProp(e, "imdbRating"),
            Runtime = StringProp(e, "runtime"), Genres = genres, PosterShape = StringProp(e, "posterShape"),
            AddedBy = null
        };
    }

    private static StreamSource? ParseStream(JsonElement e, string addonName)
    {
        if (e.ValueKind != JsonValueKind.Object) return null;
        string? Get(string key) => StringProp(e, key);
        var name = Get("name"); var title = Get("title"); var description = Get("description"); var url = Get("url");
        var infoHash = Get("infoHash"); var externalUrl = Get("externalUrl");
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (e.TryGetProperty("headers", out var directHeaders) && directHeaders.ValueKind == JsonValueKind.Object)
            foreach (var h in directHeaders.EnumerateObject()) if (h.Value.ValueKind == JsonValueKind.String) headers[h.Name] = h.Value.GetString() ?? "";
        var behaviorHints = e.TryGetProperty("behaviorHints", out var b) && b.ValueKind == JsonValueKind.Object ? b : default;
        if (behaviorHints.ValueKind == JsonValueKind.Object && behaviorHints.TryGetProperty("proxyHeaders", out var proxyHeaders) && proxyHeaders.ValueKind == JsonValueKind.Object && proxyHeaders.TryGetProperty("request", out var request) && request.ValueKind == JsonValueKind.Object && request.TryGetProperty("headers", out var requestHeaders) && requestHeaders.ValueKind == JsonValueKind.Object)
            foreach (var h in requestHeaders.EnumerateObject()) if (h.Value.ValueKind == JsonValueKind.String) headers[h.Name] = h.Value.GetString() ?? "";
        string? filename = behaviorHints.ValueKind == JsonValueKind.Object ? StringProp(behaviorHints, "filename") : null;
        string? bingeGroup = behaviorHints.ValueKind == JsonValueKind.Object ? StringProp(behaviorHints, "bingeGroup") : null;
        if (string.IsNullOrWhiteSpace(name) && string.IsNullOrWhiteSpace(title) && string.IsNullOrWhiteSpace(description) && string.IsNullOrWhiteSpace(url) && string.IsNullOrWhiteSpace(infoHash) && string.IsNullOrWhiteSpace(externalUrl)) return null;
        return new StreamSource { AddonName = addonName, Name = name, Title = title, Description = description, Url = url, InfoHash = infoHash, ExternalUrl = externalUrl, Filename = filename, BingeGroup = bingeGroup, Headers = headers, CanOpenDirectly = !string.IsNullOrWhiteSpace(url) && Uri.TryCreate(url, UriKind.Absolute, out var u) && (u.Scheme == "https" || u.Scheme == "http") };
    }
}

internal sealed class AddonInfo
{
    public string BaseUrl { get; set; } = "";
    public string Name { get; set; } = "Addon";
    public string Version { get; set; } = "";
    public List<string> Types { get; set; } = new();
    public List<string> IdPrefixes { get; set; } = new();
    public HashSet<string> Resources { get; set; } = new(StringComparer.OrdinalIgnoreCase);
    public List<AddonCatalog> Catalogs { get; set; } = new();
}
internal sealed class AddonCatalog
{
    public string Type { get; set; } = "";
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public List<AddonExtra> Extra { get; set; } = new();
}
internal sealed record AddonExtra(string Name, bool Required);
internal sealed class CatalogShelf
{
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";
    public List<CatalogMeta> Items { get; set; } = new();
}
internal sealed class CatalogMeta
{
    public string Id { get; set; } = "";
    public string Type { get; set; } = "movie";
    public string Name { get; set; } = "";
    public string? Poster { get; set; }
    public string? Background { get; set; }
    public string? Logo { get; set; }
    public string? Description { get; set; }
    public string? ReleaseInfo { get; set; }
    public string? ImdbRating { get; set; }
    public string? Runtime { get; set; }
    public List<string> Genres { get; set; } = new();
    public string? PosterShape { get; set; }
    [JsonIgnore] public string? AddedBy { get; set; }
}
internal sealed class StreamSource
{
    public string AddonName { get; set; } = "";
    public string? Name { get; set; }
    public string? Title { get; set; }
    public string? Description { get; set; }
    public string? Url { get; set; }
    public string? InfoHash { get; set; }
    public string? ExternalUrl { get; set; }
    public string? Filename { get; set; }
    public string? BingeGroup { get; set; }
    public Dictionary<string, string> Headers { get; set; } = new(StringComparer.OrdinalIgnoreCase);
    public bool CanOpenDirectly { get; set; }
}
