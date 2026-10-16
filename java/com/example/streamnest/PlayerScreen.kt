package com.example.streamnest

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Pause as RoundedPause
import androidx.compose.material.icons.rounded.PlayArrow as RoundedPlay
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.animateDp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Inter: the clean geometric sans used by the player (bundled, SIL Open Font License). */
val InterFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun subMime(url: String) = when {
    url.contains(".vtt", true) -> MimeTypes.TEXT_VTT
    url.contains(".ass", true) || url.contains(".ssa", true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

/** Opens a stream in another installed player (VLC, MX Player…) through Android's app chooser. */
fun openExternal(ctx: android.content.Context, url: String, title: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title)
        ctx.startActivity(Intent.createChooser(i, "Open with"))
    } catch (e: Exception) {
        Snack.show("No other video player found")
    }
}

const val PlayerUserAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

/** Builds the video player: fast-start buffering, the app's HTTP client (DNS setting), FFmpeg audio, remembered languages. */
@OptIn(UnstableApi::class)
fun buildPlayer(ctx: android.content.Context, vm: AppViewModel, fast: Boolean, cache: Boolean = !BuildConfig.IS_TV): ExoPlayer {
    // Smooth like Nuvio: start after ~1.5 s of video, then keep up to a minute ready (capped at ~200 MB so
    // big 4K files can't run the device out of memory), plus 20 s behind you so short rewinds don't reload.
    // TV sticks (Fire TV: 1 GB RAM) get a smaller budget: 64 MB, up to 40 s ahead and 10 s behind.
    val tv = BuildConfig.IS_TV
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(if (tv) 20_000 else 25_000, if (tv) 40_000 else 60_000, if (fast) 1_500 else 2_500, if (fast) 3_000 else 5_000)
        .setTargetBufferBytes(if (tv) 64 * 1024 * 1024 else 200 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .setBackBuffer(if (tv) 10_000 else 20_000, true)
        .build()
    // Streams go through the app's HTTP client (DNS setting) with the addon's request headers.
    val http = OkHttpDataSource.Factory(Api.http).setUserAgent(PlayerUserAgent)
    // Phones: read the saved video first (instant play), then the network. (Source Test measures without it.)
    val dataSource = DefaultDataSource.Factory(ctx, withStreamHeaders(http)).let { if (cache) StreamCache.factory(ctx, it) else it }
    // The phone's own hardware decoders first (smoothest); FFmpeg only for audio the phone can't decode
    // (Dolby Digital / DD+ / DTS / TrueHD). Asynchronous decoder feeding = steadier frame pacing.
    // Dolby Vision on phones without a Dolby Vision decoder (like Nuvio): play the picture layer underneath with the
    // normal HEVC decoder (DV profiles 7 and 8 carry an HDR10 picture; profile 9 an H.264 one).
    val dvFallback = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mime, secure, tunneling ->
        val list = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
        if (mime == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION && list.isEmpty())
            androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(androidx.media3.common.MimeTypes.VIDEO_H265, secure, tunneling)
        else list
    }
    val renderers = DefaultRenderersFactory(ctx)
        .setMediaCodecSelector(dvFallback)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)
        .forceEnableMediaCodecAsynchronousQueueing()
    return ExoPlayer.Builder(ctx, renderers).setLoadControl(load).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
        val aLang = vm.playerPref("audio")
        val sLang = vm.playerPref("subs")
        trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
            // Inside multi-audio files: your remembered language, else your Quick Start order (Hindi → English…).
            val codes = mapOf("hindi" to "hi", "english" to "en", "tamil" to "ta", "telugu" to "te", "malayalam" to "ml", "kannada" to "kn",
                "bengali" to "bn", "marathi" to "mr", "punjabi" to "pa", "japanese" to "ja", "korean" to "ko", "spanish" to "es", "french" to "fr",
                "german" to "de", "chinese" to "zh", "russian" to "ru", "italian" to "it", "portuguese" to "pt", "arabic" to "ar", "turkish" to "tr")
            val prefs = (listOf(aLang).filter { it.isNotEmpty() && it != "off" } + vm.qsLangs().mapNotNull { codes[it] }).distinct()
            if (prefs.isNotEmpty()) setPreferredAudioLanguages(*prefs.toTypedArray())
            when {
                sLang == "off" -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                sLang.isNotEmpty() -> setPreferredTextLanguage(sLang)
            }
        }.build()
    }
}

fun subtitleConfigs(subs: List<Subtitle>) = subs.filter { it.url.isNotBlank() }.map {
    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url)).setMimeType(subMime(it.url)).setLanguage(it.lang).setLabel(it.lang).build()
}

/** A video prepared in the background on the title page, handed to the player when you press Play. */
class Preloaded(val key: String, val url: String, val subs: List<Subtitle>, val source: String?, val addon: String?, val quick: List<StreamItem>, val player: ExoPlayer)

object Preloader {
    private var cur: Preloaded? = null
    fun set(p: Preloaded) { cur?.let { if (it !== p) runCatching { it.player.release() } }; cur = p }
    fun has(key: String) = cur?.key == key
    fun take(key: String): Preloaded? = cur?.takeIf { it.key == key }?.also { cur = null }
    fun release(key: String? = null) { cur?.let { if (key == null || it.key == key) { runCatching { it.player.release() }; cur = null } } }
}

/** One way of playing a link. */
data class Way(val vlc: Boolean, val hw: Boolean, val relay: Boolean) {
    fun label() = if (!vlc) "ExoPlayer" else "VLC " + (if (hw) "HW" else "SW") + (if (relay) " + relay" else "")
}

/**
 * The order in which a link is tried. Links that need more headers than VLC can send go through the relay straight away.
 * Big files ([big]) stay on hardware decoding: software decoding is only the very last resort for them.
 */
fun waysFor(url: String, engine: String, big: Boolean = false, manifest: Boolean = false): List<Way> {
    val h = StreamHeaders.forUrl(url)
    // DASH / HLS: ExoPlayer first whatever the engine setting (it plays manifests best), and never through the relay
    // (a manifest's relative segment links can't be followed through it).
    if (manifest) return listOf(Way(false, true, false), Way(true, true, false), Way(true, false, false))
    val needsRelay = h.keys.any { !it.equals("User-Agent", true) && !it.equals("Referer", true) }
    val vlcWays = when {
        needsRelay -> listOf(Way(true, true, true), Way(true, false, true))
        h.isNotEmpty() -> listOf(Way(true, true, false), Way(true, false, false), Way(true, true, true))
        else -> listOf(Way(true, true, false), Way(true, false, false))
    }
    val exo = Way(false, true, false)
    val all = if (engine == "exo") listOf(exo) + vlcWays else vlcWays + exo
    if (!big) return all
    val software = all.filter { it.vlc && !it.hw }
    return all.filter { !(it.vlc && !it.hw) } + software
}

/** VLC network cache for a stream: small files start fast (1 s), big / heavy files get more room so they don't stutter. */
fun cachingFor(s: StreamItem?): Int {
    if (s == null) return 1000
    val size = s.stream.behaviorHints?.videoSize ?: 0L
    val t = s.tags()
    val hevc = "hevc" in t
    val gb = 1024L * 1024L * 1024L
    return when {
        size > 6L * gb || "4k" in t || "remux" in t || "10bit" in t || (hevc && s.resolution() > 1080) -> 4000
        size > 5L * gb / 2L || hevc -> 2500
        else -> 1000
    }
}

/**
 * The next episode's link, found while the current one is still playing (last quarter), so it starts at once.
 * Consumed by the next episode's "Finding stream source" step (also in Quick Start mode).
 */
object NextUp {
    var key: String? = null
    var url: String? = null
    var source: String? = null
    var addon: String? = null
    var subs: List<Subtitle> = emptyList()
    var at: Long = 0

    fun set(k: String, u: String, src: String?, add: String?, s: List<Subtitle>) {
        key = k; url = u; source = src; addon = add; subs = s; at = System.currentTimeMillis()
    }
    fun fresh(k: String): Boolean = key == k && url != null && System.currentTimeMillis() - at < STREAM_CACHE_MS
    fun clear(k: String? = null) {
        if (k != null && key != k) return
        key = null; url = null; source = null; addon = null; subs = emptyList(); at = 0
    }
}

/** Opens the first megabyte of a link in the background, so the server (and DNS / TLS) is warm when it plays. */
fun warmUpLink(url: String) {
    if (!url.startsWith("http")) return
    runCatching {
        val b = okhttp3.Request.Builder().url(url).header("Range", "bytes=0-1048575").header("User-Agent", PlayerUserAgent)
        StreamHeaders.forUrl(url).forEach { (k, v) -> runCatching { b.header(k, v) } }
        Api.http.newCall(b.build()).execute().use { r ->
            r.body?.byteStream()?.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (total < 1_048_576) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
            }
        }
    }
}

/** Copies the frame on screen (VLC or ExoPlayer surface) so it can stay visible while another link loads. */
fun captureFrame(root: View, onDone: (android.graphics.Bitmap?) -> Unit) {
    fun find(v: View, pick: (View) -> Boolean): View? {
        if (pick(v)) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pick)?.let { return it }
        return null
    }
    try {
        (find(root) { it is android.view.TextureView && it.isAvailable } as? android.view.TextureView)?.let { tv ->
            onDone(tv.getBitmap(tv.width / 2, tv.height / 2)); return
        }
        val sv = find(root) { it is android.view.SurfaceView && it.width > 0 && it.visibility == View.VISIBLE } as? android.view.SurfaceView
        if (sv == null || !sv.holder.surface.isValid || android.os.Build.VERSION.SDK_INT < 24) { onDone(null); return }
        val bmp = android.graphics.Bitmap.createBitmap((sv.width / 2).coerceAtLeast(1), (sv.height / 2).coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        android.view.PixelCopy.request(sv, bmp, { r -> onDone(if (r == android.view.PixelCopy.SUCCESS) bmp else null) }, android.os.Handler(android.os.Looper.getMainLooper()))
    } catch (e: Throwable) { onDone(null) }
}

