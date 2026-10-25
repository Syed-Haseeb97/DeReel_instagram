package com.example.dmsonly.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramBackNavigationTest {

    @Test
    fun storyBackSkipsHomeFeedAndReturnsToLastAllowedProfile() {
        val history = listOf(
            "https://www.instagram.com/person/",
            "https://www.instagram.com/",
            "https://www.instagram.com/stories/person/123/"
        )

        assertEquals(0, InstagramBackNavigation.previousAllowedHistoryIndex(history, 2))
    }

    @Test
    fun storyBackUsesDmInboxWhenHistoryContainsOnlyBlockedFeed() {
        val history = listOf(
            "https://www.instagram.com/feed/",
            "https://www.instagram.com/stories/person/123/"
        )

        assertNull(InstagramBackNavigation.previousAllowedHistoryIndex(history, 1))
        assertEquals(InstagramRoutes.SAFE_BACK_URL, InstagramRoutes.DM_INBOX)
    }

    @Test
    fun normalBackWithinAllowedPagesStillUsesPreviousEntry() {
        val history = listOf(
            "https://www.instagram.com/direct/inbox/",
            "https://www.instagram.com/direct/t/42/",
            "https://www.instagram.com/stories/person/123/"
        )

        assertEquals(1, InstagramBackNavigation.previousAllowedHistoryIndex(history, 2))
    }
}
