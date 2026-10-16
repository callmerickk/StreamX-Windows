package com.example.streamnest

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One provider group in one quality group and language, as measured by the Source Test. */
@Serializable
data class RankEntry(
    val key: String, val addon: String = "", val label: String = "", val host: String = "",
    val bucket: String = "", val lang: String = "", val tests: Int = 0, val ok: Int = 0, val medianMs: Long = 0,
    val seekable: Boolean = true, val score: Double = 0.0,
    /** Never played in any test and always answered "not a video / not found / bad request": hidden from Sources. */
    val dead: Boolean = false,
)

@Serializable data class RankFile(val at: Long = 0, val author: String = "", val entries: List<RankEntry> = emptyList())

/** A provider group that failed twice in normal use (shown in Developer's Corner, re-tested by the next Source Test). */
@Serializable data class FailReport(val key: String, val label: String = "", val count: Int = 0, val reason: String = "", val at: Long = 0, val device: String = "")

/**
 * Which provider groups play fastest and most reliably, from the Source Test (shared with every StreamX user through the
 * cloud), plus what this phone learns while you watch: a group that fails twice goes "under observation" (tried last).
 */
object Ranker {
    @Volatile var file = RankFile()
        private set
    private val scores = ConcurrentHashMap<String, Double>()      // key|bucket|lang -> score
    private val anyScores = ConcurrentHashMap<String, Double>()   // key -> best score in any bucket
    private val unseekable = ConcurrentHashMap.newKeySet<String>()
    private val deadGroups = ConcurrentHashMap.newKeySet<String>()
    /** Links that answered with a web page / 400 / 404 in this session (never tried again). */
    private val deadUrls = ConcurrentHashMap.newKeySet<String>()
    private val fails = ConcurrentHashMap<String, Int>()
    private val deadHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val okHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val hostStrikes = ConcurrentHashMap<String, Int>()
    val observed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var prefs: android.content.SharedPreferences? = null
    private var dir: File? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.getSharedPreferences("ranker", Context.MODE_PRIVATE)
        dir = ctx.filesDir
        observed += prefs?.getStringSet("observed", emptySet()).orEmpty()
        deadHosts += prefs?.getStringSet("deadHosts", emptySet()).orEmpty()
        runCatching { use(Api.json.decodeFromString<RankFile>(File(ctx.filesDir, "ranking.json").readText())) }
    }

    private fun use(f: RankFile) {
        file = f
        scores.clear(); anyScores.clear(); unseekable.clear(); deadGroups.clear()
        f.entries.forEach { e ->
            scores["${e.key}|${e.bucket}|${e.lang}"] = e.score
            anyScores[e.key] = maxOf(anyScores[e.key] ?: Double.NEGATIVE_INFINITY, e.score)
            if (!e.seekable) unseekable += e.key
        }
        // A group is dead only when it never played in any quality / language and every test called it dead.
        f.entries.groupBy { it.key }.forEach { (k, l) -> if (l.all { it.dead && it.ok == 0 }) deadGroups += k }
        groupTotals.clear()
        f.entries.groupBy { it.key }.forEach { (k, l) -> groupTotals[k] = l.sumOf { it.tests } to l.sumOf { it.ok } }
    }

    /** Uses a new ranking (from a test or the cloud) and keeps it on the phone. A new test also clears "under observation". */
    fun apply(f: RankFile, fromTest: Boolean) {
        use(f)
        dir?.let { d -> runCatching { File(d, "ranking.json").writeText(Api.json.encodeToString(f)) } }
        if (fromTest) {
            val tested = f.entries.map { it.key }.toSet()
            observed.removeAll(tested); tested.forEach { fails.remove(it) }
            prefs?.edit()?.putStringSet("observed", observed.toSet())?.apply()
        }
    }

    /** Forget every test result on this phone (start the ranking from scratch). */
    fun reset() {
        deadHosts.clear(); okHosts.clear(); hostStrikes.clear()
        prefs?.edit()?.remove("deadHosts")?.apply()
        apply(RankFile(), fromTest = true)
        dir?.let { d -> runCatching { File(d, "ranking.json").delete() } }
    }

    // ───── Groups ─────

    private val noise = Regex(
        "\\b(2160p|1440p|1080p|720p|480p|360p|4k|uhd|fhd|hd|sd|hdr10\\+?|hdr|dv|dovi|sdr|x26[45]|h\\.?26[45]|hevc|avc|10bit|8bit|web-?dl|webrip|web|bluray|brrip|remux|hindi|english|dual|multi|audio|org|esub|esubs|aac|ddp?5\\.?1|atmos)\\b",
    )

    /** "addon|provider label|server" — the same for every title the provider serves. */
    fun groupKey(s: StreamItem): String {
        val host = runCatching { Uri.parse(s.stream.url.orEmpty()).host }.getOrNull().orEmpty().split('.').takeLast(2).joinToString(".")
        val base = (s.stream.behaviorHints?.bingeGroup?.takeIf { it.isNotBlank() } ?: s.stream.name ?: "").lowercase()
        val label = noise.replace(base, " ").replace(Regex("[^a-z0-9]+"), " ").trim()
            .split(" ").filter { it.isNotBlank() && it.length > 1 && !it.all(Char::isDigit) }.take(4).joinToString(" ")
        return "${s.addonName}|$label|$host"
    }

    fun labelOf(key: String): String = key.split('|').filter { it.isNotBlank() }.joinToString(" · ")

    /** 4k / fhd / mhd / low — the player's four quality groups. */
    fun bucket(res: Int): String = when { res >= 1440 -> "4k"; res >= 1080 -> "fhd"; res >= 720 -> "mhd"; else -> "low" }
    fun tier(res: Int): Int = when { res >= 1440 -> 4; res >= 1080 -> 3; res >= 720 -> 2; else -> 1 }

    fun lang(s: StreamItem): String { val l = s.languages(); return when { "hindi" in l -> "hindi"; "english" in l -> "english"; else -> "other" } }

    /** Reliability score: higher = more reliable and faster. Unknown = 0; under observation = far below everything. */
    fun score(s: StreamItem): Double {
        val k = groupKey(s)
        if (k in observed) return -1000.0
        return scores["$k|${bucket(s.resolution())}|${lang(s)}"] ?: anyScores[k] ?: 0.0
    }

    fun knownUnseekable(s: StreamItem): Boolean = groupKey(s) in unseekable

    /** Dead link: its provider group never plays (web pages, "not found", "bad request"), it failed that way this session,
     *  or it is a signed link that has already expired. Hidden from Sources and never chosen. */
    fun isDead(s: StreamItem): Boolean {
        val url = s.stream.url ?: return true
        return url in deadUrls || groupKey(s) in deadGroups || signedLinkExpired(url) || hostKey(url) in deadHosts
    }
    fun deadGroupCount(): Int = deadGroups.size
    fun deadHostList(): List<String> = deadHosts.toList().sorted()

    /** A dead link. Its server gets a strike: 3 strikes and never a working video = the whole server is hidden. */
    fun markDeadUrl(url: String) {
        deadUrls += url
        val h = hostKey(url)
        if (h.isEmpty() || h in okHosts) return
        val n = (hostStrikes[h] ?: 0) + 1
        hostStrikes[h] = n
        if (n >= 3 && deadHosts.add(h)) prefs?.edit()?.putStringSet("deadHosts", deadHosts.toSet())?.apply()
    }

    /** This server sent a real video: never hide it as a whole. */
    fun noteHostOk(url: String) {
        val h = hostKey(url)
        if (h.isEmpty() || !okHosts.add(h)) return
        hostStrikes.remove(h)
        if (deadHosts.remove(h)) prefs?.edit()?.putStringSet("deadHosts", deadHosts.toSet())?.apply()
    }

    /** Server name used for "whole server dead": the site (hubcloud.ist), but the full name on shared platforms
     *  (one workers.dev app can be dead while another works). */
    fun hostKey(url: String): String {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return ""
        val shared = listOf("workers.dev", "r2.dev", "pages.dev", "cloudflarestorage.com", "googleusercontent.com", "herokuapp.com", "vercel.app", "netlify.app")
        return if (shared.any { host.endsWith(it) }) host else host.split('.').takeLast(2).joinToString(".")
    }

    /** Is this answer a "dead link" (not a temporary problem)? 400 / 404 / 410, a web page instead of a video, or an
     *  expired / revoked signed storage link (Cloudflare R2 answers 403 with an XML error at once). */
    fun isDeadReason(status: Int, type: String?, url: String? = null): Boolean =
        status == 400 || status == 404 || status == 410 || (status in 200..299 && type?.startsWith("text/html", true) == true) ||
            (status == 403 && type?.contains("xml", true) == true && url != null && hostKey(url).endsWith("cloudflarestorage.com"))

    /**
     * A provider group that keeps failing in the Source Test (3+ tests and never played, or 4+ tests and under 25%).
     * With "Tested sources only" on, it is hidden from Sources and never picked; untested groups always stay.
     */
    fun keepsFailing(s: StreamItem): Boolean = keyFailing(groupKey(s))
    fun keyFailing(k: String): Boolean {
        val st = groupTotals[k] ?: return false
        return (st.first >= 3 && st.second == 0) || (st.first >= 4 && st.second.toDouble() / st.first < 0.25)
    }
    /** tests / played per group over every quality and language. */
    private val groupTotals = ConcurrentHashMap<String, Pair<Int, Int>>()

    /** Amazon-style signed links (Cloudflare R2 …) carry their own expiry: X-Amz-Date + X-Amz-Expires. */
    fun signedLinkExpired(url: String): Boolean = runCatching {
        val u = Uri.parse(url)
        val date = u.getQueryParameter("X-Amz-Date") ?: return false
        val secs = u.getQueryParameter("X-Amz-Expires")?.toLongOrNull() ?: return false
        val fmt = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val start = fmt.parse(date)?.time ?: return false
        System.currentTimeMillis() > start + secs * 1000 - 30_000
    }.getOrDefault(false)

    /** Score of a measured group: success rate first, then speed; files you can't jump inside are pushed down. */
    fun scoreOf(ok: Int, tests: Int, medianMs: Long, seekable: Boolean): Double {
        if (tests == 0) return 0.0
        val rate = ok.toDouble() / tests
        val secs = (medianMs / 1000.0).coerceAtMost(20.0)
        return rate * 100 - secs * 4 - (if (seekable) 0 else 60)
    }

    // ───── Learning while you watch ─────

    fun recordOk(s: StreamItem?) {
        s?.stream?.url?.let { noteHostOk(it) }
        val k = s?.let { groupKey(it) } ?: return
        fails.remove(k)
    }

    /** Failures from real plays since the last Source Test (shown at the top of the next test log). */
    fun playFails(): List<String> = prefs?.getString("playFails", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    fun clearPlayFails() { prefs?.edit()?.remove("playFails")?.apply() }
    private fun logPlayFail(item: StreamItem, reason: String) {
        val p = prefs ?: return
        val t = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date())
        val line = "$t · ${labelOf(groupKey(item))} · ${Prepare.qualityName(item.resolution())} · ${reason.replace('\n', ' ').take(140)} · ${item.stream.url.orEmpty().take(90)}"
        p.edit().putString("playFails", (playFails() + line).takeLast(150).joinToString("\n")).apply()
    }

    /** A link failed to play. The second failure in a row puts its group under observation and reports it. */
    suspend fun recordFail(s: StreamItem?, reason: String) {
        val item = s ?: return
        logPlayFail(item, reason)
        val k = groupKey(item)
        val n = (fails[k] ?: 0) + 1
        fails[k] = n
        if (n >= 2 && k !in observed) {
            observed += k
            prefs?.edit()?.putStringSet("observed", observed.toSet())?.apply()
            report(FailReport(k, labelOf(k), n, reason.take(160), System.currentTimeMillis(), android.os.Build.MODEL ?: ""))
        }
    }

    // ───── Cloud (shared/sourceRanks: everyone reads, only the owner writes; shared/reports: failure reports) ─────

    private val jsonType = "application/json".toMediaType()

    /** Downloads the shared ranking (no sign-in needed). */
    suspend fun fetchShared(): Boolean = withContext(Dispatchers.IO) {
        val f = runCatching { Api.json.decodeFromString<RankFile>(Api.get("${CloudConfig.DB_URL}/shared/sourceRanks.json")) }.getOrNull()
            ?: return@withContext false
        if (f.entries.isNotEmpty() && f.at > file.at) apply(f, fromTest = false)
        true
    }

    /** Uploads this phone's ranking for everyone. Only works from the owner's account (Firebase rules). Null = done, else why not. */
    suspend fun upload(): String? = withContext(Dispatchers.IO) {
        if (file.entries.isEmpty()) return@withContext "No ranking yet — run the Source Test first."
        val t = Cloud.token() ?: return@withContext "Sign in first (Settings › Account & Cloud Sync)."
        runCatching {
            val req = Request.Builder().url("${CloudConfig.DB_URL}/shared/sourceRanks.json?auth=$t")
                .put(Api.json.encodeToString(file).toRequestBody(jsonType)).build()
            Api.http.newCall(req).execute().use { r -> if (r.isSuccessful) null else if (r.code == 401 || r.code == 403) "Not allowed: only the owner's account can upload." else "HTTP ${r.code}" }
        }.getOrElse { "Couldn't reach the cloud: ${it.javaClass.simpleName}" }
    }

    private suspend fun report(r: FailReport) = withContext(Dispatchers.IO) {
        val uid = Cloud.user.value?.uid ?: return@withContext
        val t = Cloud.token() ?: return@withContext
        val id = Integer.toHexString(r.key.hashCode())
        runCatching {
            val req = Request.Builder().url("${CloudConfig.DB_URL}/shared/reports/$uid/$id.json?auth=$t")
                .put(Api.json.encodeToString(r).toRequestBody(jsonType)).build()
            Api.http.newCall(req).execute().close()
        }
    }

    /** Every user's failure reports (owner only). */
    suspend fun allReports(): Pair<List<FailReport>, String?> = withContext(Dispatchers.IO) {
        val t = Cloud.token() ?: return@withContext emptyList<FailReport>() to "Sign in first."
        runCatching {
            val body = Api.get("${CloudConfig.DB_URL}/shared/reports.json?auth=$t")
            if (body.trim() == "null") return@runCatching emptyList<FailReport>() to null
            val byUser = Api.json.decodeFromString<Map<String, Map<String, FailReport>>>(body)
            byUser.values.flatMap { it.values }.groupBy { it.key }.map { (k, l) ->
                FailReport(k, l.first().label, l.sumOf { it.count }, l.maxByOrNull { it.at }?.reason.orEmpty(), l.maxOf { it.at }, "${l.size} phone(s)")
            }.sortedByDescending { it.count } to null
        }.getOrElse { emptyList<FailReport>() to "Couldn't read reports (${it.message?.take(60)})" }
    }
}

