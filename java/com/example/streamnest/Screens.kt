package com.example.streamnest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.CompositingStrategy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Screen {
    /** [sourcesFor] = open the sources page for this video id straight away ("Play manually"). */
    data class Detail(val meta: Meta, val sourcesFor: String? = null) : Screen
    data class Player(
        val title: String, val url: String, val subs: List<Subtitle>, val key: String = "", val image: String? = null, val resumeMs: Long = 0L,
        val type: String = "", val meta: Meta? = null, val source: String? = null,
        /** addon that gave this link */
        val addon: String? = null,
        /** other streams to fall back to if this one fails before it starts */
        val candidates: List<StreamItem> = emptyList(),
        /** true = no link yet; the player finds one itself ("Finding stream source") using Auto Stream Selection */
        val find: Boolean = false,
        /** find from the Quick Start addons (quality selector + language versions) */
        val quick: Boolean = false,
        /** the title page already saved the start of [url]: play straight from storage, no loading screen */
        val instant: Boolean = false,
    ) : Screen
    data class Settings(val start: String = "root") : Screen
    data object Live : Screen
    /** Actor page (TMDB person id). */
    data class Person(val personId: String, val name: String, val photo: String?) : Screen
    /** Full-screen trailer player: YouTube [videos] keys with their [titles], starting at [start]. */
    data class Trailer(val videos: List<String>, val titles: List<String>, val start: Int, val title: String, val meta: Meta?) : Screen
    /** A Nuvio-style collection, opened on one of its folders. */
    data class Collection(val collectionId: String, val folderId: String) : Screen
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val stack = remember { mutableStateListOf<Screen>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    val pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); Unit }
    val profile by vm.current.collectAsStateWithLifecycle()
    val texts by vm.texts.collectAsStateWithLifecycle()

    // Hotstar-style: scrolling down hides the filter pill and shrinks the bar, scrolling up restores both.
    // The state only flips after a clear scroll in one direction, and the animation runs in the draw phase
    // (graphicsLayer), so scrolling stays smooth.
    // [scrolledDown] is NEVER read during composition: it is only observed by the two coroutines below, which
    // drive Animatables. So flipping it (each time the scroll direction changes) recomposes nothing — before, it
    // recomposed all of App() (and re-ran animateFloatAsState) right in the middle of a scroll.
    var scrolledDown by remember { mutableStateOf(false) }
    val navStyle = texts["navStyle"] ?: "adaptive"
    val capsuleAnimatable = remember { Animatable(0f) }
    val barAnimatable = remember { Animatable(0f) }
    LaunchedEffect(navStyle) {
        launch { capsuleAnimatable.follow { if (navStyle != "expanded" && scrolledDown) 1f else 0f } }
        launch {
            barAnimatable.follow {
                val compact = when (navStyle) { "expanded" -> false; "compact" -> true; else -> scrolledDown }
                if (compact) 1f else 0f
            }
        }
    }
    val capsuleAnim: State<Float> = remember(capsuleAnimatable) { capsuleAnimatable.asState() }
    val barAnim: State<Float> = remember(barAnimatable) { barAnimatable.asState() }
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                acc = if ((acc < 0f && dy > 0f) || (acc > 0f && dy < 0f)) dy else acc + dy
                if (acc < -60f && !scrolledDown) { scrolledDown = true; acc = 0f }
                else if (acc > 40f && scrolledDown) { scrolledDown = false; acc = 0f }
                return Offset.Zero
            }
        }
    }
    val homeList = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BackHandler(stack.isNotEmpty() || (profile != null && tab != 0)) { if (stack.isNotEmpty()) pop() else tab = 0 }

    // Startup animation: once per app start (Help & Settings > Advanced > Startup animation).
    var voteFor by remember { mutableStateOf<Meta?>(null) }
    var showSplash by remember { mutableStateOf(!StartupState.splashShown && isOn(texts["splash"] ?: "yes")) }
    // First start: "Sign in to StreamX" (or Skip) before "Who's watching?".
    var gateOpen by rememberSaveable { mutableStateOf(!Cloud.decided.value) }

    CompositionLocalProvider(LocalTexts provides texts) {
        LaunchedEffect(profile == null) { if (profile == null) stack.clear() }
        val open: (Meta) -> Unit = { stack.add(Screen.Detail(it)) }
        if (profile == null && gateOpen) CloudGate(onDone = { gateOpen = false })
        else if (profile == null && texts["streamMode"] == null) ModeChooser(vm) {}
        else if (profile == null) WhoIsWatchingScreen(vm, active = !showSplash) { tab = 0; scrolledDown = false }
        else when (val top = stack.lastOrNull()) {
            is Screen.Player -> key(top) {
                PlayerScreen(vm, top, onBack = pop, onReplace = { next -> if (stack.isNotEmpty()) stack[stack.lastIndex] = next }, onProgress = { pos, dur, url, subs ->
                    if (top.key.isNotEmpty() && url.isNotEmpty()) vm.saveProgress(Progress(top.key, top.title, top.image, url, subs, pos, dur, System.currentTimeMillis()))
                    // "How was it?" once you've really watched something (10 min or 30%).
                    val base = top.key.substringBefore(":")
                    val prev = vm.votes.value[base]
                    val watchedEnough = pos >= 10 * 60_000L || (dur > 0 && pos >= dur * 0.3)
                    val askAgain = prev == null || (prev.v == 0 && System.currentTimeMillis() - prev.at > 3L * 24 * 3600_000)
                    if (base.isNotEmpty() && watchedEnough && askAgain && isOn(texts["askVote"] ?: "yes")) {
                        voteFor = top.meta?.copy(id = base) ?: Meta(id = base, type = top.type.ifBlank { if (top.key.contains(":")) "series" else "movie" },
                            name = top.title.substringBefore("  S"), poster = top.image)
                    }
                })
            }
            is Screen.Detail -> key(top) {
                DetailScreen(vm, top.meta, onBack = pop, onOpen = open, sourcesFor = top.sourcesFor, onPlay = { stack.add(it) },
                    onPerson = { c -> stack.add(Screen.Person(c.id, c.name, c.photo)) },
                    onTrailer = { stack.add(it) })
            }
            is Screen.Person -> key(top) { PersonScreen(vm, top, onBack = pop, onOpen = open) }
            is Screen.Trailer -> key(top) {
                // "Play the movie": back to the title page, which then presses Play by itself.
                TrailerScreen(top, onBack = pop, onPlayMovie = { pendingDetailPlay.value = top.meta?.id; pop() })
            }
            is Screen.Settings -> key(top) { Box(Modifier.fillMaxSize().background(Bg)) { SettingsHost(vm, top.start, onClose = pop) } }
            Screen.Live -> Box(Modifier.fillMaxSize().background(Bg)) { LiveTvScreen(vm, onBack = pop) { stack.add(it) } }
            is Screen.Collection -> key(top) {
                Box(Modifier.fillMaxSize().background(Bg)) { CollectionScreen(vm, top.collectionId, top.folderId, onBack = pop, onOpen = open) }
            }
            null -> Box(Modifier.fillMaxSize().background(Bg).nestedScroll(scrollWatcher)) {
                when (tab) {
                    0 -> HomeScreen(
                        vm, homeList, capsuleAnim, open, { stack.add(it) },
                        onLive = { stack.add(Screen.Live) },
                        onAi = { voiceOpen = true },
                        onManual = { m, k -> stack.add(Screen.Detail(m, k)) },
                        onOpenCollection = { c, f -> stack.add(Screen.Collection(c.id, f.id)) },
                    )
                    1 -> SearchScreen(vm, "", open)
                    else -> MySpaceScreen(
                        vm,
                        onSettings = { stack.add(Screen.Settings()) },
                        onProfile = { stack.add(Screen.Settings("profile")) },
                        onSwitchProfile = { vm.leaveProfile() },
                        onOpen = open,
                        onPlay = { stack.add(it) },
                        onManual = { m, k -> stack.add(Screen.Detail(m, k)) },
                    )
                }
                BottomNav(
                    tab, vm, compact = barAnim,
                    onTab = {
                        scrolledDown = false
                        if (it == 0 && tab == 0) { scope.launch { homeList.animateScrollToItem(0) }; vm.refresh() } else tab = it
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).tourTarget("nav"),
                )
                if (voiceOpen) VoiceAssistant(vm, onClose = { voiceOpen = false }) { voiceOpen = false; open(it) }
            }
        }
        if (profile != null && stack.lastOrNull() !is Screen.Player && stack.lastOrNull() !is Screen.Trailer) voteFor?.let { m ->
            key(m.id) { VoteCapsule(m, onVote = { v -> vm.vote(m, v) }, onDismiss = { voteFor = null }) }
        }
        // Home screen widget: open the title and press Play (it resumes where you stopped).
        val widgetAsk = WidgetOpen.pending.value
        LaunchedEffect(widgetAsk, profile) {
            val (k, title) = widgetAsk ?: return@LaunchedEffect
            if (profile == null) return@LaunchedEffect
            WidgetOpen.pending.value = null
            val base = k.substringBefore(":")
            val series = k.contains(":")
            stack.clear()
            stack.add(Screen.Detail(Meta(id = base, type = if (series) "series" else "movie", name = title.substringBefore("  S"))))
            pendingDetailPlay.value = base
        }
        // First-time visual tour, one screen at a time (after the short start guide).
        if (profile != null && !texts["guideDone"].isNullOrEmpty() && !showSplash && !BuildConfig.IS_TV) {
            val topScreen = stack.lastOrNull()
            when {
                topScreen == null && tab == 0 -> TourOverlay("home", HomeTour)
                topScreen == null && tab == 2 -> TourOverlay("myspace", MySpaceTour)
                topScreen is Screen.Detail -> TourOverlay("detail", DetailTour)
                topScreen is Screen.Player -> TourOverlay("player", PlayerTour)
            }
        }
        if (profile != null && texts["guideDone"].isNullOrEmpty()) QuickGuide { vm.setText("guideDone", "1") }
        if (showSplash) StartupSplash { showSplash = false }
        SnackHost()
    }
}

