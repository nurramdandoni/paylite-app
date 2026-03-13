package com.paylite.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

// untuk akses camera
import android.Manifest
import android.content.BroadcastReceiver
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
import android.util.Log
import android.widget.Toast

//chaquopy
import com.chaquo.python.android.AndroidPlatform
var downloadID: Long = 0
class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
    // value callback
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var onComplete: BroadcastReceiver

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
        webView.settings.allowContentAccess = true
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebAppBridge(webView), "AndroidBridge")
        webView.loadUrl("https://account.paylite.co.id")
        webView.post {
            handleDeepLink(intent)
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->

            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)

            val request = android.app.DownloadManager.Request(Uri.parse(url))

            if (cookies != null) {
                request.addRequestHeader("cookie", cookies)
            }

            val mime = mimeType ?: "application/octet-stream"
            request.setMimeType(mime)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            )

            val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadID = dm.enqueue(request)

            Toast.makeText(
                this,
                "File sedang di-download.\nCek di Folder Download",
                Toast.LENGTH_LONG
            ).show()

        }
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

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }

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

        onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {

                val id = intent?.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)

                if (downloadID == id) {

                    Toast.makeText(
                        this@MainActivity,
                        "Download selesai. Buka di Folder Download",
                        Toast.LENGTH_LONG
                    ).show()

                    val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
                    val uri = dm.getUriForDownloadedFile(downloadID)

                    if (uri != null) {
                        val openIntent = Intent(Intent.ACTION_VIEW)
                        openIntent.setDataAndType(uri, "application/pdf")
                        openIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        startActivity(openIntent)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            onComplete,
            android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
                Toast.makeText(this, "DeepLink triggered with token : ${tokenApps}", Toast.LENGTH_SHORT).show()

                webView.post {

                    webView.loadUrl(
                        "https://account.paylite.co.id/api/mobile-login?tokenApps=$tokenApps"
                    )

                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onComplete)
    }
}


class WebAppBridge(private val webView: WebView) {
    @JavascriptInterface
    fun isApp(): Boolean {
        return true
    }

    @JavascriptInterface
    fun getVersion(): String {
        return "1.0.0"
    }
    @JavascriptInterface
    fun processImage(key_answer: String, imageBase64: String) {
        try {

            Log.d("C_BRIDGE_TEST", "Called with Key Answer : $key_answer and ImageBase64 : $imageBase64")

            val py = Python.getInstance()
            val module = py.getModule("processor")

            val result = module.callAttr("process_data_ljk", key_answer, imageBase64).toString()

            Log.d("C_BRIDGE_RESULT", result)

            webView.post {
                webView.evaluateJavascript(
                    "showResult('$result')",
                    null
                )
            }

        } catch (e: Exception) {
            Log.e("C_PYTHON_ERROR", Log.getStackTraceString(e))

            webView.post {
                webView.evaluateJavascript(
                    "alert('ERROR: ${e.message}')",
                    null
                )
            }
        }
    }
}



