package com.example.dmsonly.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramLanguagePreferencesTest {
    @Test
    fun explicitEnglishHeaderIsUsed() {
        assertEquals("en-US,en;q=0.9", InstagramLanguagePreferences.requestHeaders["Accept-Language"])
    }

    @Test
    fun onlyInstagramGetNavigationsReceiveTheLanguageHeader() {
        assertTrue(
            InstagramLanguagePreferences.shouldApply("GET", "www.instagram.com")
        )
        assertFalse(
            InstagramLanguagePreferences.shouldApply("POST", "www.instagram.com")
        )
        assertFalse(
            InstagramLanguagePreferences.shouldApply("GET", "example.com")
        )
    }
}