/**
 * Animates towards [target] whenever it changes. [target] is read inside snapshotFlow (not in composition), so the
 * state it reads can flip as often as it likes without recomposing anything. The first value is applied instantly.
 */
private suspend fun Animatable<Float, AnimationVector1D>.follow(target: () -> Float) {
    var first = true
    snapshotFlow(target).distinctUntilChanged().collectLatest { v ->
        if (first) { first = false; snapTo(v) } else animateTo(v, tween(260))
    }
}

/** Hotstar-style bottom bar: 3 items, dark base with a glowing curved top edge. Shrinks while scrolling down. */
@Composable
fun BottomNav(tab: Int, vm: AppViewModel, compact: State<Float>, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    var switcher by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // The compact animation is a pure layer translation (draw phase): the bar's contents are not re-laid-out,
    // recomposed or redrawn while it moves.
    Box(modifier.fillMaxWidth().graphicsLayer { translationY = compact.value * 16.dp.toPx() }) {
        Spacer(Modifier.matchParentSize().drawWithCache {
            // Paths, brushes and strokes are built once per size / theme colour, not on every draw.
            // A planet-like horizon: rises from the left, peaks a little right of centre, falls gently on the right.
            val glow = Accent
            val glow2 = Palette.accent2
            val yl = 26.dp.toPx(); val yr = 18.dp.toPx(); val peak = (-6).dp.toPx()
            val surface = Path().apply {
                moveTo(0f, yl); quadraticBezierTo(size.width * 0.5f, peak, size.width, yr)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            val base = Brush.verticalGradient(listOf(Color(0xFA120E18), Color(0xFF07080C)))
            val edge = Path().apply { moveTo(0f, yl); quadraticBezierTo(size.width * 0.5f, peak, size.width, yr) }
            // Light is strongest on the left and fades towards the right, like sunrise on a horizon.
            val light = Brush.horizontalGradient(listOf(glow2, glow, glow.copy(alpha = 0.35f), glow.copy(alpha = 0.08f)))
            val wide = Stroke(width = 14.dp.toPx())
            val mid = Stroke(width = 7.dp.toPx())
            val thin = Stroke(width = 2.2.dp.toPx())
            onDrawBehind {
                drawPath(surface, base)
                drawPath(edge, light, style = wide, alpha = 0.10f)
                drawPath(edge, light, style = mid, alpha = 0.22f)
                drawPath(edge, light, style = thin)
            }
        })
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 14.dp).height(66.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(tab == 0, t("navHome"), compact, { onTab(0) }) { c -> Icon(Icons.Default.AutoAwesome, t("navHome"), Modifier.size(26.dp), tint = c) }
            NavItem(tab == 1, t("navSearch"), compact, { onTab(1) }) { c -> Icon(Icons.Default.Search, t("navSearch"), Modifier.size(26.dp), tint = c) }
            Box {
                NavItem(tab == 2, t("navMySpace"), compact, { onTab(2) }, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress); switcher = true
                }) { _ ->
                    Avatar(vm, 30.dp, if (tab == 2) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                }
                if (switcher) ProfileSwitcher(vm, onDismiss = { switcher = false }, onPicked = { switcher = false; onTab(2) })
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean, label: String, compact: State<Float>, onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, icon: @Composable (Color) -> Unit,
) {
    val c = if (selected) Color.White else Color(0xFF8A94A8)
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val hasLong = onLongClick != null
    val accent = Accent
    val selectedGlow = remember(accent) { Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.10f), Color.Transparent)) }
    Box(
        Modifier.width(96.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp))
            .pointerInput(hasLong) {
                detectTapGestures(onTap = { click() }, onLongPress = if (hasLong) { _: Offset -> longClick?.invoke() } else null)
            }
            .then(if (selected) Modifier.background(selectedGlow) else Modifier),
        Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon(c)
            // Fade in the draw phase; ModulateAlpha avoids an offscreen buffer for the label on every frame.
            if (selected) Text(label, Modifier.graphicsLayer {
                alpha = 1f - compact.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c, maxLines = 1)
        }
    }
}