private data class TrackOpt(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

/** What is actually playing (the link can change: finding, fallbacks, switching source). */
private data class Now(val url: String, val subs: List<Subtitle>, val source: String?, val addon: String?)

private val noRipple = MutableInteractionSource()

@OptIn(UnstableApi::class)
@kotlin.OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun PlayerScreen(
    vm: AppViewModel,
    p: Screen.Player,
    onBack: () -> Unit,
    onReplace: (Screen.Player) -> Unit,
    onProgress: (pos: Long, dur: Long, url: String, subs: List<Subtitle>) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val texts by vm.texts.collectAsStateWithLifecycle()
    fun opt(k: String) = texts[k] ?: Defaults[k] ?: ""

    val type = p.type.ifBlank { if (p.key.contains(":")) "series" else "movie" }
    var meta by remember { mutableStateOf(p.meta) }
    // A video already prepared on the title page starts instantly.
    val pre = remember {
        Preloader.take(p.key)?.let { pl ->
            val bad = pl.player.playerError != null || pl.player.playbackState == androidx.media3.common.Player.STATE_IDLE
            if (bad) { runCatching { pl.player.release() }; PlayTiming.mark("paused player was broken — opening the link again"); null }
            else pl.also { StreamHeaders.use(it.url) }
        }
    }
    var now by remember {
        mutableStateOf(
            pre?.let { Now(it.url, it.subs, it.source, it.addon) } ?: if (p.url.isNotBlank()) Now(p.url, p.subs, p.source, p.addon) else null,
        )
    }
    var quickList by remember { mutableStateOf(pre?.quick ?: emptyList()) }
    var quality by remember { mutableStateOf(vm.playerPref("quality").ifEmpty { vm.texts.value["qsQuality"] ?: "auto" }) }
    var qualityMenu by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf(p.candidates) }
    // Every stream we know for this title (for language versions in the Audio panel).
    var known by remember { mutableStateOf(p.candidates) }
    var otherLangsOpen by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(p.resumeMs) }
    var dur by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var firstFrame by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (p.url.isBlank() && pre == null) "Finding stream source" else "Buffering…") }
    var failure by remember { mutableStateOf<String?>(null) }
    var findRequest by remember { mutableIntStateOf(if (p.url.isBlank() && pre == null) 1 else 0) }
    var controls by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    /** Intros / recaps already auto-skipped in this video (each only once, so you can seek back into them). */
    val autoSkipped = remember { mutableSetOf<String>() }
    /** A finger is on the controls (they stay up until it lifts). */
    var touchingControls by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var lastPanel by remember { mutableStateOf("sources") }
    var showEpisodes by remember { mutableStateOf(false) }
    // Remembered per profile: aspect ratio, audio language, subtitle language (Help: auto-applied next time).
    var aspectIdx by remember { mutableIntStateOf(vm.playerPref("aspect").toIntOrNull()?.coerceIn(0, AspectModes.lastIndex) ?: 0) }
    var resize by remember { mutableIntStateOf(AspectModes[aspectIdx].second) }
    var holdSpeed by remember { mutableStateOf<Float?>(null) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    val haptics = LocalHapticFeedback.current
    var speed by remember { mutableFloatStateOf(1f) }
    var advisory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showAdvisory by remember { mutableStateOf(false) }
    var segs by remember { mutableStateOf<Segments?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var aspectLabel by remember { mutableStateOf<String?>(null) }
    var hud by remember { mutableStateOf<Pair<String, Float>?>(null) }
    // ───── Remote / keyboard (TV): a focusable root that reads the keys; D-pad seeking moves a pending target first ─────
    val rootFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val failFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var panelHasFocus by remember { mutableStateOf(false) }
    var menuHasFocus by remember { mutableStateOf(false) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var seekKeyTick by remember { mutableIntStateOf(0) }
    var focusRowTick by remember { mutableIntStateOf(0) }
    // Hotstar-style remote: ▲ shows the controls and pauses; ▼ (from the buttons) hides them and plays on.
    var pausedByUp by remember { mutableStateOf(false) }
    val seekFocus = remember { FocusRequester() }
    var seekFocused by remember { mutableStateOf(false) }
    var topHasFocus by remember { mutableStateOf(false) }
    var rowHasFocus by remember { mutableStateOf(false) }
    var watchNext by remember { mutableStateOf(false) }
    // Seek bubble while the controls are hidden: direction and seconds jumped so far (◀ ▶ = 30 s each).
    var bubble by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var bubbleTick by remember { mutableIntStateOf(0) }
    // Seek-bar preview frames for the link that is playing (made only while you seek).
    val nowUrl = now?.url
    val thumbsOn = isOn(opt("seekThumbs"))
    val thumbs = remember(nowUrl, thumbsOn) { if (thumbsOn && nowUrl != null) SeekThumbs(nowUrl, StreamHeaders.forUrl(nowUrl)) else null }
    DisposableEffect(thumbs) { onDispose { thumbs?.close() } }
    // Open the file for previews a little after playback starts, so the first seek-bar frame shows quickly.
    LaunchedEffect(thumbs, firstFrame) { if (firstFrame && thumbs != null) { delay(8000); thumbs.warmUp() } }
    val audio = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var autoNextDone by remember { mutableStateOf(false) }
    var linkSaved by remember { mutableStateOf(false) }
    // Loading scenes: how many addons answered the search, the best quality seen, and the "serving" progress.
    var answered by remember { mutableIntStateOf(0) }
    var sourceTotal by remember { mutableIntStateOf(0) }
    var bestRes by remember { mutableIntStateOf(0) }
    var bufProgress by remember { mutableFloatStateOf(0f) }
    var brightApplied by remember { mutableStateOf(false) }
    // Starting volume (Playback > Volume): the phone's own volume before we changed it (-1 = untouched), restored on leaving.
    var volApplied by remember { mutableStateOf(false) }
    var volBefore by remember { mutableIntStateOf(-1) }
    // Refused links: fresh links are looked up once per title; links that already failed are never tried again.
    var refreshedOnce by remember { mutableStateOf(false) }
    var extendedAll by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    // Links the title page already found broken are never tried again here.
    val tried = remember { Prepare.failedFor(p.key).toMutableSet() }
    /** Files that failed to open (not a refusal): their other links are skipped. */
    val badFiles = remember { mutableSetOf<String>() }
    /** The same file behind different links: name + size when the addon gives them, else server + path (no signature). */
    fun fileId(url: String): String {
        val s0 = known.firstOrNull { it.stream.url == url } ?: candidates.firstOrNull { it.stream.url == url }
        val f = s0?.stream?.behaviorHints?.filename?.lowercase(); val z = s0?.stream?.behaviorHints?.videoSize
        if (!f.isNullOrBlank() && z != null && z > 0) return "f:$f:$z"
        return runCatching { val u = Uri.parse(url); "u:${u.host}${u.path}" }.getOrDefault(url)
    }
    // Play timing log: start a record when the player opens without a Play press (next episode, Continue Watching…).
    remember {
        if (!PlayTiming.active()) PlayTiming.begin(p.title, "player opened")
        PlayTiming.mark("player screen open" + (if (pre != null) " · using the ready paused player" else "") + (if (p.instant) " · instant (saved video)" else ""))
        true
    }
    fun streamAddonCount(only: Set<String>? = null): Int =
        vm.addons.value.count { vm.isAddonOn(it) && it.supports("stream", type, p.key) && (only == null || it.manifest.name in only) }
    fun noteAnswer(list: List<StreamItem>, done: Boolean) {
        answered = if (done) sourceTotal else (answered + 1).coerceAtMost(sourceTotal)
        val r = list.maxOfOrNull { it.resolution() } ?: 0
        if (r > bestRes) bestRes = r
    }

    // Faster start: begin playing after ~1 s of video instead of the default 2.5 s (Playback > Fast start).
    val player = remember { pre?.player ?: buildPlayer(ctx, vm, isOn(texts["fastStart"] ?: Defaults["fastStart"] ?: "yes")) }

    // ───── Sound (Audio boost / Night mode), Sleep timer, Picture-in-picture ─────
    var boost by remember { mutableStateOf(vm.playerPref("boost") == "yes") }
    var night by remember { mutableStateOf(vm.playerPref("night") == "yes") }
    LaunchedEffect(boost, night) { SoundFx.apply(player.audioSessionId, boost, night) }
    DisposableEffect(Unit) { onDispose { SoundFx.release() } }
    /** Sleep timer: stops at this time (elapsed realtime), or at the end of this video. */
    var sleepAt by remember { mutableStateOf<Long?>(null) }
    var sleepEnd by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        PipState.playerOpen = true
        val act = ctx as? androidx.activity.ComponentActivity
        val l = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        act?.addOnPictureInPictureModeChangedListener(l)
        onDispose { PipState.playerOpen = false; PipState.playing = false; act?.removeOnPictureInPictureModeChangedListener(l) }
    }

    // ───── Engines: VLC (default) and ExoPlayer (Playback > Player engine). Same controls for both. ─────
    // Every link is tried several ways before giving up on it:
    //   VLC hardware decoding → VLC software decoding → VLC through the header relay → ExoPlayer
    // ("exo" setting: ExoPlayer first). Only then the next source is tried.
    // Instant play (saved video) needs ExoPlayer: only it reads the saved part.
    val engine = if (p.instant) "exo" else opt("playerEngine").let { if (it == "exo") "exo" else "vlc" }
    val openedAt = remember { SystemClock.elapsedRealtime() }
    var vlc by remember { mutableStateOf<VlcEngine?>(null) }
    var usingVlc by remember { mutableStateOf(false) }
    var vlcTick by remember { mutableIntStateOf(0) } // bumps when VLC tracks may have changed
    var ways by remember { mutableStateOf<List<Way>>(emptyList()) }
    var wayIdx by remember { mutableIntStateOf(0) }
    var loadTick by remember { mutableIntStateOf(0) }
    var midRetries by remember { mutableIntStateOf(0) }
    // Where the video should start / continue (kept while a new link loads, so a switch never restarts at 0:00).
    var resumeAt by remember { mutableLongStateOf(p.resumeMs) }
    // Switching while playing: keep the last frame on screen (blurred) instead of the loading screen.
    var switching by remember { mutableStateOf(false) }
    var switchLabel by remember { mutableStateOf<String?>(null) }
    var frozen by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var videoView by remember { mutableStateOf<View?>(null) }

    fun curPos(): Long = if (usingVlc) vlc?.time ?: 0L else player.currentPosition
    fun curDur(): Long = if (usingVlc) vlc?.length ?: 0L else player.duration.coerceAtLeast(0L)
    fun curBuffered(): Long = if (usingVlc) curPos() else player.bufferedPosition
    fun isPlayingNow(): Boolean = if (usingVlc) vlc?.isPlaying == true else player.isPlaying
    /** What this player shows, for Watch together. */
    fun roomState(play: Boolean, pos: Long = curPos()) =
        RoomState(url = now?.url.orEmpty(), key = p.key, title = p.title, image = p.image, type = type, pos = pos, playing = play)
    fun seekAbs(ms: Long) {
        if (usingVlc) vlc?.seek(ms) else player.seekTo(ms)
        if (firstFrame) Together.local(roomState(isPlayingNow(), ms))
    }
    fun setRate(f: Float) { if (usingVlc) vlc?.rate = f else player.setPlaybackSpeed(f) }
    fun rateNow(): Float = if (usingVlc) vlc?.rate ?: 1f else player.playbackParameters.speed
    fun pauseNow() { if (usingVlc) vlc?.pause() else player.pause(); if (firstFrame) Together.local(roomState(false)) }
    fun playNow() { if (usingVlc) vlc?.play() else player.play() }
    fun togglePlay() { if (isPlayingNow()) pauseNow() else { playNow(); if (firstFrame) Together.local(roomState(true)) } }
    /** Position to keep when the link changes. */
    fun keepPos(): Long = if (firstFrame) curPos().takeIf { it > 0 } ?: resumeAt else resumeAt

    var onEngineError: (String) -> Unit = {}
    val probeSeq = remember { intArrayOf(0) }

    /** The stream entry behind a link, when we know it (for its size and tags). */
    fun itemFor(url: String): StreamItem? =
        known.firstOrNull { it.stream.url == url } ?: candidates.firstOrNull { it.stream.url == url } ?: quickList.firstOrNull { it.stream.url == url }

    fun startVlc(n: Now, startMs: Long, w: Way) {
        runCatching { player.stop() }
        usingVlc = true
        firstFrame = false; failure = null; status = "Buffering…"; linkSaved = false
        val v = vlc ?: VlcEngine(ctx, object : VlcListener {
            override fun onPlaying(p0: Boolean) { playing = p0; if (p0) vlcTick++ }
            override fun onBuffering(b: Boolean) { buffering = b }
            override fun onFirstFrame() {
                firstFrame = true; buffering = false; vlcTick++
                if (!linkSaved) { linkSaved = true; now?.let { nn -> if (nn.url.startsWith("http")) vm.rememberLink(p.key, nn.url, nn.source, nn.addon) } }
            }
            override fun onError() { onEngineError("VLC_ERROR") }
            override fun onEnded() { playing = false }
        }).also { vlc = it }
        v.preferAudio = vm.qsLangs().filter { it != "original" } + listOfNotNull(vm.playerPref("audio").takeIf { it.isNotBlank() && it != "off" }?.let { runCatching { java.util.Locale(it).displayLanguage.lowercase() }.getOrNull() })
        val url = if (w.relay) Relay.wrap(n.url) else n.url
        // Small files start after 1 s of cache; big / 4K / HEVC files get more so they don't stutter.
        v.load(url, if (w.relay) emptyMap() else StreamHeaders.forUrl(n.url), n.subs, startMs, hw = w.hw, cachingMs = cachingFor(itemFor(n.url)))
        now = n
    }

    fun startExo(n: Now, startMs: Long) {
        if (usingVlc) { vlc?.stop(); usingVlc = false }
        val subs = subtitleConfigs(n.subs)
        firstFrame = false; failure = null; status = "Buffering…"; linkSaved = false
        // Tell ExoPlayer what the link is (DASH / HLS / file) when the link itself doesn't say.
        val mime = StreamType.mimeFor(n.url, itemFor(n.url)?.stream?.behaviorHints?.filename)
        player.setMediaItem(MediaItem.Builder().setUri(n.url).apply { if (mime != null) setMimeType(mime) }.setSubtitleConfigurations(subs).build(), startMs)
        player.prepare(); player.playWhenReady = true
        now = n
    }

    fun startWay(n: Now, startMs: Long) {
        loadTick++
        val w = ways.getOrNull(wayIdx) ?: Way(vlc = engine == "vlc", hw = true, relay = false)
        PlayTiming.mark("opening link with ${w.label()} · ${runCatching { Uri.parse(n.url).host }.getOrNull() ?: "?"}" +
            (itemFor(n.url)?.let { " · ${Prepare.qualityName(it.resolution())}" } ?: "") + (if (startMs > 0) " · from ${fmtTime(startMs)}" else ""))
        if (w.vlc) startVlc(n, startMs, w) else startExo(n, startMs)
    }

    fun load(n: Now, startMs: Long, label: String? = null) {
        // Mid-playback switch (audio language / source / quality): freeze the last frame and continue from the same second.
        if (firstFrame) {
            switching = true
            switchLabel = label
            videoView?.let { vv -> captureFrame(vv) { bmp -> if (switching) frozen = bmp } }
        }
        StreamHeaders.active = StreamHeaders.forUrl(n.url)
        resumeAt = startMs
        ways = waysFor(n.url, engine, big = cachingFor(itemFor(n.url)) > 1000, manifest = StreamType.mimeFor(n.url, itemFor(n.url)?.stream?.behaviorHints?.filename) != null)
        wayIdx = 0
        midRetries = 0
        // A link that doesn't show its type: one quick look first (DASH / HLS manifests behind proxy links).
        if (StreamType.needsProbe(n.url, itemFor(n.url)?.stream?.behaviorHints?.filename)) {
            val seq = ++probeSeq[0]
            now = n
            scope.launch {
                val mime = withContext(Dispatchers.IO) { StreamType.probe(n.url) }
                if (seq != probeSeq[0]) return@launch // another link was picked meanwhile
                if (mime.isNotEmpty()) ways = waysFor(n.url, engine, big = cachingFor(itemFor(n.url)) > 1000, manifest = true)
                startWay(n, startMs)
            }
        } else {
            probeSeq[0]++
            startWay(n, startMs)
        }
    }

    /** Subtitles of a new stream plus the ones already loaded (from the subtitle addons), without duplicates. */
    fun withSubs(st: StreamItem, old: List<Subtitle>): List<Subtitle> =
        st.stream.subtitles.orEmpty() + old.filter { s -> st.stream.subtitles.orEmpty().none { it.url == s.url } }

    // ───── Watch together: follow the room (play / pause / seek / video), share this player's state ─────
    DisposableEffect(Unit) {
        Together.provider = { roomState(isPlayingNow()) }
        Together.myName = vm.current.value?.name ?: Cloud.user.value?.email?.substringBefore('@') ?: ""
        onDispose { Together.provider = null }
    }
    var appliedRev by remember { mutableLongStateOf(Together.remote.value?.rev ?: 0L) }
    LaunchedEffect(Unit) {
        Together.remote.collect { st ->
            if (st == null || st.by == Together.me || st.rev <= appliedRev || !st.url.startsWith("http")) return@collect
            appliedRev = st.rev
            val expected = st.pos + if (st.playing) (System.currentTimeMillis() - st.receivedAt + 300) else 0L
            when {
                // The room moved to another title: open it here too.
                st.key.isNotBlank() && st.key != p.key -> onReplace(Screen.Player(st.title, st.url, emptyList(), key = st.key, image = st.image, resumeMs = expected, type = st.type))
                // Same title, another link (source / quality changed): switch to it at the same second.
                st.url != now?.url -> load(Now(st.url, now?.subs.orEmpty(), "Watch together", null), expected, "Watch together")
                else -> {
                    Together.applying = true
                    try {
                        if (kotlin.math.abs(curPos() - expected) > 2500) seekAbs(expected)
                        if (st.playing && !isPlayingNow()) playNow() else if (!st.playing && isPlayingNow()) pauseNow()
                    } finally { Together.applying = false }
                }
            }
        }
    }

    // Booster (Settings › Playback › Parallel download): while a big file plays, the part 10–40 s ahead of what is
    // loaded is fetched in 3 pieces at once into the saved video, so the player finds it there (smoother 4K).
    LaunchedEffect(firstFrame, now?.url, usingVlc) {
        val u = now?.url ?: return@LaunchedEffect
        if (!firstFrame || usingVlc || BuildConfig.IS_TV || !StreamCache.parallel || !u.startsWith("http") || isPlaylistLink(itemFor(u), u)) return@LaunchedEffect
        val size = StreamCache.sizes[u] ?: withContext(Dispatchers.IO) { StreamCache.probe(u) }.size.takeIf { it > 0 } ?: return@LaunchedEffect
        if (size < 400L * 1024 * 1024) return@LaunchedEffect
        var boostedTo = 0L
        while (isActive) {
            delay(3_000)
            val d = curDur()
            if (d <= 0 || !isPlayingNow()) continue
            val perMs = size.toDouble() / d
            val from = ((curBuffered() + 10_000) * perMs).toLong().coerceAtLeast(boostedTo)
            if (from >= size) break
            // Only when the player is less than ~40 s from it (no point fetching far ahead).
            if (from - (curBuffered() * perMs).toLong() > (40_000 * perMs).toLong()) continue
            val len = minOf((30_000 * perMs).toLong(), 64L * 1024 * 1024, size - from)
            if (len < 1024 * 1024) continue
            StreamCache.saveParts(ctx, u, from, len, 3, null) { }
            boostedTo = from + len
        }
    }

    // A new link started here (you picked another source / quality): the room follows.
    var sharedUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(firstFrame, now?.url) {
        val u = now?.url ?: return@LaunchedEffect
        if (firstFrame && u != sharedUrl) { val first = sharedUrl == null; sharedUrl = u; if (!first) Together.local(roomState(isPlayingNow())) }
    }

    /** Other links still to try for this title? */
    fun hasMoreLinks(): Boolean = candidates.any { it.stream.url != null && it.stream.url.orEmpty() !in tried && it.stream.url != now?.url && fileId(it.stream.url!!) !in badFiles }

    /** No more ways for this link: the next source; in Quick Start, then every other addon's files; else the error. */
    fun tryNextSource(n: Now?, start: Long, code: String) {
        // This link failed every way: count it for its provider group (twice in a row = under observation).
        n?.let { nn -> val it0 = itemFor(nn.url); scope.launch { Ranker.recordFail(it0, code) } }
        // The best remaining link by the Source Test ranking (dead and keeps-failing groups last resort only).
        // …never the same file again through another link (e.g. a re-signed storage link) after it failed to open.
        fun okNext(it: StreamItem) = it.stream.url != null && it.stream.url.orEmpty() !in tried && fileId(it.stream.url!!) !in badFiles
        val next = vm.rankedCandidates(candidates, quality, quickOnly = false).firstOrNull { okNext(it) }
            ?: candidates.firstOrNull { okNext(it) }
        when {
            next != null -> {
                candidates = candidates.filter { it !== next && it.stream.url.orEmpty() !in tried }
                Snack.show("That source didn't play — trying the next one")
                load(Now(next.stream.url!!, withSubs(next, n?.subs.orEmpty()), next.stream.name ?: next.addonName, next.addonName), start)
            }
            // Quick Start ran out of files: carry on with every other addon's files before giving up.
            p.quick && !extendedAll -> {
                extendedAll = true
                recovering = true
                scope.launch {
                    val more = withTimeoutOrNull(20_000) { runCatching { vm.allStreams(type, p.key) }.getOrNull() }.orEmpty()
                        .filter { it.stream.url != null && it.stream.url.orEmpty() !in tried && it.stream.url != n?.url }
                    recovering = false
                    if (!isActive || now?.url != n?.url) return@launch // left the player, or something else was chosen meanwhile
                    if (more.isNotEmpty()) {
                        known = (known + more).distinctBy { it.stream.url }
                        candidates = (candidates + more).distinctBy { it.stream.url }
                    }
                    tryNextSource(n, start, code)
                }
            }
            else -> { forgetStreams(p.key); switching = false; frozen = null; failure = "This file couldn't be played, even after trying every way ($code). Try another source." }
        }
    }

    /** Same link another way (software decoding, header relay, other engine) — quietly; then the next source. */
    fun tryOtherWays(n: Now?, start: Long, code: String) {
        if (n != null && wayIdx + 1 < ways.size) {
            wayIdx++
            if (wayIdx == 1) Snack.show("Trying another way to play this file…")
            resumeAt = start
            startWay(n, start)
        } else tryNextSource(n, start, code)
    }

    /**
     * The server refused the link (e.g. HTTP 403 — provider links expire): look the title up again, without the cache,
     * and return the same file's fresh link (same addon and name; else same quality and language). Null if there is none.
     */
    suspend fun refreshedLink(n: Now, code: String): Now? {
        if (!code.contains("HTTP")) {
            // VLC doesn't say why it failed: ask the server.
            val probe = PlaybackLog.probe(n.url)
            if (!Regex("HTTP 4\\d\\d").containsMatchIn(probe)) return null
        }
        refreshedOnce = true
        val old = itemFor(n.url)
        val quickScope = p.quick && (old == null || vm.isQuick(old))
        forgetStreams(p.key); vm.forgetLink(p.key); NextUp.clear(p.key)
        val fresh = withTimeoutOrNull(15_000) {
            runCatching { if (quickScope) vm.quickStreams(type, p.key) else vm.allStreams(type, p.key) }.getOrNull()
        }.orEmpty().filter { it.stream.url != null && it.stream.url.orEmpty() !in tried }
        if (fresh.isEmpty()) return null
        fun txt(s: StreamItem) = listOfNotNull(s.stream.name, s.stream.title, s.stream.description, s.stream.behaviorHints?.filename).joinToString("\n")
        val match = old?.let { o -> fresh.firstOrNull { it.addonName == o.addonName && txt(it) == txt(o) } }
            ?: old?.let { o -> fresh.firstOrNull { it.addonName == o.addonName && it.resolution() == o.resolution() && vm.langRank(it) == vm.langRank(o) } }
            ?: old?.let { o -> fresh.firstOrNull { it.resolution() == o.resolution() && vm.langRank(it) == vm.langRank(o) } }
            ?: fresh.firstOrNull { it.addonName == n.addon && it.stream.name == n.source }
            ?: return null
        known = (fresh + known).distinctBy { it.stream.url }
        if (quickScope) quickList = fresh
        val rest = fresh.filter { it !== match }
        candidates = if (quickScope) rest.filter { vm.isQuick(it) }.sortedBy { vm.langRank(it) } else rest
        return Now(match.stream.url!!, withSubs(match, n.subs), match.stream.name ?: match.addonName, match.addonName)
    }

    /** A link failed: reload it after a blip, refresh a refused link once, try it another way, then the next source, else show the error. */
    onEngineError = { code ->
        PlayTiming.mark("error: $code")
        val n = now
        val start = keepPos()
        if (n != null) {
            tried += n.url
            val w = ways.getOrNull(wayIdx)
            val title = p.title
            scope.launch { PlaybackLog.add(ctx, "$title · ${n.addon ?: "?"} · ${w?.label() ?: "?"} · $code · ${PlaybackLog.probe(n.url)}") }
        }
        if (n != null && vm.lastLink(p.key)?.url == n.url) vm.forgetLink(p.key)
        if (n != null && NextUp.url == n.url) NextUp.clear()
        val refused = code.contains("HTTP") || (code == "VLC_ERROR" && wayIdx == 0)
        when {
            // A fresh link is already being looked up.
            recovering -> Unit
            // Broke while playing (network blip): reload the same link at the same second, twice at most.
            firstFrame && n != null && midRetries < 2 -> {
                midRetries++
                resumeAt = start
                startWay(n, start)
            }
            // Refused by the server: get a fresh link for the same file once, and continue from the same second.
            n != null && refused && !refreshedOnce && n.url.startsWith("http") -> {
                recovering = true
                scope.launch {
                    val fresh = refreshedLink(n, code)
                    recovering = false
                    if (!isActive || now?.url != n.url) return@launch
                    if (fresh != null) load(fresh, start) else if (code.contains("HTTP")) tryNextSource(n, start, code) else tryOtherWays(n, start, code)
                }
            }
            // The server refused (403 / 428 / 5xx…) or the link stalls while other links are waiting: VLC can't fix a
            // refused link and a stuck one wastes time — straight to the next source.
            n != null && !firstFrame && (code.contains("HTTP") || (code == "TIMEOUT" && hasMoreLinks())) -> {
                if (code.contains("400") || code.contains("404")) Ranker.markDeadUrl(n.url)
                tryNextSource(n, start, code)
            }
            // "Not a video file": if the server sent a web page, it's a dead link (no VLC either).
            n != null && !firstFrame && code.contains("PARSING_CONTAINER") -> {
                scope.launch {
                    val pr = StreamCache.probe(n.url)
                    if (!isActive || now?.url != n.url) return@launch
                    badFiles += fileId(n.url)
                    if (!pr.ok || pr.type?.startsWith("text/html", true) == true) {
                        if (Ranker.isDeadReason(pr.status, pr.type, n.url) || pr.type?.startsWith("text/html", true) == true) Ranker.markDeadUrl(n.url)
                        tryNextSource(n, start, code)
                    } else tryOtherWays(n, start, code)
                }
            }
            else -> tryOtherWays(n, start, code)
        }
        Unit
    }

    // A link that never starts (no error, no picture) counts as failed. With other links waiting: switched when nothing
    // has arrived after 6 s — but a link that is sending video (file opened / data loaded) gets up to 14 s. VLC: 8 s.
    // Nothing better to try: 30 s.
    LaunchedEffect(loadTick) {
        if (loadTick == 0) return@LaunchedEffect
        if (usingVlc) delay(8_000L)
        else if (hasMoreLinks()) {
            delay(6_000L)
            var waited = 6_000L
            while (waited < 14_000L && !firstFrame && (tracks.groups.isNotEmpty() || player.bufferedPosition > 0)) { delay(500); waited += 500 }
        } else delay(30_000L)
        if (!firstFrame && failure == null && now != null && !recovering) onEngineError("TIMEOUT")
    }
    // The paused player from the title page: if no picture 6 s after Play, it is treated like a stuck link.
    LaunchedEffect(Unit) {
        if (pre == null) return@LaunchedEffect
        delay(6_000L)
        if (!firstFrame && failure == null && !recovering && loadTick == 0) { PlayTiming.mark("paused player gave no picture in 6 s"); onEngineError("TIMEOUT") }
    }
    // Back on screen after a switch: say where we continued, with a quick way to start over.
    // Started: the provider group worked; instant play says how fast; phones save the other qualities' next minute.
    var instantShown by remember { mutableStateOf(false) }
    LaunchedEffect(firstFrame) {
        if (!firstFrame) return@LaunchedEffect
        Ranker.recordOk(now?.url?.let { itemFor(it) })
        PlayTiming.finish("✓ first picture" + (now?.url?.let { u -> itemFor(u)?.let { " · ${Ranker.labelOf(Ranker.groupKey(it))}" } } ?: ""))
        // A video of this title is playing: slow addons (CNCVerse) stop refreshing it.
        SlowAddons.played(p.key)
        if (p.instant && !instantShown) {
            instantShown = true
            val secs = (SystemClock.elapsedRealtime() - openedAt) / 1000f
            Snack.show("⚡ Started in ${"%.1f".format(secs)} s from saved video")
        }
    }
    LaunchedEffect(firstFrame, quickList.size) {
        if (!firstFrame || BuildConfig.IS_TV || quickList.isEmpty()) return@LaunchedEffect
        // Wait until the video playing has ~60 s loaded ahead (so the extra download never makes it stutter).
        var waited = 0
        while (waited < 180) {
            delay(2000); waited += 2
            if (!usingVlc && player.bufferedPosition - player.currentPosition >= 60_000) break
        }
        val u = now?.url ?: return@LaunchedEffect
        val d = curDur()
        if (d > 0) Prepare.saveOthers(vm, ctx, quickList, u, curPos(), d)
    }
    DisposableEffect(Unit) { onDispose { Prepare.stopOthers() } }
    LaunchedEffect(firstFrame) {
        if (firstFrame && switching) {
            switching = false
            frozen = null
            val at = resumeAt
            if (at > 5_000) Snack.show("${switchLabel?.let { "$it · " } ?: ""}Resumed at ${fmtTime(at)}", "Start from beginning", 5000) { seekAbs(0L) }
            else switchLabel?.let { Snack.show(it) }
            switchLabel = null
        }
    }

    // Start straight away when a link was given; the player is preparing while the loading screen shows.
    LaunchedEffect(player) {
        if (pre != null) {
            ways = waysFor(pre.url, "exo"); wayIdx = 0
            buffering = player.playbackState == Player.STATE_BUFFERING
            player.playWhenReady = true
        } else now?.let { load(it, p.resumeMs) }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (usingVlc) return
                onEngineError(error.errorCodeName.removePrefix("ERROR_CODE_"))
            }
            override fun onTracksChanged(t: Tracks) {
                tracks = t
                if (!t.isEmpty) PlayTiming.mark("tracks found: " + t.groups.filter { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO }
                    .joinToString(", ") { g -> g.getTrackFormat(0).let { f -> (f.sampleMimeType ?: "?").substringAfter('/') + if (g.isTrackSupported(0)) "" else " (not supported)" } }, once = "tracks")
            }
            override fun onCues(cueGroup: CueGroup) { cues = cueGroup.cues }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!usingVlc) playing = isPlaying }
            override fun onRenderedFirstFrame() { if (!usingVlc) firstFrame = true }
            override fun onPlaybackStateChanged(state: Int) {
                if (usingVlc) return
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) PlayTiming.mark("ready (enough loaded to start)", once = "ready")
                if (state == Player.STATE_READY && !linkSaved) {
                    linkSaved = true
                    now?.let { n -> if (n.url.startsWith("http")) vm.rememberLink(p.key, n.url, n.source, n.addon) }
                    // Audio-only or no video track: there is no "first frame", so don't wait for it.
                    if (player.videoSize.width == 0) scope.launch { delay(800); firstFrame = true }
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // Media session: earbuds / headset / Bluetooth remote play-pause buttons control the video (also with VLC playing).
    DisposableEffect(player) {
        val fp = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun play() { if (usingVlc) vlc?.play() else super.play() }
            override fun pause() { if (usingVlc) vlc?.pause() else super.pause() }
            override fun getPlayWhenReady(): Boolean = if (usingVlc) vlc?.isPlaying == true else super.getPlayWhenReady()
            override fun getPlaybackState(): Int = if (usingVlc) Player.STATE_READY else super.getPlaybackState()
        }
        val session = runCatching { androidx.media3.session.MediaSession.Builder(ctx, fp).setId("streamx-${System.nanoTime()}").build() }.getOrNull()
        onDispose { runCatching { session?.release() } }
    }

    // "Finding stream source": Auto Stream Selection inside the player (and for the next episode).
    /**
     * Fast start (like Nuvio, but racing): the best three links of [list] (your quality, language, Source Test ranking)
     * are asked at once and the first server that answers with a video plays — no waiting for slow addons or slow
     * servers. False when none answered within 5 s.
     */
    suspend fun raceStart(list: List<StreamItem>, subsJob: kotlinx.coroutines.Deferred<List<Subtitle>>): Boolean {
        val ranked = vm.rankedCandidates(list, quality, quickOnly = false)
            .filter { it.stream.url != null && it.stream.url.orEmpty() !in tried && fileId(it.stream.url!!) !in badFiles }
        if (ranked.isEmpty()) return false
        val top = ranked.take(3)
        PlayTiming.mark("racing ${top.size} link(s): " + top.joinToString(" / ") { Ranker.labelOf(Ranker.groupKey(it)).substringAfter(" · ") }, once = "race")
        val url = if (top.size == 1) top[0].stream.url!! else StreamCache.race(top.mapNotNull { it.stream.url }, 5000)?.first ?: return false
        val q = ranked.first { it.stream.url == url }
        known = (known + list).distinctBy { it.stream.url }
        candidates = ranked.filter { it !== q }
        StreamCache.register(q)
        StreamHeaders.use(url)
        val extra = withTimeoutOrNull(600) { subsJob.await() }.orEmpty()
        load(Now(url, q.stream.subtitles.orEmpty() + extra, q.stream.name ?: q.addonName, q.addonName), resumeAt)
        return true
    }

    LaunchedEffect(findRequest) {
        if (findRequest == 0) return@LaunchedEffect
        val findT0 = SystemClock.elapsedRealtime()
        failure = null; firstFrame = false; status = "Finding stream source"
        PlayTiming.mark("finding stream source (no prepared link)", once = "find")
        answered = 0; bestRes = 0; bufProgress = 0f; sourceTotal = streamAddonCount()
        val subsJob = async { runCatching { vm.subtitles(type, p.key) }.getOrDefault(emptyList()) }
        val forced = findRequest > 1 // asked again after a failure: never reuse the old link
        // Next episode handoff: its link was found while the last episode was still playing (also in Quick Start).
        if (!forced && NextUp.fresh(p.key)) {
            val hUrl = NextUp.url!!
            val hSubs = NextUp.subs
            val hSource = NextUp.source
            val hAddon = NextUp.addon
            NextUp.clear(p.key)
            val extra = withTimeoutOrNull(1200) { subsJob.await() }.orEmpty().filter { s -> hSubs.none { it.url == s.url } }
            load(Now(hUrl, hSubs + extra, hSource, hAddon), resumeAt)
            // The other files (quality menu, Audio panel, fallbacks) — already cached by the look-up ahead of time.
            scope.launch {
                if (p.quick) {
                    val l = runCatching { vm.quickStreams(type, p.key) }.getOrDefault(emptyList())
                    if (l.isEmpty()) return@launch
                    quickList = l
                    known = (known + l).distinctBy { it.stream.url }
                    candidates = l.filter { it.stream.url != hUrl && vm.isQuick(it) }.sortedBy { vm.langRank(it) }
                    vm.qualityMap(l).firstOrNull { it.first.id != "auto" && it.second?.stream?.url == hUrl }?.let { quality = it.first.id }
                } else {
                    val l = runCatching { vm.allStreams(type, p.key) }.getOrDefault(emptyList())
                    if (l.isEmpty()) return@launch
                    known = l
                    candidates = l.filter { it.stream.url != hUrl }
                }
            }
            return@LaunchedEffect
        }
        // Links the title page already found: race them straight away (no second search).
        if (!forced && p.candidates.isNotEmpty()) {
            if (p.quick) quickList = p.candidates
            if (raceStart(p.candidates, subsJob)) return@LaunchedEffect
        }
        val last = if (!forced && !p.quick && isOn(opt("reuseLastLink"))) vm.lastLink(p.key) else null
        if (last != null) {
            load(Now(last.url, withTimeoutOrNull(1200) { subsJob.await() }.orEmpty(), last.source, last.addon), resumeAt)
            // Also look up other sources in the background, in case the saved link has expired.
            scope.launch { vm.streamsFlow(type, p.key).first { it.second }.let { (l, _) -> val fl = vm.filtered(l).list; known = fl; candidates = fl.filter { it.stream.url != last.url } } }
            return@LaunchedEffect
        }
        // Quick Start: fast addons, chosen quality and your language order.
        // The first file in your top language (e.g. Hindi) starts at once; the other addons keep loading
        // in the background and fill the Audio panel / quality menu as they arrive.
        if (p.quick) {
            var started = false
            suspend fun startWith(list: List<StreamItem>, q: StreamItem) {
                started = true
                candidates = list.filter { it !== q && vm.isQuick(it) }.sortedBy { vm.langRank(it) }
                val extra = withTimeoutOrNull(800) { subsJob.await() }.orEmpty()
                load(Now(q.stream.url!!, q.stream.subtitles.orEmpty() + extra, q.stream.name ?: q.addonName, q.addonName), resumeAt)
            }
            val cached = if (forced) null else cachedQuick(p.key)
            if (cached != null) {
                sourceTotal = streamAddonCount(vm.qsAddons()); noteAnswer(cached, true)
                quickList = cached; known = (known + cached).distinctBy { it.stream.url }
                val okCached = cached.filter { it.stream.url !in tried }
                val q = vm.pickQuick(okCached, quality) ?: vm.pickQuick(okCached, "auto")
                if (q != null) { startWith(cached, q); return@LaunchedEffect }
            } else {
                val topLang = vm.qsLangs().firstOrNull()
                var last: List<StreamItem> = emptyList()
                sourceTotal = streamAddonCount(vm.qsAddons())
                vm.streamsFlow(type, p.key, only = vm.qsAddons()).collect { (l, done) ->
                    val list = vm.filtered(l).list
                    if (!started) noteAnswer(list, done)
                    last = list
                    quickList = list
                    known = (known + list).distinctBy { it.stream.url }
                    if (started) {
                        candidates = (candidates + list.filter { it.stream.url != now?.url && vm.isQuick(it) }).distinctBy { it.stream.url }.sortedBy { vm.langRank(it) }
                    } else {
                        val okList = list.filter { it.stream.url !in tried }
                        val q = vm.pickQuick(okList, quality) ?: vm.pickQuick(okList, "auto")
                        val inTopLang = q != null && topLang != null && topLang in q.languages()
                        // Don't wait for every addon: 1.5 s after the first links (or a link in your language), race them.
                        if (q != null && (inTopLang || done || SystemClock.elapsedRealtime() - findT0 > 1500)) {
                            if (raceStart(list, subsJob)) started = true
                            else if (done) startWith(list, q)
                        }
                    }
                }
                putQuick(p.key, last)
                if (started) return@LaunchedEffect
            }
        }
        val mode = opt("autoPlayMode").let { if (it == "manual") "first" else it }
        val patterns = regexList(opt("autoRegex"))
        val prefer = p.source
        var picked: StreamItem? = null
        var pool: List<StreamItem> = emptyList()
        var hiddenAll = false
        val cachedList = if (forced) null else cachedAll(p.key)
        val flow = if (cachedList != null) kotlinx.coroutines.flow.flowOf(cachedList to true) else vm.streamsFlow(type, p.key)
        answered = 0; sourceTotal = streamAddonCount()
        flow.first { (l, done) ->
            val f = vm.filtered(l)
            noteAnswer(f.list, done)
            // Never links the title page already found broken; the most reliable providers (Source Test) first.
            pool = f.list.filter { it.stream.url !in tried }.sortedByDescending { Ranker.score(it) }
            hiddenAll = f.forcedEmpty
            picked = when {
                pool.isEmpty() -> null
                prefer != null && !done -> pool.firstOrNull { it.stream.name == prefer } // same source as the last episode
                mode == "regex" && patterns.isNotEmpty() -> matchChain(pool, patterns.take(1))?.first ?: if (done) vm.autoPick(pool, "regex", opt("autoRegex")) else null
                else -> if (done || prefer == null) vm.autoPick(pool, "first", "", prefer) else null
            }
            picked != null || done
        }
        val s0 = picked ?: (if (pool.isNotEmpty()) vm.autoPick(pool, mode, opt("autoRegex"), prefer) else null)
        // Prefer your audio language (Hindi → Original…) among versions of the same file from the same addon.
        val s1 = s0?.let { a -> pool.filter { it.addonName == a.addonName && it.resolution() == a.resolution() }.minByOrNull { vm.langRank(it) }
            ?.takeIf { vm.langRank(it) < vm.langRank(a) } ?: a }
        // TV: above 1080p only when the same addon has no 1080p-or-lower copy in the same language.
        val s = if (BuildConfig.IS_TV && s1 != null && s1.resolution() > 1080)
            pool.filter { it.addonName == s1.addonName && it.resolution() in 1..1080 && vm.langRank(it) <= vm.langRank(s1) }.maxByOrNull { it.resolution() } ?: s1
        else s1
        if (s == null) {
            failure = if (hiddenAll) "No streams match your forced stream filters." else "No stream found for this title."
            return@LaunchedEffect
        }
        known = pool
        candidates = pool.filter { it !== s }
        val extra = withTimeoutOrNull(1500) { subsJob.await() }.orEmpty()
        load(Now(s.stream.url!!, s.stream.subtitles.orEmpty() + extra, s.stream.name ?: s.addonName, s.addonName), resumeAt)
    }
    LaunchedEffect(firstFrame, buffering) { if (!firstFrame && now != null && failure == null) status = "Buffering…" }
    // "Serving" progress on the loading screen: eases to 90% over ~4 s (faster when ExoPlayer reports a real buffer),
    // creeps on slowly after that and jumps to 100% with the first frame. It never goes backwards.
    LaunchedEffect(status, loadTick, firstFrame) {
        if (firstFrame) { bufProgress = 1f; return@LaunchedEffect }
        if (!status.startsWith("Buffering")) return@LaunchedEffect
        val t0 = SystemClock.uptimeMillis()
        while (true) {
            val t = (SystemClock.uptimeMillis() - t0) / 4000f
            val eased = if (t < 1f) { val u = 1f - t; 0.9f * (1f - u * u * u) } else 0.9f + 0.08f * (1f - kotlin.math.exp(-(t - 1f)))
            val real = if (!usingVlc) ((player.bufferedPosition - resumeAt).toFloat() / 2500f).coerceIn(0f, 0.95f) else 0f
            bufProgress = maxOf(bufProgress, eased, real)
            delay(50)
        }
    }
    // Starting brightness (Playback > Brightness): applied once, when the video is first on screen.
    LaunchedEffect(firstFrame) {
        if (!firstFrame || brightApplied) return@LaunchedEffect
        brightApplied = true
        // TVs have their own picture settings: never touch the brightness there.
        if (BuildConfig.IS_TV || !isOn(opt("startBright"))) return@LaunchedEffect
        val act = ctx as? Activity ?: return@LaunchedEffect
        val saved = if (opt("startBrightMode") == "last") vm.playerPref("bright").toFloatOrNull() else null
        val level = (saved ?: ((opt("startBrightLevel").toFloatOrNull() ?: 50f) / 100f)).coerceIn(0.1f, 1f)
        act.window.attributes = act.window.attributes.apply { screenBrightness = level }
    }
    // Starting volume (Playback > Volume): applied once and quietly (no system slider); the phone's volume comes back on leaving.
    LaunchedEffect(firstFrame) {
        if (!firstFrame || volApplied) return@LaunchedEffect
        volApplied = true
        // TVs (and their sound bars) keep their own volume.
        if (BuildConfig.IS_TV || !isOn(opt("startVol"))) return@LaunchedEffect
        val saved = if (opt("startVolMode") == "last") vm.playerPref("vol").toFloatOrNull() else null
        val level = (saved ?: ((opt("startVolLevel").toFloatOrNull() ?: 50f) / 100f)).coerceIn(0.1f, 1f)
        runCatching {
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, kotlin.math.round(level * maxVol).toInt().coerceIn(0, maxVol), 0)
            volBefore = before
        }
    }
    // The Full HD / Mid HD / Low menu is always there when Quick Start is on — also when playing from
    // Continue Watching, the sources list or a saved link: its streams are looked up in the background.
    LaunchedEffect(p.key) {
        if (!vm.qsActive()) return@LaunchedEffect
        delay(1500)
        if (quickList.isNotEmpty()) return@LaunchedEffect
        val l = cachedQuick(p.key) ?: runCatching { vm.quickStreams(type, p.key) }.getOrDefault(emptyList())
        if (quickList.isEmpty() && l.isNotEmpty()) {
            quickList = l
            known = (known + l).distinctBy { it.stream.url }
            // Show the level that matches what's playing.
            now?.url?.let { u -> vm.qualityMap(l).firstOrNull { it.first.id != "auto" && it.second?.stream?.url == u }?.let { quality = it.first.id } }
        }
    }

    // Episodes in order, for "Next episode".
    val episodes = remember(meta) {
        meta?.videos.orEmpty().filter { (it.season ?: 0) > 0 && it.episode != null }.sortedWith(compareBy({ it.season }, { it.episode }))
    }
    val nextEp = remember(episodes, p.key) { episodes.indexOfFirst { it.id == p.key }.let { i -> if (i >= 0) episodes.getOrNull(i + 1) else null } }
    val nextEpState = rememberUpdatedState(nextEp)

    LaunchedEffect(p.key) {
        if (meta == null || meta?.videos == null && type == "series") {
            vm.fullMeta(Meta(id = p.key.substringBefore(":"), type = type))?.let { meta = it }
        }
    }
    // Content advisory once the video is on screen.
    LaunchedEffect(p.key, firstFrame) {
        if (firstFrame && advisory.isEmpty() && isOn(opt("showAdvisory")) && p.key.startsWith("tt")) {
            val g = vm.parentalGuide(p.key.substringBefore(":"))
            if (g.isNotEmpty()) { advisory = g; showAdvisory = true; delay(9_000); showAdvisory = false }
        }
    }
    LaunchedEffect(p.key) { if (isOn(opt("skipIntro"))) segs = vm.segments(p.key) }

    LaunchedEffect(player) {
        while (true) {
            pos = if (firstFrame) curPos() else resumeAt
            dur = curDur()
            buffered = curBuffered()
            delay(500)
        }
    }
    // Phones: keep a small copy of the picture every 10 s of playback, so seeking back always shows real frames.
    LaunchedEffect(thumbs, firstFrame) {
        val t = thumbs ?: return@LaunchedEffect
        if (BuildConfig.IS_TV || !firstFrame) return@LaunchedEffect
        while (true) {
            delay(2500)
            val at = curPos()
            val vv = videoView
            if (playing && at > 0 && vv != null && !t.has(at)) captureFrame(vv) { bmp -> if (bmp != null) t.put(at, bmp) }
        }
    }
    // Controls hide after 4 s — never while a finger is on the seek bar (scrubbing) or a remote seek is pending.
    // …and never while a finger is anywhere on the controls (choosing a button, sliding the button row): the 4 s start
    // again only after the finger lifts.
    LaunchedEffect(controls, poke, playing, panel, qualityMenu, pendingSeek, scrubbing, touchingControls) {
        if (controls && playing && panel == null && !qualityMenu && pendingSeek == null && !scrubbing && !touchingControls) { delay(4000); controls = false; more = false }
    }
    LaunchedEffect(playing) { PipState.playing = playing }
    LaunchedEffect(controls) { if (!controls) touchingControls = false }
    LaunchedEffect(inPip) { if (inPip) { controls = false; panel = null; showInfo = false } }
    // Sleep timer: the sound fades out over the last 30 s, then the video pauses.
    LaunchedEffect(sleepAt, sleepEnd) {
        val at = sleepAt
        if (at == null && !sleepEnd) return@LaunchedEffect
        while (true) {
            val left = if (sleepEnd) (curDur() - curPos()).coerceAtLeast(0L) else at!! - SystemClock.elapsedRealtime()
            if (left <= 30_000) {
                if (!usingVlc) player.volume = (left / 30_000f).coerceIn(0f, 1f)
                if (left <= 300) break
            }
            delay(if (left > 40_000) 5_000 else 250)
        }
        pauseNow()
        if (!usingVlc) player.volume = 1f
        sleepAt = null; sleepEnd = false
        Snack.show("Sleep timer: paused. Sleep well!")
    }
    LaunchedEffect(playing, firstFrame) {
        if (firstFrame && !playing && !buffering && isOn(opt("pauseInfo"))) { delay(5000); if (!isPlayingNow()) { showInfo = true; controls = false } }
        else showInfo = false
    }

    fun seekBy(ms: Long) { seekAbs((curPos() + ms).coerceIn(0L, maxOf(0L, curDur()))); poke++ }

    fun playEpisode(ep: Video) {
        val m = meta ?: return
        onReplace(
            Screen.Player(
                "${m.name}  S${ep.season}E${ep.episode}", "", emptyList(), key = ep.id,
                image = ep.thumbnail ?: m.background ?: m.poster, type = type, meta = m,
                source = if (p.quick) null else now?.source, addon = now?.addon, find = true, quick = p.quick,
            )
        )
    }
    fun playNext() { nextEp?.let { playEpisode(it) } }
    /** Play another Quick Start file (quality or language change) from the same second. */
    fun switchTo(st: StreamItem, label: String? = null) {
        val keep = keepPos()
        val addonSubs = now?.subs.orEmpty().filter { sub -> st.stream.subtitles.orEmpty().none { it.url == sub.url } }
        load(Now(st.stream.url!!, st.stream.subtitles.orEmpty() + addonSubs, st.stream.name ?: st.addonName, st.addonName), keep, label)
    }
    /** An online subtitle: reload this link from the same second with it added first, and pick it. */
    fun addOnlineSub(sub: Subtitle) {
        val cur = now ?: return
        panel = null
        vm.setPlayerPref("subs", sub.lang)
        if (!usingVlc) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(sub.lang).build()
        load(Now(cur.url, listOf(sub) + cur.subs.filter { it.url != sub.url }, cur.source, cur.addon), keepPos(), "Adding subtitles")
    }
    fun setQuality(id: String) {
        quality = id; qualityMenu = false; poke++
        vm.setPlayerPref("quality", id)
        val st = vm.pickQuick(quickList, id) ?: return
        if (st.stream.url != now?.url) switchTo(st, AllQualityLevels.firstOrNull { it.id == id }?.label)
    }
    fun setAspect(i: Int, label: String = AspectModes[i].first) {
        aspectIdx = i; resize = AspectModes[i].second; aspectLabel = label
        vlc?.setAspect(i)
        vm.setPlayerPref("aspect", i.toString())
    }

    // ───── Elite auto quality ─────
    // With "Auto" chosen and Elite on, the player steps between 4K / 1080p / 720p (TV: 1080p / 720p) at the same second:
    // down after two stalls (or one long one), up again after 90 s of smooth playback with a full buffer.
    var stalls by remember { mutableIntStateOf(0) }
    var stallAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(buffering, firstFrame) {
        if (firstFrame && buffering && !switching) {
            val t = SystemClock.uptimeMillis()
            stalls = if (t - stallAt < 90_000) stalls + 1 else 1
            stallAt = t
        }
    }
    LaunchedEffect(firstFrame, quality, quickList.size) {
        if (!firstFrame || quality != "auto" || quickList.isEmpty() || !isOn(opt("autoQuality")) || !vm.eliteOn()) return@LaunchedEffect
        stalls = 0
        var smoothSince = SystemClock.uptimeMillis()
        val ladder = if (BuildConfig.IS_TV) listOf(720, 1080) else listOf(720, 1080, 2160)
        fun streamAt(r: Int, cur: StreamItem): StreamItem? = quickList
            .filter { vm.isQuick(it) && it.resolution() == r && it.stream.url.let { u -> u != null && u !in tried } }
            .sortedWith(compareBy({ if (it.addonName == cur.addonName) 0 else 1 }, { vm.langRank(it) }))
            .firstOrNull()?.takeIf { vm.langRank(it) <= vm.langRank(cur) }
        while (isActive) {
            delay(4000)
            val url = now?.url ?: continue
            val cur = itemFor(url) ?: continue
            val res = cur.resolution()
            val t = SystemClock.uptimeMillis()
            if (buffering || stalls > 0) smoothSince = t
            val longStall = buffering && t - stallAt > 5000
            if (stalls >= 2 || longStall) {
                val lower = ladder.filter { it < res }.sortedDescending().firstNotNullOfOrNull { r -> streamAt(r, cur) }
                if (lower != null) { stalls = 0; switchTo(lower, "Auto · ${lower.resolution()}p (slow connection)"); return@LaunchedEffect }
            } else if (!usingVlc && t - smoothSince > 90_000 && player.bufferedPosition - player.currentPosition > 45_000) {
                val higher = ladder.filter { it > res }.sorted().firstNotNullOfOrNull { r -> streamAt(r, cur) }
                if (higher != null) { switchTo(higher, "Auto · ${higher.resolution()}p"); return@LaunchedEffect }
                smoothSince = t // nothing better: check again later
            }
        }
    }

    // ───── Remote seeking: ◀ ▶ move a target (with a preview frame); OK / Play jumps there, or it jumps by itself 1.6 s later ─────
    fun commitSeek() {
        val t = pendingSeek ?: return
        pendingSeek = null
        seekAbs(t); pos = t; poke++
    }
    fun nudgeSeek(forward: Boolean, repeat: Int) {
        val d = curDur()
        if (d <= 0L) return
        // On the seek bar: 10 s a press (30 s while held down).
        val step = if (repeat >= 6) 30_000L else 10_000L
        val base = pendingSeek ?: curPos()
        pendingSeek = (base + if (forward) step else -step).coerceIn(0L, d)
        controls = true; poke++; seekKeyTick++
        thumbs?.warmUp()
    }
    /** Controls hidden: ◀ ▶ jump 30 s straight away, shown as a bubble that adds up while you keep pressing. */
    fun bubbleSeek(forward: Boolean) {
        val d = curDur()
        if (d <= 0L) return
        seekAbs((curPos() + if (forward) 30_000L else -30_000L).coerceIn(0L, d))
        val b = bubble
        bubble = if (b != null && b.first == forward) forward to (b.second + 30) else forward to 30
        bubbleTick++
    }
    /** Hide the controls; play on if ▲ had paused the video. */
    fun hideControls() {
        controls = false; more = false
        if (pausedByUp) { pausedByUp = false; if (!isPlayingNow()) togglePlay() }
    }
    fun handleBack() {
        when {
            watchNext -> watchNext = false
            panel != null -> panel = null
            qualityMenu -> qualityMenu = false
            showEpisodes -> showEpisodes = false
            showInfo -> showInfo = false
            pendingSeek != null -> pendingSeek = null
            locked && firstFrame -> { controls = true; poke++ }
            // TV: the first Back hides the controls, the next one leaves.
            BuildConfig.IS_TV && controls && firstFrame -> { controls = false; more = false }
            else -> onBack()
        }
    }
    /** Remote / keyboard keys. Arrows and OK act on the video only while nothing else (a button, a panel) has focus. */
    fun onKey(e: KeyEvent): Boolean {
        val k = e.key
        if (k == Key.Back) {
            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) handleBack()
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        val repeat = e.nativeKeyEvent.repeatCount
        val free = rootFocused && panel == null && !showEpisodes && !qualityMenu && !watchNext
        if (free && showInfo) showInfo = false
        if (firstFrame) poke++
        return when (k) {
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                if (!firstFrame) return false
                if (pendingSeek != null) commitSeek()
                else {
                    val playingNow = isPlayingNow()
                    if (k == Key.MediaPlayPause || (k == Key.MediaPlay && !playingNow) || (k == Key.MediaPause && playingNow)) togglePlay()
                }
                if (!locked && panel == null && !showEpisodes) controls = true
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                // On the seek bar: OK jumps to the chosen point (or plays / pauses).
                if (seekFocused && controls && firstFrame) {
                    if (pendingSeek != null) commitSeek() else { togglePlay(); pausedByUp = false }
                    return true
                }
                if (!free || !firstFrame) return false
                if (locked) controls = true
                else if (pendingSeek != null) commitSeek()
                else { togglePlay(); controls = true }
                true
            }
            Key.DirectionLeft, Key.DirectionRight, Key.MediaRewind, Key.MediaFastForward -> {
                val media = k == Key.MediaRewind || k == Key.MediaFastForward
                val fwd = k == Key.DirectionRight || k == Key.MediaFastForward
                when {
                    // Panels (Audio & Subtitles columns, Sources tabs): the remote moves between them.
                    panel != null || watchNext -> false
                    !firstFrame -> false
                    media && (showEpisodes || qualityMenu) -> false
                    locked -> { controls = true; true }
                    // On the seek bar: move a target 10 s at a time, with a preview frame.
                    seekFocused && controls -> { nudgeSeek(fwd, repeat); true }
                    // Controls hidden: 30 s jumps with a bubble.
                    free && !controls -> { bubbleSeek(fwd); true }
                    media -> { if (controls) nudgeSeek(fwd, repeat) else bubbleSeek(fwd); true }
                    // Controls shown, focus on a button: move between buttons.
                    !free -> false
                    else -> { nudgeSeek(fwd, repeat); true }
                }
            }
            Key.DirectionUp -> {
                if (!firstFrame) return false
                when {
                    free -> {
                        // ▲ : controls on, video paused, focus on the buttons.
                        controls = true
                        if (!locked) {
                            if (isPlayingNow()) { pauseNow(); pausedByUp = true }
                            focusRowTick++
                        }
                        true
                    }
                    // ▲ from the top bar: hide everything.
                    topHasFocus && controls && panel == null && !qualityMenu -> { hideControls(); true }
                    else -> false // buttons → seek bar → top bar
                }
            }
            Key.DirectionDown -> {
                if (!firstFrame) return false
                when {
                    free -> { controls = true; if (!locked) focusRowTick++; true }
                    // ▼ from the buttons: hide and play on.
                    rowHasFocus && controls && panel == null -> { hideControls(); true }
                    else -> false
                }
            }
            Key.Menu -> {
                if (!firstFrame || locked) return false
                panel = "avsubs"
                true
            }
            else -> false
        }
    }
    LaunchedEffect(playing) { if (playing) pausedByUp = false }
    LaunchedEffect(bubbleTick) { if (bubbleTick > 0) { delay(1000); bubble = null } }
    LaunchedEffect(seekKeyTick) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(1600)
        commitSeek()
    }
    LaunchedEffect(Unit) { delay(80); runCatching { rootFocus.requestFocus() } }
    // ▲ : into the control row (once it is on screen).
    LaunchedEffect(focusRowTick) {
        if (focusRowTick == 0) return@LaunchedEffect
        for (i in 0 until 12) { delay(50); runCatching { rowFocus.requestFocus() }; if (!rootFocused) break }
    }
    // Controls gone (or a pop-up closed): the keys go back to the video.
    LaunchedEffect(controls, panel, showEpisodes, qualityMenu, watchNext) {
        if (!controls && panel == null && !showEpisodes && !qualityMenu && !watchNext && !rootFocused) { delay(30); runCatching { rootFocus.requestFocus() } }
    }
    // A side panel opened: focus its first item (TV: the controls step aside).
    LaunchedEffect(panel) {
        if (panel == null) return@LaunchedEffect
        if (BuildConfig.IS_TV) { controls = false; more = false }
        panelHasFocus = false
        for (i in 0 until 40) { delay(100); if (panelHasFocus) break; runCatching { panelFocus.requestFocus() } }
    }
    LaunchedEffect(qualityMenu) {
        if (!qualityMenu) return@LaunchedEffect
        menuHasFocus = false
        for (i in 0 until 10) { delay(80); if (menuHasFocus) break; runCatching { qualityFocus.requestFocus() } }
    }
    LaunchedEffect(failure) {
        if (failure == null) return@LaunchedEffect
        for (i in 0 until 10) { delay(120); runCatching { failFocus.requestFocus() }; if (!rootFocused && rootHasFocus) break }
    }

    // 60% into an episode: find the next one's link now (picked exactly like its own search would),
    // hand it over to the next episode and warm up its server, so it starts at once.
    var nextPrefetched by remember { mutableStateOf(false) }
    LaunchedEffect(dur > 60_000 && pos >= dur * 0.6) {
        val ep = nextEp
        if (nextPrefetched || ep == null || dur <= 60_000 || pos < dur * 0.6) return@LaunchedEffect
        nextPrefetched = true
        val cur = now
        scope.launch {
            val picked: StreamItem? = if (p.quick) {
                val list = runCatching { vm.quickStreams(type, ep.id) }.getOrDefault(emptyList())
                vm.pickQuick(list, quality) ?: vm.pickQuick(list, "auto")
            } else {
                val pool = runCatching { vm.allStreams(type, ep.id) }.getOrDefault(emptyList())
                val mode = opt("autoPlayMode").let { if (it == "manual") "first" else it }
                val patterns = regexList(opt("autoRegex"))
                val prefer = cur?.source
                val s0 = when {
                    pool.isEmpty() -> null
                    else -> prefer?.let { pr -> pool.firstOrNull { it.stream.name == pr && it.addonName == (cur?.addon ?: it.addonName) } ?: pool.firstOrNull { it.stream.name == pr } }
                        ?: (if (mode == "regex" && patterns.isNotEmpty()) matchChain(pool, patterns.take(1))?.first ?: vm.autoPick(pool, "regex", opt("autoRegex")) else null)
                        ?: cur?.addon?.let { a -> pool.firstOrNull { it.addonName == a } }
                        ?: vm.autoPick(pool, "first", "", prefer)
                }
                // Prefer your audio language among versions of the same file from the same addon (as the search does).
                s0?.let { a -> pool.filter { it.addonName == a.addonName && it.resolution() == a.resolution() }.minByOrNull { vm.langRank(it) }
                    ?.takeIf { vm.langRank(it) < vm.langRank(a) } ?: a }
            }
            val pk = picked ?: return@launch
            val url = pk.stream.url ?: return@launch
            if (!isActive) return@launch
            val source = pk.stream.name ?: pk.addonName
            vm.rememberLink(ep.id, url, source, pk.addonName)
            NextUp.set(ep.id, url, source, pk.addonName, pk.stream.subtitles.orEmpty())
            launch(kotlinx.coroutines.Dispatchers.IO) { warmUpLink(url) }
        }
    }
    // Elite, 75% in: the next episode's first minute is loaded in the background, so it starts instantly.
    var nextPreloaded by remember { mutableStateOf(false) }
    LaunchedEffect(dur > 60_000 && pos >= dur * 0.75) {
        val ep = nextEp ?: return@LaunchedEffect
        if (BuildConfig.IS_TV) return@LaunchedEffect // too heavy for 1 GB TV sticks
        if (nextPreloaded || dur <= 60_000 || pos < dur * 0.75 || usingVlc || engine != "exo" || !vm.eliteOn()) return@LaunchedEffect
        // The link from the 60% look-up (wait for it a little if it's still coming).
        var tries = 0
        while (!NextUp.fresh(ep.id) && tries < 20) { delay(1500); tries++ }
        if (!NextUp.fresh(ep.id) || Preloader.has(ep.id)) return@LaunchedEffect
        val url = NextUp.url ?: return@LaunchedEffect
        nextPreloaded = true
        val subs = NextUp.subs
        val fname = itemFor(url)?.stream?.behaviorHints?.filename
        val mime = if (StreamType.needsProbe(url, fname)) withContext(Dispatchers.IO) { StreamType.probe(url) }.takeIf { it.isNotEmpty() } else StreamType.mimeFor(url, fname)
        val next = buildPlayer(ctx, vm, isOn(opt("fastStart")))
        next.setMediaItem(MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.setSubtitleConfigurations(subtitleConfigs(subs)).build(), 0L)
        next.playWhenReady = false
        next.prepare()
        Preloader.set(Preloaded(ep.id, url, subs, NextUp.source, NextUp.addon, emptyList(), next))
    }
    val threshold = (opt("nextThreshold").toIntOrNull() ?: 95).coerceIn(50, 100) / 100f
    LaunchedEffect(pos, dur) {
        if (!autoNextDone && isOn(opt("autoNext")) && nextEp != null && dur > 60_000 && pos >= dur * threshold) {
            autoNextDone = true
            playNext()
        }
    }

    DisposableEffect(Unit) {
        val activity = ctx as Activity
        val ctrl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            val d = curDur()
            now?.let { n -> if (firstFrame) onProgress(curPos(), if (d > 0) d else 0L, n.url, n.subs) }
            if (PlayTiming.active()) PlayTiming.finish("left the player before the first picture")
            player.release()
            vlc?.release()
            // Next episode prepared but not played (left the player): free it. (Taken already when it plays next.)
            nextEpState.value?.let { Preloader.release(it.id) }
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = -1f }
            // Give the phone its own volume back (only when we set a starting volume).
            if (volBefore >= 0) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volBefore, 0) }
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler { handleBack() }

    val subSize = opt("subSize").toFloatOrNull() ?: 20f
    val subStyle = CaptionStyleCompat(
        (parseColor(opt("subColor")) ?: Color.White).toArgb(),
        (parseColor(opt("subBg")) ?: Color.Transparent).toArgb(),
        Color.Transparent.toArgb(),
        if (isOn(opt("subOutline"))) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        (parseColor(opt("subOutlineColor")) ?: Color.Black).toArgb(),
        if (isOn(opt("subBold"))) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
    )

    // Loading screen fades out, then the video fades in from black.
    val loadingAlpha by animateFloatAsState(if ((firstFrame || switching) && failure == null) 0f else 1f, tween(420), label = "loading")
    val videoAlpha by animateFloatAsState(if (firstFrame) 1f else 0f, tween(650, delayMillis = 180), label = "video")

    ProvideTextStyle(TextStyle(fontFamily = InterFont, color = Color.White)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black)
                .onPreviewKeyEvent { onKey(it) }
                .focusRequester(rootFocus)
                .onFocusChanged {
                    rootFocused = it.isFocused
                    val lost = rootHasFocus && !it.hasFocus
                    rootHasFocus = it.hasFocus
                    // The focused button went away (controls hid, a list changed): take the keys back.
                    if (lost) scope.launch { delay(60); if (!rootHasFocus) runCatching { rootFocus.requestFocus() } }
                }
                .focusable(),
        ) {
            // Measured from the reference player: wide side margins, scaled to this screen.
            val side = (maxWidth * 0.11f).coerceIn(28.dp, 110.dp)
            val topEnd = (maxWidth * 0.12f).coerceIn(28.dp, 120.dp)
            val topY = (maxHeight * 0.12f).coerceIn(16.dp, 64.dp)
            val bottomY = (maxHeight * 0.06f).coerceIn(12.dp, 36.dp)

            Box(Modifier.fillMaxSize()) {
                if (usingVlc) {
                    val v = vlc
                    if (v != null) AndroidView(
                        factory = { c -> org.videolan.libvlc.util.VLCVideoLayout(c).apply { keepScreenOn = true; videoView = this } },
                        update = { layout -> v.attach(layout); v.setAspect(aspectIdx) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else AndroidView(
                    factory = { c -> PlayerView(c).apply { useController = false; this.player = player; keepScreenOn = true; videoView = this } },
                    update = { v -> v.resizeMode = resize; v.subtitleView?.visibility = View.GONE },
                    modifier = Modifier.fillMaxSize(),
                )
                // Subtitles live outside the video frame, so zoom / crop never moves or enlarges them.
                if (!usingVlc) AndroidView(
                    factory = { c -> SubtitleView(c).apply { setBottomPaddingFraction(0.07f) } },
                    update = { sv ->
                        sv.setApplyEmbeddedStyles(false); sv.setApplyEmbeddedFontSizes(false)
                        sv.setStyle(subStyle); sv.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subSize); sv.setCues(cues)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // The video surface can't fade itself, so a black layer on top fades away instead.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - videoAlpha }.background(Color.Black))
                // Switching link mid-playback: the last frame stays, softly blurred, until the new one is on screen.
                androidx.compose.animation.AnimatedVisibility(switching && failure == null, Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(450))) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        frozen?.let { bmp ->
                            androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize().blur(18.dp), contentScale = if (aspectIdx == 1) ContentScale.Crop else ContentScale.Fit)
                        }
                        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x33000000), Color(0x99000000)))))
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(38.dp), color = Color.White, strokeWidth = 2.5.dp)
                            Text(
                                (switchLabel?.let { "$it · " } ?: "") + "Continuing at ${fmtTime(resumeAt)}",
                                Modifier.padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 7.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6E8EE),
                            )
                        }
                    }
                }
            }

            // Gestures:
            //  tap = controls · double-tap left/right = ∓10 s · double-tap middle = play/pause
            //  swipe up/down left = brightness, right = volume · pinch = zoom to fill / original
            //  press & hold = 2x, keep holding and slide right/left = faster/slower (0.5x – 4x)
            Box(
                Modifier.fillMaxSize().pointerInput(locked, firstFrame) {
                    if (!firstFrame) return@pointerInput
                    if (locked) { detectTapGestures(onTap = { controls = !controls; poke++ }); return@pointerInput }
                    val slop = viewConfiguration.touchSlop
                    val longMs = viewConfiguration.longPressTimeoutMillis
                    val stepPx = 36.dp.toPx()
                    var lastTapAt = 0L
                    var lastTapX = 0f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val downAt = SystemClock.uptimeMillis()
                        var mode = 0 // 0 = undecided, 1 = brightness/volume, 2 = hold speed, 3 = pinch, 4 = ignore
                        val left = start.x < size.width / 2f
                        var level = 0f
                        var pinchStart = 0f
                        var pinchRatio = 1f
                        var baseSpeed = 1f
                        var lastStep = 0
                        while (true) {
                            val wait = if (mode == 0) (longMs - (SystemClock.uptimeMillis() - downAt)).coerceAtLeast(1L) else Long.MAX_VALUE
                            val ev = this.withTimeoutOrNull(wait) { awaitPointerEvent() }
                            if (ev == null) {
                                // Held still long enough: 2x while holding.
                                if (mode == 0) {
                                    mode = 2
                                    baseSpeed = rateNow()
                                    setRate(2f); holdSpeed = 2f
                                    controls = false
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                continue
                            }
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2 && (mode == 0 || mode == 3)) {
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                if (mode == 0) { mode = 3; pinchStart = d.coerceAtLeast(1f) } else pinchRatio = d / pinchStart
                                ev.changes.forEach { it.consume() }
                                continue
                            }
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                            val delta = c.position - start
                            when (mode) {
                                0 -> if (abs(delta.y) > slop && abs(delta.y) > abs(delta.x)) {
                                    mode = 1
                                    val act = ctx as Activity
                                    level = if (left) act.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                                    else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                                } else if (abs(delta.x) > slop * 2) mode = 4
                                1 -> {
                                    val dy = c.position.y - c.previousPosition.y
                                    level = (level - dy / (size.height * 0.8f)).coerceIn(0f, 1f)
                                    if (left) {
                                        val act = ctx as Activity
                                        act.window.attributes = act.window.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) }
                                        hud = "brightness" to level
                                    } else {
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxVol).toInt(), 0)
                                        hud = "volume" to level
                                    }
                                    c.consume()
                                }
                                2 -> {
                                    val step = (delta.x / stepPx).toInt()
                                    if (step != lastStep) {
                                        lastStep = step
                                        val sp = (2f + 0.25f * step).coerceIn(0.5f, 4f)
                                        if (sp != holdSpeed) {
                                            setRate(sp); holdSpeed = sp
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    c.consume()
                                }
                            }
                        }
                        when (mode) {
                            0 -> {
                                // A tap. Two quick taps = double-tap.
                                val t = SystemClock.uptimeMillis()
                                if (t - lastTapAt < 300 && abs(start.x - lastTapX) < 120.dp.toPx()) {
                                    tapJob?.cancel(); lastTapAt = 0L
                                    val third = size.width / 3f
                                    when {
                                        // Like the TV: controls hidden = 30 s jumps with the bubble; controls shown = 10 s.
                                        start.x < third -> if (!controls) bubbleSeek(false) else { seekBy(-10_000); seekHint = "-10s" }
                                        start.x > third * 2 -> if (!controls) bubbleSeek(true) else { seekBy(10_000); seekHint = "+10s" }
                                        else -> { val was = isPlayingNow(); togglePlay(); seekHint = if (was) "❚❚" else "▶" }
                                    }
                                } else {
                                    lastTapAt = t; lastTapX = start.x
                                    tapJob?.cancel()
                                    tapJob = scope.launch {
                                        delay(260)
                                        if (showInfo) showInfo = false else { controls = !controls; poke++ }
                                    }
                                }
                            }
                            1 -> {
                                // Remember the brightness you swiped to (used by "Remember my last").
                                if (left) {
                                    val b = (ctx as Activity).window.attributes.screenBrightness
                                    if (b > 0f) vm.setPlayerPref("bright", b.toString())
                                } else {
                                    // And the volume (0..1), for the volume's "Remember my last".
                                    runCatching { vm.setPlayerPref("vol", (audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol).toString()) }
                                }
                                hud = null
                            }
                            2 -> { setRate(baseSpeed); holdSpeed = null }
                            3 -> when {
                                pinchRatio > 1.12f && aspectIdx != 1 -> setAspect(1, "Zoomed to fill")
                                pinchRatio < 0.9f && aspectIdx != 0 -> setAspect(0, "Original")
                            }
                        }
                    }
                },
            )

            // Watch together: reactions from everyone float up the right side.
            val roomCode by Together.code.collectAsState()
            if (roomCode != null) ReactionsOverlay(Modifier.fillMaxSize())

            // Hold-for-speed pill.
            AnimatedVisibility(holdSpeed != null, Modifier.align(Alignment.TopCenter).padding(top = 28.dp), enter = fadeIn(tween(150)), exit = fadeOut(tween(200))) {
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${(holdSpeed ?: 2f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}x", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.FastForward, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Slide ◀ ▶ to change", fontSize = 11.sp, color = Color(0xFFB6BDCC))
                }
            }

            LaunchedEffect(seekHint) { if (seekHint != null) { delay(700); seekHint = null } }
            seekHint?.let {
                Text(it, Modifier.align(when { it.startsWith("-") -> Alignment.CenterStart; it.startsWith("+") -> Alignment.CenterEnd; else -> Alignment.Center }).padding(horizontal = side)
                    .clip(RoundedCornerShape(30.dp)).background(Color(0x88000000)).padding(horizontal = 18.dp, vertical = 10.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            // Mid-playback buffering: a thin spinner only.
            if (firstFrame && buffering && failure == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White, strokeWidth = 3.dp)
            hud?.let { (kind, lvl) ->
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 32.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xB3000000))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (kind == "brightness") Icons.Default.LightMode else if (lvl == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width(140.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(lvl).background(Color.White))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${(lvl * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LaunchedEffect(aspectLabel) { if (aspectLabel != null) { delay(1500); aspectLabel = null } }
            aspectLabel?.let {
                Text(it, Modifier.align(Alignment.Center).padding(top = 110.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xB3000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            // Content advisory, top-left: white bar + "Category · Level" lines, 12sp.
            AnimatedVisibility(showAdvisory && advisory.isNotEmpty() && !controls, Modifier.align(Alignment.TopStart).padding(start = side, top = 20.dp),
                enter = fadeIn(tween(500)), exit = fadeOut(tween(500))) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Color.White))
                    Column(Modifier.padding(start = 12.dp)) {
                        advisory.forEach { (cat, level) ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text(cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("  ·  $level", fontSize = 12.sp, color = Color(0xFFB6BDCC))
                            }
                        }
                    }
                }
            }

            // Skip intro / recap, and Next episode near the end.
            val s = segs
            val inIntro = s?.intro?.let { pos >= it.start_ms && pos < it.end_ms - 1500 } == true
            val inRecap = s?.recap?.let { pos >= it.start_ms && pos < it.end_ms - 1500 } == true
            val nearEnd = nextEp != null && dur > 0 && (s?.outro?.let { pos >= it.start_ms } ?: (pos >= dur - 120_000))
            // Auto-skip (Settings › Skip Intro): jumps over an intro / recap by itself, once each, with a short note.
            val autoSkip = isOn(opt("autoSkipIntro"))
            LaunchedEffect(inIntro, inRecap, autoSkip) {
                if (!autoSkip || !(inIntro || inRecap) || s == null) return@LaunchedEffect
                val seg = if (inIntro) s.intro else s.recap
                val tag = (if (inIntro) "i" else "r") + (seg?.start_ms ?: 0)
                if (seg == null || tag in autoSkipped) return@LaunchedEffect
                autoSkipped += tag
                delay(600)
                seekAbs(seg.end_ms)
                Snack.show(if (inIntro) "Intro skipped" else "Recap skipped")
            }
            if (firstFrame && !inPip) Column(Modifier.align(Alignment.BottomEnd).padding(end = side, bottom = if (controls) 160.dp else 32.dp), horizontalAlignment = Alignment.End) {
                AnimatedVisibility(inIntro || inRecap,
                    enter = fadeIn(tween(220)) + slideInHorizontally(tween(320)) { it / 3 },
                    exit = fadeOut(tween(180))) {
                    PillButton(if (inIntro) "Skip Intro" else "Skip Recap", Icons.Default.FastForward) {
                        seekAbs(if (inIntro) s!!.intro!!.end_ms else s!!.recap!!.end_ms); poke++
                    }
                }
                AnimatedVisibility(nearEnd && !locked,
                    enter = fadeIn(tween(260)) + slideInHorizontally(tween(360)) { it / 3 },
                    exit = fadeOut(tween(200))) {
                    val windowStart = s?.outro?.start_ms ?: (dur - 120_000)
                    NextEpisodePill(left = (dur - pos).coerceAtLeast(0L), window = (dur - windowStart).coerceAtLeast(1L)) { playNext() }
                }
            }

            // ───── Controls (fade in / out), Hotstar layout ─────
            //  top-left: title · top-right: lock, quality, back (+ clock)
            //  bottom: seek bar, then Next Episode · Audio & Subtitles · Episodes · Watch Next · More ▸
            AnimatedVisibility(controls && !locked && !showInfo && firstFrame && !inPip, Modifier.fillMaxSize(), enter = fadeIn(tween(220)), exit = fadeOut(tween(260))) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xB3000000), 0.25f to Color(0x26000000), 0.6f to Color(0x33000000), 1f to Color(0xE6000000)))
) {
                    // Only the buttons watch for a finger (the empty video area keeps its tap-to-hide and double-taps).
                    val touch: (Boolean) -> Unit = { d -> touchingControls = d; if (!d) poke++ }
                    // Phones: one play / pause button in the middle.
                    if (!BuildConfig.IS_TV) CenterPlayPause(playing, Modifier.align(Alignment.Center).watchTouch(touch)) { togglePlay(); pausedByUp = false; poke++ }
                    // Title, top-left.
                    val showName = meta?.name?.takeIf { it.isNotBlank() }
                    val epNow = episodes.firstOrNull { it.id == p.key }
                    Column(Modifier.align(Alignment.TopStart).padding(start = side, top = topY - 4.dp).fillMaxWidth(0.55f)) {
                        Text(if (type == "series") showName ?: p.title.substringBefore("  S") else showName ?: p.title,
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (type == "series" && epNow != null) Text(
                            "S${epNow.season} E${epNow.episode}" + (epNow.title ?: epNow.name)?.let { " · $it" }.orEmpty(),
                            Modifier.padding(top = 2.dp), fontSize = 14.sp, color = Color(0xFFD0D4DD), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        now?.source?.let { Text("Via ${it.replace("\n", " | ")}", Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Color(0xFF9AA1AE), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    // Top-right: clock, lock (phone), quality, back.
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = topY - 8.dp, end = side - 8.dp).onFocusChanged { topHasFocus = it.hasFocus }.watchTouch(touch),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clockNow(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6E8EE))
                        if (!BuildConfig.IS_TV) GlassBtn(Icons.Default.Groups, null, "Watch together") { panel = "together" }
                        if (!BuildConfig.IS_TV) GlassBtn(Icons.Default.LockOpen, null, "Lock") { locked = true; controls = true; poke++ }
                        if (quickList.isNotEmpty()) GlassBtn(if (qualityMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            run {
                                // Show what is really playing (a 1080p file is never labelled 4K).
                                val res = now?.url?.let { itemFor(it) }?.resolution() ?: 0
                                val real = when { res >= 1440 -> "4K"; res >= 1080 -> "Full HD"; res >= 720 -> "Mid HD"; res > 0 -> "Low"; else -> null }
                                if (quality == "auto") "Auto" + (real?.let { " · $it" } ?: "") else real ?: (AllQualityLevels.firstOrNull { it.id == quality }?.label ?: "Auto")
                            }, "Quality", iconFirst = false) { qualityMenu = !qualityMenu; poke++ }
                        GlassBtn(Icons.Default.Close, null, "Back") { onBack() }
                    }
                    // Phones: play / pause is the big centre button. TV: it stays the first button of the bottom row (remote focus).
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = side, end = side, bottom = bottomY).watchTouch(touch)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${fmtTime(pendingSeek ?: pos)} / ${fmtTime(dur)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            if (dur > 0) Text("Ends at ${endsAt(dur - pos, rateNow())}", fontSize = 13.sp, color = Color(0xFFB6BDCC))
                        }
                        Box(
                            Modifier.fillMaxWidth()
                                .then(if (BuildConfig.IS_TV) Modifier.focusRequester(seekFocus).onFocusChanged { seekFocused = it.isFocused }.focusable() else Modifier),
                        ) {
                            SeekBar(pos, buffered, dur, thumbs, pendingSeek, focused = seekFocused,
                                onScrub = { scrubbing = it; if (it) { controls = true; thumbs?.warmUp() }; poke++ }) { pendingSeek = null; seekAbs(it); poke++ }
                        }
                        Spacer(Modifier.height(10.dp))
                        val railScroll = androidx.compose.foundation.rememberScrollState()
                        Row(
                            Modifier.onFocusChanged { rowHasFocus = it.hasFocus }
                                // Buttons slide away under a rounded, invisible edge (same shape as the buttons) and fade out
                                // there, instead of being cut by a straight line.
                                .clip(RoundedCornerShape(percent = 50))
                                .railFade(railScroll)
                                .horizontalScroll(railScroll)
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            var firstSet = false
                            fun firstMod(): Modifier = if (!firstSet) { firstSet = true; Modifier.focusRequester(rowFocus) } else Modifier
                            if (BuildConfig.IS_TV) GlassBtn(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, if (playing) "Pause" else "Play", firstMod()) {
                                togglePlay(); pausedByUp = false; poke++
                            }
                            // Order: Watch Next · Audio & Subtitles · Episodes · Next Episode · ›
                            GlassBtn(Icons.Default.Theaters, "Watch Next", "Watch next", firstMod()) { watchNext = true; controls = false }
                            GlassBtn(Icons.Default.Subtitles, "Audio & Subtitles", "Audio and subtitles", firstMod()) { panel = "avsubs" }
                            if (type == "series" && episodes.isNotEmpty()) GlassBtn(Icons.Default.VideoLibrary, "Episodes", "Episodes", firstMod()) { showEpisodes = true; controls = false }
                            if (nextEp != null) GlassBtn(Icons.Default.SkipNext, "Next Episode", "Next episode", firstMod()) { playNext() }
                            if (more) {
                                GlassBtn(Icons.Default.CloudQueue, "Sources", "Sources") { panel = "sources" }
                                GlassBtn(Icons.Default.AspectRatio, AspectModes[aspectIdx].first, "Aspect ratio") { setAspect((aspectIdx + 1) % AspectModes.size); poke++ }
                                GlassBtn(Icons.Default.Speed, "${speed.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}x", "Speed") { panel = "speed" }
                                GlassBtn(Icons.Default.Bedtime, if (sleepAt != null || sleepEnd) "Sleep on" else "Sleep", "Sleep timer") { panel = "sleep" }
                                if (!BuildConfig.IS_TV && PipState.supported(ctx)) GlassBtn(Icons.Default.PictureInPictureAlt, null, "Picture in picture") { PipState.enter(ctx as Activity, player.videoSize) }
                                if (!BuildConfig.IS_TV) GlassBtn(Icons.Default.OpenInNew, null, "Open in another player") { pauseNow(); now?.let { openExternal(ctx, it.url, p.title) } }
                                GlassBtn(Icons.Outlined.Info, null, "Info") { showInfo = true; controls = false }
                            }
                            GlassBtn(if (more) Icons.Default.ChevronLeft else Icons.Default.ChevronRight, null, if (more) "Less" else "More") { more = !more; poke++ }
                        }
                    }
                }
            }

            // Seek bubble (controls hidden): « 30s / 30s » that adds up while you keep pressing.
            AnimatedVisibility(bubble != null, Modifier.align(if (bubble?.first == true) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = side),
                enter = fadeIn(tween(120)) + androidx.compose.animation.scaleIn(tween(160), initialScale = 0.85f), exit = fadeOut(tween(260))) {
                val b = bubble ?: (true to 30)
                Column(
                    Modifier.size(132.dp).clip(CircleShape).background(Color(0x66000000)).border(1.dp, Color(0x33FFFFFF), CircleShape),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (b.first) Icons.Default.FastForward else Icons.Default.FastRewind, null, Modifier.size(34.dp))
                    Text("${b.second}s", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(fmtTime(pos), fontSize = 12.sp, color = Color(0xFFC9CED9))
                }
            }

            if (locked && controls) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = topY, end = topEnd).size(44.dp).clip(CircleShape).background(Color(0x99000000))
                    .focusRing(CircleShape).clickable { locked = false; poke++ }, Alignment.Center) { Icon(Icons.Default.Lock, "Unlock", Modifier.size(22.dp)) }
                LaunchedEffect(poke) { delay(2500); if (locked) controls = false }
            }

            // Info screen while paused.
            AnimatedVisibility(
                showInfo, Modifier.fillMaxSize(),
                enter = fadeIn(tween(450)) + androidx.compose.animation.scaleIn(tween(450), initialScale = 1.03f),
                exit = fadeOut(tween(380)) + androidx.compose.animation.scaleOut(tween(380), targetScale = 1.06f),
            ) {
                val m = meta
                // Tap anywhere: the info softly blurs, grows and fades away (no click ripple).
                val infoBlur by transition.animateDp(transitionSpec = { tween(380) }, label = "infoBlur") {
                    if (it == androidx.compose.animation.EnterExitState.Visible) 0.dp else 14.dp
                }
                Box(Modifier.fillMaxSize().blur(infoBlur).background(Brush.horizontalGradient(0f to Color(0xF2000000), 0.6f to Color(0xAA000000), 1f to Color(0x22000000)))
                    .pointerInput(Unit) { detectTapGestures { showInfo = false } }) {
                    Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.55f).padding(start = side)) {
                        Text("You're watching", fontSize = 15.sp, color = Color(0xFFB6BDCC))
                        if (m != null && logoOf(m) != null) AsyncImage(logoOf(m), m.name, Modifier.padding(vertical = 10.dp).height(70.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                        else Text(m?.name ?: p.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        if (type == "series") Text(p.title.removePrefix(m?.name ?: "").trim(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        m?.let { mm ->
                            Text(listOfNotNull(mm.releaseInfo, mm.runtime, mm.imdbRating?.let { "IMDb $it" }).joinToString("  •  "), fontSize = 14.sp, color = Color(0xFFC9CEDB), modifier = Modifier.padding(top = 6.dp))
                            mm.genres?.takeIf { it.isNotEmpty() }?.let { Text(it.take(3).joinToString(" · "), fontSize = 14.sp, color = Accent, modifier = Modifier.padding(top = 4.dp)) }
                            val epDesc = mm.videos?.firstOrNull { it.id == p.key }?.let { it.title ?: it.name }
                            Text(mm.description ?: epDesc ?: "", fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFFE2E5EC), maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                        }
                        Text("Tap anywhere to continue", fontSize = 13.sp, color = Color(0xFF8A94A8), modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }

            // ───── Loading screen: dimmed artwork, "Finding stream source" → "Buffering…" ─────
            if (loadingAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = loadingAlpha }.noFocus().clickable(interactionSource = noRipple, indication = null) {}) {
                    AsyncImage(p.image ?: meta?.background ?: meta?.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color(0xA6000000)))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.5f to Color.Transparent, 1f to Color(0x99000000))))
                    val f = failure
                    if (f == null) {
                        // Finding a source → the cat inspects the poster; buffering → the movie is served under a cloche.
                        val scenePoster = meta?.poster ?: p.image
                        val sceneQuality = when {
                            bestRes >= 2160 -> "4K · SHARP"
                            bestRes >= 1440 -> "1440p · SHARP"
                            bestRes >= 1080 -> "1080p · SHARP"
                            bestRes >= 720 -> "720p · CLEAR"
                            else -> null
                        }
                        // Instant play: no loading scenes — just the artwork for the moment the saved video takes to open
                        // (a small ring only if it takes longer than 1.5 s).
                        if (p.instant && !status.startsWith("Finding")) {
                            var slow by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { delay(1500); slow = true }
                            if (slow) CircularProgressIndicator(Modifier.align(Alignment.Center).size(34.dp), color = Color.White, strokeWidth = 2.5.dp)
                        } else Crossfade(status.startsWith("Finding"), Modifier.fillMaxSize(), animationSpec = tween(600), label = "scene") { finding ->
                            if (finding) FindingScene(scenePoster, meta?.name ?: p.title, answered, sourceTotal, sceneQuality, Modifier.fillMaxSize())
                            else ServingScene(scenePoster, bufProgress, Modifier.fillMaxSize())
                        }
                    } else {
                        Column(Modifier.align(Alignment.Center).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(f, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LoadBtn("Find another source", true, Modifier.focusRequester(failFocus)) { findRequest = if (findRequest < 1) 2 else findRequest + 1 }
                                LoadBtn("Choose source", false) { failure = null; panel = "sources"; status = "Buffering…" }
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = topEnd - 14.dp).size(50.dp).clip(CircleShape).background(Color(0x80000000)).focusRing(CircleShape).clickable { onBack() },
                        Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(22.dp)) }
                    // Sources: top-left, a little down from the edge so the thumb reaches it.
                    Box(
                        Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 74.dp).size(44.dp)
                            .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .background(Color(0x8C101014), CircleShape).border(1.dp, Color(0x24FFFFFF), CircleShape)
                            .focusRing(CircleShape)
                            .clickable(interactionSource = noRipple, indication = null) { panel = "sources" },
                        Alignment.Center,
                    ) { Icon(Icons.Default.KeyboardArrowDown, "Sources", Modifier.size(20.dp), tint = Color.White) }
                }
            }

            // ───── Quality menu (Auto / Full HD / Mid HD / Low) ─────
            LaunchedEffect(controls) { if (!controls) qualityMenu = false }
            AnimatedVisibility(
                qualityMenu && controls, Modifier.align(Alignment.TopEnd).padding(top = topY + 40.dp, end = topEnd + 40.dp),
                enter = fadeIn(tween(160)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.9f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)),
                exit = fadeOut(tween(140)) + androidx.compose.animation.scaleOut(tween(140), targetScale = 0.95f),
            ) {
                val map = remember(quickList) { vm.qualityMap(quickList) }
                val hideDupes = isOn(opt("qsHideDupes"))
                val shape = RoundedCornerShape(20.dp)
                // The row to focus with the remote: the chosen level, else the first one that exists.
                val focusId = map.firstOrNull { it.first.id == quality && it.second != null }?.first?.id ?: map.firstOrNull { it.second != null }?.first?.id
                Column(
                    Modifier.width(230.dp).clip(shape).background(Color(0xF0141418)).border(1.dp, Color(0x1FFFFFFF), shape)
                        .onFocusChanged {
                            val had = menuHasFocus
                            menuHasFocus = it.hasFocus
                            // Moved out of the menu with the remote: close it.
                            if (had && !it.hasFocus) qualityMenu = false
                        }
                        .padding(vertical = 6.dp),
                ) {
                    map.forEachIndexed { i, (lvl, st) ->
                        val dupOf = if (lvl.id == "auto" || st == null) null else map.subList(1, i).firstOrNull { it.second?.stream?.url == st.stream.url }?.first
                        if (!(dupOf != null && hideDupes)) {
                        val on = lvl.id == quality
                        // Show "1080p" only when this level really has that quality; otherwise nothing.
                        val res = st?.resolution() ?: 0
                        val sub = when {
                            st == null || dupOf != null -> ""
                            lvl.id == "auto" -> if (res > 0) "${res}p" else ""
                            res == lvl.res -> "${res}p"
                            else -> ""
                        }
                        Row(
                            Modifier.fillMaxWidth().then(if (lvl.id == focusId) Modifier.focusRequester(qualityFocus) else Modifier)
                                .focusFill().clickable(enabled = st != null) { setQuality(lvl.id) }.graphicsLayer { alpha = if (st == null) 0.4f else 1f }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(lvl.label, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Color(0xFF9AA3B5))
                            }
                            if (on) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Accent)
                        }
                        }
                    }
                }
            }

            // ───── Episodes (series): premium pop-up grouped by season ─────
            EpisodesPopup(
                visible = showEpisodes, episodes = episodes, currentId = p.key, showName = meta?.name ?: "",
                maxW = maxWidth, maxH = maxHeight, onClose = { showEpisodes = false },
            ) { ep -> showEpisodes = false; if (ep.id != p.key) playEpisode(ep) }

            // ───── Watch Next: the next episodes and titles like this one ─────
            WatchNextOverlay(
                vm, watchNext, meta, type, p.key, episodes, onClose = { watchNext = false },
                onEpisode = { ep -> watchNext = false; playEpisode(ep) },
            ) { m ->
                watchNext = false
                scope.launch {
                    if (m.type == "series") {
                        val full = vm.fullMeta(m) ?: m
                        val first = full.videos.orEmpty().filter { (it.season ?: 0) > 0 && it.episode != null }
                            .sortedWith(compareBy({ it.season }, { it.episode })).firstOrNull()
                        if (first == null) { Snack.show("No episodes found for ${m.name}"); return@launch }
                        onReplace(Screen.Player("${full.name}  S${first.season}E${first.episode}", "", emptyList(), key = first.id,
                            image = first.thumbnail ?: full.background ?: full.poster, type = "series", meta = full, find = true, quick = vm.qsActive()))
                    } else onReplace(Screen.Player(m.name, "", emptyList(), key = m.id, image = m.background ?: m.poster, type = "movie", meta = m,
                        find = true, quick = vm.qsActive()))
                }
            }

            // ───── Side panels slide in from the right ─────
            LaunchedEffect(panel) { panel?.let { lastPanel = it } }
            AnimatedVisibility(panel != null, Modifier.fillMaxSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)).noFocus().clickable(interactionSource = noRipple, indication = null) { panel = null })
            }
            AnimatedVisibility(
                panel != null, Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(tween(160)),
                exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
            ) {
                val which = panel ?: lastPanel
                Column(
                    Modifier.fillMaxHeight().width(if (which == "avsubs") (maxWidth * 0.94f).coerceAtMost(1060.dp) else 400.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .background(Color(0xF5111216)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .onFocusChanged {
                            val had = panelHasFocus
                            panelHasFocus = it.hasFocus
                            // Remote focus slipped out of the open panel (or its list changed): back to its first item.
                            if (had && !it.hasFocus) scope.launch { delay(60); if (panel != null && !panelHasFocus) runCatching { panelFocus.requestFocus() } }
                        }
                        .noFocus()
                        .clickable(interactionSource = noRipple, indication = null) {}
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Text(
                        when (which) { "subs" -> "Subtitles"; "audio" -> "Audio"; "avsubs" -> "Audio & Subtitles"; "speed" -> "Playback speed"; "sleep" -> "Sleep timer"; "sound" -> "Sound"; "together" -> "Watch together"; else -> "Sources" },
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    val audioContent: @Composable ColumnScope.() -> Unit = {
                            val versions = remember(known, quickList, now) { vm.languageVersions((known + quickList).distinctBy { it.stream.url }, now?.url) }
                            val main = versions.filter { it.preferred || it.current }
                            val others = versions.filter { !it.preferred && !it.current }
                            // Remote: the first other language gets the focus (else "Other languages", else the track list).
                            val focusVer = main.firstOrNull { !it.current }
                            val focusOthers = focusVer == null && others.isNotEmpty()
                            val versionRow: @Composable (LangVersion) -> Unit = { v -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (v === focusVer) Modifier.focusRequester(panelFocus) else Modifier)
                                    .focusFill().clickable(enabled = !v.current) { panel = null; switchTo(v.stream, langLabel(v.lang)) }
                                    .padding(vertical = 11.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(langLabel(v.lang), Modifier.weight(1f), fontSize = 16.sp, fontWeight = if (v.current) FontWeight.Bold else FontWeight.Normal)
                                v.stream.resolution().takeIf { it > 0 }?.let { Text("${it}p", fontSize = 12.sp, color = Color(0xFF8A94A8)) }
                                if (v.current) Icon(Icons.Default.Check, null, Modifier.padding(start = 8.dp).size(18.dp), tint = Accent)
                            } }
                            if (versions.size > 1) {
                                Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                    main.forEach { versionRow(it) }
                                    if (others.isNotEmpty()) {
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (focusOthers) Modifier.focusRequester(panelFocus) else Modifier)
                                                .focusFill().clickable { otherLangsOpen = !otherLangsOpen }.padding(vertical = 11.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("Other languages (${others.size})", Modifier.weight(1f), fontSize = 15.sp, color = Color(0xFFB6BDCC), fontWeight = FontWeight.SemiBold)
                                            Icon(if (otherLangsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFB6BDCC))
                                        }
                                        AnimatedVisibility(otherLangsOpen) { Column { others.forEach { versionRow(it) } } }
                                    }
                                    Text("Tracks in this file", Modifier.padding(top = 12.dp, bottom = 2.dp), fontSize = 13.sp, color = Color(0xFF8A94A8))
                                    Column(Modifier.heightIn(max = 220.dp)) {
                                        CompositionLocalProvider(LocalFirstFocus provides (if (focusVer == null && !focusOthers) panelFocus else null)) {
                                            if (usingVlc) vlc?.let { v -> VlcTrackList(v, audio = true, tick = vlcTick) }
                                            else TrackList(tracks, C.TRACK_TYPE_AUDIO, player, allowOff = false) { lang -> vm.setPlayerPref("audio", lang) }
                                        }
                                    }
                                }
                            } else {
                                if (usingVlc) vlc?.let { v -> VlcTrackList(v, audio = true, tick = vlcTick) }
                                else TrackList(tracks, C.TRACK_TYPE_AUDIO, player, allowOff = false) { lang -> vm.setPlayerPref("audio", lang) }
                            }
                    }
                    CompositionLocalProvider(LocalFirstFocus provides panelFocus) {
                    when (which) {
                        "subs" -> SubsPanel(
                            vm, type, p.key, loaded = now?.subs.orEmpty(),
                            tracks = {
                                if (usingVlc) vlc?.let { v -> VlcTrackList(v, audio = false, tick = vlcTick) }
                                else TrackList(tracks, C.TRACK_TYPE_TEXT, player, allowOff = true) { lang -> vm.setPlayerPref("subs", lang) }
                            },
                        ) { sub -> addOnlineSub(sub) }
                        "audio" -> audioContent()
                        "avsubs" -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            // Audio | Subtitles | Style — the remote moves between the columns with ◀ ▶.
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                SubSection("Audio")
                                Column(Modifier.weight(1f, fill = false)) { audioContent() }
                                // Sound: Audio boost and Night mode (remembered).
                                if (!BuildConfig.IS_TV) {
                                    Text("Sound", Modifier.padding(top = 14.dp, bottom = 4.dp), fontSize = 13.sp, color = Color(0xFF8A94A8))
                                    SoundToggle("Audio boost", "Up to ~2× louder on phone speakers", boost) { boost = it; vm.setPlayerPref("boost", if (it) "yes" else "") }
                                    SoundToggle("Night mode", "Softer loud scenes, clearer dialogue", night) { night = it; vm.setPlayerPref("night", if (it) "yes" else "") }
                                }
                            }
                            Column(Modifier.weight(1.15f).fillMaxHeight()) {
                                CompositionLocalProvider(LocalFirstFocus provides null) {
                                    SubtitlesColumn(
                                        vm, type, p.key, meta?.name ?: p.title, episodes.firstOrNull { it.id == p.key }, loaded = now?.subs.orEmpty(),
                                        tracks = {
                                            if (usingVlc) vlc?.let { v -> VlcTrackList(v, audio = false, tick = vlcTick) }
                                            else TrackList(tracks, C.TRACK_TYPE_TEXT, player, allowOff = true) { lang -> vm.setPlayerPref("subs", lang) }
                                        },
                                    ) { sub -> addOnlineSub(sub) }
                                }
                            }
                            Column(Modifier.weight(0.9f).fillMaxHeight()) {
                                CompositionLocalProvider(LocalFirstFocus provides null) { SubStyleColumn(vm) }
                            }
                        }
                        "speed" -> OptionList(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { "${it}x" to (speed == it) }) { i ->
                            speed = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)[i]; setRate(speed); panel = null
                        }
                        "sleep" -> {
                            val mins = listOf(0, 15, 30, 45, 60)
                            val cur = sleepAt?.let { ((it - SystemClock.elapsedRealtime()) / 60_000L).toInt() }
                            OptionList(mins.map { m -> (if (m == 0) "Off" else "$m minutes") to (if (m == 0) sleepAt == null && !sleepEnd else false) } +
                                listOf("End of this video" to sleepEnd)) { i ->
                                if (i < mins.size) {
                                    sleepEnd = false
                                    sleepAt = if (mins[i] == 0) null else SystemClock.elapsedRealtime() + mins[i] * 60_000L
                                    Snack.show(if (mins[i] == 0) "Sleep timer off" else "Pausing in ${mins[i]} minutes (the sound fades out first)")
                                } else { sleepAt = null; sleepEnd = true; Snack.show("Pausing at the end of this video") }
                                panel = null
                            }
                            if (cur != null) Text("About $cur min left", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = Color(0xFF9AA1AE))
                        }
                        "together" -> TogetherPanel(current = { roomState(isPlayingNow()) }) { st ->
                            panel = null
                            appliedRev = st.rev
                            val expected = st.pos + if (st.playing) (System.currentTimeMillis() - st.receivedAt + 300) else 0L
                            when {
                                st.key.isNotBlank() && st.key != p.key -> onReplace(Screen.Player(st.title, st.url, emptyList(), key = st.key, image = st.image, resumeMs = expected, type = st.type))
                                st.url != now?.url -> load(Now(st.url, now?.subs.orEmpty(), "Watch together", null), expected, "Watch together")
                                else -> { Together.applying = true; try { seekAbs(expected); if (st.playing) playNow() else pauseNow() } finally { Together.applying = false } }
                            }
                        }
                        "sound" -> OptionList(listOf(
                            "Audio boost${if (boost) "  ✓" else ""} — up to 2× louder on phone speakers" to boost,
                            "Night mode${if (night) "  ✓" else ""} — softer loud scenes, clearer quiet dialogue" to night,
                        )) { i ->
                            if (i == 0) { boost = !boost; vm.setPlayerPref("boost", if (boost) "yes" else "") }
                            else { night = !night; vm.setPlayerPref("night", if (night) "yes" else "") }
                        }
                        else -> SourcesPanel(vm, type, p.key, now?.url, except = if (vm.qsActive()) vm.qsAddons() else null, quick = quickList) { st, all ->
                            panel = null
                            known = all
                            candidates = all.filter { it !== st }
                            val keep = keepPos()
                            scope.launch {
                                val extra = withTimeoutOrNull(1500) { runCatching { vm.subtitles(type, p.key) }.getOrDefault(emptyList()) }.orEmpty()
                                load(Now(st.stream.url!!, st.stream.subtitles.orEmpty() + extra, st.stream.name ?: st.addonName, st.addonName), keep, "Source changed")
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/** The first item of an open side panel gets the remote's focus through this. */
private val LocalFirstFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** Never takes the remote's focus (tap-through blockers and scrims). */
private fun Modifier.noFocus(): Modifier = this.focusProperties { canFocus = false }

/** White ring while a control has the remote's focus. Put it before the clickable. */
@Composable
private fun Modifier.focusRing(shape: Shape, width: Dp = 2.dp, color: Color = Color.White): Modifier {
    var f by remember { mutableStateOf(false) }
    return this.onFocusChanged { f = it.isFocused }.border(width, if (f) color else Color.Transparent, shape)
}

/** Soft highlight behind a list row while it has the remote's focus. Put it before the clickable. */
@Composable
private fun Modifier.focusFill(color: Color = Color(0x2EFFFFFF)): Modifier {
    var f by remember { mutableStateOf(false) }
    return this.onFocusChanged { f = it.isFocused }.background(if (f) color else Color.Transparent)
}

@Composable
private fun CtlIcon(icon: ImageVector, desc: String, size: Int, modifier: Modifier = Modifier, pad: Int = 8, onClick: () -> Unit) {
    // Remote focus: a soft white circle behind the icon and a slightly bigger icon.
    var focused by remember { mutableStateOf(false) }
    Icon(
        icon, desc,
        modifier
            .graphicsLayer { val sc = if (focused) 1.1f else 1f; scaleX = sc; scaleY = sc }
            .drawBehind { if (focused) drawCircle(Color(0x40FFFFFF), radius = this.size.minDimension / 2f + 6.dp.toPx()) }
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(pad.dp).size(size.dp),
        tint = Color.White,
    )
}

@Composable
private fun LoadBtn(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Text(text, modifier.clip(RoundedCornerShape(22.dp)).background(if (primary) Color.White else Color(0x33FFFFFF))
        .focusRing(RoundedCornerShape(22.dp), if (primary) 3.dp else 2.dp, if (primary) Accent else Color.White).clickable(onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Color.Black else Color.White)

@OptIn(UnstableApi::class)
private val AspectModes = listOf(
    "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Crop" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    "Stretch" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
)

@Composable
private fun PillButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.padding(top = 10.dp).clip(shape).background(Color(0xE6FFFFFF)).border(1.dp, Color(0x33000000), shape)
            .focusRing(shape, 3.dp, Accent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.Black)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

/**
 * "Next Episode" at the end credits: a white pill whose ring fills up as the credits run out (the next episode starts by
 * itself at the end, as before). Tap to start it now.
 */
@Composable
private fun NextEpisodePill(left: Long, window: Long, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val frac = (1f - left.toFloat() / window).coerceIn(0f, 1f)
    val ring by androidx.compose.animation.core.animateFloatAsState(frac, tween(900), label = "nextRing")
    Row(
        Modifier.padding(top = 10.dp).softHalo(26.dp, spread = 8.dp, strength = 0.35f).clip(shape).background(Color(0xF2FFFFFF))
            .focusRing(shape, 3.dp, Accent).clickable(onClick = onClick).padding(start = 8.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), Alignment.Center) {
            CircularProgressIndicator(progress = { ring }, modifier = Modifier.fillMaxSize(), color = Color.Black, strokeWidth = 2.5.dp, trackColor = Color(0x22000000))
            Icon(Icons.Default.SkipNext, null, Modifier.size(16.dp), tint = Color.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Next Episode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            if (left in 1L..60_000L) Text("in ${(left + 999) / 1000} s", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4A4F5C))
        }
    }
}

/** "9:41 PM" (or 21:41, as the device shows time). */
private fun clockNow(): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())

/** When the video ends at this speed. */
private fun endsAt(leftMs: Long, rate: Float): String {
    val r = if (rate > 0f) rate else 1f
    return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(System.currentTimeMillis() + (leftMs.coerceAtLeast(0L) / r).toLong()))
}

/**
 * Player button: icon (+ optional label). Remote focus = a frosted glass circle / pill around it, a little bigger
 * (no square highlight). [big] = the phone's centre play / pause.
 */
@Composable
private fun GlassBtn(
    icon: ImageVector, label: String?, desc: String, modifier: Modifier = Modifier, big: Boolean = false, iconFirst: Boolean = true, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val sc by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "glass")
    val shape = if (label == null) CircleShape else RoundedCornerShape(50)
    val iconSize = if (big) 44.dp else if (BuildConfig.IS_TV) 22.dp else 22.dp
    // Labelled buttons get a little depth: a soft shadow under them, lit from the top (brighter edge and fill on top).
    val depth = label != null && !focused
    Row(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .onFocusChanged { focused = it.isFocused }
            .then(if (depth) Modifier.softHalo(22.dp, spread = 6.dp, strength = 0.32f, dy = 3.dp) else Modifier)
            .clip(shape)
            .then(
                if (depth) Modifier.background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
                else Modifier.background(
                    when {
                        focused -> Color(0x40FFFFFF)
                        big -> Color(0x33000000)
                        else -> Color.Transparent
                    },
                ),
            )
            .then(
                if (depth) Modifier.border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0DFFFFFF))), shape)
                else Modifier.border(1.dp, if (focused) Color(0x99FFFFFF) else Color.Transparent, shape),
            )
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = if (label == null) (if (big) 18.dp else 9.dp) else 14.dp, vertical = if (big) 18.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconFirst) Icon(icon, null, Modifier.size(iconSize), tint = Color.White)
        if (label != null) Text(label, Modifier.padding(start = if (iconFirst) 8.dp else 0.dp, end = if (iconFirst) 2.dp else 4.dp),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!iconFirst) Icon(icon, null, Modifier.size(iconSize), tint = Color.White)
    }
}

