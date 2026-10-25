package com.example.dmsonly.web

import android.net.Uri
import java.net.URI

object InstagramRoutes {
    const val DM_INBOX = "https://www.instagram.com/direct/inbox/"
    const val SAFE_BACK_URL = DM_INBOX
    private const val INSTAGRAM_HOST = "instagram.com"
    private const val WWW_INSTAGRAM_HOST = "www.instagram.com"

    val FEED_HOSTS = setOf(INSTAGRAM_HOST, WWW_INSTAGRAM_HOST)
    val BLOCKED_FIRST_SEGMENTS = setOf("feed", "home", "timeline", "reels", "p", "tv")

    fun isInstagramHost(uri: Uri): Boolean = isInstagramHostName(uri.host)
    private fun isInstagramHostName(host: String?): Boolean {
        val normalized = host?.lowercase() ?: return false
        return normalized == INSTAGRAM_HOST || normalized == WWW_INSTAGRAM_HOST
    }

    fun isInstagramHost(raw: String?): Boolean = parseJavaUri(raw)?.host?.let(::isInstagramHostName) == true

    fun isInboxUrl(raw: String?): Boolean = parseJavaUri(raw)?.let { uri ->
        isInstagramHostName(uri.host) && normalizePath(uri.path) == "/direct/inbox"
    } == true

    fun isStoryUrl(raw: String?): Boolean = parseJavaUri(raw)?.let { uri ->
        isInstagramHostName(uri.host) && isStoryPath(uri.path)
    } == true

    /** Home-launched Stories are rejected because their underlying page is a blocked feed. */
    fun canOpenStoryFrom(sourceUrl: String?): Boolean = !isBlocked(sourceUrl)

    fun isStoryPath(path: String?): Boolean {
        val normalized = normalizePath(path).lowercase()
        return normalized == "/stories" || normalized.startsWith("/stories/")
    }

    fun isAuthFlowUrl(raw: String?): Boolean = parseJavaUri(raw)?.let { uri ->
        isInstagramHostName(uri.host) && isAuthenticationRoute(normalizePath(uri.path))
    } == true

    fun isBlocked(uri: Uri): Boolean = isInstagramHost(uri) && isBlockedPath(uri.path)

    fun isBlocked(raw: String?): Boolean = parseJavaUri(raw)?.let { uri ->
        isInstagramHostName(uri.host) && isBlockedPath(uri.path)
    } == true

    fun decide(uri: Uri): Decision = when {
        isInstagramHost(uri) && isBlocked(uri) -> Decision.Redirect(DM_INBOX)
        isInstagramHost(uri) -> Decision.Allow
        uri.scheme == "http" || uri.scheme == "https" -> Decision.OpenExternally(uri.toString())
        else -> Decision.Block
    }

    fun decide(raw: String): Decision? = parseJavaUri(raw)?.let { uri ->
        when {
            isInstagramHostName(uri.host) && isBlockedPath(uri.path) -> Decision.Redirect(DM_INBOX)
            isInstagramHostName(uri.host) -> Decision.Allow
            uri.scheme == "http" || uri.scheme == "https" -> Decision.OpenExternally(uri.toString())
            else -> Decision.Block
        }
    }

    fun isBlockedPath(path: String?): Boolean {
        val normalized = normalizePath(path)
        if (normalized == "/") return true
        val encodedFirst = normalized.removePrefix("/").substringBefore("/")
        val first = runCatching {
            URI("/$encodedFirst").path.removePrefix("/").substringBefore("/")
        }.getOrDefault(encodedFirst).lowercase()
        if (first == "explore") return !isPeopleSearchPath(normalized)
        return first in BLOCKED_FIRST_SEGMENTS
    }

    fun isPeopleSearchPath(path: String?): Boolean {
        val normalized = normalizePath(path).lowercase()
        return normalized == "/explore" || normalized == "/explore/search" ||
            normalized.startsWith("/explore/search/")
    }

    private fun isAuthenticationRoute(path: String): Boolean =
        path == "/accounts/login" || path.startsWith("/accounts/login/") ||
        path == "/challenge" || path.startsWith("/challenge/") ||
        path == "/checkpoint" || path.startsWith("/checkpoint/") ||
        path == "/oauth" || path.startsWith("/oauth/") ||
        path == "/consent" || path.startsWith("/consent/")

    private fun normalizePath(path: String?): String {
        val value = path?.trim().orEmpty()
        if (value.isEmpty()) return "/"
        return value.replace(Regex("/+"), "/").let { if (it.length > 1) it.trimEnd('/') else it }
    }

    private fun parseJavaUri(raw: String?): URI? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { URI(it) }.getOrNull() }

    sealed interface Decision {
        data object Allow : Decision
        data object Block : Decision
        data class Redirect(val url: String) : Decision
        data class OpenExternally(val url: String) : Decision
    }
}