/** Instagram-style quick profile switcher: a small capsule of avatars above the My Space button. */
@Composable
private fun ProfileSwitcher(vm: AppViewModel, onDismiss: () -> Unit, onPicked: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val above = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width).coerceIn(16, maxOf(16, windowSize.width - popupContentSize.width - 16))
                val y = (anchorBounds.top - popupContentSize.height - 24).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = above, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val shape = RoundedCornerShape(40.dp)
        val pop = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) }
        Row(
            Modifier.graphicsLayer {
                val v = pop.value
                alpha = v.coerceIn(0f, 1f); scaleX = 0.88f + 0.12f * v; scaleY = scaleX
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.9f, 1f)
            }.shadow(18.dp, shape).clip(shape).background(Color(0xF21A1820)).border(1.dp, Color(0x26FFFFFF), shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            profiles.forEach { pr ->
                val on = pr.id == current?.id
                Column(
                    Modifier.width(62.dp).clip(RoundedCornerShape(16.dp)).clickable { if (!on) ProfileLock.ask(pr) { vm.selectProfile(pr); onPicked() } else onPicked() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileAvatar(vm, pr, 48.dp, if (on) Modifier.border(2.dp, Accent, CircleShape) else Modifier, animate = false)
                    Text(pr.name, Modifier.padding(top = 4.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color(0xFFB6BDCC))
                }
            }
        }
    }
}

@Composable
fun Poster(m: Meta, modifier: Modifier = Modifier, onClick: (Meta) -> Unit) {
    AsyncImage(
        model = m.poster, contentDescription = m.name, contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(posterCorner())).background(Surface1).clickable { onClick(m) },
    )
}

// ───────────── Search ─────────────
@Composable
fun SearchScreen(vm: AppViewModel, initial: String, onOpen: (Meta) -> Unit) {
    PremiumSearch(vm, initial, onOpen)
}
