package com.greenaltambe.vela.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.greenaltambe.vela.playback.PlaybackRuntime

/**
 * Shows YouTube's captcha page, lets the viewer solve it, and on "Done" keeps the `GOOGLE_ABUSE_EXEMPTION` cookie the page set so anonymous
 * requests can carry it (only to YouTube hosts, for a few hours). Nothing else from the WebView is read or kept. (Compile-only: never run by the agent.)
 */
class CaptchaActivity : Activity() {
    private val runtime by lazy { PlaybackRuntime.get(this) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") } ?: run { runtime.captcha.dismiss(); finish(); return }
        val web = WebView(this).apply { settings.javaScriptEnabled = true; webViewClient = WebViewClient(); loadUrl(url) }
        val done = Button(this).apply { text = "Done"; setOnClickListener { finishWithCookie(url) } }
        val cancel = Button(this).apply { text = "Cancel"; setOnClickListener { runtime.captcha.dismiss(); finish() } }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setBackgroundColor(Color.WHITE); addView(cancel); addView(done) }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(bar); addView(FrameLayout(this@CaptchaActivity).apply { addView(web) }, LinearLayout.LayoutParams(-1, 0, 1f)) })
    }

    private fun finishWithCookie(url: String) {
        val cookies = listOf(url, "https://www.youtube.com/", "https://www.google.com/").mapNotNull { CookieManager.getInstance().getCookie(it) }.joinToString("; ")
        val value = cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("GOOGLE_ABUSE_EXEMPTION=") }?.substringAfter('=')
        if (value != null) { runtime.exemption.save(value); runtime.captcha.solved() } else runtime.captcha.dismiss()
        finish()
    }

    override fun onBackPressed() { runtime.captcha.dismiss(); super.onBackPressed() }

    companion object { const val EXTRA_URL = "url" }
}
