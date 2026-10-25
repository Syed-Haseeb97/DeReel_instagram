package com.example.dmsonly.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmReelNavigationGuardTest {

    @Test
    fun dmOriginatedReelAllowsSameItemButRejectsNextReel() {
        val guard = DmReelNavigationGuard()

        assertNull(guard.redirectTarget(InstagramRoutes.DM_INBOX))
        assertNull(guard.redirectTarget("https://www.instagram.com/reel/shared/"))
        assertNull(guard.redirectTarget("https://www.instagram.com/reel/shared/?igsh=abc"))
        assertEquals(InstagramRoutes.DM_INBOX, guard.redirectTarget("https://www.instagram.com/reel/next/"))
        assertEquals(
            InstagramRoutes.DM_INBOX,
            guard.redirectTarget("https://www.instagram.com/reels/")
        )
        assertEquals(
            InstagramRoutes.DM_INBOX,
            guard.redirectTarget("https://www.instagram.com/reels/audio/123/")
        )
    }

    @Test
    fun dmReelCannotEscapeToHomeFeedOrGeneralReelsRoutes() {
        val escapePaths = listOf(
            "https://www.instagram.com/",
            "https://www.instagram.com/feed/",
            "https://www.instagram.com/reels/",
            "https://www.instagram.com/reels/audio/123/"
        )

        escapePaths.forEach { url ->
            val guard = DmReelNavigationGuard()
            guard.redirectTarget(InstagramRoutes.DM_INBOX)
            guard.redirectTarget("https://www.instagram.com/reel/shared/")
            assertEquals(
                "Escape route should return to the originating DM: $url",
                InstagramRoutes.DM_INBOX,
                guard.redirectTarget(url)
            )
        }
    }

    @Test
    fun leavingTheDmReelContextClearsItsAllowance() {
        val guard = DmReelNavigationGuard()

        guard.redirectTarget(InstagramRoutes.DM_INBOX)
        guard.redirectTarget("https://www.instagram.com/reel/shared/")
        assertNull(guard.redirectTarget("https://www.instagram.com/direct/inbox/"))
        assertNull(guard.redirectTarget("https://www.instagram.com/reels/"))
    }

    @Test
    fun individualReelNavigationWithoutDmOriginDoesNotCreateContext() {
        val guard = DmReelNavigationGuard()

        guard.redirectTarget("https://www.instagram.com/accounts/login/")
        assertEquals(
            InstagramRoutes.DM_INBOX,
            guard.redirectTarget("https://www.instagram.com/reel/unrelated/")
        )

        assertNull(guard.redirectTarget("https://www.instagram.com/reels/"))
    }

    @Test
    fun searchCannotOpenAnIndividualReelButDmShareCan() {
        val searchGuard = DmReelNavigationGuard()
        searchGuard.redirectTarget("https://www.instagram.com/explore/search/keyword/")
        assertEquals(
            InstagramRoutes.DM_INBOX,
            searchGuard.redirectTarget("https://www.instagram.com/reel/from-search/")
        )

        val dmGuard = DmReelNavigationGuard()
        dmGuard.redirectTarget(InstagramRoutes.DM_INBOX)
        assertNull(dmGuard.redirectTarget("https://www.instagram.com/reel/from-dm/"))
    }

    @Test
    fun restoredHistoryRetainsDmReelContextAndRejectsDifferentReel() {
        val guard = DmReelNavigationGuard()
        val history = listOf(
            InstagramRoutes.DM_INBOX,
            "https://www.instagram.com/reel/shared/"
        )

        guard.restoreFromHistory(history, currentIndex = 1)
        assertEquals(
            InstagramRoutes.DM_INBOX,
            guard.redirectTarget("https://www.instagram.com/reels/")
        )
        assertEquals(
            InstagramRoutes.DM_INBOX,
            guard.redirectTarget("https://www.instagram.com/reel/next/")
        )
    }

    @Test
    fun verticalEscapeFailsClosedToOriginAndResetsForTheNextSharedReel() {
        val guard = DmReelNavigationGuard()
        val firstConversation = "https://www.instagram.com/direct/t/42/"
        val secondConversation = "https://www.instagram.com/direct/t/99/"

        guard.redirectTarget(firstConversation)
        guard.redirectTarget("https://www.instagram.com/reel/shared/")
        assertTrue(guard.isDmReelPinned)
        assertEquals(firstConversation, guard.exitToOriginatingDm())
        assertFalse(guard.isDmReelPinned)

        guard.redirectTarget(secondConversation)
        guard.redirectTarget("https://www.instagram.com/reel/another-shared/")
        assertEquals(secondConversation, guard.originatingDmUrl())
        assertEquals(secondConversation, guard.redirectTarget("https://www.instagram.com/reels/"))
    }

    @Test
    fun restoredUnrelatedReelHistoryDoesNotCreateContext() {
        val guard = DmReelNavigationGuard()
        val history = listOf(
            "https://www.instagram.com/explore/",
            "https://www.instagram.com/reel/unrelated/"
        )

        guard.restoreFromHistory(history, currentIndex = 1)
        assertEquals(
            InstagramRoutes.DM_INBOX,
            guard.redirectTarget("https://www.instagram.com/reel/unrelated/")
        )
    }
}
