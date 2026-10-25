package com.example.dmsonly.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramRoutesTest {

    @Test
    fun dmRoutesAreAllowed() {
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/direct/inbox/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/direct/t/123/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/direct/requests/"))
    }

    @Test
    fun homeFeedAndGeneralReelsAreBlocked() {
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/?source=stories-back"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/feed/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/feed/timeline/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/home/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/timeline/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/%66eed/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/reels/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/reels/audio/123/"))
    }

    @Test
    fun peopleSearchAndProfileDestinationsRemainAccessible() {
        val allowedRoutes = listOf(
            "https://www.instagram.com/explore/",
            "https://www.instagram.com/explore/search/",
            "https://www.instagram.com/explore/search/keyword/",
            "https://www.instagram.com/explore/search/people/",
            "https://www.instagram.com/username/",
            "https://www.instagram.com/username/reels/",
            "https://www.instagram.com/profile-with-reels/"
        )

        allowedRoutes.forEach { url ->
            assertFalse("Should allow $url", InstagramRoutes.isBlocked(url))
            assertEquals(InstagramRoutes.Decision.Allow, InstagramRoutes.decide(url))
        }
    }

    @Test
    fun exploreFeedsPostsAndVideoDestinationsAreBlockedButSearchRemainsOpen() {
        val blockedRoutes = listOf(
            "https://www.instagram.com/explore/popular/",
            "https://www.instagram.com/explore/tags/cats/",
            "https://www.instagram.com/explore/locations/123/",
            "https://www.instagram.com/p/postcode/",
            "https://www.instagram.com/tv/videocode/"
        )

        blockedRoutes.forEach { url ->
            assertTrue("Should block $url", InstagramRoutes.isBlocked(url))
        }
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/explore/search/keyword/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/person/"))
    }

    @Test
    fun blockedSegmentsAreExactAndDoNotMatchProfileNamesOrQueryValues() {
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/reelsfan/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/username/?next=/feed/"))
        assertTrue(InstagramRoutes.isBlocked("https://www.instagram.com/feed/"))
    }

    @Test
    fun storiesAndIndividualReelsRemainAddressable() {
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/stories/user/123/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/reel/shortcode/"))
    }

    @Test
    fun storyCanOpenFromProfileOrDmButNotHomeFeed() {
        val story = "https://www.instagram.com/stories/person/123/"
        assertTrue(InstagramRoutes.isStoryUrl(story))
        assertTrue(InstagramRoutes.canOpenStoryFrom("https://www.instagram.com/person/"))
        assertTrue(InstagramRoutes.canOpenStoryFrom(InstagramRoutes.DM_INBOX))
        assertFalse(InstagramRoutes.canOpenStoryFrom("https://www.instagram.com/"))
        assertFalse(InstagramRoutes.canOpenStoryFrom("https://www.instagram.com/feed/"))
    }

    @Test
    fun authenticationRoutesRemainAvailable() {
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/accounts/login/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/challenge/abc/"))
        assertFalse(InstagramRoutes.isBlocked("https://www.instagram.com/checkpoint/foo/"))
    }

    @Test
    fun navigationDecisionRedirectsBlockedInstagramRoutes() {
        val decision = InstagramRoutes.decide("https://www.instagram.com/reels/audio/123/")
        assertTrue(decision is InstagramRoutes.Decision.Redirect)
        assertEquals(InstagramRoutes.DM_INBOX, (decision as InstagramRoutes.Decision.Redirect).url)
    }

    @Test
    fun navigationDecisionOpensExternalHttpLinks() {
        val decision = InstagramRoutes.decide("https://example.com/help")
        assertTrue(decision is InstagramRoutes.Decision.OpenExternally)
    }

    @Test
    fun navigationDecisionBlocksNonHttpSchemes() {
        assertTrue(InstagramRoutes.decide("intent://instagram/") is InstagramRoutes.Decision.Block)
    }

    @Test
    fun onlyInstagramHostsAreSubjectToInstagramRules() {
        assertFalse(InstagramRoutes.isInstagramHost("https://example.com/explore/"))
        assertFalse(InstagramRoutes.isBlocked("https://example.com/explore/"))
        assertTrue(InstagramRoutes.isInstagramHost("https://www.instagram.com/direct/"))
    }
}
