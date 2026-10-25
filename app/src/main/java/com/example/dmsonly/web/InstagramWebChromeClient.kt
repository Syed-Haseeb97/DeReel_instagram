package com.example.dmsonly.web

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView

class InstagramWebChromeClient(
    private val onProgress: (Int) -> Unit,
) : WebChromeClient() {
    var launchChooser: ((Intent) -> Unit)? = null
    private var pendingCallback: ValueCallback<Array<Uri>>? = null

    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

    override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = callback
        val launcher = launchChooser ?: run { cancelPending(); return true }
        return try { launcher(params.createIntent()); true }
        catch (_: ActivityNotFoundException) { cancelPending(); true }
    }

    fun deliverFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = pendingCallback ?: return
        pendingCallback = null
        callback.onReceiveValue(FileChooserParams.parseResult(resultCode, data))
    }

    private fun cancelPending() {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = null
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
}
