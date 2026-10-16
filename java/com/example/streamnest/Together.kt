package com.example.streamnest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** What everyone in a watch party should be seeing. [rev] grows with every change; [by] = who changed it. */
@Serializable
data class RoomState(
    val rev: Long = 0, val by: String = "", val url: String = "", val key: String = "", val title: String = "",
    val image: String? = null, val type: String = "", val pos: Long = 0, val playing: Boolean = false, val host: String = "",
    @Transient val receivedAt: Long = 0,
)

@Serializable data class RoomMember(val name: String = "", val seen: Long = 0)
@Serializable data class RoomReact(val k: String = "", val by: String = "", val at: Long = 0)

/**
 * Watch together: a room (4-letter code) in the StreamX cloud. Play, pause and seeking by anyone happen for everyone;
 * the host's player is the clock (its position is shared every 3 s and players more than 2.5 s off jump to it).
 * Reactions float up on everyone's screen. Needs sign-in.
 */
object Together {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = "application/json".toMediaType()
    private var poll: Job? = null
    private var ticker: Job? = null
    private val seenReacts = HashSet<String>()
    private var joinedAt = 0L

    val code = MutableStateFlow<String?>(null)
    val remote = MutableStateFlow<RoomState?>(null)
    val members = MutableStateFlow<Map<String, RoomMember>>(emptyMap())
    val reacts = MutableSharedFlow<RoomReact>(extraBufferCapacity = 16)
    @Volatile var isHost = false
    /** True while the player applies a state that came from the room (so it isn't sent back). */
    @Volatile var applying = false
    @Volatile var myName = ""
    /** The open player: (position, playing, url, key, title, image, type). */
    @Volatile var provider: (() -> RoomState)? = null

    val me: String get() = Cloud.user.value?.uid ?: ""
    fun active() = code.value != null

    private fun base(c: String) = "${CloudConfig.DB_URL}/rooms/$c"

