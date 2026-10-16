package com.example.streamnest

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

internal val CardShape = RoundedCornerShape(24.dp)
internal val Muted = Color(0xFF9AA3B5)
internal val CardBg = Color(0xFF17171B)

// ───────────── Shared building blocks ─────────────

@Composable
internal fun PageHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onBack == null) 20.dp else 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp))
    }
}

@Composable
internal fun SectionLabel(text: String) =
    Text(text.uppercase(), fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, color = Muted,
        modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 10.dp))

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(CardShape).background(CardBg)
            .border(1.dp, Color(0x12FFFFFF), CardShape),
        content = content,
    )

@Composable
internal fun RowDivider(start: Dp = 82.dp) = HorizontalDivider(Modifier.padding(start = start), color = Color(0x12FFFFFF))

@Composable
internal fun IconBadge(icon: ImageVector, size: Dp = 48.dp, tint: Color = Color.White) =
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2A30)), Alignment.Center) {
        Icon(icon, null, tint = tint)
    }

@Composable
internal fun SettingRow(icon: ImageVector?, title: String, sub: String, enabled: Boolean = true, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBadge(icon); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                if (badge != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(badge, Modifier.border(1.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 2.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                }
            }
            Text(sub, fontSize = 14.sp, color = Muted)
        }
    }
}

@Composable
internal fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles (so the TV remote lands on the row, with its focus highlight); the switch just shows it.
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 13.sp, color = Muted)
        }
        Switch(checked, null)
    }
}

// ───────────── My Profile ─────────────

