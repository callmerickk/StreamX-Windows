package com.example.streamnest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

private val StreamCardBg = Color(0xFF121216)

/** One small Fusion-style badge. Uses the badge pack image when there is one. */
@Composable
fun BadgeChip(b: Badge) {
    if (b.image != null) {
        AsyncImage(b.image, b.text.ifBlank { null }, Modifier.height(20.dp).widthIn(max = 90.dp), contentScale = ContentScale.Fit)
        return
    }
    Text(
        b.text,
        Modifier.clip(RoundedCornerShape(6.dp)).background(b.kind.bg)
            .then(if (b.kind == BadgeKind.Dv) Modifier.border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = b.kind.fg, maxLines = 1, letterSpacing = 0.2.sp,
    )
}

/** Stream card: badge row, stream name, then the addon's own description lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamCard(
    s: StreamItem, fusion: Boolean, showAddon: Boolean, pack: List<PackBadge>,
    playing: Boolean = false, compact: Boolean = false, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 16.dp else 20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(StreamCardBg)
            .border(1.dp, if (playing) Accent.copy(alpha = 0.8f) else Color(0x0FFFFFFF), shape)
            .clickable(enabled = !playing, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 12.dp else 16.dp),
    ) {
        if (fusion) {
            val badges = remember(s, pack) { s.badges(pack) }
            if (badges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    badges.forEach { BadgeChip(it) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (s.stream.name ?: s.addonName).replace("\n", " "), Modifier.weight(1f),
                fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (playing) Text("Playing", Modifier.padding(start = 8.dp), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        s.detailLines().forEach { line ->
            Text(line, fontSize = if (compact) 13.sp else 15.sp, color = Color(0xFFC9CDD6), lineHeight = if (compact) 18.sp else 21.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        if (showAddon) Text("via ${s.addonName}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Grey shimmering placeholder while addons answer. */
@Composable
private fun SkeletonCard() {
    val a by animateFloatAsState(1f, tween(600), label = "sk")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(StreamCardBg).padding(18.dp).graphicsLayer { alpha = a }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.size(46.dp, 18.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1F1F25))) } }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1F1F25)))
        Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A1F)))
        Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A1F)))
    }
}

/** Data the sources page needs about what is being played. */
data class SourceTarget(val videoId: String, val title: String, val epLabel: String?, val epTitle: String?, val image: String?)

/**
 * Full-screen sources page (like the screenshot): backdrop header with ratings, episode label and title,
 * "Resume from" pill, addon filter chips, and stream cards with Fusion badges.
 */