/**
 * 6dp rounded seek bar: white = watched, grey = buffered. Tap or drag to seek.
 * While dragging (phone) or moving a remote seek target ([pending], TV) a preview frame shows above it.
 */
@Composable
private fun SeekBar(pos: Long, buffered: Long, dur: Long, thumbs: SeekThumbs?, pending: Long?, focused: Boolean = false, onScrub: (Boolean) -> Unit = {}, onSeek: (Long) -> Unit) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    // Read through State so a changing duration never restarts (= cancels) a drag in progress.
    val durNow = rememberUpdatedState(dur)
    val scrub = rememberUpdatedState(onScrub)
    val seek = rememberUpdatedState(onSeek)
    val frac = dragFrac ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val bFrac = if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val pendFrac = if (pending != null && dur > 0) (pending.toFloat() / dur).coerceIn(0f, 1f) else null
    val previewFrac = dragFrac ?: pendFrac
    val previewMs = if (previewFrac != null && dur > 0) (dragFrac?.let { (it * dur).toLong() } ?: pending ?: 0L) else null
    Box(Modifier.fillMaxWidth().height(22.dp)) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { o -> val d = durNow.value; if (d > 0) seek.value((o.x / size.width * d).toLong()) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragFrac = (o.x / size.width).coerceIn(0f, 1f); scrub.value(true) },
                        onDragEnd = {
                            val d = durNow.value
                            dragFrac?.let { if (d > 0) seek.value((it * d).toLong()) }
                            dragFrac = null; scrub.value(false)
                        },
                        onDragCancel = { dragFrac = null; scrub.value(false) },
                    ) { change, _ -> change.consume(); dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
        ) {
            val h = (if (focused) 7 else 5).dp.toPx(); val y = (size.height - h) / 2f; val r = CornerRadius(h / 2f)
            drawRoundRect(Color(0x4DFFFFFF), Offset(0f, y), Size(size.width, h), r)
            drawRoundRect(Color(0x80FFFFFF), Offset(0f, y), Size(size.width * bFrac, h), r)
            drawRoundRect(Color.White, Offset(0f, y), Size(size.width * frac, h), r)
            if (dragFrac != null) drawCircle(Color.White, 8.dp.toPx(), Offset(size.width * frac, size.height / 2f))
            else if (pendFrac != null) {
                // Remote seek target: the jump shown as a light span, with a white marker where it lands.
                val a = minOf(frac, pendFrac); val b = maxOf(frac, pendFrac)
                drawRoundRect(Color(0x99FFFFFF), Offset(size.width * a, y), Size(size.width * (b - a), h), r)
                drawRoundRect(Color.White, Offset(size.width * pendFrac - 1.5.dp.toPx(), size.height / 2f - 8.dp.toPx()), Size(3.dp.toPx(), 16.dp.toPx()), CornerRadius(1.5.dp.toPx()))
                drawCircle(if (focused) BrandRed else Color.White, (if (focused) 9 else 6).dp.toPx(), Offset(size.width * pendFrac, size.height / 2f))
            } else if (focused) {
                // Seek bar has the remote's focus: a red dot where you are (◀ ▶ = 10 s).
                drawCircle(Color(0x55E50914), 15.dp.toPx(), Offset(size.width * frac, size.height / 2f))
                drawCircle(BrandRed, 9.dp.toPx(), Offset(size.width * frac, size.height / 2f))
            }
        }
        if (previewFrac != null && previewMs != null) SeekPreview(thumbs, previewMs, previewFrac, Modifier.align(Alignment.TopStart))
    }
}

