package com.greenaltambe.vela.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import com.greenaltambe.vela.playback.PlaybackRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Google sign-in in a WebView. When the flow reaches youtube.com signed in, the session cookies are read from the
 * WebView's CookieManager, validated through [AccountLogin.addFromJar] and stored, and then the WebView's own cookies
 * are cleared so the app and the WebView cannot diverge (a stale WebView session would rotate the stored cookies away).
 *
 * Google blocks some embedded WebViews; this one presents a mainstream mobile Chrome user agent. The WebView still adds
 * `X-Requested-With: <package>` itself, which cannot be removed with the public API. Whether Google accepts this on a
 * given account is verified on a device only, which is why "Import cookies.txt" stays available as the fallback.
 */
class WebLoginActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var web: WebView
    private var finishing = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CHROME_MOBILE_UA
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val host = android.net.Uri.parse(url).host.orEmpty()
                    if (!finishing && host.endsWith("youtube.com")) capture()
                }
            }
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else { setResult(Activity.RESULT_CANCELED); finish() } }
        })
        web.loadUrl(START_URL)
    }

    private fun capture() {
        val cm = CookieManager.getInstance()
        fun read(url: String, domain: String) = cm.getCookie(url).orEmpty().split("; ").mapNotNull { pair ->
            val i = pair.indexOf('='); if (i <= 0) null else Cookie(pair.substring(0, i), pair.substring(i + 1), domain)
        }
        val jar = CookieJar(read("https://www.youtube.com", ".youtube.com") + read("https://accounts.google.com", ".google.com"))
        if (!jar.looksSignedIn) return // still mid-flow (2FA, passkey, consent screens)
        finishing = true
        val login = PlaybackRuntime.get(this).login
        scope.launch {
            val result = login.addFromJar(jar)
            cm.removeAllCookies(null); cm.flush(); web.clearCache(true)
            setResult(if (result is LoginResult.Success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() { scope.cancel(); web.destroy(); super.onDestroy() }

    private companion object {
        const val START_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3D%252F"
        const val CHROME_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
