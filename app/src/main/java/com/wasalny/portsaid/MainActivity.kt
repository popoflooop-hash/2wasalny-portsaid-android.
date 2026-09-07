package com.wasalny.portsaid

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var splashOverlay: FrameLayout
    private lateinit var progressBar: ProgressBar

    private val APP_URL = "https://ais-pre-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // إخفاء الشريط العلوي لتشغيل التطبيق شاشة كاملة
        supportActionBar?.hide()

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        splashOverlay = findViewById(R.id.splashOverlay)
        progressBar = findViewById(R.id.loadingProgress)

        splashOverlay.visibility = View.VISIBLE

        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        handleIntent(intent)

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // استقبال العودة من نافذة جوجل بعد المصادقة الناجحة (Deep Link)
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null) {
            if (uri.scheme == "wasalny" && uri.host == "auth") {
                // إعادة تحميل الصفحة لتطبيق الكوكيز والجلسة
                webView.evaluateJavascript("window.location.reload();", null)
            } else if (uri.scheme == "https" && uri.host == "ais-pre-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app") {
                webView.loadUrl(uri.toString())
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setGeolocationEnabled(true)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // تفعيل دعم النوافذ المنبثقة لمصادقة OAuth
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // تفعيل الكوكيز الرسمية والطرف الثالث للمصادقة
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            // التقاط النوافذ المنبثقة لمصادقة Google وتحويلها تلقائياً لـ Chrome Custom Tabs الآمنة
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val tempWebView = WebView(this@MainActivity)
                tempWebView.settings.javaScriptEnabled = true
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        subView: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val popupUrl = request?.url?.toString() ?: return false
                        openSecureCustomTab(popupUrl)
                        return true
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // إذا كان الرابط يخص مصادقة جوجل أو فايربيز، يتم فتحه في نافذة كروم الآمنة
                if (url.contains("accounts.google.com") || 
                    url.contains("firebaseapp.com/__/auth/handler") ||
                    url.contains("google.com/accounts")) {
                    openSecureCustomTab(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                view?.postDelayed({
                    hideSplashScreen()
                }, 600)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                swipeRefresh.isRefreshing = false
            }
        }
    }

    // فتح شاشة المصادقة عبر Chrome Custom Tabs المعتمدة قانونياً ورسمياً من Google Play
    fun openSecureCustomTab(url: String) {
        runOnUiThread {
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .setToolbarColor(ContextCompat.getColor(this, android.R.color.white))
                    .build()

                customTabsIntent.launchUrl(this, Uri.parse(url))
            } catch (e: Exception) {
                // في حال عدم توفر متصفح كروم، الفتح عبر المتصفح الافتراضي للجهاز
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(android.R.color.holo_green_dark)
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun hideSplashScreen() {
        if (splashOverlay.visibility == View.VISIBLE) {
            splashOverlay.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    splashOverlay.visibility = View.GONE
                }
                .start()
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onAppReady() {
            runOnUiThread {
                hideSplashScreen()
            }
        }

        // استدعاء Chrome Custom Tabs فوراً عند النقر على زر جوجل في تطبيق الويب
        @JavascriptInterface
        fun requestGoogleSignIn() {
            runOnUiThread {
                // فتح صفحة التوجيه الرسمية المباشرة لـ Google Auth
                val googleAuthUrl = "$APP_URL/#/login?mode=cct"
                openSecureCustomTab(googleAuthUrl)
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