/** The preview frame above the seek bar (160×90 on phones, 240×135 on TV), with the time under it. */
@Composable
private fun SeekPreview(thumbs: SeekThumbs?, ms: Long, frac: Float, modifier: Modifier = Modifier) {
    val tv = BuildConfig.IS_TV
    val bucket = ms / 10_000L
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    var imgBucket by remember { mutableLongStateOf(-1L) }
    // Asked for after the finger / remote rests for ~120 ms on a 10-second step; a newer step cancels the older one.
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(thumbs, bucket) {
        val t = thumbs ?: return@LaunchedEffect
        t.cached(ms)?.let { img = it; imgBucket = bucket; loading = false; return@LaunchedEffect }
        loading = true
        delay(90)
        val b = t.frameAt(ms)
        if (b != null) { img = b; imgBucket = bucket }
        loading = false
    }
    // The exact frame, else the closest one already made (so the box is rarely empty while you drag).
    val shown = if (imgBucket == bucket) img else (thumbs?.nearest(ms) ?: img)
    // No empty box: the frame shows when there is one (or while one is on its way); otherwise just the time.
    val withFrame = shown != null || (loading && thumbs?.available == true)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.layout { m, c ->
            val pl = m.measure(Constraints())
            val full = c.maxWidth
            layout(0, 0) {
                val x = (frac * full - pl.width / 2f).roundToInt().coerceIn(0, (full - pl.width).coerceAtLeast(0))
                pl.place(x, -pl.height - 8.dp.roundToPx())
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (withFrame) Box(
            Modifier.size(if (tv) 240.dp else 160.dp, if (tv) 135.dp else 90.dp).clip(shape).background(Color(0xFF15161B)).border(2.dp, Color.White, shape),
        ) {
            shown?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (loading && imgBucket != bucket && thumbs?.available == true) CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(if (tv) 26.dp else 20.dp), color = Color.White, strokeWidth = 2.dp,
            )
        }
        Text(
            fmtTime(ms),
            Modifier.padding(top = if (withFrame) 6.dp else 0.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xB3000000)).padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = if (tv) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
        )
    }
}

