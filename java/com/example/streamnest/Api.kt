package com.example.streamnest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** DNS choices shown in Help & Settings > Network & DNS. */
val DnsProviders = linkedMapOf(
    "system" to ("System default" to "Use your phone's / Wi-Fi's normal DNS."),
    "cloudflare" to ("Cloudflare (1.1.1.1)" to "Fast and privacy-focused."),
    "google" to ("Google (8.8.8.8)" to "Reliable public DNS from Google."),
    "quad9" to ("Quad9 (9.9.9.9)" to "Blocks known malicious domains."),
    "adguard" to ("AdGuard" to "Blocks many ads and trackers."),
)

object Api {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val bootstrap: OkHttpClient by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build() }

    /** Shared HTTP client for catalogs, addons, images and video streams. Rebuilt when the DNS choice changes. */
    @Volatile var http: OkHttpClient = build("system")
        private set

    fun setDns(provider: String) { http = build(provider) }

    private fun build(provider: String): OkHttpClient {
        val b = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            // Keep connections to addons and video servers open for reuse (faster repeat requests).
            .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
            .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 12 })
            .retryOnConnectionFailure(true)
            // Keep cookies like a browser (and like Nuvio): some video servers (MovieBox, VA Player) set a cookie on the
            // playlist and refuse the video pieces (403 / 428) without it.
            .cookieJar(MemoryCookies)
        b.dns(CachedDns(dohFor(provider)?.let { FallbackDns(it) } ?: Dns.SYSTEM))
        return b.build()
    }

    private fun dohFor(p: String): Dns? = runCatching {
        when (p) {
            "cloudflare" -> doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
            "google" -> doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
            "quad9" -> doh("https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112")
            "adguard" -> doh("https://dns.adguard-dns.com/dns-query", "94.140.14.14", "94.140.15.15")
            else -> null
        }
    }.getOrNull()

    private fun doh(url: String, vararg ips: String): Dns =
        DnsOverHttps.Builder().client(bootstrap).url(url.toHttpUrl())
            .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
            .includeIPv6(false).build()

    /** Cookies kept in memory while the app runs (shared by addons, probes and the player). */
    object MemoryCookies : okhttp3.CookieJar {
        private val store = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            if (cookies.isEmpty()) return
            for (c in cookies) {
                val list = store.getOrPut(c.domain) { java.util.Collections.synchronizedList(ArrayList()) }
                synchronized(list) { list.removeAll { it.name == c.name && it.path == c.path }; list += c }
            }
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            // The addon gave this server's links their own Cookie header: keep it (OkHttp would replace it with ours).
            if (url.host in StreamHeaders.ownCookieHosts) return emptyList()
            val now = System.currentTimeMillis()
            val out = ArrayList<okhttp3.Cookie>()
            for ((_, list) in store) synchronized(list) {
                list.removeAll { it.expiresAt < now }
                list.filterTo(out) { it.matches(url) }
            }
            return out
        }
        fun count(): Int = store.values.sumOf { it.size }
    }

    /** Remembers server addresses for 10 minutes, so repeat lookups skip DNS. */
    private class CachedDns(val base: Dns) : Dns {
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
        override fun lookup(hostname: String): List<InetAddress> {
            cache[hostname]?.let { (at, ips) -> if (System.currentTimeMillis() - at < 600_000) return ips }
            return base.lookup(hostname).also { if (it.isNotEmpty()) cache[hostname] = System.currentTimeMillis() to it }
        }
    }

    /** If the chosen DNS can't answer, fall back to the normal one instead of failing. */
    private class FallbackDns(val primary: Dns) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            try { primary.lookup(hostname) } catch (e: Exception) { Dns.SYSTEM.lookup(hostname) }
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use {
            require(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
    }

    /** GET with extra headers (used for pages that need a browser-like request). */
    suspend fun getWith(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use {
            require(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
    }

    suspend inline fun <reified T> fetch(url: String): T = json.decodeFromString<T>(get(url))

    fun normalize(input: String): String =
        input.trim().replace("stremio://", "https://").removeSuffix("/manifest.json").trimEnd('/')
}
