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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.mediarouter.app.MediaRouteButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DizillaApp"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun isAdVideoUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains("doubleclick") || lower.contains("googleads") ||
                   lower.contains("popads") || lower.contains("vignette") ||
                   lower.contains("adsystem") || lower.contains("adnxs") ||
                   lower.contains("trafficjunky") || lower.contains("/ads/")
        }
    }

    private var currentWebViewVersion: String = "Bilinmiyor"
    private var isTelevision: Boolean = false

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

    // TV Box & Remote Controls
    private lateinit var btnPointerToggle: ImageButton
    private lateinit var virtualCursor: ImageView
    private lateinit var txtTvModeNotice: TextView
    private var isPointerMode: Boolean = false
    private var cursorX: Float = -1f
    private var cursorY: Float = -1f
    private var cursorStep: Float = 30f
    private val hideNoticeHandler = Handler(Looper.getMainLooper())
    private val hideNoticeRunnable = Runnable { txtTvModeNotice.visibility = View.GONE }

    // Jetpack Compose Settings / Setup View
    private lateinit var composeSettingsView: ComposeView

    // Detected Video Cast Banner
    private lateinit var castStreamBanner: View
    private lateinit var txtCastStreamInfo: TextView
    private lateinit var btnCastNow: Button
    private lateinit var btnCloseCastBanner: ImageButton

    // Fullscreen and PiP
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var fullscreenOverlayControls: View
    private lateinit var btnExitFullscreen: ImageButton
    private lateinit var btnPipFullscreen: ImageButton
    private lateinit var btnLandscapeToggle: ImageButton
    private var isLandscapeMenuExpanded: Boolean = false
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

        // Support edge-to-edge display cutout in landscape so video/page fills the entire screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        // Read WebView Version
        val webViewPackage = WebViewCompat.getCurrentWebViewPackage(this)
        currentWebViewVersion = webViewPackage?.versionName ?: "Bilinmiyor"
        Log.i(TAG, "WebView Version: $currentWebViewVersion (Package: ${webViewPackage?.packageName})")

        // Detect TV Box / Leanback
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature("android.software.leanback")
        if (isTelevision) {
            Log.i(TAG, "Running on Android TV / Leanback box")
        }

        addressManager = SiteAddressManager(this)
        profileManager = ProfileManager(this)

        adBlocker = AdBlocker(this)
        adBlocker.siteAddressManager = addressManager

        initViews()
        applyOrientationMode(resources.configuration.orientation)
        setupCast()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        // Address Management: Load active URL or show setup screen if empty
        val activeUrl = addressManager.currentUrl
        if (activeUrl.isNullOrBlank()) {
            showSetupUrlScreen()
        } else {
            updateActiveProfile(activeUrl)
            webView.loadUrl(activeUrl)
        }
    }

    private fun initViews() {
        mainContentContainer = findViewById(R.id.mainContentContainer)
        topBar = findViewById(R.id.topBar)

        // Apply status bar & display cutout insets so top bar is never cut off in portrait
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

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

        // TV Remote Virtual Pointer & Notice
        btnPointerToggle = findViewById(R.id.btnPointerToggle)
        virtualCursor = findViewById(R.id.virtualCursor)
        txtTvModeNotice = findViewById(R.id.txtTvModeNotice)

        btnPointerToggle.setOnClickListener {
            togglePointerMode()
        }

        // Configure D-Pad focus chaining for TV remote
        btnAdBlockStatus.nextFocusDownId = R.id.webView
        btnPointerToggle.nextFocusDownId = R.id.webView
        mediaRouteButton.nextFocusDownId = R.id.webView
        btnRefresh.nextFocusDownId = R.id.webView
        btnSettings.nextFocusDownId = R.id.webView

        composeSettingsView = findViewById(R.id.composeSettingsView)

        castStreamBanner = findViewById(R.id.castStreamBanner)
        txtCastStreamInfo = findViewById(R.id.txtCastStreamInfo)
        btnCastNow = findViewById(R.id.btnCastNow)
        btnCloseCastBanner = findViewById(R.id.btnCloseCastBanner)

        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        fullscreenOverlayControls = findViewById(R.id.fullscreenOverlayControls)
        btnExitFullscreen = findViewById(R.id.btnExitFullscreen)
        btnPipFullscreen = findViewById(R.id.btnPipFullscreen)

        // Floating Landscape Menu Toggle Button
        btnLandscapeToggle = findViewById(R.id.btnLandscapeToggle)
        btnLandscapeToggle.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                isLandscapeMenuExpanded = !isLandscapeMenuExpanded
                if (isLandscapeMenuExpanded) {
                    topBar.visibility = View.VISIBLE
                    btnLandscapeToggle.setImageResource(R.drawable.ic_fullscreen_exit)
                    btnLandscapeToggle.contentDescription = "Üst Çubuğu Gizle"
                } else {
                    topBar.visibility = View.GONE
                    btnLandscapeToggle.setImageResource(R.drawable.ic_settings)
                    btnLandscapeToggle.contentDescription = "Üst Çubuğu Göster"
                }
            }
        }

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

        // Cast Stream Banner Actions
        btnCastNow.setOnClickListener {
            lastDetectedVideoUrl?.let { url ->
                val title = webView.title ?: lastDetectedVideoTitle
                castVideo(url, title)
            }
        }

        btnCloseCastBanner.setOnClickListener {
            castStreamBanner.visibility = View.GONE
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
                currentProfile = adBlocker.currentProfile,
                webViewVersion = currentWebViewVersion,
                onProfileUpdated = { updatedProfile ->
                    profileManager.saveProfile(updatedProfile)
                    adBlocker.currentProfile = updatedProfile
                    applyProfileSettings(updatedProfile)
                    webView.reload()
                },
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
                }
            )
        }
    }

    private fun updateActiveProfile(url: String) {
        val host = try {
            Uri.parse(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
        val profile = profileManager.getProfileForHost(host)
        adBlocker.currentProfile = profile
        txtDomain.text = if (host.isNotBlank()) host else "Adres Tanımsız"
        applyProfileSettings(profile)
    }

    private fun applyProfileSettings(profile: SiteProfile?) {
        val isDesktop = profile?.desktopMode == true || profile?.userAgentMode.equals("desktop", ignoreCase = true)
        if (isDesktop) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
        } else {
            val defaultUa = WebSettings.getDefaultUserAgent(this)
            val cleanUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
            webView.settings.userAgentString = cleanUa
        }
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        if (isTelevision) {
            webView.settings.textZoom = 115
        }
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

        // Use child scroll-up callback to cleanly prevent pull-to-refresh when webView is scrolled,
        // rather than toggling isEnabled mid-motion which causes "Got ACTION_MOVE event but don't have an active pointer id."
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            customView != null ||
            composeSettingsView.visibility == View.VISIBLE ||
            isPointerMode ||
            webView.scrollY > 0 ||
            webView.canScrollVertically(-1)
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
                isLandscapeMenuExpanded && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> {
                    isLandscapeMenuExpanded = false
                    applyOrientationMode(Configuration.ORIENTATION_LANDSCAPE)
                }
                webView.canGoBack() -> {
                    webView.goBack()
                }
                else -> {
                    finish()
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

        val defaultUa = WebSettings.getDefaultUserAgent(this)
        val cleanUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
        settings.userAgentString = cleanUa
        settings.setSupportMultipleWindows(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Allow third-party cookies for embedded video players and CDNs
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        settings.saveFormData = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                settings.safeBrowsingEnabled = false
            } catch (e: Exception) {
                // Ignored if unsupported
            }
        }

        // TV Box & Landscape display optimizations
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = if (isTelevision) 115 else 105
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // D-Pad focusability on TV
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        // Cosmetic script injection at document start if supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    adBlocker.getEffectiveCosmeticScript(),
                    setOf("*")
                )
                Log.d(TAG, "DocumentStartJavaScript injected successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register DocumentStartJavaScript", e)
            }
        }

        // Javascript Bridge for video stream & player discovery (SPA & Debug)
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoDetected(src: String?) {
                if (!src.isNullOrBlank() && !isAdVideoUrl(src)) {
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

                // Detect video stream URLs (ignoring ad and tracking videos)
                if ((uriString.contains(".m3u8") || uriString.contains(".mp4") ||
                     uriString.contains("/hls/") || uriString.contains("videoplayback") ||
                     uriString.contains(".mp4?") || uriString.contains(".m3u8?")) &&
                    !isAdVideoUrl(uriString)) {
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
                if (request.isForMainFrame == false) {
                    return false
                }

                // 1. Block if in active profile or global blocklist (FIRST, so ad domains are never white-listed)
                if (adBlocker.isBlocked(host) != null) {
                    Log.d("AdBlock", "Blocked navigation to blocked domain: $host")
                    return true
                }

                // 2. Google Sign-In Support (strictly accounts.google.com and oauth, not broad *.google.com)
                if (host == "accounts.google.com" || host == "oauth2.googleapis.com" || host.startsWith("accounts.youtube.")) {
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
                view?.evaluateJavascript(adBlocker.getEffectiveCosmeticScript(), null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefreshLayout.isRefreshing = false
                view?.evaluateJavascript(adBlocker.getEffectiveCosmeticScript(), null)
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
        if (isAdVideoUrl(url)) return
        if (lastDetectedVideoUrl == url && currentCastSession?.isConnected == true) {
            return
        }
        lastDetectedVideoUrl = url
        lastDetectedVideoTitle = webView.title ?: "Dizilla Video"
        txtCastStreamInfo.text = "Akış: ${Uri.parse(url).host ?: "Video"}"
        castStreamBanner.visibility = View.VISIBLE

        // Auto cast if Cast Session is actively connected
        currentCastSession?.let { session ->
            if (session.isConnected) {
                castVideo(url, lastDetectedVideoTitle)
            }
        }
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
        castStreamBanner.visibility = View.GONE
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
        btnLandscapeToggle.visibility = View.GONE

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
        applyOrientationMode(resources.configuration.orientation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationMode(newConfig.orientation)
    }

    private fun applyOrientationMode(orientation: Int) {
        if (customView != null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)) {
            return
        }

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape: Immersive fullscreen edge-to-edge
            setSystemBarsVisible(false)
            if (!isLandscapeMenuExpanded) {
                topBar.visibility = View.GONE
                btnLandscapeToggle.setImageResource(R.drawable.ic_settings)
                btnLandscapeToggle.contentDescription = "Üst Çubuğu Göster"
            } else {
                topBar.visibility = View.VISIBLE
                btnLandscapeToggle.setImageResource(R.drawable.ic_fullscreen_exit)
                btnLandscapeToggle.contentDescription = "Üst Çubuğu Gizle"
            }
            btnLandscapeToggle.visibility = View.VISIBLE
        } else {
            // Portrait: Standard layout with top header and visible system bars
            setSystemBarsVisible(true)
            topBar.visibility = View.VISIBLE
            btnLandscapeToggle.visibility = View.GONE
            isLandscapeMenuExpanded = false
        }
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
        if (customView != null) {
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
            btnLandscapeToggle.visibility = View.GONE
            setSystemBarsVisible(false)
        } else {
            if (customView != null) {
                fullscreenOverlayControls.visibility = View.VISIBLE
                setSystemBarsVisible(false)
            } else {
                applyOrientationMode(resources.configuration.orientation)
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
                connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
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

    private fun togglePointerMode(enabled: Boolean? = null) {
        isPointerMode = enabled ?: !isPointerMode
        if (isPointerMode) {
            virtualCursor.visibility = View.VISIBLE
            btnPointerToggle.setColorFilter(Color.parseColor("#06B6D4"))
            if (cursorX < 0 || cursorY < 0) {
                cursorX = (webView.width / 2f).coerceAtLeast(100f)
                cursorY = (webView.height / 2f).coerceAtLeast(100f)
            }
            updateCursorPosition(cursorX, cursorY)
            txtTvModeNotice.text = "Kumanda Fare Modu: Yön Tuşları (Hareket) • OK (Tıkla) • Mavi Tuş / Menü (Kapat)"
            txtTvModeNotice.visibility = View.VISIBLE
            hideNoticeHandler.removeCallbacks(hideNoticeRunnable)
            hideNoticeHandler.postDelayed(hideNoticeRunnable, 4000)
        } else {
            virtualCursor.visibility = View.GONE
            btnPointerToggle.clearColorFilter()
            txtTvModeNotice.text = "Normal Kaydırma Moduna Geçildi"
            txtTvModeNotice.visibility = View.VISIBLE
            hideNoticeHandler.removeCallbacks(hideNoticeRunnable)
            hideNoticeHandler.postDelayed(hideNoticeRunnable, 2500)
        }
    }

    private fun updateCursorPosition(x: Float, y: Float) {
        val maxX = (rootLayout.width - virtualCursor.width).coerceAtLeast(0).toFloat()
        val maxY = (rootLayout.height - virtualCursor.height).coerceAtLeast(0).toFloat()
        cursorX = x.coerceIn(0f, maxX)
        cursorY = y.coerceIn(0f, maxY)
        virtualCursor.x = cursorX
        virtualCursor.y = cursorY
    }

    private val rootLayout: View
        get() = findViewById(R.id.rootLayout)

    private fun simulateClickAtCursor() {
        val now = android.os.SystemClock.uptimeMillis()
        val location = IntArray(2)
        webView.getLocationOnScreen(location)

        val clickX = cursorX - location[0] + (virtualCursor.width / 4f)
        val clickY = cursorY - location[1] + (virtualCursor.height / 4f)

        val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, clickX, clickY, 0)
        val upEvent = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, clickX, clickY, 0)

        webView.dispatchTouchEvent(downEvent)
        webView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()

        // Visual click feedback
        virtualCursor.animate()
            .scaleX(0.75f)
            .scaleY(0.75f)
            .setDuration(70)
            .withEndAction {
                virtualCursor.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
            }
            .start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        // TV Remote Shortcuts (Menu & Color Keys)
        if (action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    if (composeSettingsView.visibility == View.VISIBLE) {
                        composeSettingsView.visibility = View.GONE
                        updateBadgeCount()
                    } else {
                        showSettingsScreen()
                    }
                    return true
                }
                KeyEvent.KEYCODE_PROG_BLUE,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_SEARCH,
                KeyEvent.KEYCODE_BOOKMARK -> {
                    togglePointerMode()
                    return true
                }
                KeyEvent.KEYCODE_PROG_RED -> {
                    webView.reload()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    triggerVideoPlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekVideo(10)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekVideo(-10)
                    return true
                }
            }
        }

        // 1. Virtual Mouse Pointer Mode
        if (isPointerMode && composeSettingsView.visibility != View.VISIBLE) {
            val step = if (event.repeatCount > 0) cursorStep * 1.8f else cursorStep
            if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_MULTIPLE) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        updateCursorPosition(cursorX, cursorY - step)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        updateCursorPosition(cursorX, cursorY + step)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        updateCursorPosition(cursorX - step, cursorY)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        updateCursorPosition(cursorX + step, cursorY)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        simulateClickAtCursor()
                        return true
                    }
                }
            }
        }

        // 2. Normal TV Navigation Mode (WebView scrolling & top bar focus)
        if (!isPointerMode && composeSettingsView.visibility != View.VISIBLE && customView == null) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (currentFocus == webView || currentFocus == null) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            webView.scrollBy(0, 180)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (webView.scrollY > 15) {
                                webView.scrollBy(0, -180)
                                return true
                            } else {
                                btnAdBlockStatus.requestFocus()
                                return true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            webView.scrollBy(120, 0)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            webView.scrollBy(-120, 0)
                            return true
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun triggerVideoPlayPause() {
        val js = """
            (function() {
                var v = document.querySelector('video');
                if (!v) {
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        try {
                            var iv = iframes[i].contentDocument.querySelector('video');
                            if (iv) { v = iv; break; }
                        } catch(e) {}
                    }
                }
                if (v) {
                    if (v.paused) v.play(); else v.pause();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun seekVideo(seconds: Int) {
        val js = """
            (function() {
                var v = document.querySelector('video');
                if (!v) {
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        try {
                            var iv = iframes[i].contentDocument.querySelector('video');
                            if (iv) { v = iv; break; }
                        } catch(e) {}
                    }
                }
                if (v) {
                    v.currentTime += ($seconds);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onDestroy() {
        hideNoticeHandler.removeCallbacks(hideNoticeRunnable)
        try {
            castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        } catch (e: Exception) {}
        webView.destroy()
        super.onDestroy()
    }
}