@Composable
private fun OptionList(options: List<Pair<String, Boolean>>, enabled: List<Boolean> = emptyList(), onPick: (Int) -> Unit) {
    val first = LocalFirstFocus.current
    // Remote: focus starts on the chosen option (else the first one that can be picked).
    val focusAt = options.indices.firstOrNull { options[it].second && enabled.getOrElse(it) { true } }
        ?: options.indices.firstOrNull { enabled.getOrElse(it) { true } } ?: -1
    LazyColumn {
        items(options.size) { i ->
            val (label, on) = options[i]
            val en = enabled.getOrElse(i) { true }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (i == focusAt && first != null) Modifier.focusRequester(first) else Modifier)
                .focusFill().clickable(enabled = en) { onPick(i) }.graphicsLayer { alpha = if (en) 1f else 0.4f }.padding(vertical = 13.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                if (on) Icon(Icons.Default.Check, null, tint = Accent)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackList(tracks: Tracks, trackType: Int, player: ExoPlayer, allowOff: Boolean, onRemember: (String) -> Unit) {
    // Every track is listed; ones this phone can't decode are shown greyed out instead of hidden.
    val opts = tracks.groups.filter { it.type == trackType }.flatMap { g ->
        (0 until g.length).map { i ->
            val f = g.getTrackFormat(i)
            val name = listOfNotNull(f.label, f.language?.let { java.util.Locale(it).displayLanguage.takeIf { d -> d.isNotBlank() } ?: it })
                .distinct().joinToString(" · ").ifBlank { "Track ${i + 1}" }
            val codec = if (trackType == C.TRACK_TYPE_AUDIO) audioCodecName(f.sampleMimeType) else null
            val extra = buildString {
                if (trackType == C.TRACK_TYPE_AUDIO && f.channelCount > 0) append("  ").append(channelsLabel(f.channelCount))
                if (codec != null) append("  ").append(codec)
                if (!g.isTrackSupported(i)) append("  · Not supported on this device")
            }
            TrackOpt(name + extra, g, i, g.isTrackSelected(i)) to (g.isTrackSupported(i) to f.language)
        }
    }
    val off = player.trackSelectionParameters.disabledTrackTypes.contains(trackType)
    if (opts.isEmpty()) {
        Text(if (trackType == C.TRACK_TYPE_TEXT) "No subtitles in this stream. Install a subtitles addon to get more." else "Only one audio track.", color = Color(0xFF9AA3B5))
        return
    }
    Text("Your choice is remembered for the next videos.", fontSize = 12.sp, color = Color(0xFF8A94A8), modifier = Modifier.padding(bottom = 6.dp))
    val rows = (if (allowOff) listOf("Off" to off) else emptyList()) + opts.map { it.first.label to (it.first.selected && !off) }
    val enabled = (if (allowOff) listOf(true) else emptyList()) + opts.map { it.second.first }
    OptionList(rows, enabled) { i ->
        val params = player.trackSelectionParameters.buildUpon()
        if (allowOff && i == 0) {
            params.setTrackTypeDisabled(trackType, true)
            onRemember("off")
        } else {
            val (o, info) = opts[if (allowOff) i - 1 else i]
            params.setTrackTypeDisabled(trackType, false).setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
            val lang = info.second.orEmpty()
            if (trackType == C.TRACK_TYPE_AUDIO) params.setPreferredAudioLanguage(lang.ifEmpty { null }) else params.setPreferredTextLanguage(lang.ifEmpty { null })
            onRemember(lang)
        }
        player.trackSelectionParameters = params.build()
    }
}

private fun channelsLabel(n: Int) = when (n) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${n}ch" }

@OptIn(UnstableApi::class)
private fun audioCodecName(mime: String?): String? = when (mime) {
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
    MimeTypes.AUDIO_AC4 -> "AC-4"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    else -> null
}

/**
 * Sources inside the player. With Quick Start on there are two tabs: "Quick Start" (your Quick Start addons' links,
 * grouped by addon, the playing one marked) and "All sources" (every other addon, same filters and badges as the
 * sources page).
 */
@Composable
private fun SourcesPanel(
    vm: AppViewModel, type: String, key: String, playingUrl: String?, except: Set<String>? = null,
    quick: List<StreamItem> = emptyList(), onSwitch: (StreamItem, List<StreamItem>) -> Unit,
) {
    val texts by vm.texts.collectAsStateWithLifecycle()
    val pack by vm.badgePack.collectAsStateWithLifecycle()
    val hasQuick = except != null
    var qs by remember { mutableStateOf(quick) }
    LaunchedEffect(key, hasQuick) {
        if (hasQuick && qs.isEmpty()) qs = runCatching { vm.quickStreams(type, key) }.getOrDefault(emptyList())
    }
    var tab by remember { mutableStateOf(if (hasQuick && (playingUrl == null || quick.isEmpty() || quick.any { it.stream.url == playingUrl })) 0 else 1) }
    val first = LocalFirstFocus.current
    Column {
        if (hasQuick) {
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Quick Start", "All sources").forEachIndexed { i, label ->
                    val sel = tab == i
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .then(if (sel && first != null) Modifier.focusRequester(first) else Modifier)
                            .focusRing(RoundedCornerShape(18.dp))
                            .background(if (sel) Color.White else Color(0x1FFFFFFF))
                            .clickable { tab = i }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.Black else Color.White,
                    )
                }
            }
        }
        if (hasQuick && tab == 0) QuickSourcesList(vm, qs, playingUrl, pack, texts) { st -> onSwitch(st, qs) }
        else CompositionLocalProvider(LocalFirstFocus provides (if (hasQuick) null else first)) {
            AllSourcesList(vm, type, key, playingUrl, except, texts, pack, onSwitch)
        }
    }
}

