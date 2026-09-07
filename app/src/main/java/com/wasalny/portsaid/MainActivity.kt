package com.wasalny.portsaid

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var splashOverlay: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var credentialManager: CredentialManager

    private val WEB_CLIENT_ID = "508562005255-pntg0mj2fq5457kpairniveoq68vr4df.apps.googleusercontent.com"
    private val APP_URL = "https://ais-pre-pvgpazyr7qqyc4cetwc52r-283597327008.europe-west1.run.app"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()
        credentialManager = CredentialManager.create(this)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        splashOverlay = findViewById(R.id.splashOverlay)
        progressBar = findViewById(R.id.loadingProgress)

        splashOverlay.visibility = View.VISIBLE

        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        webView.loadUrl(APP_URL)
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

        // إزالة تعريف WebView المقيد ليتعرف جوجل على الحسابات بسهولة
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = defaultUserAgent.replace("; wv", "").replace("Version/4.0 ", "")

        // تفعيل ملفات تعريف الارتباط والكوكيز
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
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                view?.postDelayed({
                    hideSplashScreen()
                }, 1000)
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
                .setDuration(300)
                .withEndAction {
                    splashOverlay.visibility = View.GONE
                }
                .start()
        }
    }

    // استدعاء نافذة جوجل الرسمية لاختيار الحساب
    fun launchGoogleSignIn() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )

                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    passTokenToWeb(googleIdToken.idToken)
                }
            } catch (e: Exception) {
                // إذا لم يتم الاختيار أو حدث تعذر في الأندرويد، نطلب من الويب المتابعة دون تجميد
                runOnUiThread {
                    webView.evaluateJavascript("window.onNativeAuthFailed && window.onNativeAuthFailed();", null)
                }
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

    inner class WebAppInterface {
        @JavascriptInterface
        fun onAppReady() {
            runOnUiThread {
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
