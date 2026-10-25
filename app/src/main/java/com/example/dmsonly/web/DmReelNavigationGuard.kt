package com.example.dmsonly.web

import java.net.URI

/**
 * Keeps a DM-opened Reel pinned to its shortcode and separate from general
 * Reels/feed destinations. This state is intentionally in-memory only.
 */
internal class DmReelNavigationGuard {
    private var lastObservedUrl: String? = null
    private var dmReelContextActive = false
    private var lastDmReelUrl: String? = null
    private var dmOriginUrl: String? = null

    val isDmReelPinned: Boolean
        get() = dmReelContextActive && lastDmReelUrl != null

    fun pinnedReelUrl(): String? = lastDmReelUrl.takeIf { isDmReelPinned }
    fun originatingDmUrl(): String? = dmOriginUrl.takeIf { isDmReelPinned }

    fun exitToOriginatingDm(): String? {
        val origin = originatingDmUrl() ?: return null
        clearContext()
        lastObservedUrl = origin
        return origin
    }

    fun restoreFromHistory(urls: List<String>, currentIndex: Int) {
        if (currentIndex !in urls.indices || !isIndividualReelPath(pathOf(urls[currentIndex]))) return
        val currentReelId = reelIdOf(urls[currentIndex]) ?: return

        for (index in currentIndex - 1 downTo 0) {
            val previousPath = pathOf(urls[index]) ?: return
            if (isDirectPath(previousPath)) {
                dmReelContextActive = true
                dmOriginUrl = urls[index]
                lastDmReelUrl = urls[currentIndex]
                lastObservedUrl = urls[currentIndex]
                return
            }
            if (!isIndividualReelPath(previousPath) || reelIdOf(urls[index]) != currentReelId) return
        }
    }

    /**
     * Returns the originating DM when navigation attempts to leave a
     * DM-originated Reel or enter a different Reel.
     *
     * Callers may invoke this from multiple WebView navigation callbacks, so
     * blocked transitions deliberately do not replace lastObservedUrl.
     */
    fun redirectTarget(nextUrl: String?): String? {
        val target = nextUrl ?: return null
        val targetUri = runCatching { URI(target) }.getOrNull()
        if (targetUri == null || !isInstagramHost(targetUri.host)) {
            clearContext()
            lastObservedUrl = target
            return null
        }

        val targetPath = normalizePath(targetUri.path)
        val previousPath = pathOf(lastObservedUrl)

        if (isIndividualReelPath(targetPath)) {
            if (isDirectPath(previousPath)) {
                dmReelContextActive = true
                dmOriginUrl = lastObservedUrl
                lastDmReelUrl = target
            }
            if (!dmReelContextActive) {
                lastObservedUrl = target
                return InstagramRoutes.DM_INBOX
            }
            if (!sameIndividualReel(target, lastDmReelUrl)) {
                return dmOriginUrl ?: InstagramRoutes.DM_INBOX
            }
            lastDmReelUrl = target
            lastObservedUrl = target
            return null
        }

        if (isFeedEscapePath(targetPath)) {
            if (dmReelContextActive) {
                return dmOriginUrl ?: InstagramRoutes.DM_INBOX
            }
            // The native route policy handles blocked destinations.
            lastObservedUrl = target
            return null
        }

        clearContext()
        lastObservedUrl = target
        return null
    }

    private fun clearContext() {
        dmReelContextActive = false
        lastDmReelUrl = null
        dmOriginUrl = null
    }

    private fun pathOf(url: String?): String? =
        url?.let { runCatching { URI(it) }.getOrNull() }
            ?.takeIf { isInstagramHost(it.host) }
            ?.path
            ?.let(::normalizePath)

    private fun reelIdOf(url: String?): String? {
        val path = pathOf(url) ?: return null
        if (!isIndividualReelPath(path)) return null
        return path.removePrefix("/reel/").substringBefore('/').takeIf(String::isNotEmpty)
    }

    private fun sameIndividualReel(first: String?, second: String?): Boolean {
        val firstId = reelIdOf(first) ?: return false
        return firstId == reelIdOf(second)
    }

    private fun normalizePath(path: String?): String {
        val value = path?.replace(Regex("/+"), "/")?.trimEnd('/')
        return if (value.isNullOrEmpty()) "/" else value
    }

    private fun isInstagramHost(host: String?): Boolean =
        host.equals("instagram.com", ignoreCase = true) ||
            host.equals("www.instagram.com", ignoreCase = true)

    private fun isDirectPath(path: String?): Boolean =
        path == "/direct" || path?.startsWith("/direct/") == true

    private fun isIndividualReelPath(path: String?): Boolean =
        path == "/reel" || path?.startsWith("/reel/") == true

    private fun isFeedEscapePath(path: String?): Boolean = InstagramRoutes.isBlockedPath(path)
}