/** Quick Start links grouped by addon: which one is playing, and what else each addon has. */
@Composable
private fun QuickSourcesList(vm: AppViewModel, list: List<StreamItem>, playingUrl: String?, pack: List<PackBadge>, texts: Map<String, String>, onPick: (StreamItem) -> Unit) {
    val fusion = isOn(texts["fusionBadges"] ?: Defaults["fusionBadges"] ?: "yes")
    if (list.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(30.dp), Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp) }
        return
    }
    val groups = remember(list) { list.groupBy { it.addonName } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { (addon, links) ->
            item(key = "h:$addon") {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(addon, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB6BDCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val playingHere = links.any { it.stream.url == playingUrl }
                    Text(if (playingHere) "Playing · ${links.size} links" else "${links.size} links", fontSize = 12.sp,
                        color = if (playingHere) Accent else Color(0xFF8A94A8))
                }
            }
            itemsIndexed(links, key = { i, it -> "q:$addon:$i:${it.stream.url}" }) { _, s ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(16.dp)
                Box(Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus }.border(2.dp, if (focused) Color.White else Color.Transparent, shape)) {
                    StreamCard(s, fusion, true, pack, playing = s.stream.url == playingUrl, compact = true) { if (s.stream.url != playingUrl) onPick(s) }
                }
            }
        }
    }
}

