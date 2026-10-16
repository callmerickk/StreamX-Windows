package com.example.streamnest

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A muted, control-less YouTube trailer that covers its box (the page crops the 16:9 video), using YouTube's official IFrame player
 * inside a WebView. The page identifies the app as its origin/referrer, which YouTube now requires
 * (otherwise embeds fail with error 152/153). Some uploaders block embedding; then onError is called.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerVideo(
    videoId: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    /** true: the whole 16:9 picture is shown (fitted to the width, no zoom) over a blurred [backdrop]. */
    fitWhole: Boolean = false,
    backdrop: String? = null,
    /** With [fitWhole]: zoom the fitted picture (1.35 = the phone hero cards: bigger, sides cropped a little). */
    zoom: Float = 1f,
    onPlaying: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val cbPlaying by rememberUpdatedState(onPlaying)
    val cbEnded by rememberUpdatedState(onEnded)
    val cbFailed by rememberUpdatedState(onError)
    // Sound on/off is read when the page is ready too (setting it before the page loaded was lost → no sound).
    val mutedNow by rememberUpdatedState(muted)
    val main = remember { Handler(Looper.getMainLooper()) }

    // The WebView is exactly the size of [modifier]; the page itself crops the 16:9 video to cover it (CSS), so no
    // Compose clip / oversize is needed. Default layer type: a hardware layer (or any clip / alpha / transform on the
    // WebView's ancestors) can make the video draw black while its sound plays.
    AndroidView(
        factory = { c ->
            // Tells the page its real size whenever the box changes, so the video always covers the whole card.
            object : WebView(c) {
                override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
                    super.onSizeChanged(w, h, ow, oh)
                    if (w > 0 && h > 0) runCatching { evaluateJavascript("fit()", null) }
                }
            }.apply {
                setBackgroundColor(AColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                isFocusableInTouchMode = false
                webChromeClient = object : WebChromeClient() {
                    // No grey "play" poster before the first frame.
                    override fun getDefaultVideoPoster(): Bitmap? = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        runCatching { view?.evaluateJavascript("fit();setMuted(${if (mutedNow) "true" else "false"})", null) }
                        // Nudge playback from the app side too, in case the page's own autoplay was blocked.
                        main.postDelayed({ runCatching { view?.evaluateJavascript("tryPlay()", null) } }, 1200)
                        main.postDelayed({ runCatching { view?.evaluateJavascript("tryPlay()", null) } }, 3000)
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun playing() { main.post { cbPlaying() } }
                    @JavascriptInterface fun ended() { main.post { cbEnded() } }
                    @JavascriptInterface fun error(code: Int) { main.post { cbFailed() } }
                }, "Android")
                val origin = "https://${c.packageName}"
                loadDataWithBaseURL("$origin/", trailerHtml(videoId, origin, fitWhole, backdrop, zoom, muted), "text/html", "utf-8", null)
                web = this
            }
        },
        modifier = modifier,
    )
    LaunchedEffect(muted, web) { web?.evaluateJavascript("setMuted(${if (muted) "true" else "false"})", null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { web?.stopLoading(); web?.loadUrl("about:blank"); web?.removeJavascriptInterface("Android"); web?.destroy() }
        }
    }
}

/** CSS-safe image URL (no quotes / brackets that could break out of url('…')). */
private fun cssUrl(u: String?): String = u?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    ?.filter { it !in "'\"()\\<> " } ?: ""

private fun trailerHtml(id: String, origin: String, fitWhole: Boolean, backdrop: String?, zoom: Float = 1f, muted: Boolean = true) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}#p{position:absolute;pointer-events:none;left:0;top:0;width:100%;height:100%;border:0}
#bg{position:absolute;left:-30px;top:-30px;right:-30px;bottom:-30px;background:#000 center/cover no-repeat;filter:blur(22px) brightness(0.45)}</style>
</head><body><div id="bg"></div><div id="p"></div>
<script>
// Cover the whole box with the 16:9 video (cropping the sides on tall cards). Sizes are measured in pixels here —
// CSS vh/vw can be read before the WebView has its final height, which left the video covering only the top strip.
var FIT_WHOLE=${if (fitWhole) "true" else "false"}, BG='${cssUrl(backdrop)}', ZOOM=${zoom.coerceIn(1f, 3f)};
if(BG){var b=document.getElementById('bg'); if(b) b.style.backgroundImage="url('"+BG+"')";}
function fit(){try{
  // Sized by layout rules only (no pixel measuring, no CSS transform): the YouTube frame is ZOOM times as wide as the
  // card and centred by its left edge; YouTube centres the 16:9 picture vertically inside it.
  var el=document.getElementById('p'); if(!el)return;
  el.style.position='absolute'; el.style.transform='none'; el.style.maxHeight='none'; el.style.top='0'; el.style.height='100%';
  if(FIT_WHOLE){
    el.style.width=(100*ZOOM)+'%'; el.style.left=(-(ZOOM-1)*50)+'%';
  } else {
    // Cover: as wide as needed so the 16:9 picture fills the whole height.
    var W=window.innerWidth||1, H=window.innerHeight||1, k=Math.max(1,(H*16/9)/W);
    el.style.width=(100*k)+'%'; el.style.left=(-(k-1)*50)+'%';
  }
  el.removeAttribute('width'); el.removeAttribute('height');
}catch(e){}}
window.addEventListener('resize',fit); fit();
var fitN=0, fitT=setInterval(function(){fit();if(++fitN>20)clearInterval(fitT);},250);
</script>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player=null,ready=false,tries=0,timer=null,wantMuted=${if (muted) "true" else "false"};
function tryPlay(){try{if(player&&ready){if(wantMuted)player.mute();if(player.getPlayerState()!=1)player.playVideo();}}catch(e){}}
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{videoId:'$id',
    playerVars:{autoplay:1,mute:1,controls:0,rel:0,playsinline:1,modestbranding:1,iv_load_policy:3,fs:0,disablekb:1,cc_load_policy:0,origin:'$origin'},
    events:{
      onReady:function(e){ready=true;fit();e.target.mute();e.target.playVideo();
        // Keep asking to play (muted) until it really plays — autoplay is sometimes blocked at first.
        timer=setInterval(function(){tries++;tryPlay();if(tries>24||(player&&player.getPlayerState&&player.getPlayerState()==1)){clearInterval(timer);}},400);},
      onStateChange:function(e){if(e.data==1){fit();Android.playing();if(!wantMuted){player.unMute();player.setVolume(100);}}if(e.data==0)Android.ended();},
      onError:function(e){Android.error(e.data);}
    }});
}
function setMuted(m){wantMuted=m;if(player&&player.mute){if(m)player.mute();else{player.unMute();player.setVolume(100);}}}
</script></body></html>
""".trimIndent()
