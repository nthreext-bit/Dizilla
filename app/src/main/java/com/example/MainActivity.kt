package com.example

import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.mediarouter.app.MediaRouteButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DizillaApp"
        private const val MIN_WEBVIEW_MAJOR = 100
        private const val FALLBACK_CHROME_VERSION = "124.0.0.0"
    }

    // TV / kumanda
    private var tvMode = false
    private var cursorMode = false
    private var webViewMajor = 0
    private var chromeVersion = FALLBACK_CHROME_VERSION
    private var docStartHandler: ScriptHandler? = null
    private var lastBackPressAt = 0L
    private var centerDownAt = 0L
    private lateinit var cursorView: CursorView

    private lateinit var addressManager: SiteAddressManager
    private lateinit var profileManager: ProfileManager
    private lateinit var adBlocker: AdBlocker

    // UI references
    private lateinit var mainContentContainer: LinearLayout
    private lateinit var topBar: View
    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var pageProgressBar: ProgressBar
    private lateinit var errorContainer: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var txtDomain: TextView
    private lateinit var txtAdBlockBadge: TextView
    private lateinit var btnAdBlockStatus: View
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var mediaRouteButton: MediaRouteButton

    // Jetpack Compose Settings / Setup View
    private lateinit var composeSettingsView: ComposeView

    // Fullscreen and PiP
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var fullscreenOverlayControls: View
    private lateinit var btnExitFullscreen: ImageButton
    private lateinit var btnPipFullscreen: ImageButton
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Chromecast
    private var castContext: CastContext? = null
    private var currentCastSession: CastSession? = null
    private var lastDetectedVideoUrl: String? = null
    private var lastDetectedVideoTitle: String = "Dizilla Video"

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            currentCastSession = session
            lastDetectedVideoUrl?.let { url ->
                castVideo(url, lastDetectedVideoTitle)
            }
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            currentCastSession = session
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            currentCastSession = null
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ SplashScreen
        installSplashScreen()

        super.onCreate(savedInstanceState)

        addressManager = SiteAddressManager(this)
        tvMode = detectTv()
        Log.i("DeviceMode", "tvMode=$tvMode (setting=${addressManager.tvMode})")

        // WebView yüklenemezse (eksik/bozuk WebView paketi) çökmek yerine açıklayıcı ekran göster
        try {
            setContentView(R.layout.activity_main)
        } catch (t: Throwable) {
            Log.e(TAG, "WebView/layout could not be created", t)
            showFatalWebViewError(t)
            return
        }

        profileManager = ProfileManager(this)
        readWebViewInfo()

        adBlocker = AdBlocker(this)
        adBlocker.siteAddressManager = addressManager

        initViews()
        applyTvUi()
        setupCast()

        // Profil, WebView ayarlarından ve belge-başı script kaydından ÖNCE yüklenmeli
        val activeUrl = addressManager.currentUrl
        if (!activeUrl.isNullOrBlank()) {
            updateActiveProfile(activeUrl, reloadScript = false)
        }
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        // Address Management: Load active URL or show setup screen if empty
        if (activeUrl.isNullOrBlank()) {
            showSetupUrlScreen()
        } else {
            webView.loadUrl(activeUrl)
        }
    }

    private fun detectTv(): Boolean = when (addressManager.tvMode) {
        TvMode.ON -> true
        TvMode.OFF -> false
        TvMode.AUTO -> {
            val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        }
    }

    private fun showFatalWebViewError(t: Throwable) {
        val tv = TextView(this).apply {
            setBackgroundColor(0xFF111827.toInt())
            setTextColor(0xFFF3F4F6.toInt())
            textSize = 16f
            setPadding(48, 48, 48, 48)
            text = "WebView başlatılamadı.\n\n" +
                "Sistemde 'Android System WebView' veya Chromium WebView paketi eksik ya da bozuk olabilir. " +
                "Ayarlar > Uygulamalar bölümünden WebView'ı güncelleyin/etkinleştirin ve uygulamayı yeniden açın.\n\n" +
                "Teknik ayrıntı: ${t.javaClass.simpleName}: ${t.message}"
        }
        setContentView(tv)
    }

    /** WebView paket/sürüm bilgisini okur, loglar (etiket: WebViewInfo) ve eskiyse uyarır. */
    private fun readWebViewInfo() {
        try {
            val pkg = WebViewCompat.getCurrentWebViewPackage(this)
            val version = pkg?.versionName ?: ""
            Log.i("WebViewInfo", "package=${pkg?.packageName} version=$version sdk=${Build.VERSION.SDK_INT}")
            webViewMajor = Regex("""^(\d+)""").find(version)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (t: Throwable) {
            Log.w("WebViewInfo", "WebView package info unavailable: ${t.message}")
        }
        try {
            val defaultUa = WebSettings.getDefaultUserAgent(this)
            Regex("""Chrome/(\d+(?:\.\d+)*)""").find(defaultUa)?.groupValues?.get(1)?.let {
                chromeVersion = it
                if (webViewMajor == 0) {
                    webViewMajor = it.substringBefore('.').toIntOrNull() ?: 0
                }
            }
            Log.i("WebViewInfo", "defaultUA=$defaultUa")
        } catch (t: Throwable) {
            Log.w("WebViewInfo", "Default UA unavailable: ${t.message}")
        }
        if (webViewMajor in 1 until MIN_WEBVIEW_MAJOR) {
            Toast.makeText(
                this,
                "WebView eski (Chromium $webViewMajor). Site düzgün çalışmayabilir; WebView'ı güncelleyin.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** TV modunda: imleç açık, dokunmatik/telefon odaklı öğeler kapalı, yatay ekran. */
    private fun applyTvUi() {
        cursorView = findViewById(R.id.cursorView)
        if (tvMode) {
            cursorMode = true
            cursorView.visibility = View.VISIBLE
            cursorView.wake()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            swipeRefreshLayout.isEnabled = false
            mediaRouteButton.visibility = View.GONE
            btnPipFullscreen.visibility = View.GONE
        } else {
            cursorView.visibility = View.GONE
        }
    }

    /** Masaüstü mu mobil mi: ayar > site profili > cihaz türü (TV = masaüstü). */
    private fun useDesktopUa(): Boolean = when (addressManager.uaMode) {
        UaMode.DESKTOP -> true
        UaMode.MOBILE -> false
        UaMode.AUTO -> {
            val p = adBlocker.currentProfile
            when {
                p?.desktopMode == true || p?.userAgentMode.equals("desktop", ignoreCase = true) -> true
                p?.userAgentMode.equals("mobile", ignoreCase = true) && !tvMode -> false
                else -> tvMode
            }
        }
    }

    /** UA'daki Chrome sürümü cihazdaki gerçek WebView sürümünden üretilir (sabit eski sürüm yok). */
    private fun applyUserAgent() {
        val desktop = useDesktopUa()
        val ua = if (desktop) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
        } else {
            // Cihazın gerçek WebView UA'sı; "; wv" ve "Version/4.0" kaldırılarak WebView izi silinir
            try {
                WebSettings.getDefaultUserAgent(this).replace("; wv", "").replace("Version/4.0 ", "")
            } catch (t: Throwable) {
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
            }
        }
        val s = webView.settings
        s.userAgentString = ua
        s.useWideViewPort = desktop
        s.loadWithOverviewMode = desktop
        Log.i("WebViewInfo", "UA(desktop=$desktop)=$ua")
    }

    /** Kozmetik script'i belge başında enjekte eder; profil/adres değişince yeniden kaydeder. */
    private fun registerDocStartScript() {
        try {
            docStartHandler?.remove()
        } catch (_: Throwable) {}
        docStartHandler = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (!adBlocker.isEnabled) return
            try {
                docStartHandler = WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    adBlocker.getEffectiveCosmeticScript(),
                    setOf("*")
                )
                Log.d(TAG, "DocumentStartJavaScript registered.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register DocumentStartJavaScript", e)
            }
        } else {
            Log.w(TAG, "DOCUMENT_START_SCRIPT unsupported; falling back to onPageStarted injection.")
        }
    }

    // ---------------------------------------------------------------- Kumanda tuşları

    private fun runMediaCommand(cmd: String) {
        webView.evaluateJavascript(
            "(function(){try{if(window.__dzMedia){window.__dzMedia('$cmd');}}catch(e){}})();",
            null
        )
    }

    private fun toggleCursorMode() {
        cursorMode = !cursorMode
        if (cursorMode) cursorView.wake() else cursorView.hideNow()
        Toast.makeText(
            this,
            if (cursorMode) "İmleç modu: AÇIK" else "İmleç modu: KAPALI (odak/yön tuşları)",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun sendTouch(action: Int, x: Float, y: Float, downTime: Long) {
        val loc = IntArray(2)
        val wLoc = IntArray(2)
        cursorView.getLocationOnScreen(loc)
        webView.getLocationOnScreen(wLoc)
        val lx = x + (loc[0] - wLoc[0])
        val ly = y + (loc[1] - wLoc[1])
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, lx, ly, 0)
        ev.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun sendHover(x: Float, y: Float) {
        val loc = IntArray(2)
        val wLoc = IntArray(2)
        cursorView.getLocationOnScreen(loc)
        webView.getLocationOnScreen(wLoc)
        val ev = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_HOVER_MOVE,
            x + (loc[0] - wLoc[0]), y + (loc[1] - wLoc[1]), 0
        )
        ev.source = android.view.InputDevice.SOURCE_MOUSE
        webView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!tvMode || !::cursorView.isInitialized) return super.dispatchKeyEvent(event)

        // Ayarlar / kurulum ekranı (Compose) açıkken tuşları Compose'a bırak
        if (composeSettingsView.visibility == View.VISIBLE) return super.dispatchKeyEvent(event)

        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        // Medya tuşları: her zaman <video> öğesine iletilir
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (down) runMediaCommand("play"); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> { if (down) runMediaCommand("pause"); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { if (down) runMediaCommand("toggle"); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) runMediaCommand("seek+10"); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) runMediaCommand("seek-10"); return true }
            KeyEvent.KEYCODE_PROG_RED -> { if (down) runMediaCommand("fs"); return true }
        }

        // Menü / Info: imleç <-> odak modu
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO) {
            if (event.action == KeyEvent.ACTION_UP) toggleCursorMode()
            return true
        }

        // Tam ekran videoda: sol/sağ = ±10 sn, merkez = oynat/duraklat, yukarı/aşağı = ses
        if (customView != null) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) runMediaCommand("seek-10"); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) runMediaCommand("seek+10"); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (down && event.repeatCount == 0) runMediaCommand("toggle"); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (down) {
                        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            if (code == KeyEvent.KEYCODE_DPAD_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }

        if (!cursorMode) return super.dispatchKeyEvent(event)

        // Ekran klavyesi açıkken imleç duraklar (yön tuşları klavyeye gider)
        if (isKeyboardOpen()) return super.dispatchKeyEvent(event)

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!down) return true
                // İvmelenme: ilk ~300 ms yavaş, sonra kademeli hızlanır
                val density = resources.displayMetrics.density
                val step = (4f + (event.repeatCount.coerceAtMost(40) * 0.9f)) * density
                val dx = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -step; KeyEvent.KEYCODE_DPAD_RIGHT -> step; else -> 0f }
                val dy = when (code) { KeyEvent.KEYCODE_DPAD_UP -> -step; KeyEvent.KEYCODE_DPAD_DOWN -> step; else -> 0f }
                val (ox, oy) = cursorView.moveBy(dx, dy)
                // İmleç kenara dayanınca sayfa kayar
                if (ox != 0f || oy != 0f) webView.scrollBy(ox.toInt(), oy.toInt())
                sendHover(cursorView.cursorX, cursorView.cursorY)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                cursorView.wake()
                if (down && event.repeatCount == 0) {
                    centerDownAt = SystemClock.uptimeMillis()
                    sendTouch(MotionEvent.ACTION_DOWN, cursorView.cursorX, cursorView.cursorY, centerDownAt)
                } else if (event.action == KeyEvent.ACTION_UP) {
                    // Kısa basış = tıklama, uzun basış = WebView'ın uzun dokunma algısı
                    sendTouch(MotionEvent.ACTION_UP, cursorView.cursorX, cursorView.cursorY, centerDownAt)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isKeyboardOpen(): Boolean {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
        return insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun initViews() {
        mainContentContainer = findViewById(R.id.mainContentContainer)
        topBar = findViewById(R.id.topBar)
        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        pageProgressBar = findViewById(R.id.pageProgressBar)
        errorContainer = findViewById(R.id.errorContainer)
        btnRetry = findViewById(R.id.btnRetry)
        txtDomain = findViewById(R.id.txtDomain)
        txtAdBlockBadge = findViewById(R.id.txtAdBlockBadge)
        btnAdBlockStatus = findViewById(R.id.btnAdBlockStatus)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSettings = findViewById(R.id.btnSettings)
        mediaRouteButton = findViewById(R.id.mediaRouteButton)

        composeSettingsView = findViewById(R.id.composeSettingsView)

        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        fullscreenOverlayControls = findViewById(R.id.fullscreenOverlayControls)
        btnExitFullscreen = findViewById(R.id.btnExitFullscreen)
        btnPipFullscreen = findViewById(R.id.btnPipFullscreen)

        txtDomain.text = addressManager.currentHost ?: "Adres Ayarlanmadı"

        // Retry on connection error
        btnRetry.setOnClickListener {
            errorContainer.visibility = View.GONE
            webView.visibility = View.VISIBLE
            val target = addressManager.currentUrl
            if (!target.isNullOrBlank()) {
                webView.loadUrl(target)
            } else {
                showSetupUrlScreen()
            }
        }

        // Open Compose Settings UI from shield badge or settings icon
        btnAdBlockStatus.setOnClickListener {
            showSettingsScreen()
        }
        btnSettings.setOnClickListener {
            showSettingsScreen()
        }
        updateBadgeCount()

        // Top bar refresh
        btnRefresh.setOnClickListener {
            webView.reload()
        }

        // Fullscreen Controls
        btnExitFullscreen.setOnClickListener {
            customViewCallback?.onCustomViewHidden()
            onHideCustomView()
        }

        btnPipFullscreen.setOnClickListener {
            enterPipMode()
        }

    }

    private fun showSetupUrlScreen() {
        composeSettingsView.visibility = View.VISIBLE
        composeSettingsView.setContent {
            SetupUrlScreen(
                addressManager = addressManager,
                onUrlConfigured = { url ->
                    composeSettingsView.visibility = View.GONE
                    updateActiveProfile(url)
                    webView.loadUrl(url)
                }
            )
        }
    }

    private fun showSettingsScreen() {
        composeSettingsView.visibility = View.VISIBLE
        composeSettingsView.setContent {
            SettingsScreen(
                adBlocker = adBlocker,
                addressManager = addressManager,
                onDismiss = {
                    composeSettingsView.visibility = View.GONE
                    updateBadgeCount()
                },
                onUrlSelected = { url ->
                    composeSettingsView.visibility = View.GONE
                    updateActiveProfile(url)
                    webView.loadUrl(url)
                },
                onClearSiteData = {
                    clearSiteData()
                },
                onShareNetworkLogs = {
                    shareNetworkLogs()
                },
                onUpdateBlocklist = {
                    adBlocker.updateRemoteBlocklist()
                },
                onAdBlockEnabledChanged = {
                    registerDocStartScript()
                    webView.reload()
                }
            )
        }
    }

    private fun updateActiveProfile(url: String, reloadScript: Boolean = true) {
        val host = try {
            Uri.parse(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
        val profile = profileManager.getProfileForHost(host)
        adBlocker.currentProfile = profile
        txtDomain.text = if (host.isNotBlank()) host else "Adres Tanımsız"
        applyUserAgent()
        // Profil (ek CSS seçicileri) değiştiği için belge-başı script'i yeniden kaydet
        if (reloadScript) registerDocStartScript()
    }

    private fun clearSiteData() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Site verileri, çerezler ve önbellek temizlendi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing site data", e)
        }
    }

    private fun shareNetworkLogs() {
        val logs = NetworkLogger.exportLogsAsText()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Dizilla Ağ İstek Günlüğü (Son 500 İstek)")
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        startActivity(Intent.createChooser(sendIntent, "İstek Günlüğünü Paylaş"))
    }

    private fun setupCast() {
        // TV'nin kendisi ekran olduğu için Cast gereksiz; ayrıca Pi gibi cihazlarda Play Services yok
        if (tvMode) {
            mediaRouteButton.visibility = View.GONE
            return
        }
        try {
            castContext = CastContext.getSharedInstance(this)
            CastButtonFactory.setUpMediaRouteButton(applicationContext, mediaRouteButton)
            castContext?.sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        } catch (t: Throwable) {
            Log.w(TAG, "Cast SDK initialization skipped or unavailable: ${t.message}")
            try {
                mediaRouteButton.visibility = View.GONE
            } catch (_: Throwable) {}
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            0xFF06B6D4.toInt(),
            0xFF3B82F6.toInt(),
            0xFF8B5CF6.toInt()
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFF1F2937.toInt())

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        // Allow pull to refresh only when webView is at the top
        swipeRefreshLayout.isEnabled = !tvMode
        webView.viewTreeObserver.addOnScrollChangedListener {
            swipeRefreshLayout.isEnabled = (!tvMode && webView.scrollY == 0 && customView == null && composeSettingsView.visibility != View.VISIBLE)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                composeSettingsView.visibility == View.VISIBLE -> {
                    if (addressManager.currentUrl.isNullOrBlank()) {
                        finish()
                    } else {
                        composeSettingsView.visibility = View.GONE
                        updateBadgeCount()
                    }
                }
                customView != null -> {
                    customViewCallback?.onCustomViewHidden()
                    onHideCustomView()
                }
                webView.canGoBack() -> {
                    webView.goBack()
                }
                else -> {
                    // Son sayfada: çıkmak için 2 sn içinde tekrar basılmalı
                    val now = SystemClock.uptimeMillis()
                    if (now - lastBackPressAt < 2000) {
                        finish()
                    } else {
                        lastBackPressAt = now
                        Toast.makeText(this@MainActivity, "Çıkmak için tekrar basın", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        try {
            webView.setLayerType(View.LAYER_TYPE_NONE, null)
        } catch (_: Throwable) {}

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        applyUserAgent()
        settings.setSupportMultipleWindows(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Allow third-party cookies for embedded video players and CDNs
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        settings.saveFormData = false
        try {
            settings.safeBrowsingEnabled = false
        } catch (e: Exception) {
            // Ignored if unsupported
        }

        // Cosmetic script injection at document start if supported (fallback: onPageStarted)
        registerDocStartScript()

        // Javascript Bridge for video stream & player discovery (SPA & Debug)
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoDetected(src: String?) {
                if (!src.isNullOrBlank()) {
                    runOnUiThread { onVideoStreamFound(src) }
                }
            }

            @JavascriptInterface
            fun onPlayerInfoDiscovered(jsonData: String?) {
                if (!jsonData.isNullOrBlank()) {
                    Log.d("PlayerInfo", "================ PLAYER INFO DISCOVERED ================")
                    Log.d("PlayerInfo", jsonData)
                    Log.d("PlayerInfo", "=========================================================")
                }
            }
        }, "DizillaNative")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    pageProgressBar.visibility = View.VISIBLE
                    pageProgressBar.progress = newProgress
                } else {
                    pageProgressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // Prevent popup ad windows and redirects
                Log.d("AdBlock", "Blocked popup window request (isUserGesture=$isUserGesture)")
                return false
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                this@MainActivity.onShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                this@MainActivity.onHideCustomView()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return null
                val uriString = uri.toString().lowercase(Locale.ROOT)

                // Detect video stream URLs (engellenen/reklam host'larındaki medya yok sayılır)
                if ((uriString.contains(".m3u8") || uriString.contains(".mp4")) &&
                    request?.isForMainFrame != true &&
                    adBlocker.isBlocked(uri.host) == null) {
                    runOnUiThread {
                        onVideoStreamFound(uri.toString())
                    }
                }

                // Determine request type for discovery mode NetLog
                val headers = request?.requestHeaders
                val acceptHeader = headers?.get("Accept") ?: ""
                val isMainFrame = request?.isForMainFrame == true

                val requestType = when {
                    uriString.contains(".m3u8") || uriString.contains(".mp4") || uriString.contains("/hls/") || uriString.contains("videoplayback") -> "media"
                    uriString.contains(".js") || acceptHeader.contains("javascript") -> "script"
                    !isMainFrame && (uriString.contains("embed") || uriString.contains("player") || acceptHeader.contains("text/html")) -> "iframe"
                    headers?.get("X-Requested-With") != null || acceptHeader.contains("application/json") -> "xhr"
                    uriString.contains(".png") || uriString.contains(".jpg") || uriString.contains(".jpeg") || uriString.contains(".webp") || uriString.contains(".svg") -> "image"
                    uriString.contains(".css") -> "stylesheet"
                    isMainFrame -> "document"
                    else -> "other"
                }

                // AdBlocker network check with NetLog recording
                val blockedResponse = adBlocker.shouldIntercept(uri, requestType)
                if (blockedResponse != null) {
                    runOnUiThread {
                        updateBadgeCount()
                    }
                    return blockedResponse
                }

                // If not blocked, check subframes for X-Frame-Options to eliminate ERR_BLOCKED_BY_RESPONSE
                if (!isMainFrame && (acceptHeader.contains("text/html") || requestType == "iframe" ||
                    uriString.contains("embed") || uriString.contains("player") ||
                    uriString.contains("/e/") || uriString.contains("/w/"))) {
                    val unblocked = stripFrameBlockingHeaders(request)
                    if (unblocked != null) {
                        return unblocked
                    }
                }

                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                val host = uri.host?.lowercase(Locale.ROOT) ?: ""

                // Block external application intents and market links
                if (scheme != "http" && scheme != "https") {
                    Log.d("AdBlock", "Blocked non-web scheme navigation: $uri")
                    return true
                }

                // SUBFRAME / IFRAME CHECK: Never block iframe navigations in shouldOverrideUrlLoading!
                // Blocking subframes in shouldOverrideUrlLoading causes ERR_BLOCKED_BY_RESPONSE or player load failure.
                if (request.isForMainFrame == false) {
                    return false
                }

                // Engel listesi Google kontrolünden ÖNCE bakılır (adservice.google.com gibi reklam
                // adresleri *.google.com izniyle geçmesin)
                if (adBlocker.isBlocked(host) != null) {
                    Log.d("AdBlock", "Blocked navigation to blocked domain: $host")
                    return true
                }

                // Google Sign-In Support (accounts.google.com)
                if (host == "accounts.google.com" || host.endsWith(".google.com") || host.endsWith(".google.com.tr") ||
                    host == "accounts.youtube.com" || host.endsWith(".youtube.com")) {
                    Log.d("AdBlock", "Allowing Google Sign-In navigation: $uri")
                    return false
                }

                // Dynamic same-site policy or allowlist check
                if (addressManager.isSameSite(host) || adBlocker.isAllowed(host)) {
                    return false
                }

                // Top-level domain change detection (301/302 or brand migration)
                val currentBrand = addressManager.currentHost?.let { ProfileManager.extractBrandTag(it) } ?: ""
                val targetBrand = ProfileManager.extractBrandTag(host)

                val isBrandMatch = currentBrand.isNotEmpty() && currentBrand.equals(targetBrand, ignoreCase = true)
                val isFlexible = addressManager.sameSiteMode == SameSiteMode.FLEXIBLE

                if (isBrandMatch || isFlexible) {
                    promptDomainMigration(host, uri.toString())
                    return true
                }

                // Block rogue redirect ads to unrelated external domains
                Log.d("AdBlock", "Blocked rogue navigation redirect to: $uri")
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                errorContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE
                // Belge-başı script destekleniyorsa tekrar enjekte etmeye gerek yok
                if (docStartHandler == null) {
                    view?.evaluateJavascript(adBlocker.getEffectiveCosmeticScript(), null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefreshLayout.isRefreshing = false
                if (docStartHandler == null) {
                    view?.evaluateJavascript(adBlocker.getEffectiveCosmeticScript(), null)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    webView.visibility = View.GONE
                    errorContainer.visibility = View.VISIBLE
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun promptDomainMigration(newHost: String, targetUrl: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Site Adresi Değişti")
                .setMessage("Site adresi şuna yönlendirildi:\n$newHost\n\nAktif site adresi ve kurallar bu adrese güncellensin mi?\n\n(Not: Güvenlik gereği çerezler yeni adrese aktarılmayabilir, tekrar oturum açmanız gerekebilir.)")
                .setPositiveButton("Güncelle ve Aç") { _, _ ->
                    addressManager.currentUrl = targetUrl
                    updateActiveProfile(targetUrl)
                    webView.loadUrl(targetUrl)
                }
                .setNegativeButton("Engelle ve İptal Et", null)
                .show()
        }
    }

    private fun updateBadgeCount() {
        val count = adBlocker.blockedCount.get()
        txtAdBlockBadge.text = count.toString()
    }

    private fun onVideoStreamFound(url: String) {
        // Cast yoksa (TV/Pi veya Play Services yok) afişi hiç gösterme
        if (castContext == null || tvMode) return
        // Aynı adres tekrar tekrar bildirilirse yok say: yayın sürekli baştan yüklenmesin
        if (url == lastDetectedVideoUrl) return
        // Master playlist zaten yakalandıysa aynı sunucudaki alt playlist onu ezmesin
        val prev = lastDetectedVideoUrl
        if (prev != null && prev.contains(".m3u8") && url.contains(".m3u8") &&
            Uri.parse(prev).host == Uri.parse(url).host &&
            prev.contains("master", ignoreCase = true)) {
            return
        }
        lastDetectedVideoUrl = url
        lastDetectedVideoTitle = webView.title ?: "Dizilla Video"
    }

    fun castVideo(videoUrl: String, title: String) {
        val session = currentCastSession ?: castContext?.sessionManager?.currentCastSession
        if (session == null || !session.isConnected) {
            mediaRouteButton.performClick()
            Toast.makeText(this, "Yayınlamak için bir Chromecast cihazı seçin", Toast.LENGTH_SHORT).show()
            return
        }

        val remoteMediaClient = session.remoteMediaClient
        if (remoteMediaClient == null) {
            Toast.makeText(this, "Cast istemcisine bağlanılamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, "Dizilla Akışı")
        }

        val isHls = videoUrl.contains(".m3u8")
        val contentType = if (isHls) "application/x-mpegURL" else "video/mp4"

        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()

        remoteMediaClient.load(mediaInfo, MediaLoadOptions.Builder().setAutoplay(true).build())
        Toast.makeText(this, "TV'de oynatılıyor: $title", Toast.LENGTH_LONG).show()
    }

    // Fullscreen View Handlers
    private fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback

        mainContentContainer.visibility = View.GONE
        fullscreenContainer.addView(
            view,
            0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenOverlayControls.visibility = View.VISIBLE

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSystemBarsVisible(false)

        updatePiPParams()
    }

    private fun onHideCustomView() {
        if (customView == null) return

        fullscreenContainer.removeView(customView)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        fullscreenContainer.visibility = View.GONE
        mainContentContainer.visibility = View.VISIBLE

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSystemBarsVisible(true)
    }

    private fun setSystemBarsVisible(visible: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updatePiPParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(customView != null)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "PiP modu bu Android sürümünde desteklenmiyor", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // TV'de PiP kullanılmaz
        if (customView != null && !tvMode) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            fullscreenOverlayControls.visibility = View.GONE
            topBar.visibility = View.GONE
            setSystemBarsVisible(false)
        } else {
            if (customView != null) {
                fullscreenOverlayControls.visibility = View.VISIBLE
                setSystemBarsVisible(false)
            } else {
                topBar.visibility = View.VISIBLE
                setSystemBarsVisible(true)
            }
        }
    }

    private fun stripFrameBlockingHeaders(request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        val urlStr = uri.toString()
        val method = request.method ?: "GET"
        if (!method.equals("GET", ignoreCase = true)) return null

        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            // Forward request headers
            request.requestHeaders?.forEach { (key, value) ->
                try {
                    connection.setRequestProperty(key, value)
                } catch (_: Throwable) {}
            }

            // Ensure proper User-Agent
            if (connection.getRequestProperty("User-Agent") == null) {
                connection.setRequestProperty("User-Agent", webView.settings.userAgentString)
            }
            // Pass Referer if not set
            if (connection.getRequestProperty("Referer") == null) {
                addressManager.currentUrl?.let { current ->
                    connection.setRequestProperty("Referer", current)
                }
            }

            // Pass cookies from CookieManager
            val cookie = CookieManager.getInstance().getCookie(urlStr)
            if (!cookie.isNullOrBlank()) {
                connection.setRequestProperty("Cookie", cookie)
            }

            connection.connect()

            val statusCode = connection.responseCode
            val statusMessage = connection.responseMessage ?: "OK"

            // Save Set-Cookie headers back into CookieManager
            val headerFields = connection.headerFields
            headerFields["Set-Cookie"]?.forEach { c ->
                try {
                    CookieManager.getInstance().setCookie(urlStr, c)
                } catch (_: Throwable) {}
            }
            headerFields["set-cookie"]?.forEach { c ->
                try {
                    CookieManager.getInstance().setCookie(urlStr, c)
                } catch (_: Throwable) {}
            }

            // Strip restrictive frame-blocking headers
            val responseHeaders = mutableMapOf<String, String>()
            headerFields.forEach { (key, values) ->
                if (key != null && values != null && values.isNotEmpty()) {
                    val lowerKey = key.lowercase(Locale.ROOT)
                    if (lowerKey != "x-frame-options" &&
                        lowerKey != "content-security-policy" &&
                        lowerKey != "content-security-policy-report-only" &&
                        lowerKey != "cross-origin-resource-policy" &&
                        lowerKey != "cross-origin-embedder-policy"
                    ) {
                        responseHeaders[key] = values.joinToString(", ")
                    }
                }
            }
            // Allow embedding
            responseHeaders["Access-Control-Allow-Origin"] = "*"
            responseHeaders["Cross-Origin-Resource-Policy"] = "cross-origin"

            var mimeType = "text/html"
            var encoding = "utf-8"
            val contentType = connection.contentType
            if (contentType != null) {
                val parts = contentType.split(";").map { it.trim() }
                if (parts.isNotEmpty()) mimeType = parts[0]
                for (part in parts) {
                    if (part.lowercase(Locale.ROOT).startsWith("charset=")) {
                        encoding = part.substring(8).trim()
                    }
                }
            }

            val inputStream = if (statusCode in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
            }

            WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                if (statusMessage.isBlank()) "OK" else statusMessage,
                responseHeaders,
                inputStream
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to proxy frame headers for $urlStr: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        try {
            castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        } catch (e: Exception) {}
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