@Composable
private fun AllSourcesList(
    vm: AppViewModel, type: String, key: String, playingUrl: String?, except: Set<String>?, texts: Map<String, String>,
    pack: List<PackBadge>, onSwitch: (StreamItem, List<StreamItem>) -> Unit,
) {
    var all by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var done by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(key) { vm.streamsFlow(type, key, except = except).collect { (l, d) -> all = l; done = d } }
    // Links a slow-server addon (CNCVerse) finds later appear here by themselves.
    LaunchedEffect(key) { SlowAddons.watch(vm, type, key) }
    val slow by SlowAddons.links.collectAsStateWithLifecycle()
    val merged = remember(all, slow[key]) { (all + slow[key].orEmpty().filter { except == null || it.addonName !in except }).distinctBy { it.stream.url } }
    val f = remember(merged, texts["tagRules"], showAll) { if (showAll) Filtered(merged, 0, false, emptyList()) else vm.filtered(merged) }
    val fusion = isOn(texts["fusionBadges"] ?: Defaults["fusionBadges"] ?: "yes")
    val showAddon = isOn(texts["showAddonName"] ?: "yes")
    val first = LocalFirstFocus.current
    when {
        f.forcedEmpty -> Column {
            Text("No ${f.forcedLabels.joinToString(" / ")} streams for this title.", color = Color(0xFF9AA3B5))
            LoadBtn("Show all streams", true, first?.let { Modifier.focusRequester(it) } ?: Modifier) { showAll = true }
        }
        f.list.isEmpty() && done -> Text("No sources found.", color = Color(0xFF9AA3B5))
        f.list.isEmpty() -> Box(Modifier.fillMaxWidth().padding(30.dp), Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp) }
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Remote: focus starts on the first card that isn't the one playing.
            val focusUrl = f.list.firstOrNull { it.stream.url != playingUrl }?.stream?.url
            items(f.list) { s ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier.fillMaxWidth().then(if (first != null && s.stream.url == focusUrl) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.hasFocus }
                        .border(2.dp, if (focused) Color.White else Color.Transparent, shape),
                ) {
                    StreamCard(s, fusion, showAddon, pack, playing = s.stream.url == playingUrl, compact = true) { onSwitch(s, f.list) }
                }
            }
            if (!done) item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) } }
        }
    }
}

/**
 * Audio & Subtitles › Subtitles column: the subtitles inside the video first, then online ones (your subtitle addons +
 * OpenSubtitles, looked up by title and SxxEyy) in the languages chosen in Settings › Playback (English by default),
 * grouped by language; "Other languages" shows the rest.
 */
