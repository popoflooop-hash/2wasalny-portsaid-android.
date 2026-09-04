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
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var customLoadingOverlay: FrameLayout
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // الرابط المباشر لتطبيق وصلني بورسعيد
    private val appUrl = "https://ais-dev-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app"

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    // معالج إذن الموقع (يتم تفعيله فقط عندما يطلبه المستخدم من داخل التطبيق)
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
            Toast.makeText(this, "يرجى السماح بالموقع لتحديد مكانك على الخريطة", Toast.LENGTH_SHORT).show()
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

        createCustomLoadingOverlay()
        setupWebView()
        setupBackNavigation()

        // ملاحظة هامة: تم إلغاء طلب إذن الموقع من هنا تماماً حتى لا يزعج المستخدم عند الفتح

        swipeRefreshLayout.setColorSchemeColors(getColor(R.color.primary_lime))
        swipeRefreshLayout.setOnRefreshListener {
            if (isNetworkAvailable()) {
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

    // واجهة تواصل آمنة بين أزرار الـ HTML وكود الأندرويد الأصلي لإعادة المحاولة الحقيقية
    inner class AndroidBridge {
        @JavascriptInterface
        fun retryConnection() {
            runOnUiThread {
                if (isNetworkAvailable()) {
                    showLoading(true)
                    webView.loadUrl(appUrl)
                } else {
                    Toast.makeText(this@MainActivity, "لا زال الهاتف غير متصل بالإنترنت", Toast.LENGTH_SHORT).show()
                    showOfflineScreen()
                }
            }
        }
    }

    // شاشة تحميل بورسعيد الفخمة لمنع ظهور أي شعارات خارجية
    private fun createCustomLoadingOverlay() {
        customLoadingOverlay = FrameLayout(this).apply {
            setBackgroundColor(0xFF0F172A.toInt()) // لون بورسعيد الكحلي الليلي
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val innerLayout = FrameLayout(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER
                )
            }

            val progressBar = ProgressBar(this@MainActivity).apply {
                indeterminateDrawable?.setTint(0xFF9EF01A.toInt()) // الأخضر الليموني
            }

            val text = TextView(this@MainActivity).apply {
                text = "وصلني بورسعيد..."
                setTextColor(0xFFF8FAFC.toInt())
                textSize = 16f
                setPadding(0, 140, 0, 0)
                gravity = android.view.Gravity.CENTER
            }

            innerLayout.addView(progressBar)
            innerLayout.addView(text)
            addView(innerLayout)
        }

        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        rootLayout.addView(customLoadingOverlay)
    }

    private fun showLoading(show: Boolean) {
        customLoadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showOfflineScreen() {
        showLoading(false)
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
                    .icon {
                        font-size: 64px;
                        margin-bottom: 16px;
                    }
                    h1 {
                        font-size: 22px;
                        margin: 0 0 10px 0;
                        color: #ffffff;
                    }
                    p {
                        font-size: 15px;
                        color: #94a3b8;
                        margin: 0 0 28px 0;
                        line-height: 1.6;
                    }
                    .btn {
                        background: #9ef01a;
                        color: #0f172a;
                        font-weight: bold;
                        border: none;
                        padding: 14px 28px;
                        border-radius: 9999px;
                        font-size: 16px;
                        cursor: pointer;
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

        // ربط نافذة أندرويد للتواصل مع كود صفحة الأوفلاين
        webView.addJavascriptInterface(AndroidBridge(), "AndroidApp")

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = defaultUserAgent.replace("; wv", "")

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

                // عند الضغط على تسجيل الدخول بجوجل: فتح نافذة مخصصة بلون التطبيق للتعرف على حسابات الهاتف تلقائياً
                if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                    val colorScheme = CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(0xFF0F172A.toInt())
                        .build()

                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setDefaultColorSchemeParams(colorScheme)
                        .setShowTitle(false) // إخفاء العنوان لمنع ظهور شكل المتصفح
                        .setUrlBarHidingEnabled(true) // إخفاء شريط الروابط لملء الشاشة
                        .build()

                    customTabsIntent.launchUrl(this@MainActivity, Uri.parse(url))
                    return true
                }

                // الروابط الخارجية (واتساب، هاتف)
                if (url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("mailto:")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // طلب إذن الموقع الجغرافي فقط عند احتياج الخريطة أو طلب الرحلة
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