@Composable
fun SourcesScreen(
    vm: AppViewModel,
    meta: Meta,
    target: SourceTarget,
    resumeMs: Long,
    onBack: () -> Unit,
    onPick: (picked: StreamItem, all: List<StreamItem>, resume: Long) -> Unit,
) {
    val texts by vm.texts.collectAsStateWithLifecycle()
    val pack by vm.badgePack.collectAsStateWithLifecycle()
    fun v(k: String) = texts[k] ?: Defaults[k] ?: ""
    LaunchedEffect(v("badgePack")) { vm.loadBadgePack(v("badgePack")) }

    var reload by remember { mutableIntStateOf(0) }
    var all by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(target.videoId, reload) {
        all = emptyList(); done = false
        vm.streamsFlow(meta.type, target.videoId).collect { (l, d) -> all = l; done = d }
    }
    var showAll by remember(target.videoId) { mutableStateOf(false) }
    val rules = remember(texts["tagRules"]) { parseTagRules(v("tagRules")) }
    // Links a slow-server addon (CNCVerse) finds later appear here by themselves (asked again every 5 s).
    LaunchedEffect(target.videoId) { SlowAddons.watch(vm, meta.type, target.videoId) }
    val slow by SlowAddons.links.collectAsStateWithLifecycle()
    val merged = remember(all, slow[target.videoId]) { (all + slow[target.videoId].orEmpty()).distinctBy { it.stream.url } }
    val f = remember(merged, rules, showAll) { if (showAll) Filtered(merged, 0, false, emptyList()) else applyTagRules(merged, rules) }
    val addons = remember(f.list) { f.list.map { it.addonName }.distinct() }
    var addon by remember(target.videoId) { mutableStateOf<String?>(null) }
    // distinctBy: the list below uses this as its key, and LazyColumn crashes on duplicate keys.
    val shown = remember(f.list, addon) {
        (if (addon == null) f.list else f.list.filter { it.addonName == addon }).distinctBy { it.addonName + (it.stream.url ?: "") + (it.stream.name ?: "") }
    }
    var resume by remember(resumeMs) { mutableStateOf(resumeMs > 0) }
    val fusion = isOn(v("fusionBadges"))
    val showAddon = isOn(v("showAddonName"))

    Box(Modifier.fillMaxSize().background(Color(0xFF07070A))) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(330.dp)) {
                    AsyncImage(target.image ?: meta.background ?: meta.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.35f to Color.Transparent, 1f to Color(0xFF07070A))))
                    Row(Modifier.statusBarsPadding().padding(top = 8.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        meta.imdbRating?.takeIf { it.isNotBlank() }?.let { RatingChip("IMDb", it, Color(0xFFF5C518)) }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 6.dp)) {
                        target.epLabel?.let { Text(it, color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                        Text(target.epTitle ?: meta.name, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (target.epTitle != null) Text(meta.name.uppercase(), fontSize = 14.sp, color = Color(0xFFB6BDCC), letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (resumeMs > 0) item {
                Row(Modifier.padding(start = 18.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Resume from ${fmtTime(resumeMs)}", resume) { resume = true }
                    Pill("Start over", !resume) { resume = false }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1A1A1F)).clickable { reload++ }, Alignment.Center) {
                            Icon(Icons.Default.Refresh, "Refresh", Modifier.size(22.dp))
                        }
                    }
                    item { Pill("All", addon == null, big = true) { addon = null } }
                    items(addons) { a -> Pill(a, addon == a, big = true) { addon = a } }
                }
            }
            if (f.hidden > 0 && !f.forcedEmpty) item {
                Text("${f.hidden} hidden by your stream filters", Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), fontSize = 13.sp, color = Color(0xFF8A94A8))
            }
            when {
                f.forcedEmpty -> item {
                    EmptyBox(Icons.Default.FilterAlt, "No ${f.forcedLabels.joinToString(" / ")} streams for this title.", "Show all streams") { showAll = true }
                }
                shown.isEmpty() && done -> item {
                    EmptyBox(Icons.Default.CloudOff, t("noStreams"), "Try again") { reload++ }
                }
                else -> {
                    items(shown, key = { it.addonName + (it.stream.url ?: "") + (it.stream.name ?: "") }) { s ->
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                            StreamCard(s, fusion, showAddon, pack) { onPick(s, f.list, if (resume) resumeMs else 0L) }
                        }
                    }
                    if (!done) items(if (shown.isEmpty()) 3 else 1) { Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { SkeletonCard() } }
                }
            }
        }
        Box(
            Modifier.statusBarsPadding().padding(12.dp).size(46.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = onBack),
            Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp)) }
    }
}

@Composable
private fun RatingChip(label: String, value: String, c: Color) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xB3000000)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.clip(RoundedCornerShape(4.dp)).background(c).padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Pill(text: String, on: Boolean, big: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(24.dp)).background(if (on) Accent else Color(0xFF1A1A1F)).clickable(onClick = onClick)
            .padding(horizontal = if (big) 20.dp else 18.dp, vertical = if (big) 12.dp else 11.dp),
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) OnAccent else Color.White, maxLines = 1,
    )
}

@Composable
private fun EmptyBox(icon: androidx.compose.ui.graphics.vector.ImageVector, msg: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(42.dp), tint = Color(0xFF8A94A8))
        Text(msg, Modifier.padding(top = 12.dp), fontSize = 15.sp, color = Color(0xFFC9CDD6), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(action, Modifier.padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(Accent).clickable(onClick = onAction)
            .padding(horizontal = 22.dp, vertical = 11.dp), color = OnAccent, fontWeight = FontWeight.SemiBold)
    }
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