@Composable
private fun SubtitlesColumn(
    vm: AppViewModel, type: String, key: String, title: String, ep: Video?, loaded: List<Subtitle>,
    tracks: @Composable () -> Unit, onPick: (Subtitle) -> Unit,
) {
    val texts by vm.texts.collectAsStateWithLifecycle()
    val wanted = remember(texts["subLangs"]) { subLangCodes(texts["subLangs"] ?: Defaults["subLangs"] ?: "english") }
    var online by remember { mutableStateOf<List<Subtitle>?>(null) }
    var openLang by remember { mutableStateOf<String?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        online = runCatching { vm.onlineSubtitles(type, key) }.getOrDefault(emptyList()).filter { o -> loaded.none { it.url == o.url } }
    }
    val label = title + (ep?.let { " S%02dE%02d".format(it.season ?: 0, it.episode ?: 0) } ?: "")
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        SubSection("Subtitles · in this video")
        Column(Modifier.heightIn(max = 260.dp)) { tracks() }
        SubSection("Online · $label")
        val list = online
        when {
            list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Searching OpenSubtitles & your addons…", Modifier.padding(start = 10.dp), fontSize = 13.sp, color = Color(0xFF9AA3B5))
            }
            list.isEmpty() -> Text("No online subtitles found.", fontSize = 13.sp, color = Color(0xFF9AA3B5))
            else -> {
                val groups = remember(list, wanted) {
                    list.groupBy { subLangName(it.lang) }.entries.toList().sortedWith(
                        compareBy<Map.Entry<String, List<Subtitle>>> { e -> wanted.indexOfFirst { w -> e.value.first().lang.lowercase().startsWith(w) }.let { if (it < 0) 99 else it } }
                            .thenBy { it.key },
                    )
                }
                val mine = groups.filter { e -> wanted.any { w -> e.value.first().lang.lowercase().startsWith(w) } }
                val rest = groups - mine.toSet()
                // Your first language is open from the start.
                LaunchedEffect(mine) { if (openLang == null) openLang = mine.firstOrNull()?.key }
                val subRow: @Composable (String, List<Subtitle>) -> Unit = { lang, subs ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).focusFill().clickable { openLang = if (openLang == lang) null else lang }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lang, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${subs.size}", fontSize = 12.sp, color = Color(0xFF8A94A8))
                        Icon(if (openLang == lang) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFB6BDCC))
                    }
                    if (openLang == lang) subs.take(25).forEachIndexed { i, sub ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).focusFill().clickable { onPick(sub) }.padding(vertical = 9.dp, horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp), tint = Color(0xFFB6BDCC))
                            Spacer(Modifier.width(10.dp))
                            Text("$lang ${i + 1}", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                            Text(if (sub.url.contains("opensubtitles", true)) "OpenSubtitles" else "Addon", fontSize = 11.sp, color = Color(0xFF8A94A8))
                        }
                    }
                }
                if (mine.isEmpty()) Text("None in your languages (Settings › Playback › Subtitle languages).", fontSize = 12.sp, color = Color(0xFF8A94A8),
                    modifier = Modifier.padding(bottom = 6.dp))
                mine.forEach { (lang, subs) -> subRow(lang, subs) }
                if (rest.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).focusFill().clickable { showOthers = !showOthers }.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Other languages (${rest.size})", Modifier.weight(1f), fontSize = 14.sp, color = Color(0xFFB6BDCC), fontWeight = FontWeight.SemiBold)
                        Icon(if (showOthers) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFB6BDCC))
                    }
                    if (showOthers) rest.forEach { (lang, subs) -> subRow(lang, subs) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Audio & Subtitles › Style column: size, colour, background, bold, outline — changes show at once. */
@Composable
private fun SubStyleColumn(vm: AppViewModel) {
    val texts by vm.texts.collectAsStateWithLifecycle()
    fun v(k: String) = texts[k] ?: Defaults[k] ?: ""
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        SubSection("Style · Size")
        SubChips(listOf("Small" to "16", "Medium" to "20", "Large" to "26", "Extra large" to "32"), v("subSize")) { vm.setText("subSize", it) }
        SubSection("Text colour")
        SubChips(listOf("White" to "#FFFFFF", "Yellow" to "#FFE14D", "Cyan" to "#6FE3FF", "Green" to "#7CFF8A", "Pink" to "#FF8FB1"), v("subColor").uppercase()) { vm.setText("subColor", it) }
        SubSection("Background")
        SubChips(listOf("None" to "#00000000", "Shadow" to "#80000000", "Black" to "#FF000000", "Grey" to "#B3333333"), v("subBg").uppercase()) { vm.setText("subBg", it) }
        SubSection("Text")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubChip("Bold", isOn(v("subBold"))) { vm.setText("subBold", if (isOn(v("subBold"))) "no" else "yes") }
            SubChip("Outline", isOn(v("subOutline"))) { vm.setText("subOutline", if (isOn(v("subOutline"))) "no" else "yes") }
        }
        if (isOn(v("subOutline"))) {
            SubSection("Outline colour")
            SubChips(listOf("Black" to "#000000", "Dark grey" to "#333333", "Red" to "#B00020", "Blue" to "#0A3D91"), v("subOutlineColor").uppercase()) { vm.setText("subOutlineColor", it) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** "english, hindi" → language-code prefixes to match subtitle languages ("en", "eng", "hi", "hin"…). */
fun subLangCodes(setting: String): List<String> = setting.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.flatMap { name ->
    val code = java.util.Locale.getAvailableLocales().firstOrNull { it.getDisplayLanguage(java.util.Locale.ENGLISH).equals(name, true) }?.language
    listOfNotNull(code, runCatching { code?.let { java.util.Locale(it).isO3Language } }.getOrNull(), name.take(3))
}.distinct()

/**
 * Subtitles panel: the tracks in this video, "Find subtitles online" (your subtitle addons + OpenSubtitles, preferred
 * languages first) and the look of the subtitles (size, colour, background, bold, outline) — changes show at once.
 */
@Composable
private fun SubsPanel(
    vm: AppViewModel, type: String, key: String, loaded: List<Subtitle>,
    tracks: @Composable () -> Unit, onPick: (Subtitle) -> Unit,
) {
    val texts by vm.texts.collectAsStateWithLifecycle()
    fun v(k: String) = texts[k] ?: Defaults[k] ?: ""
    var online by remember { mutableStateOf<List<Subtitle>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var openLang by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val prefs = remember(texts["qsLangs"]) {
        (listOf("en", "eng", "english") + v("qsLangs").split(",").map { it.trim().lowercase() }).distinct()
    }

    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        SubSection("In this video")
        Column(Modifier.heightIn(max = 230.dp)) { tracks() }

        SubSection("Find subtitles online")
        when {
            online == null -> SubChip(if (searching) "Searching…" else "Search OpenSubtitles & your addons", selected = false) {
                if (!searching) {
                    searching = true
                    scope.launch {
                        online = runCatching { vm.onlineSubtitles(type, key) }.getOrDefault(emptyList())
                            .filter { o -> loaded.none { it.url == o.url } }
                        searching = false
                    }
                }
            }
            online!!.isEmpty() -> Text("No more subtitles found for this title.", fontSize = 13.sp, color = Color(0xFF9AA3B5))
            else -> {
                val byLang = remember(online) {
                    val rank = { code: String -> val l = code.lowercase(); prefs.indexOfFirst { p -> l.startsWith(p.take(2)) }.let { if (it < 0) 99 else it } }
                    online!!.groupBy { subLangName(it.lang) }.entries.toList()
                        .sortedWith(compareBy<Map.Entry<String, List<Subtitle>>> { rank(it.value.first().lang) }.thenBy { it.key })
                }
                Text("${online!!.size} found · tap one to add it", fontSize = 12.sp, color = Color(0xFF8A94A8), modifier = Modifier.padding(bottom = 6.dp))
                byLang.forEach { (lang, list) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).focusFill().clickable { openLang = if (openLang == lang) null else lang }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lang, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${list.size}", fontSize = 12.sp, color = Color(0xFF8A94A8))
                        Icon(if (openLang == lang) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFB6BDCC))
                    }
                    if (openLang == lang) list.take(25).forEachIndexed { i, sub ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).focusFill().clickable { onPick(sub) }
                                .padding(vertical = 9.dp, horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.ClosedCaption, null, Modifier.size(16.dp), tint = Color(0xFFB6BDCC))
                            Spacer(Modifier.width(10.dp))
                            Text("$lang ${i + 1}", Modifier.weight(1f), fontSize = 14.sp)
                            Text(if (sub.url.contains("opensubtitles", true)) "OpenSubtitles" else "Addon", fontSize = 11.sp, color = Color(0xFF8A94A8))
                        }
                    }
                }
            }
        }

        SubSection("Size")
        SubChips(listOf("Small" to "16", "Medium" to "20", "Large" to "26", "Extra large" to "32"), v("subSize")) { vm.setText("subSize", it) }
        SubSection("Text colour")
        SubChips(listOf("White" to "#FFFFFF", "Yellow" to "#FFE14D", "Cyan" to "#6FE3FF", "Green" to "#7CFF8A", "Pink" to "#FF8FB1"), v("subColor").uppercase()) { vm.setText("subColor", it) }
        SubSection("Background")
        SubChips(listOf("None" to "#00000000", "Shadow" to "#80000000", "Black" to "#FF000000", "Grey" to "#B3333333"), v("subBg").uppercase()) { vm.setText("subBg", it) }
        SubSection("Style")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubChip("Bold", isOn(v("subBold"))) { vm.setText("subBold", if (isOn(v("subBold"))) "no" else "yes") }
            SubChip("Outline", isOn(v("subOutline"))) { vm.setText("subOutline", if (isOn(v("subOutline"))) "no" else "yes") }
        }
        if (isOn(v("subOutline"))) {
            SubSection("Outline colour")
            SubChips(listOf("Black" to "#000000", "Dark grey" to "#333333", "Red" to "#B00020", "Blue" to "#0A3D91"), v("subOutlineColor").uppercase()) { vm.setText("subOutlineColor", it) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** "eng" / "en" / "hin" → English / Hindi (falls back to the code). */
private fun subLangName(code: String): String =
    runCatching { java.util.Locale(code.trim()).displayLanguage }.getOrNull()?.takeIf { it.isNotBlank() && !it.equals(code.trim(), true) }
        ?: code.replaceFirstChar { it.uppercase() }

@Composable
private fun SubSection(text: String) =
    Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A94A8))

@Composable
private fun SubChips(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    // Two rows at most, wrapping by hand (no FlowRow needed).
    options.chunked(3).forEach { row ->
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, value) -> SubChip(label, value.equals(current, ignoreCase = true)) { onPick(value) } }
        }
    }
}

@Composable
private fun SubChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        label,
        modifier.clip(shape).focusRing(shape).background(if (selected) Color.White else Color(0x1FFFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.Black else Color.White, maxLines = 1,
    )
}

/** Episodes pop-up: scales in with a spring, season chips on top, "Now playing" on the current episode. */
@Composable
private fun EpisodesPopup(
    visible: Boolean, episodes: List<Video>, currentId: String, showName: String,
    maxW: androidx.compose.ui.unit.Dp, maxH: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit, onPick: (Video) -> Unit,
) {
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) prog.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f)) else prog.animateTo(0f, tween(200))
    }
    if (!visible && prog.value <= 0.01f) return
    val seasons = remember(episodes) { episodes.mapNotNull { it.season }.distinct().sorted() }
    val currentSeason = episodes.firstOrNull { it.id == currentId }?.season ?: seasons.firstOrNull() ?: 1
    var season by remember(currentSeason) { mutableIntStateOf(currentSeason) }
    val list = remember(episodes, season) { episodes.filter { it.season == season } }
    val listState = rememberLazyListState()
    LaunchedEffect(season, visible) {
        val i = list.indexOfFirst { it.id == currentId }
        if (visible && i > 0) listState.scrollToItem((i - 1).coerceAtLeast(0))
    }
    // Remote: focus starts on the episode playing (else the season's first one) and stays inside the pop-up.
    val epFocus = remember { FocusRequester() }
    val popScope = rememberCoroutineScope()
    var popHasFocus by remember { mutableStateOf(false) }
    val focusId = list.firstOrNull { it.id == currentId }?.id ?: list.firstOrNull()?.id
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        popHasFocus = false
        for (i in 0 until 30) { delay(80); if (popHasFocus) break; runCatching { epFocus.requestFocus() } }
    }
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = prog.value.coerceIn(0f, 1f) }.background(Color(0xA6000000))
            .noFocus().clickable(interactionSource = noRipple, indication = null) { onClose() })
        Column(
            Modifier.align(Alignment.Center)
                .width((maxW * 0.86f).coerceAtMost(820.dp)).height((maxH * 0.84f))
                .graphicsLayer {
                    val v = prog.value
                    alpha = v.coerceIn(0f, 1f); scaleX = 0.9f + 0.1f * v; scaleY = scaleX
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF21C1C22), Color(0xF5121216))))
                .border(1.dp, Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))), shape)
                .onFocusChanged {
                    val had = popHasFocus
                    popHasFocus = it.hasFocus
                    if (had && !it.hasFocus) popScope.launch { delay(60); if (visible && !popHasFocus) runCatching { epFocus.requestFocus() } }
                }
                .noFocus()
                .clickable(interactionSource = noRipple, indication = null) {}
                .padding(top = 18.dp, bottom = 10.dp),
        ) {
            Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (showName.isNotBlank()) Text(showName, fontSize = 13.sp, color = Color(0xFF9AA3B5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).focusRing(CircleShape).clickable { onClose() }, Alignment.Center) {
                    Icon(Icons.Default.Close, "Close", Modifier.size(20.dp))
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons) { sn ->
                    val on = sn == season
                    Text(
                        "Season $sn" + if (sn == currentSeason) " •" else "",
                        Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color.White else Color(0x1AFFFFFF))
                            .focusRing(RoundedCornerShape(20.dp), 2.dp, if (on) Accent else Color.White).clickable { season = sn }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White,
                    )
                }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list, key = { _, e -> e.id }) { _, ep ->
                    val now = ep.id == currentId
                    val rs = RoundedCornerShape(18.dp)
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clip(rs).background(if (focused) Color(0x26FFFFFF) else if (now) Accent.copy(alpha = 0.14f) else Color(0x0DFFFFFF))
                            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else if (now) Accent.copy(alpha = 0.7f) else Color.Transparent, rs)
                            .then(if (ep.id == focusId) Modifier.focusRequester(epFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onPick(ep) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22222A))) {
                            AsyncImage(ep.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (now) Box(Modifier.fillMaxSize().background(Color(0x66000000)), Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, null, Modifier.size(28.dp), tint = Color.White)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp, end = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("E${ep.episode}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9AA3B5))
                                if (now) Text("NOW PLAYING", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(Accent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnAccent, letterSpacing = 0.5.sp)
                            }
                            Text(ep.title ?: ep.name ?: "Episode ${ep.episode}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp))
                            (ep.overview ?: ep.description)?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, color = Color(0xFFB6BDCC), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One card in Watch Next. */
private data class WnItem(val key: String, val title: String, val sub: String?, val image: String?, val ep: Video? = null, val rec: TmRec? = null, val meta: Meta? = null)

/**
 * Watch Next (player): "Up next" episodes of this show, then "More like this" (TMDB recommendations, else your Home rows).
 * Slides up from the bottom; the remote stays inside; Back closes it.
 */
@Composable
private fun WatchNextOverlay(
    vm: AppViewModel, visible: Boolean, meta: Meta?, type: String, key: String, episodes: List<Video>,
    onClose: () -> Unit, onEpisode: (Video) -> Unit, onTitle: (Meta) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var similar by remember(key) { mutableStateOf<List<WnItem>?>(null) }
    LaunchedEffect(visible, key) {
        if (!visible || similar != null) return@LaunchedEffect
        val imdb = key.substringBefore(":")
        val fromTmdb = runCatching {
            val info = vm.tmdbInfo(imdb, type)
            if (info == null) emptyList() else vm.likedCandidates(imdb, info).map { r ->
                WnItem("t:${r.kind}${r.tmdbId}", r.title, listOfNotNull(r.year, if (r.kind == "tv") "Series" else "Movie").joinToString(" · "), r.poster, rec = r)
            }
        }.getOrDefault(emptyList())
        similar = fromTmdb.ifEmpty {
            vm.shelves.value.flatMap { it.items }.filter { it.id != imdb && (it.type == "movie" || it.type == "series") }.distinctBy { it.id }.take(20)
                .map { m -> WnItem("m:${m.id}", m.name, listOfNotNull(m.releaseInfo, if (m.type == "series") "Series" else "Movie").joinToString(" · "), m.poster, meta = m) }
        }
    }
    val upNext = remember(episodes, key) {
        val i = episodes.indexOfFirst { it.id == key }
        if (i < 0) emptyList() else episodes.drop(i + 1).take(8).map { e ->
            WnItem("e:${e.id}", "S${e.season} E${e.episode}" + (e.title ?: e.name)?.let { " · $it" }.orEmpty(), null, e.thumbnail ?: meta?.background, ep = e)
        }
    }
    val firstFr = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(visible, similar, upNext) {
        if (!visible) return@LaunchedEffect
        for (i in 0 until 25) { delay(80); if (hasFocus) break; runCatching { firstFr.requestFocus() } }
    }
    fun pick(it: WnItem) {
        val ep = it.ep
        val mt = it.meta
        when {
            ep != null -> onEpisode(ep)
            mt != null -> onTitle(mt)
            it.rec != null -> scope.launch {
                val r = it.rec ?: return@launch
                val imdb = vm.tmdbToImdb(r)
                if (imdb == null) Snack.show("Couldn't open ${r.title}")
                else onTitle(Meta(id = imdb, type = if (r.kind == "tv") "series" else "movie", name = r.title, poster = r.poster))
            }
        }
    }
    AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x33000000), 0.45f to Color(0xB3000000), 1f to Color(0xF2000000)))
            .noFocus().clickable(interactionSource = noRipple, indication = null) { onClose() }) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 28.dp)
                    .onFocusChanged {
                        val had = hasFocus
                        hasFocus = it.hasFocus
                        if (had && !it.hasFocus) scope.launch { delay(60); if (visible && !hasFocus) runCatching { firstFr.requestFocus() } }
                    },
            ) {
                Text("Watch Next", Modifier.padding(start = 40.dp, bottom = 6.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                var first = true
                if (upNext.isNotEmpty()) {
                    Text("Up next", Modifier.padding(start = 40.dp, top = 6.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB6BDCC))
                    val isFirst = first; first = false
                    WnRow(upNext, wide = true, firstFr = if (isFirst) firstFr else null) { pick(it) }
                }
                val sim = similar
                Text("More like this", Modifier.padding(start = 40.dp, top = 12.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB6BDCC))
                when {
                    sim == null -> CircularProgressIndicator(Modifier.padding(start = 40.dp).size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    sim.isEmpty() -> Text("Nothing to suggest yet.", Modifier.padding(start = 40.dp), fontSize = 13.sp, color = Color(0xFF9AA3B5))
                    else -> WnRow(sim, wide = false, firstFr = if (first) firstFr else null) { pick(it) }
                }
            }
        }
    }
}

@Composable
private fun WnRow(items: List<WnItem>, wide: Boolean, firstFr: FocusRequester?, onPick: (WnItem) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(items, key = { _, it -> it.key }) { i, it ->
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(12.dp)
            Column(Modifier.width(if (wide) 220.dp else 116.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f)
                        .graphicsLayer { val sc = if (focused) 1.06f else 1f; scaleX = sc; scaleY = sc }
                        .then(if (i == 0 && firstFr != null) Modifier.focusRequester(firstFr) else Modifier)
                        .onFocusChanged { f -> focused = f.isFocused }
                        .clip(shape).background(Color(0xFF1E1F26))
                        .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(it) },
                ) {
                    AsyncImage(it.image, it.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (wide) Box(Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(Color(0x80000000)), Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp), tint = Color.White)
                    }
                }
                Text(it.title, Modifier.padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = if (wide) 2 else 1, overflow = TextOverflow.Ellipsis)
                it.sub?.let { sub -> Text(sub, fontSize = 11.sp, color = Color(0xFF9AA3B5), maxLines = 1) }
            }
        }
    }
}

/** Audio / subtitle lists when the VLC engine is playing. */
@Composable
private fun VlcTrackList(v: VlcEngine, audio: Boolean, tick: Int) {
    var refresh by remember { mutableIntStateOf(0) }
    val list = remember(tick, refresh) { if (audio) v.audioTracks() else v.subTracks() }
    val cur = remember(tick, refresh) { if (audio) v.audioTrack else v.subTrack }
    if (list.isEmpty()) {
        Text(if (audio) "Only one audio track." else "No subtitles in this stream.", color = Color(0xFF9AA3B5))
        return
    }
    val rows = (if (!audio) listOf("Off" to (cur < 0)) else emptyList()) + list.map { it.second to (it.first == cur) }
    OptionList(rows) { i ->
        if (!audio && i == 0) v.selectSub(-1)
        else { val id = list[if (audio) i else i - 1].first; if (audio) v.selectAudio(id) else v.selectSub(id) }
        refresh++
    }
}


/** The play / pause in the middle of the phone player: a clean, flat, see-through circle with a bold icon and a soft press. */
@Composable
private fun CenterPlayPause(playing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.9f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f), label = "ppScale")
    Box(
        modifier.size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color(0x59000000))
            .border(1.5.dp, Color(0x40FFFFFF), CircleShape)
            .clickable(interactionSource = press, indication = null, onClickLabel = if (playing) "Pause" else "Play", onClick = onClick),
        Alignment.Center,
    ) {
        androidx.compose.animation.Crossfade(playing, label = "pp") { pl ->
            Icon(if (pl) Icons.Rounded.RoundedPause else Icons.Rounded.RoundedPlay, if (pl) "Pause" else "Play", Modifier.size(44.dp), tint = Color.White)
        }
    }
}

/** Notes when a finger is on these controls (they then stay up), without taking the touch from them. */
private fun Modifier.watchTouch(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var down = false
        while (true) {
            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val now = e.changes.any { it.pressed }
            if (now != down) { down = now; onChange(now) }
        }
    }
}

/** Fades the button row out at an edge only where more buttons are hidden behind it. */
private fun Modifier.railFade(scroll: androidx.compose.foundation.ScrollState): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val f = 28.dp.toPx()
            if (scroll.value > 0) drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = f),
                size = Size(f, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
            if (scroll.value < scroll.maxValue) drawRect(
                Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - f, endX = size.width),
                topLeft = Offset(size.width - f, 0f), size = Size(f, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }


/** Audio boost (LoudnessEnhancer) and Night mode (a soft compressor that tames loud scenes and lifts dialogue). */
object SoundFx {
    private var boostFx: android.media.audiofx.LoudnessEnhancer? = null
    private var nightFx: android.media.audiofx.DynamicsProcessing? = null
    private var session = 0

    fun apply(sessionId: Int, boost: Boolean, night: Boolean) {
        if (sessionId <= 0) return
        if (sessionId != session) { release(); session = sessionId }
        runCatching {
            if (boost) {
                val fx = boostFx ?: android.media.audiofx.LoudnessEnhancer(sessionId).also { boostFx = it }
                fx.setTargetGain(700) // +7 dB
                fx.enabled = true
            } else boostFx?.enabled = false
        }
        runCatching {
            if (night && android.os.Build.VERSION.SDK_INT >= 28) {
                val fx = nightFx ?: run {
                    val cfg = android.media.audiofx.DynamicsProcessing.Config.Builder(
                        android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                        false, 0, false, 0, false, 0, true,
                    ).build()
                    android.media.audiofx.DynamicsProcessing(0, sessionId, cfg).also { nightFx = it }
                }
                // Loud parts are held back above -26 dB, then everything is lifted: explosions softer, voices louder.
                fx.setLimiterAllChannelsTo(android.media.audiofx.DynamicsProcessing.Limiter(true, true, 0, 2f, 120f, 6f, -26f, 12f))
                fx.enabled = true
            } else if (android.os.Build.VERSION.SDK_INT >= 28) nightFx?.enabled = false
        }
    }

    fun release() {
        runCatching { boostFx?.release() }; boostFx = null
        runCatching { if (android.os.Build.VERSION.SDK_INT >= 28) nightFx?.release() }; nightFx = null
        session = 0
    }
}

/** Picture-in-picture: the player keeps playing in a small window (button in the player, or when you go Home while playing). */
object PipState {
    @Volatile var playerOpen = false
    @Volatile var playing = false

    fun supported(ctx: android.content.Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= 26 && ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(act: Activity, size: androidx.media3.common.VideoSize? = null) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        runCatching {
            val w = size?.width?.takeIf { it > 0 } ?: 16
            val h = size?.height?.takeIf { it > 0 } ?: 9
            // Android allows aspect ratios between 1:2.39 and 2.39:1.
            val ratio = (w.toFloat() / h).coerceIn(0.42f, 2.39f)
            val r = android.util.Rational((ratio * 1000).toInt(), 1000)
            act.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(r).build())
        }
    }
}


/** A compact switch row in the player's Audio column. */
@Composable
private fun SoundToggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!on) }.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.sp, color = Color(0xFF9AA1AE))
        }
        androidx.compose.material3.Switch(on, onChange)
    }
}
