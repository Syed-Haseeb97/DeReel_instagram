package com.example.dmsonly.web

import android.webkit.WebView

/**
 * Keeps browser history useful while skipping Home-feed and general Reels destinations.
 */
object InstagramBackNavigation {
    fun goBack(webView: WebView): Boolean {
        if (InstagramRoutes.isInboxUrl(webView.url)) return false

        val history = webView.copyBackForwardList()
        val urls = if (history.currentIndex >= 0) {
            (0..history.currentIndex).map { history.getItemAtIndex(it).url }
        } else {
            emptyList()
        }
        val target = previousAllowedHistoryIndex(urls, history.currentIndex)
        if (target != null) {
            webView.goBackOrForward(target - history.currentIndex)
            return true
        }

        if (!InstagramRoutes.isAuthFlowUrl(webView.url)) {
            webView.loadUrl(InstagramRoutes.SAFE_BACK_URL)
            return true
        }

        return false
    }

    internal fun previousAllowedHistoryIndex(urls: List<String>, currentIndex: Int): Int? {
        if (currentIndex !in urls.indices) return null
        for (index in currentIndex - 1 downTo 0) {
            if (!InstagramRoutes.isBlocked(urls[index])) return index
        }
        return null
    }
}
