package com.wasalny.portsaid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.Window
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
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
                <p>يحتاج تطبيق وصلني بورسعيد للاتصال بالشبكة لتحديث الرادار والرحلات.</p>
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

        // تفعيل النوافذ المتعددة والمنبثقة للحفاظ على تجربة تسجيل الدخول
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // تعديل الـ User-Agent ليبدو كـ Chrome نقي دون سمات webview
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

                // فتح روابط الاتصال والواتساب في التطبيقات المخصصة لها
                if (url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("mailto:")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                // منع فتح متصفح خارجي لأي رابط داخل التطبيق أو روابط المصادقة
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // فتح نوافذ تسجيل دخول جوجل داخل نافذة منبثقة داخل التطبيق نفسه دون الخروج لكروم
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val dialog = Dialog(this@MainActivity)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                val popupWebView = WebView(this@MainActivity)
                popupWebView.settings.javaScriptEnabled = true
                popupWebView.settings.domStorageEnabled = true
                popupWebView.settings.userAgentString = settings.userAgentString

                val cookieManagerPopup = CookieManager.getInstance()
                cookieManagerPopup.setAcceptCookie(true)
                cookieManagerPopup.setAcceptThirdPartyCookies(popupWebView, true)

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val popupUrl = request?.url?.toString() ?: return false
                        if (popupUrl.contains("ais-dev-pvgpazyr7qqyc4cetwc52r") || popupUrl.contains("run.app")) {
                            dialog.dismiss()
                            webView.loadUrl(popupUrl)
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url?.contains("ais-dev-pvgpazyr7qqyc4cetwc52r") == true || url?.contains("run.app") == true) {
                            dialog.dismiss()
                            webView.loadUrl(url)
                        }
                    }
                }

                dialog.setContentView(popupWebView)
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                dialog.show()

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
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