    private suspend fun call(method: String, path: String, body: String? = null): String? = withContext(Dispatchers.IO) {
        val t = Cloud.token() ?: return@withContext null
        runCatching {
            val b = Request.Builder().url("$path.json?auth=$t")
            when (method) {
                "PUT" -> b.put(body!!.toRequestBody(json))
                "POST" -> b.post(body!!.toRequestBody(json))
                "DELETE" -> b.delete()
                else -> b.get()
            }
            Api.http.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull()
    }

    /** Starts a room with what is playing now. Null = done, else why not. */
    suspend fun create(state: RoomState): String? {
        if (Cloud.user.value == null) return "Sign in first (Settings › Account & Cloud Sync)."
        if (!state.url.startsWith("http")) return "Downloaded videos can't be shared — play it from a source."
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(5) {
            val c = (1..4).map { alphabet.random() }.joinToString("")
            if (call("GET", "${base(c)}/state") == "null") {
                val st = state.copy(rev = System.currentTimeMillis(), by = me, host = me)
                call("PUT", "${base(c)}/state", Api.json.encodeToString(st))
                    ?: return "Couldn't create the room. Check the Firebase rules (rooms) and your connection."
                isHost = true
                start(c)
                return null
            }
        }
        return "Couldn't create a room, try again."
    }

    /** Joins a room. Returns its state (to open its video), or an error message. */
    suspend fun join(c0: String): Pair<RoomState?, String?> {
        if (Cloud.user.value == null) return null to "Sign in first (Settings › Account & Cloud Sync)."
        val c = c0.trim().uppercase()
        if (c.length != 4) return null to "Room codes have 4 letters."
        val body = call("GET", "${base(c)}/state") ?: return null to "Couldn't reach the room."
        if (body == "null") return null to "No room with code $c."
        val st = runCatching { Api.json.decodeFromString<RoomState>(body) }.getOrNull() ?: return null to "That room looks broken."
        isHost = st.host == me
        start(c)
        return st.copy(receivedAt = System.currentTimeMillis()) to null
    }

    private fun start(c: String) {
        leaveQuietly()
        code.value = c
        joinedAt = System.currentTimeMillis()
        seenReacts.clear()
        poll = io.launch {
            var n = 0
            while (isActive) {
                val body = call("GET", base(c))
                if (body == "null") { withContext(Dispatchers.Main) { Snack.show("The watch party ended") }; leave(); break }
                if (body != null) runCatching {
                    val o = Api.json.parseToJsonElement(body).jsonObject
                    o["state"]?.let { e ->
                        val st = Api.json.decodeFromJsonElement(RoomState.serializer(), e)
                        if (st.rev > (remote.value?.rev ?: 0)) remote.value = st.copy(receivedAt = System.currentTimeMillis())
                    }
                    (o["members"] as? JsonObject)?.let { m ->
                        members.value = m.mapValues { (_, v) -> runCatching { Api.json.decodeFromJsonElement(RoomMember.serializer(), v) }.getOrDefault(RoomMember()) }
                    }
                    (o["reacts"] as? JsonObject)?.forEach { (k, v) ->
                        if (seenReacts.add(k)) {
                            val r = runCatching { Api.json.decodeFromJsonElement(RoomReact.serializer(), v) }.getOrNull()
                            if (r != null && r.by != me && r.at >= joinedAt - 2000) reacts.tryEmit(r)
                        }
                    }
                }
                if (n++ % 7 == 0) call("PUT", "${base(c)}/members/$me", Api.json.encodeToString(RoomMember(myName.ifBlank { "Guest" }, System.currentTimeMillis())))
                delay(1500)
            }
        }
        // The host's player is the clock: its position goes out every 3 s while it plays.
        ticker = io.launch {
            while (isActive) {
                delay(3000)
                if (!isHost) continue
                val p = provider?.invoke() ?: continue
                if (!p.playing) continue
                // Someone else just changed something: let it settle first.
                val r = remote.value
                if (r != null && r.by != me && System.currentTimeMillis() - r.receivedAt < 4000) continue
                send(p)
            }
        }
    }

    /** This player changed something (play / pause / seek / new video): tell the room. */
    fun local(p: RoomState) {
        if (applying || !active()) return
        io.launch { send(p) }
    }

    private suspend fun send(p: RoomState) {
        val c = code.value ?: return
        if (!p.url.startsWith("http")) return
        val st = p.copy(rev = maxOf(System.currentTimeMillis(), (remote.value?.rev ?: 0) + 1), by = me, host = remote.value?.host?.ifBlank { null } ?: me)
        remote.value = st.copy(receivedAt = System.currentTimeMillis())
        call("PUT", "${base(c)}/state", Api.json.encodeToString(st))
    }

    fun react(kind: String) {
        val c = code.value ?: return
        val r = RoomReact(kind, me, System.currentTimeMillis())
        reacts.tryEmit(r)
        io.launch { call("POST", "${base(c)}/reacts", Api.json.encodeToString(r)) }
    }

    private fun leaveQuietly() { poll?.cancel(); ticker?.cancel(); poll = null; ticker = null }

    fun leave() {
        val c = code.value
        val host = isHost
        leaveQuietly()
        code.value = null; remote.value = null; members.value = emptyMap(); isHost = false
        if (c != null) io.launch { if (host) call("DELETE", base(c)) else call("DELETE", "${base(c)}/members/$me") }
    }
}

private val ReactIcons: Map<String, Pair<ImageVector, Color>> = mapOf(
    "love" to (Icons.Default.Favorite to Color(0xFFFF5A7A)),
    "star" to (Icons.Default.Star to Color(0xFFF5B942)),
    "laugh" to (Icons.Default.SentimentVerySatisfied to Color(0xFF7DE2B8)),
    "fire" to (Icons.Default.LocalFireDepartment to Color(0xFFFF8A3D)),
)

/** Reactions floating up the right side of the player. */
@Composable
fun ReactionsOverlay(modifier: Modifier = Modifier) {
    val live = remember { mutableStateListOf<Pair<Long, RoomReact>>() }
    LaunchedEffect(Unit) {
        var n = 0L
        Together.reacts.collect { r -> val id = n++; live.add(id to r); launch { delay(2200); live.removeAll { it.first == id } } }
    }
    Box(modifier) {
        live.forEach { (id, r) ->
            key(id) {
                val y = remember { Animatable(0f) }
                val x = remember { (id * 37 % 60).toFloat() }
                LaunchedEffect(Unit) { y.animateTo(1f, tween(2000)) }
                val (icon, tint) = ReactIcons[r.k] ?: (Icons.Default.Favorite to Color.White)
                Icon(icon, null, Modifier.align(Alignment.BottomEnd).padding(end = (24 + x).dp, bottom = 40.dp).size(34.dp)
                    .graphicsLayer { translationY = -y.value * 260.dp.toPx(); alpha = 1f - y.value * y.value; val s = 0.7f + 0.5f * minOf(1f, y.value * 4); scaleX = s; scaleY = s },
                    tint = tint)
            }
        }
    }
}

/** Player panel: start / join a room, the code, who's watching, reactions, leave. */
@Composable
fun TogetherPanel(current: () -> RoomState, onJoined: (RoomState) -> Unit) {
    val scope = rememberCoroutineScope()
    val code by Together.code.collectAsState()
    val mem by Together.members.collectAsState()
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        val c = code
        if (c == null) {
            Text("Watch with friends, in sync: pause, seek or skip — it happens for everyone.", fontSize = 13.sp, color = Color(0xFF9AA3B5))
            TextButton({
                busy = true
                scope.launch { msg = Together.create(current()); busy = false }
            }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text("Start a room with this video") }
            OutlinedTextField(input, { input = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(4) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true, label = { Text("Room code") }, shape = RoundedCornerShape(12.dp))
            TextButton({
                busy = true
                scope.launch {
                    val (st, err) = Together.join(input)
                    busy = false
                    msg = err
                    if (st != null) onJoined(st)
                }
            }, enabled = !busy && input.length == 4) { Text("Join") }
        } else {
            Text("Room code", fontSize = 12.sp, color = Color(0xFF9AA3B5))
            Text(c, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text("Friends join from their player (More › Together) or My Space › Watch together, with this code.", fontSize = 12.sp, color = Color(0xFF9AA3B5))
            val now = System.currentTimeMillis()
            val here = mem.filterValues { now - it.seen < 40_000 }
            Text("Watching now: ${here.size}", Modifier.padding(top = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            here.values.forEach { m -> Text("• ${m.name}", fontSize = 13.sp, color = Color(0xFFD5D9E2)) }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReactIcons.forEach { (k, v) ->
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).border(1.dp, Color(0x22FFFFFF), CircleShape).clickable { Together.react(k) }, Alignment.Center) {
                        Icon(v.first, k, Modifier.size(24.dp), tint = v.second)
                    }
                }
            }
            TextButton({ Together.leave() }, Modifier.padding(top = 10.dp)) { Text(if (Together.isHost) "End the room" else "Leave the room", color = Color(0xFFFF8A80)) }
        }
        msg?.let { Text(it, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = Color(0xFFFFB4A8)) }
    }
}

/** My Space › Watch together: join a room by its code and open its video. */
@Composable
fun JoinRoomDialog(onJoined: (RoomState) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    PremiumAlert(
        onDismissRequest = onDismiss,
        title = { Text("Watch together") },
        text = {
            Column {
                Text("Enter the 4-letter room code your friend sees in their player (More › Together).", fontSize = 13.sp, color = Color(0xFF9AA3B5))
                OutlinedTextField(input, { input = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(4) }, Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true, label = { Text("Room code") }, shape = RoundedCornerShape(12.dp))
                msg?.let { Text(it, Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Color(0xFFFFB4A8)) }
            }
        },
        confirmButton = {
            TextButton({
                busy = true
                scope.launch {
                    val (st, err) = Together.join(input)
                    busy = false; msg = err
                    if (st != null) onJoined(st)
                }
            }, enabled = !busy && input.length == 4) { Text("Join") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
