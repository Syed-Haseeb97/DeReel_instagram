package com.example.dmsonly.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramUiShieldTest {

    @Test
    fun shieldHidesFeedAndSearchContentButKeepsPeopleSearchControls() {
        val script = InstagramUiShield.script

        assertTrue(script.contains("""html[data-dms-only-home=\"true\"] main article"""))
        assertTrue(script.contains("data-dms-search-filtered"))
        assertTrue(script.contains("function applySearchContentFilter()"))
        assertTrue(script.contains("function isPeopleSearchPath(path)"))
        assertTrue(script.contains("display: none !important"))
        assertFalse(script.contains("a[aria-label=\"Search\"]"))
        assertFalse(script.contains("a[aria-label=\"Explore\"]"))
    }

    @Test
    fun shieldBlocksFeedRoutesThroughSpaAndBrowserHistory() {
        val script = InstagramUiShield.script

        assertTrue(script.contains("function isBlockedPath(path)"))
        assertTrue(script.contains("if (normalized === \"/\") return true;"))
        assertTrue(script.contains("function routeTransitionBlocked(raw)"))
        assertTrue(script.contains("function guardHistoryBack(delta)"))
        assertTrue(script.contains("history.back = function ()"))
        assertTrue(script.contains("history.go = function (delta)"))
        assertTrue(script.contains("location.replace(SAFE_BACK_URL)"))
        assertTrue(script.contains("window.addEventListener(\"popstate\", function ()"))
        assertTrue(script.contains("window.addEventListener(\"hashchange\", applyShield)"))
    }

    @Test
    fun shieldAllowsPeopleSearchPathButBlocksContentDestinations() {
        val script = InstagramUiShield.script

        assertTrue(script.contains("return normalized === \"/explore\" || normalized === \"/explore/search\""))
        assertTrue(script.contains("isSearchContentPath(target.pathname)"))
        assertTrue(script.contains("anchor.closest('[role=\"gridcell\"], article, [role=\"article\"]')"))
    }

    @Test
    fun shieldGuardsDmReelContextAndVerticalSwipeEscape() {
        val script = InstagramUiShield.script

        assertTrue(script.contains("function isDirectPath(path)"))
        assertTrue(script.contains("function isIndividualReelPath(path)"))
        assertTrue(script.contains("function sameIndividualReel(raw, reference)"))
        assertTrue(script.contains("!sameIndividualReel(target.href, lastDmReelUrl)"))
        assertTrue(script.contains("function returnToDmConversation()"))
        assertTrue(script.contains("window.__DM_ONLY_PIN_DM_REEL__"))
        assertTrue(script.contains("dmReelOriginUrl || INBOX_URL"))
        assertTrue(script.contains("dmReelTouchStart"))
        assertTrue(script.contains("event.preventDefault()"))
        assertTrue(script.contains("window.addEventListener(\"wheel\""))
        assertTrue(script.contains("returnToDmConversation();"))
        assertTrue(script.contains("passive: false"))
    }

    @Test
    fun homeFeedIsBlankedAndHomeLaunchedStoriesAreRedirectedBeforeShowing() {
        val script = InstagramUiShield.script
        assertTrue(script.contains("html[data-dms-only-home=\\\"true\\\"] body"))
        assertTrue(script.contains("overflow: hidden !important"))
        assertTrue(script.contains("isStoryPath(target.pathname) && isBlockedPath(lastRoutePath)"))
        assertTrue(script.contains("root.setAttribute(\"data-dms-only-home\", \"true\")"))
        assertTrue(script.contains("goInbox();"))
    }

    @Test
    fun shieldDoesNotPersistReelContextInStorage() {
        val script = InstagramUiShield.script
        assertFalse(script.contains("sessionStorage"))
        assertFalse(script.contains("localStorage"))
    }
}