@Composable
private fun StatCard(value: String, label: String, sub: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(CardShape).background(Surface1).border(1.dp, Color(0x14FFFFFF), CardShape).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(value, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconBadge(icon, 44.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(sub, fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun StatRow(a: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = a)

@Composable
private fun MiniChip(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 13.sp, color = Color(0xFFD0D4E0))
    }
}

@Composable
private fun TagChip(text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(modifier.clip(shape).background(Accent.copy(alpha = 0.14f)).border(1.dp, Accent.copy(alpha = 0.5f), shape).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = Accent)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val myList by vm.myList.collectAsStateWithLifecycle()
    val doneKeys by vm.doneKeys.collectAsStateWithLifecycle()
    val doneMs by vm.doneMs.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val trackedMs = doneMs + progress.sumOf { it.positionMs }
    val tracked = if (trackedMs >= 3_600_000L) "${trackedMs / 3_600_000L} h" else "${trackedMs / 60_000L} m"
    val thisWeek = progress.count { it.updated > weekAgo }
    val upcoming = myList.count { (it.releaseInfo?.take(4)?.toIntOrNull() ?: 0) > thisYear }
    val series = myList.count { it.type == "series" } + progress.count { it.key.contains(":") }
    val movies = myList.count { it.type != "series" } + progress.count { !it.key.contains(":") }
    val frac = if (series + movies == 0) 0.5f else series.toFloat() / (series + movies)
    val balance = if (frac > 0.6f) "Series lover" else if (frac < 0.4f) "Movie buff" else "Balanced"
    val topGenre = myList.flatMap { it.genres.orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val tags = buildList {
        add(if (frac > 0.6f) "Series first" else if (frac < 0.4f) "Movies first" else "Balanced taste")
        if (progress.size >= 3) add("Binge-ready")
        if (thisWeek >= 5) add("Highly active")
        if (doneKeys.size >= 5) add("Completionist")
        if (topGenre != null) add("Loves $topGenre")
    }

    Column(Modifier.fillMaxSize().pageScroll().padding(bottom = 130.dp)) {
        PageHeader("My Profile", onBack)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(27.dp)).clickable { editing = true }, Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text("Edit profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(CardShape)
                .background(Brush.linearGradient(listOf(Palette.tint, Surface1)))
                .border(1.dp, Color(0x22FFFFFF), CardShape).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm, 92.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(vm.current.collectAsStateWithLifecycle().value?.name ?: "", fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Viewing history and library are stored on this device.", fontSize = 15.sp, color = Color(0xFFC9CEDB))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniChip("${progress.size}", "In progress", Modifier.weight(1f))
                MiniChip("${myList.size}", "Library", Modifier.weight(1f))
                MiniChip("$upcoming", "Upcoming", Modifier.weight(1f))
            }
        }
        SectionLabel("Overview")
        StatRow {
            StatCard("${progress.size}", "In progress", "Ready to resume", Icons.Default.PlayArrow, Modifier.weight(1f))
            StatCard("${doneKeys.size}", "Completed", "Watched to the end", Icons.Default.Favorite, Modifier.weight(1f))
        }
        StatRow {
            StatCard("${myList.size}", "Library", "Saved titles", Icons.Default.VideoLibrary, Modifier.weight(1f))
            StatCard(tracked, "Tracked time", "From playback progress", Icons.Default.AutoAwesome, Modifier.weight(1f))
        }
        StatRow {
            StatCard("$thisWeek", "This week", "Recent activity", Icons.Default.Notifications, Modifier.weight(1f))
            StatCard("$upcoming", "Upcoming", "In your library", Icons.Default.VideoLibrary, Modifier.weight(1f))
        }
        SectionLabel("Taste profile")
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CardShape).background(Surface1).border(1.dp, Color(0x14FFFFFF), CardShape).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.AutoAwesome, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Taste DNA", fontSize = 16.sp, color = Muted)
                    Text(balance, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Based on your library and what you watch.", fontSize = 14.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Text("Movie / series balance", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(balance, fontSize = 15.sp, color = Muted)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF4A4F5C))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.04f, 1f)).align(Alignment.CenterEnd).clip(RoundedCornerShape(7.dp)).background(Accent))
            }
            Row(Modifier.padding(top = 6.dp)) {
                Text("Movie", fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
                Text("Series", fontSize = 14.sp, color = Muted)
            }
            Spacer(Modifier.height(16.dp))
            tags.chunked(2).forEach { rowTags ->
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowTags.forEach { TagChip(it, Modifier.weight(1f)) }
                    if (rowTags.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (editing) vm.current.value?.let { ProfileEditor(vm, it) { editing = false } }
}

// ───────────── Account ─────────────

@Composable
internal fun AccountScreen(vm: AppViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().pageScroll().padding(bottom = 130.dp)) {
        PageHeader("Account", onBack)
        CloudAccountSection()
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SettingRow(Icons.Default.Delete, "Clear watch history", "Removes Continue Watching and your stats.") { confirm = true }
        }
    }
    if (confirm) PremiumAlert(
        onDismissRequest = { confirm = false },
        containerColor = Surface1,
        title = { Text("Clear watch history?") },
        text = { Text("Continue Watching, completed titles and tracked time will be reset. Your library stays.") },
        confirmButton = { TextButton({ vm.clearHistory(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

// ───────────── Developer's Corner: password gate ─────────────

@Composable
internal fun DevGate(vm: AppViewModel, onUnlocked: () -> Unit, onBack: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val glow = rememberInfiniteTransition(label = "glow")
    val pulse by glow.animateFloat(0.85f, 1.2f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val lockScale by animateFloatAsState(if (unlocking) 1.6f else 1f, tween(600), label = "lockScale")
    val formAlpha by animateFloatAsState(if (unlocking) 0f else 1f, tween(500), label = "formAlpha")

    LaunchedEffect(unlocking) { if (unlocking) { delay(900); onUnlocked() } }
    // Fingerprint instead of typing the password each time (the password still works).
    val gateCtx = androidx.compose.ui.platform.LocalContext.current
    val bio = remember { !BuildConfig.IS_TV && ProfileLock.devFingerprint && ProfileLock.biometricReady(gateCtx) }
    LaunchedEffect(Unit) { if (bio) ProfileLock.askBiometric(gateCtx, "Developer's Corner", "Use password") { unlocking = true } }

    fun attempt() {
        if (pw.isEmpty() || unlocking) return
        if (vm.checkDevPassword(pw)) {
            denied = false
            unlocking = true
        } else {
            denied = true
            pw = ""
            scope.launch {
                repeat(3) { shake.animateTo(18f, tween(45)); shake.animateTo(-18f, tween(45)) }
                shake.animateTo(0f, tween(45))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.tint, Bg, Color.Black)))) {
        IconButton(onBack, Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(170.dp), Alignment.Center) {
                Box(
                    Modifier.size(170.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.55f), Color.Transparent)), CircleShape),
                )
                Box(
                    Modifier.size(92.dp).graphicsLayer { scaleX = lockScale; scaleY = lockScale; alpha = if (unlocking) formAlpha else 1f }
                        .clip(CircleShape).background(Color(0xFF111827)).border(2.dp, Accent, CircleShape),
                    Alignment.Center,
                ) { Icon(if (unlocking) Icons.Default.LockOpen else Icons.Default.Lock, null, Modifier.size(44.dp), tint = Accent) }
            }
            Column(
                Modifier.graphicsLayer { alpha = formAlpha }.offset { IntOffset(shake.value.roundToInt(), 0) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Developer's Corner", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Restricted area. Enter the password to continue.", fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
                OutlinedTextField(
                    pw, { pw = it; denied = false }, Modifier.fillMaxWidth().tvOkKeyboard(), singleLine = true,
                    label = { Text("Password") }, shape = RoundedCornerShape(16.dp), isError = denied,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = tvKeyboard(KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)),
                    keyboardActions = KeyboardActions(onDone = { attempt() }),
                    trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password") } })
                AnimatedVisibility(denied) {
                    Text("You are not allowed in this space", color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(18.dp))
                Button({ attempt() }, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(26.dp)) {
                    Text("Unlock", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                if (bio) TextButton({ ProfileLock.askBiometric(gateCtx, "Developer's Corner", "Use password") { unlocking = true } }, Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Use fingerprint")
                }
            }
        }
    }
}

// ───────────── Developer's Corner: inside ─────────────

@Composable
internal fun DevCorner(vm: AppViewModel, onBack: () -> Unit) {
    val texts by vm.texts.collectAsStateWithLifecycle()
    val ver by vm.imgVersion.collectAsStateWithLifecycle()
    val logo = remember(ver) { vm.logoFile().takeIf { it.exists() } }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.setLogo(uri) }
    var resetN by remember { mutableIntStateOf(0) }

    var curPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newPw2 by remember { mutableStateOf("") }
    var pwMsg by remember { mutableStateOf<String?>(null) }
    var pwOk by remember { mutableStateOf(false) }

    var sourceTest by remember { mutableStateOf(false) }
    if (sourceTest) { SourceTestScreen(vm) { sourceTest = false }; return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { PageHeader("Developer's Corner", onBack) }

        item { SectionLabel("Streaming") }
        item {
            SettingsCard {
                SettingRow(Icons.Default.Speed, "Source Test", "Play every link of 2 movies and 2 series, rank the providers, share the ranking, download the log.") { sourceTest = true }
            }
        }

        // Fingerprint unlock for locked profiles (Profile editor › Profile lock).
        if (!BuildConfig.IS_TV) {
            item { SectionLabel("Fingerprint") }
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var fp by remember { mutableStateOf(ProfileLock.fingerprint) }
                var devFp by remember { mutableStateOf(ProfileLock.devFingerprint) }
                val ready = remember { ProfileLock.biometricReady(ctx) }
                SettingsCard {
                    Row(Modifier.fillMaxWidth().clickable(enabled = ready) { devFp = !devFp; ProfileLock.devFingerprint = devFp }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Open Developer's Corner with fingerprint", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("No more typing the password each time (it still works as a backup).", fontSize = 12.sp, color = Muted)
                        }
                        androidx.compose.material3.Switch(devFp && ready, null, enabled = ready)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    Row(Modifier.fillMaxWidth().clickable(enabled = ready) { fp = !fp; ProfileLock.fingerprint = fp }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fingerprint unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (ready) "Locked profiles open with your fingerprint (the PIN still works). Lock a profile in My Space › Edit profile."
                                else "No fingerprint set up on this phone (Android Settings › Security).", fontSize = 12.sp, color = Muted)
                        }
                        androidx.compose.material3.Switch(fp && ready, null, enabled = ready)
                    }
                }
            }
        }

        // Play timing log: every Play, step by step (tap → player → link → tracks → first picture).
        item { SectionLabel("Play timing log") }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val clip = androidx.compose.ui.platform.LocalClipboardManager.current
            var logs by remember { mutableStateOf(PlayTiming.all()) }
            SettingsCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Every time you press Play: how long each step took, and how much came from the saved video. Newest first (last 30).",
                        fontSize = 13.sp, color = Muted)
                    if (logs.isEmpty()) Text("Nothing yet — play something, then come back.", Modifier.padding(top = 10.dp), fontSize = 14.sp)
                    logs.take(10).forEach { l ->
                        Text(l, Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1D23)).padding(10.dp),
                            fontSize = 11.sp, color = Color(0xFFD5D8DF), lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton({ logs = PlayTiming.all() }) { Text("Refresh") }
                        if (logs.isNotEmpty()) {
                            TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(logs.joinToString("\n\n"))) }) { Text("Copy all") }
                            TextButton({
                                runCatching {
                                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, logs.joinToString("\n\n")), "Play timing log").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }) { Text("Share") }
                            TextButton({ PlayTiming.clear(); logs = emptyList() }) { Text("Clear", color = Color(0xFFFF6B6B)) }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Playback log") }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var lines by remember { mutableStateOf(PlaybackLog.all(ctx)) }
            SettingsCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Why links failed (newest first): title · addon · way tried · error · server answer. Send a line to fix a stubborn file.",
                        fontSize = 13.sp, color = Muted)
                    if (lines.isEmpty()) Text("Nothing failed yet.", Modifier.padding(top = 10.dp), fontSize = 14.sp)
                    lines.take(25).forEach { l ->
                        Text(l, Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1D23)).padding(10.dp),
                            fontSize = 12.sp, color = Color(0xFFD5D8DF), lineHeight = 16.sp)
                    }
                    if (lines.isNotEmpty()) Row(Modifier.padding(top = 10.dp)) {
                        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                        TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(lines.joinToString("\n"))); Snack.show("Playback log copied") }) { Text("Copy all") }
                        TextButton({ PlaybackLog.clear(ctx); lines = emptyList() }) { Text("Clear") }
                    }
                }
            }
        }

        item { SectionLabel("App identity") }
        item {
            SettingsCard {
                Column(Modifier.padding(20.dp)) {
                    key(resetN) {
                        var name by remember { mutableStateOf(texts["appName"] ?: Defaults["appName"] ?: "") }
                        OutlinedTextField(name, { name = it; vm.setText("appName", it) }, Modifier.fillMaxWidth().tvOkKeyboard(), singleLine = true, label = { Text("App name") }, shape = RoundedCornerShape(14.dp), keyboardOptions = tvKeyboard())
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF232B40)), Alignment.Center) {
                            if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Image, null, tint = Muted)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Button({ pickLogo.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Upload logo") }
                            if (logo != null) TextButton({ vm.clearLogo() }) { Text("Remove logo") }
                        }
                    }
                    Text(
                        "The name and logo update inside the app instantly. The icon and name under the app on your phone's home screen are fixed at build time (res/values/strings.xml and the launcher icon).",
                        fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }

        item { SectionLabel("Security") }
        item {
            SettingsCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Change password", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val kb = KeyboardOptions(keyboardType = KeyboardType.Password)
                    OutlinedTextField(curPw, { curPw = it }, Modifier.fillMaxWidth().tvOkKeyboard(), singleLine = true, label = { Text("Current password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = tvKeyboard(kb), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(newPw, { newPw = it }, Modifier.fillMaxWidth().tvOkKeyboard(), singleLine = true, label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = tvKeyboard(kb), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(newPw2, { newPw2 = it }, Modifier.fillMaxWidth().tvOkKeyboard(), singleLine = true, label = { Text("Repeat new password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = tvKeyboard(kb), shape = RoundedCornerShape(14.dp))
                    pwMsg?.let { Text(it, color = if (pwOk) Color(0xFF5BD68A) else Color(0xFFFF6B6B), fontSize = 14.sp) }
                    Button(
                        {
                            if (newPw.length < 6) {
                                pwOk = false; pwMsg = "New password must be at least 6 characters"
                            } else if (newPw != newPw2) {
                                pwOk = false; pwMsg = "The new passwords don't match"
                            } else if (!vm.changeDevPassword(curPw, newPw)) {
                                pwOk = false; pwMsg = "Current password is wrong"
                            } else {
                                pwOk = true; pwMsg = "Password changed"
                                curPw = ""; newPw = ""; newPw2 = ""
                            }
                        },
                        enabled = curPw.isNotEmpty() && newPw.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text("Update password") }
                }
            }
        }

        item {
            SectionLabel("Customize UI")
            Text("Edit any text, color or roundness. Changes apply instantly and are saved.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        }
        items(TextFields + StyleFields, key = { "f_" + it.key }) { f ->
            key(resetN) {
                var v by remember { mutableStateOf(texts[f.key] ?: Defaults[f.key] ?: "") }
                OutlinedTextField(v, { v = it; vm.setText(f.key, it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).tvOkKeyboard(), label = { Text(f.label) }, shape = RoundedCornerShape(14.dp), keyboardOptions = tvKeyboard())
            }
        }
        item {
            OutlinedButton({ vm.resetTexts(); resetN++ }, Modifier.padding(16.dp)) { Text("Reset to defaults") }
        }
    }
}

// ───────────── Library tab ─────────────

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (Meta) -> Unit) {
    val list by vm.myList.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PageHeader(t("navLibrary"), null)
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text("Nothing saved yet. Tap + on any title to add it here.", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(105.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { items(list, key = { it.type + it.id }) { m -> Poster(m, Modifier.fillMaxWidth(), onOpen) } }
        }
    }
}

// ───────────── Live TV tab (M3U playlist) ─────────────

@Composable
fun LiveTvScreen(vm: AppViewModel, onBack: () -> Unit, onPlay: (Screen.Player) -> Unit) {
    val channels by vm.channels.collectAsStateWithLifecycle()
    val saved by vm.tvUrl.collectAsStateWithLifecycle()
    val loading by vm.tvLoading.collectAsStateWithLifecycle()
    val error by vm.tvError.collectAsStateWithLifecycle()
    var url by remember(saved) { mutableStateOf(saved) }
    var q by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Live TV", onBack)
        if (channels.isEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Paste the link of an M3U playlist to watch live channels.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().tvOkKeyboard(), singleLine = true, placeholder = { Text("https://.../playlist.m3u") }, shape = RoundedCornerShape(14.dp), keyboardOptions = tvKeyboard())
                Spacer(Modifier.height(10.dp))
                Button({ vm.loadChannels(url) }, enabled = url.isNotBlank() && !loading, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Load channels")
                }
                error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        } else {
            val shown = remember(channels, q) { if (q.isBlank()) channels else channels.filter { it.name.contains(q, ignoreCase = true) } }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f).tvOkKeyboard(), singleLine = true, placeholder = { Text("Search channels") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(14.dp), keyboardOptions = tvKeyboard())
                IconButton({ vm.clearChannels() }) { Icon(Icons.Default.Delete, "Remove playlist") }
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp)) {
                items(shown, key = { it.url + it.name }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlay(Screen.Player(c.name, c.url, emptyList())) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Surface1), Alignment.Center) {
                            if (c.logo != null) AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                            else Icon(Icons.Default.LiveTv, null, tint = Muted)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            c.group?.let { Text(it, fontSize = 13.sp, color = Muted, maxLines = 1) }
                        }
                        Icon(Icons.Default.PlayCircle, null, tint = Accent)
                    }
                }
            }
        }
    }
}
