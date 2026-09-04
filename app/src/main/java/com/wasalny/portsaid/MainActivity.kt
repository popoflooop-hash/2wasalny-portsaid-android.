package com.wasalny.portsaid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var customLoadingOverlay: FrameLayout
    private lateinit var customToastView: TextView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Client ID الخاص بجوجل لمشروع تطبيق وصلني بورسعيد
    private val googleClientId = "283597327008-pvgpazyr7qqyc4cetwc52r.apps.googleusercontent.com"

    private val appUrl = "https://ais-dev-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app"
    private val handler = Handler(Looper.getMainLooper())

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            geolocationCallback?.invoke(geolocationOrigin, true, false)
        } else {
            geolocationCallback?.invoke(geolocationOrigin, false, false)
            showCustomNotification("يرجى السماح بالموقع لتحديد مكانك على الخريطة")
        }
        geolocationCallback = null
        geolocationOrigin = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        createCustomViews()
        setupWebView()
        setupBackNavigation()

        swipeRefreshLayout.setColorSchemeColors(getColor(R.color.primary_lime))
        swipeRefreshLayout.setOnRefreshListener {
            if (isNetworkAvailable()) {
                showLoading(true)
                webView.loadUrl(appUrl)
            } else {
                showOfflineScreen()
                swipeRefreshLayout.isRefreshing = false
            }
        }

        if (isNetworkAvailable()) {
            showLoading(true)
            webView.loadUrl(appUrl)
        } else {
            showOfflineScreen()
        }
    }

    // جسر التواصل بين JavaScript والـ Android
    inner class AndroidBridge {
        @JavascriptInterface
        fun retryConnection() {
            runOnUiThread {
                if (isNetworkAvailable()) {
                    showLoading(true)
                    webView.loadUrl(appUrl)
                } else {
                    showCustomNotification("لا زال الهاتف غير متصل بالإنترنت")
                    showOfflineScreen()
                }
            }
        }

        @JavascriptInterface
        fun triggerGoogleSignIn() {
            runOnUiThread {
                launchNativeGoogleSignIn()
            }
        }
    }

    // استدعاء نافذة جوجل الرسمية بضغطة واحدة (One-Tap Bottom Sheet)
    private fun launchNativeGoogleSignIn() {
        val credentialManager = CredentialManager.create(this)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(googleClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(request = request, context = this@MainActivity)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    
                    // إرسال التوكن للـ WebView لإكمال الدخول مباشرة بدون متصفح خارجي
                    webView.evaluateJavascript("javascript:handleGoogleToken('$idToken');", null)
                }
            } catch (e: GetCredentialException) {
                Log.e("GoogleSignIn", "فشل تسجيل الدخول: ${e.message}")
            }
        }
    }

    private fun createCustomViews() {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content)

        customLoadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val innerLayout = FrameLayout(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }

            val progressBar = ProgressBar(this@MainActivity).apply {
                indeterminateDrawable?.setTint(0xFF9EF01A.toInt())
            }

            val text = TextView(this@MainActivity).apply {
                text = "وصلني بورسعيد\nجاري الاتصال بالرادار..."
                setTextColor(0xFFF8FAFC.toInt())
                textSize = 15f
                setPadding(0, 150, 0, 0)
                gravity = Gravity.CENTER
            }

            innerLayout.addView(progressBar)
            innerLayout.addView(text)
            addView(innerLayout)
        }

        customToastView = TextView(this).apply {
            setBackgroundColor(0xEE1E293B.toInt())
            setTextColor(0xFFF8FAFC.toInt())
            textSize = 14f
            setPadding(40, 24, 40, 24)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 100
            }
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.dialog_holo_dark_frame)
        }

        rootLayout.addView(customLoadingOverlay)
        rootLayout.addView(customToastView)
    }

    private fun showCustomNotification(message: String) {
        customToastView.text = message
        customToastView.alpha = 0f
        customToastView.visibility = View.VISIBLE
        customToastView.animate().alpha(1f).setDuration(250).start()

        handler.postDelayed({
            customToastView.animate().alpha(0f).setDuration(250).withEndAction {
                customToastView.visibility = View.GONE
            }.start()
        }, 2500)
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            handler.removeCallbacksAndMessages(null)
            customLoadingOverlay.visibility = View.VISIBLE
        } else {
            handler.postDelayed({
                customLoadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        customLoadingOverlay.visibility = View.GONE
                        customLoadingOverlay.alpha = 1f
                    }
            }, 1800)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showOfflineScreen() {
        handler.removeCallbacksAndMessages(null)
        customLoadingOverlay.visibility = View.GONE
        val offlineHtml = """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background: #0f172a;
                        color: #f8fafc;
                        font-family: system-ui, -apple-system, sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        text-align: center;
                        box-sizing: border-box;
                        padding: 24px;
                    }
                    .icon { font-size: 64px; margin-bottom: 16px; }
                    h1 { font-size: 22px; margin: 0 0 10px 0; color: #ffffff; }
                    p { font-size: 15px; color: #94a3b8; margin: 0 0 28px 0; line-height: 1.6; }
                    .btn {
                        background: #9ef01a; color: #0f172a; font-weight: bold;
                        border: none; padding: 14px 28px; border-radius: 9999px;
                        font-size: 16px; cursor: pointer;
                        box-shadow: 0 10px 15px -3px rgba(158, 240, 26, 0.3);
                    }
                </style>
            </head>
            <body>
                <div class="icon">📡</div>
                <h1>لا يوجد اتصال بالإنترنت</h1>
                <p>يحتاج تطبيق وصلني بورسعيد للاتصال بالشبكة لتحديث الرادار والرحلات.</p>
                <button class="btn" onclick="AndroidApp.retryConnection()">إعادة المحاولة الآن</button>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://local.app", offlineHtml, "text/html", "UTF-8", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.addJavascriptInterface(AndroidBridge(), "AndroidApp")

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.startsWith("https://local.app") != true) {
                    showLoading(true)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                showLoading(false)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showOfflineScreen()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // عند طلب تسجيل دخول بجوجل، يتم تشغيل نافذة أندرويد السفلية الرسمية مباشرة بدون متصفح علوي
                if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                    launchNativeGoogleSignIn()
                    return true
                }

                if (url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("mailto:")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val hasFine = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine) {
                    callback?.invoke(origin, true, false)
                } else {
                    geolocationCallback = callback
                    geolocationOrigin = origin
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                filePickerLauncher.launch(intent)
                return true
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }
}
