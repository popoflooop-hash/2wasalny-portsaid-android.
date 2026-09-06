package com.wasalny.portsaid

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var splashOverlay: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var credentialManager: CredentialManager

    // Web Client ID الصحيح الخاص بمشروع Firebase لهذا التطبيق
    private val WEB_CLIENT_ID = "508562005255-pntg0mj2fq5457kpairniveoq68vr4df.apps.googleusercontent.com"

    // الرابط المستقر المباشر للتطبيق
    private val APP_URL = "https://ais-pre-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app"

    private var isAppReadyReceived = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // إخفاء الـ ActionBar إن وجد ليعمل التطبيق ملء الشاشة
        supportActionBar?.hide()

        credentialManager = CredentialManager.create(this)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        splashOverlay = findViewById(R.id.splashOverlay)
        progressBar = findViewById(R.id.loadingProgress)

        // إبقاء شاشة التحميل ظاهرة في البداية لمنع ظهور أي شعارات خارجية
        splashOverlay.visibility = View.VISIBLE

        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        // تحميل الصفحة
        webView.loadUrl(APP_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // إعدادات الكاش لدعم العمل دون اتصال بالإنترنت
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // ربط واجهة الأندرويد مع الويب
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                // منح صلاحية الموقع الجغرافي لتطبيق الخرائط
                callback?.invoke(origin, true, false)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // الحفاظ على شاشة البداية ظاهرة لمنع وميض الشاشات الخارجية
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false

                // في حال لم ترسل صفحة الويب إشارة الجاهزية، يتم إخفاء شاشة البداية بعد ثانية واحدة بأمان
                view?.postDelayed({
                    hideSplashScreen()
                }, 1200)
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
                .setDuration(350)
                .withEndAction {
                    splashOverlay.visibility = View.GONE
                }
                .start()
        }
    }

    // بدء عملية تسجيل الدخول بجوجل عبر Credential Manager بنقرة واحدة
    fun launchGoogleSignIn() {
        lifecycleScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false) // يسمح باختيار أي حساب مسجل على الهاتف
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = withContext(Dispatchers.IO) {
                    credentialManager.getCredential(
                        request = request,
                        context = this@MainActivity
                    )
                }

                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val token = googleIdToken.idToken

                    // تمرير التوكن مباشرة لدالة الويب لتسجيل الدخول الفوري
                    passTokenToWeb(token)
                }
            } catch (e: GetCredentialException) {
                // المستخدم ألغى الاختيار أو لم تكتمل العملية
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun passTokenToWeb(token: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                """
                if (window.handleGoogleToken) {
                    window.handleGoogleToken('$token');
                } else {
                    window._pendingGoogleToken = '$token';
                }
                """.trimIndent(), null
            )
        }
    }

    // الكائن المتاح لصفحة الويب لاستدعاء مزايا الأندرويد
    inner class WebAppInterface {

        @JavascriptInterface
        fun onAppReady() {
            runOnUiThread {
                isAppReadyReceived = true
                hideSplashScreen()
            }
        }

        @JavascriptInterface
        fun requestGoogleSignIn() {
            runOnUiThread {
                launchGoogleSignIn()
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
