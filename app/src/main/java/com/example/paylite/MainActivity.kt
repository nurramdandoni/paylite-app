package com.example.paylite

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

// untuk akses camera
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

//untuk webview bisa upload gambar
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.net.Uri
import android.content.Intent
import android.webkit.JavascriptInterface
import com.chaquo.python.Python
import android.util.Base64
import android.util.Log

//chaquopy
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
    // value callback
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContentView(R.layout.activity_main)
        handleDeepLink(intent)

        webView = findViewById(R.id.webView)

//        webView.webViewClient = WebViewClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {

                val url = request?.url.toString()

                if (url.startsWith("https://accounts.google.com")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                return false
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.settings.allowFileAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.settings.javaScriptCanOpenWindowsAutomatically = true

        webView.addJavascriptInterface(WebAppBridge(webView), "AndroidBridge")
        webView.loadUrl("https://account.paylite.co.id")
//        webView.loadUrl("file:///android_asset/test.html")


        // akses kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        }
        // file
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {

                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                startActivityForResult(intent!!, 200)
                return true
            }

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Log.d("WEBVIEW_CONSOLE", message.message())
                return true
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200) {
            filePathCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            filePathCallback = null
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        Log.d("DEEP","test")
        if (intent == null) return

        Log.d("DEEP_LINK", "Action: ${intent.action}")
        Log.d("DEEP_LINK", "Data: ${intent.data}")
        Log.d("DEEP_LINK", "Extras: ${intent.extras}")

        val data = intent.data

        // 1️⃣ Cek jika data ada
        if (data != null) {

            val tokenApps = data.getQueryParameter("tokenApps")

            if (!tokenApps.isNullOrEmpty()) {

                Log.d("DEEP_LINK_TOKEN", tokenApps)

                webView.post {
                    webView.evaluateJavascript(
                        "window.onTokenReceived('$tokenApps');",
                        null
                    )
                }
            }
        }
    }

//    private fun handleDeepLink(intent: Intent?) {
//        Log.d("DEEP_LINK", "Intent: $intent")
//        Log.d("DEEP_LINK", "Data: ${intent?.data}")
//        val data = intent?.data
//
//        if (data != null && data.scheme == "paylite") {
//
//            val tokenApps = data.getQueryParameter("tokenApps")
//
//            if (tokenApps != null) {
//                Log.d("DEEP_LINK_TOKEN_APPS", tokenApps)
//
//                // Kirim ke WebView
//                webView.post {
//                    webView.evaluateJavascript(
//                        "window.onTokenReceived && window.onTokenReceived('$tokenApps');",
//                        null
//                    )
//                }
//            }
//        }
//    }
}

class WebAppBridge(private val webView: WebView) {

//    @JavascriptInterface
//    fun processText(text: String) {
//
//        val py = Python.getInstance()
//        val module = py.getModule("processor")
//
//        val result = module.callAttr("process_text", text).toString()
//
//        webView.post {
//            webView.evaluateJavascript(
//                "showResult('$result')",
//                null
//            )
//        }
//    }

    @JavascriptInterface
    fun processText(text: String) {
        try {

            Log.d("C_BRIDGE_TEST", "Called with: $text")

            val py = Python.getInstance()
            val module = py.getModule("processor")

            val result = module.callAttr("process_text", text).toString()

            webView.post {
                webView.evaluateJavascript(
                    "showResult('$result')",
                    null
                )
            }

        } catch (e: Exception) {
            Log.e("C_PYTHON_ERROR", "Error:", e)

            webView.post {
                webView.evaluateJavascript(
                    "alert('ERROR: ${e.message}')",
                    null
                )
            }
        }
    }
}