/**
 * Candidates for a quality level, best first: the right quality group first (4K / Full HD / Mid HD / Low — falling back
 * to the next lower one), then your language (Hindi → English…), then the measured reliability, then resolution.
 * Groups under observation and files you can't jump inside go last.
 */
fun AppViewModel.rankedCandidates(list: List<StreamItem>, level: String, quickOnly: Boolean): List<StreamItem> {
    val testedOnly = isOn(texts.value["testedOnly"] ?: "yes")
    val pool = list.filter { it.stream.url != null && (!quickOnly || isQuick(it)) && !Ranker.isDead(it) && !(testedOnly && Ranker.keepsFailing(it)) }
        .distinctBy { it.stream.url }
    val target = AllQualityLevels.firstOrNull { it.id == level }?.res ?: 0
    // Auto: Full HD (TV and phone start fast), else the best below, else anything.
    val targetTier = if (target == 0) 3 else Ranker.tier(target)
    fun tierCost(s: StreamItem): Int { val t = Ranker.tier(s.resolution()); return if (t == targetTier) 0 else if (t < targetTier) targetTier - t else 10 + t }
    return pool.sortedWith(
        compareBy<StreamItem>({ if (Ranker.score(it) <= -1000.0) 1 else 0 }, { if (Ranker.knownUnseekable(it)) 1 else 0 }, { if (it.unplayableHere()) 1 else 0 },
            { tierCost(it) }, { langRank(it) }, { if (it.isHeavy()) 1 else 0 })
            .thenByDescending { Ranker.score(it) }.thenByDescending { it.resolution() },
    )
}


