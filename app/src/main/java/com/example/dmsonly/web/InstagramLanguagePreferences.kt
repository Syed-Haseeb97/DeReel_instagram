package com.example.dmsonly.web

/** Explicit browser-language hint for Instagram document navigations. */
object InstagramLanguagePreferences {
    const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"

    val requestHeaders: Map<String, String> = mapOf("Accept-Language" to ACCEPT_LANGUAGE)

    /** Only replay safe Instagram GET navigations with the language hint. */
    fun shouldApply(method: String, host: String?): Boolean {
        val normalizedHost = host?.lowercase().orEmpty()
        val isInstagramHost = normalizedHost == "instagram.com" ||
            normalizedHost.endsWith(".instagram.com")
        return method.equals("GET", ignoreCase = true) && isInstagramHost
    }
}
