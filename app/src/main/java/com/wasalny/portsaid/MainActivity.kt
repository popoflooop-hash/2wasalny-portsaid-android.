package com.wasalny.portsaid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // الرابط المباشر والحي لتطبيق وصلني بورسعيد
    private val appUrl = "https://ais-dev-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app"

    // معالج رفع صور توثيق الكباتن والسيارة والهوية (يفتح فقط عند ضغط المستخدم)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback != null) {
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    // طلب إذن الموقع الجغرافي فقط بدقة لعرض الخريطة ورادار الرحلات
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineLocationGranted) {
            Toast.makeText(this, "يرجى السماح بالموقع لعرض رادار وكباتن بورسعيد حولك", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        checkAndRequestLocationPermission()
        setupWebView()
        setupBackNavigation()

        // لون السحب للتحديث بلون التطبيق المميز
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
            webView.loadUrl(appUrl)
        } else {
            showOfflineScreen()
        }
    }

    private fun checkAndRequestLocationPermission() {
        val neededPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            requestLocationPermissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showOfflineScreen() {
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
                <p>يحتاج تطبيق وصلني بورسعيد للاتصال بالشبكة لتحديث الرادار والرحلات والتسجيل.</p>
                <button class="btn" onclick="location.reload()">إعادة المحاولة الآن</button>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null)
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

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // إخفاء سمة الـ WebView لضمان قبول جوجل للتعرف على الجلسات
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = defaultUserAgent.replace("; wv", "")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
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

                // 1. فتح روابط تسجيل دخول جوجل في Google Custom Tabs للتعرف على حسابات الهاتف فوراً
                if (url.contains("accounts.google.com") || url.contains("google.com/signin")) {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    customTabsIntent.launchUrl(this@MainActivity, Uri.parse(url))
                    return true
                }

                // 2. الروابط الخارجية الأخرى (واتساب، الاتصال الهاتفي)
                if (url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("mailto:")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // تفعيل إذن الموقع الجغرافي للخرائط تلقائياً
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            // تفعيل اختيار الصور عند الحاجة فقط دون طلب إذن مسبق
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