/** What this device can decode (checked once). */
object DeviceCodecs {
    val dolbyVision: Boolean by lazy {
        runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .any { !it.isEncoder && it.supportedTypes.any { t -> t.equals("video/dolby-vision", true) } }
        }.getOrDefault(false)
    }
}

private fun StreamItem.allText(): String = listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints?.filename).joinToString(" ").lowercase()
private val heavyRx = Regex("remux|truehd|true-hd|dts-?hd|dts-?x|dts[ ._-]?ma")
private val dvRx = Regex("(^|[^a-z])(dv|dovi|dolby[ ._-]?vision)([^a-z]|$)")
private val hdrRx = Regex("hdr10|hdr")

/** Huge "REMUX" files or lossless TrueHD / DTS-HD audio: slow to start on mobile data — chosen only when nothing lighter exists. */
fun StreamItem.isHeavy(): Boolean = heavyRx.containsMatchIn(allText()) || (stream.behaviorHints?.videoSize ?: 0L) > 12L * 1024 * 1024 * 1024

/** Dolby Vision-only video (no HDR10 / SDR base) on a device without a Dolby Vision decoder: no picture. */
fun StreamItem.unplayableHere(): Boolean {
    if (DeviceCodecs.dolbyVision) return false
    val t = allText()
    return dvRx.containsMatchIn(t) && !hdrRx.containsMatchIn(t)
}
