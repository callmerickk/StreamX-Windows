package com.example.streamnest

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

/** A FragmentActivity (still a ComponentActivity) so the fingerprint prompt can be shown. */
class MainActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        // App icon picked with "Later": apply it now that the app was fully closed and opened again.
        if (!started) { started = true; AppIcons.applyPendingOnColdStart(this) }
        // Image loader that can also play GIFs and show video thumbnails (for profile pictures).
        Coil.setImageLoader(
            ImageLoader.Builder(this).components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }.okHttpClient { Api.http }.crossfade(!BuildConfig.IS_TV).allowRgb565(BuildConfig.IS_TV).build()
        )
        WidgetOpen.take(intent)
        enableEdgeToEdge()
        // StreamX TV (tv flavor) gets the remote-friendly TV interface; the phone app keeps its own.
        setContent {
            StreamNestTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    if (BuildConfig.IS_TV) TvApp() else App()
                    UpdatePrompt()
                    ProfileLockDialog()
                    // Source Test running: the screen stays awake; afterwards your normal screen timeout applies again.
                    val testing by SourceTester.running.collectAsState()
                    val view = androidx.compose.ui.platform.LocalView.current
                    DisposableEffect(testing) { view.keepScreenOn = testing; onDispose { view.keepScreenOn = false } }
                    CrashNotice()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        WidgetOpen.take(intent)
    }

    /** Going Home while a video plays: keep watching in a small window (Playback › Picture-in-picture). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!BuildConfig.IS_TV && PipState.playerOpen && PipState.playing && PipState.supported(this) &&
            AutoPip.on(this)) PipState.enter(this)
    }

    companion object {
        /** false only on the first Activity of a fresh process (a "cold start"). */
        private var started = false
    }
}

/** "Picture-in-picture when I go Home" (Help & Settings › Playback), on by default. */
object AutoPip {
    fun on(ctx: android.content.Context): Boolean =
        ctx.getSharedPreferences("player_misc", android.content.Context.MODE_PRIVATE).getBoolean("autoPip", true)
    fun set(ctx: android.content.Context, v: Boolean) =
        ctx.getSharedPreferences("player_misc", android.content.Context.MODE_PRIVATE).edit().putBoolean("autoPip", v).apply()
}
