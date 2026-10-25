package com.example.dmsonly.web

import android.webkit.WebView

/**
 * DOM/CSS shielding is deliberately isolated here.
 *
 * URL-level blocking in InstagramRoutes/WebViewClient remains authoritative;
 * this shield blanks a blocked Home document and prevents a Home-backed Story
 * from exposing feed content underneath it.
 */
object InstagramUiShield {

    val script: String by lazy { buildScript() }

    fun install(webView: WebView) {
        webView.evaluateJavascript(script, null)
    }

    private fun buildScript(): String {
        val css = listOf(
            """a[href="/"]""",
            """a[href^="/?"]""",
            """a[href="https://www.instagram.com/"]""",
            """a[href^="/reels"]""",
            """a[href^="/feed"]""",
            """a[aria-label="Home"]""",
            """a[aria-label="Reels"]""",
            """[role="link"][aria-label="Home"]""",
            """[role="link"][aria-label="Reels"]""",
            """[role="button"][aria-label="Home"]""",
            """[role="button"][aria-label="Reels"]""",
            """[role="tab"][aria-label="Home"]""",
            """[role="tab"][aria-label="Reels"]""",
            """html[data-dms-only-home="true"] body""",
            """html[data-dms-only-home="true"] main article""",
            """html[data-dms-only-home="true"] main [role="article"]"""
        ).joinToString(",\n") + " { visibility: hidden !important; pointer-events: none !important; }\n" +
            "html[data-dms-only-home=\"true\"] { overflow: hidden !important; }\n" +
            "html[data-dms-only-search=\"true\"] [data-dms-search-filtered=\"true\"] { display: none !important; }\n" +
            "html[data-dms-dm-reel=\"true\"] { overscroll-behavior: none !important; }"

        val segments = InstagramRoutes.BLOCKED_FIRST_SEGMENTS
            .joinToString(",") { "\"" + it + "\"" }

        val hosts = InstagramRoutes.FEED_HOSTS
            .joinToString(",") { "\"" + it + "\"" }

        return TEMPLATE
            .replace("__INBOX_URL__", InstagramRoutes.DM_INBOX)
            .replace("__SAFE_BACK_URL__", InstagramRoutes.SAFE_BACK_URL)
            .replace("__BLOCKED_SEGMENTS__", segments)
            .replace("__FEED_HOSTS__", hosts)
            .replace("__CSS__", jsString(css))
    }

    private fun jsString(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""

    private const val TEMPLATE = """
(function () {
  "use strict";
  if (window.top !== window) return;
  if (window.__DM_ONLY_SHIELD__) {
    if (window.__DM_ONLY_APPLY__) window.__DM_ONLY_APPLY__();
    return;
  }

  var INBOX_URL = "__INBOX_URL__";
  var SAFE_BACK_URL = "__SAFE_BACK_URL__";
  var BLOCKED_FIRST_SEGMENTS = [__BLOCKED_SEGMENTS__];
  var FEED_HOSTS = [__FEED_HOSTS__];
  var STYLE_ID = "dms-only-shield-style";
  var CSS = __CSS__;
  var lastRoutePath = location.pathname;
  var observedRoutes = [location.href];
  var observedRouteIndex = 0;
  var dmReelContextActive = false;
  var lastDmReelUrl = null;
  var dmReelOriginUrl = null;

  try {
    var referrer = new URL(document.referrer);
    if (isDirectPath(referrer.pathname) && isIndividualReelPath(lastRoutePath)) {
      dmReelContextActive = true;
      dmReelOriginUrl = referrer.href;
      lastDmReelUrl = location.href;
      if (document.documentElement) document.documentElement.setAttribute("data-dms-dm-reel", "true");
    }
  } catch (e) {}

  function normalizedPath(path) {
    var value = String(path || "/").replace(/\/+/g, "/");
    if (value.length > 1) value = value.replace(/\/+$/, "");
    return value || "/";
  }

  function isBlockedPath(path) {
    var normalized = normalizedPath(path);
    if (normalized === "/") return true;
    var first = normalized.replace(/^\//, "").split("/")[0];
    try { first = decodeURIComponent(first); } catch (e) {}
    if (first.toLowerCase() === "explore") return !isPeopleSearchPath(normalized);
    return BLOCKED_FIRST_SEGMENTS.indexOf(first.toLowerCase()) !== -1;
  }

  function isPeopleSearchPath(path) {
    var normalized = normalizedPath(path).toLowerCase();
    return normalized === "/explore" || normalized === "/explore/search" ||
      normalized.indexOf("/explore/search/") === 0;
  }

  function isStoryPath(path) {
    var normalized = normalizedPath(path).toLowerCase();
    return normalized === "/stories" || normalized.indexOf("/stories/") === 0;
  }

  function isBlockedUrl(raw) {
    try {
      var u = new URL(String(raw), location.href);
      return FEED_HOSTS.indexOf(u.hostname.toLowerCase()) !== -1 && isBlockedPath(u.pathname);
    } catch (e) {
      return false;
    }
  }

  function isSearchContentPath(path) {
    var normalized = normalizedPath(path).toLowerCase();
    return isBlockedPath(normalized) || normalized === "/reel" || normalized.indexOf("/reel/") === 0;
  }

  function applySearchContentFilter() {
    var root = document.documentElement;
    if (!root) return;
    var active = isPeopleSearchPath(location.pathname);
    if (active) root.setAttribute("data-dms-only-search", "true");
    else root.removeAttribute("data-dms-only-search");

    document.querySelectorAll('[data-dms-search-filtered="true"]').forEach(function (item) {
      item.removeAttribute("data-dms-search-filtered");
    });
    if (!active) return;
    document.querySelectorAll("a[href]").forEach(function (anchor) {
      try {
        var target = new URL(anchor.href, location.href);
        if (FEED_HOSTS.indexOf(target.hostname.toLowerCase()) === -1 ||
            !isSearchContentPath(target.pathname)) return;
        var card = anchor.closest('[role="gridcell"], article, [role="article"]') || anchor;
        card.setAttribute("data-dms-search-filtered", "true");
      } catch (e) {}
    });
  }

  function isDirectPath(path) {
    var normalized = normalizedPath(path);
    return normalized === "/direct" || normalized.indexOf("/direct/") === 0;
  }

  function isIndividualReelPath(path) {
    var normalized = normalizedPath(path);
    return normalized === "/reel" || normalized.indexOf("/reel/") === 0;
  }

  function reelId(path) {
    var parts = normalizedPath(path).split("/");
    return parts[1] === "reel" && parts[2] ? parts[2] : null;
  }

  function sameIndividualReel(raw, reference) {
    try {
      var target = new URL(String(raw), location.href);
      var original = new URL(String(reference), location.href);
      return FEED_HOSTS.indexOf(target.hostname.toLowerCase()) !== -1 &&
        FEED_HOSTS.indexOf(original.hostname.toLowerCase()) !== -1 &&
        reelId(target.pathname) !== null &&
        reelId(target.pathname) === reelId(original.pathname);
    } catch (e) {
      return false;
    }
  }

  function isFeedEscapePath(path) {
    return isBlockedPath(path);
  }

  function goInbox() {
    if (location.href !== INBOX_URL) location.replace(INBOX_URL);
  }

  function returnToDmConversation() {
    var target = dmReelOriginUrl || INBOX_URL;
    clearDmReelContext();
    location.replace(target);
  }

  function clearDmReelContext() {
    dmReelContextActive = false;
    lastDmReelUrl = null;
    dmReelOriginUrl = null;
    if (document.documentElement) document.documentElement.removeAttribute("data-dms-dm-reel");
  }

  window.__DM_ONLY_PIN_DM_REEL__ = function (originUrl, reelUrl) {
    try {
      var origin = new URL(String(originUrl), location.href);
      var reel = new URL(String(reelUrl), location.href);
      if (!isDirectPath(origin.pathname) || !isIndividualReelPath(reel.pathname)) return;
      dmReelContextActive = true;
      dmReelOriginUrl = origin.href;
      lastDmReelUrl = reel.href;
      if (document.documentElement) document.documentElement.setAttribute("data-dms-dm-reel", "true");
    } catch (e) {}
  };

  function routeTransitionBlocked(raw) {
    try {
      var target = new URL(String(raw), location.href);
      if (FEED_HOSTS.indexOf(target.hostname.toLowerCase()) === -1) return false;

      if (isDirectPath(lastRoutePath) && isIndividualReelPath(target.pathname)) {
        dmReelContextActive = true;
        dmReelOriginUrl = location.href;
        lastDmReelUrl = target.href;
        if (document.documentElement) document.documentElement.setAttribute("data-dms-dm-reel", "true");
        return false;
      }
      if (isIndividualReelPath(target.pathname)) {
        if (!dmReelContextActive) {
          goInbox();
          return true;
        }
        if (!sameIndividualReel(target.href, lastDmReelUrl)) {
          returnToDmConversation();
          return true;
        }
        lastDmReelUrl = target.href;
        return false;
      }
      if (dmReelContextActive && isFeedEscapePath(target.pathname)) {
        returnToDmConversation();
        return true;
      }
      if (isStoryPath(target.pathname) && isBlockedPath(lastRoutePath)) {
        var root = document.documentElement;
        if (root) root.setAttribute("data-dms-only-home", "true");
        goInbox();
        return true;
      }
      if (dmReelContextActive) clearDmReelContext();
      if (isBlockedUrl(target.href)) {
        goInbox();
        return true;
      }
      return false;
    } catch (e) {
      return false;
    }
  }

  function previousAllowedRouteIndex(startIndex) {
    for (var i = Math.min(startIndex, observedRouteIndex - 1); i >= 0; i--) {
      if (observedRoutes[i] && !isBlockedUrl(observedRoutes[i])) return i;
    }
    return -1;
  }

  function guardHistoryBack(delta) {
    if (delta >= 0) return false;
    var requestedIndex = observedRouteIndex + delta;
    var safeIndex = previousAllowedRouteIndex(requestedIndex);
    if (safeIndex >= 0) {
      originalHistoryGo.call(history, safeIndex - observedRouteIndex);
      return true;
    }
    var requestedUrl = observedRoutes[requestedIndex];
    if (isStoryPath(location.pathname) || (requestedUrl && isBlockedUrl(requestedUrl))) {
      location.replace(SAFE_BACK_URL);
      return true;
    }
    return false;
  }

  function updateRouteState() {
    var currentPath = location.pathname;
    if (isDirectPath(lastRoutePath) && isIndividualReelPath(currentPath)) {
      dmReelContextActive = true;
      dmReelOriginUrl = "https://www.instagram.com" + lastRoutePath;
      lastDmReelUrl = location.href;
      if (document.documentElement) document.documentElement.setAttribute("data-dms-dm-reel", "true");
    } else if (dmReelContextActive && isIndividualReelPath(currentPath)) {
      if (!sameIndividualReel(location.href, lastDmReelUrl)) {
        returnToDmConversation();
        return;
      }
      lastDmReelUrl = location.href;
    } else if (dmReelContextActive && isFeedEscapePath(currentPath)) {
      returnToDmConversation();
      return;
    } else if (dmReelContextActive) {
      clearDmReelContext();
    }
    lastRoutePath = currentPath;
  }

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = CSS;
    root.appendChild(style);
  }

  var stylePending = false;
  function scheduleStyle() {
    if (stylePending || document.getElementById(STYLE_ID)) return;
    stylePending = true;
    setTimeout(function () {
      stylePending = false;
      installStyle();
      applySearchContentFilter();
    }, 100);
  }

  if (document.documentElement) {
    new MutationObserver(scheduleStyle).observe(
      document.documentElement,
      { childList: true, subtree: true }
    );
  }

  function guardLocation() {
    if (dmReelContextActive && isIndividualReelPath(location.pathname) &&
        !sameIndividualReel(location.href, lastDmReelUrl)) {
      returnToDmConversation();
      return;
    }
    if (dmReelContextActive && isFeedEscapePath(location.pathname)) {
      returnToDmConversation();
      return;
    }
    if (isBlockedUrl(location.href)) goInbox();
  }

  function applyShield() {
    updateRouteState();
    installStyle();

    var root = document.documentElement;
    var homeValue = isBlockedUrl(location.href) ? "true" : null;
    if (root && root.getAttribute("data-dms-only-home") !== homeValue) {
      if (homeValue) root.setAttribute("data-dms-only-home", homeValue);
      else root.removeAttribute("data-dms-only-home");
    }

    applySearchContentFilter();
    guardLocation();
  }

  function scheduleApply() {
    if (typeof queueMicrotask === "function") queueMicrotask(applyShield);
    else setTimeout(applyShield, 0);
  }

  var originalHistoryGo = history.go;
  var originalHistoryBack = history.back;
  ["pushState", "replaceState"].forEach(function (name) {
    var original = history[name];
    history[name] = function (state, title, url) {
      var targetUrl = null;
      if (url !== undefined && url !== null) {
        targetUrl = new URL(String(url), location.href).href;
        if (routeTransitionBlocked(targetUrl)) return;
      }
      var result = original.apply(this, arguments);
      if (targetUrl !== null) {
        if (name === "pushState") {
          observedRoutes = observedRoutes.slice(0, observedRouteIndex + 1);
          observedRoutes.push(targetUrl);
          observedRouteIndex = observedRoutes.length - 1;
        } else {
          observedRoutes[observedRouteIndex] = targetUrl;
        }
      }
      scheduleApply();
      return result;
    };
  });

  try {
    var initialReferrer = new URL(document.referrer);
    if (FEED_HOSTS.indexOf(initialReferrer.hostname.toLowerCase()) !== -1 &&
        !isBlockedUrl(initialReferrer.href)) {
      observedRoutes.unshift(initialReferrer.href);
      observedRouteIndex = 1;
    }
  } catch (e) {}

  history.go = function (delta) {
    var steps = Number(delta);
    if (steps < 0 && guardHistoryBack(steps)) return;
    return originalHistoryGo.apply(this, arguments);
  };
  history.back = function () {
    if (guardHistoryBack(-1)) return;
    return originalHistoryBack.apply(this, arguments);
  };

  document.addEventListener("click", function (event) {
    var target = event.target;
    var anchor = target && target.closest ? target.closest("a[href]") : null;
    if (anchor && routeTransitionBlocked(anchor.getAttribute("href"))) {
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
    }
  }, true);

  // The DM-shared individual Reel is allowed, but vertical gestures must not
  // turn that entry point into an endless generic Reels feed.
  var dmReelTouchStart = null;

  document.addEventListener("touchstart", function (event) {
    if (!dmReelContextActive || !isIndividualReelPath(location.pathname) ||
        !event.touches || event.touches.length !== 1) {
      dmReelTouchStart = null;
      return;
    }
    dmReelTouchStart = {
      x: event.touches[0].clientX,
      y: event.touches[0].clientY
    };
  }, { capture: true, passive: true });

  document.addEventListener("touchmove", function (event) {
    if (!dmReelContextActive || !isIndividualReelPath(location.pathname) ||
        !dmReelTouchStart || !event.touches || event.touches.length !== 1) return;
    var dx = event.touches[0].clientX - dmReelTouchStart.x;
    var dy = event.touches[0].clientY - dmReelTouchStart.y;
    if (Math.abs(dy) < 22 || Math.abs(dy) <= Math.abs(dx)) return;
    if (event.cancelable) event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    returnToDmConversation();
  }, { capture: true, passive: false });

  window.addEventListener("wheel", function (event) {
    if (!dmReelContextActive || !isIndividualReelPath(location.pathname) ||
        Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
    if (event.cancelable) event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    returnToDmConversation();
  }, { capture: true, passive: false });

  document.addEventListener("touchend", function () {
    dmReelTouchStart = null;
  }, true);

  document.addEventListener("touchcancel", function () {
    dmReelTouchStart = null;
  }, true);

  window.addEventListener("popstate", function () {
    var currentUrl = location.href;
    var previousIndex = observedRoutes.lastIndexOf(currentUrl);
    if (previousIndex >= 0) observedRouteIndex = previousIndex;
    else {
      observedRoutes = observedRoutes.slice(0, observedRouteIndex + 1);
      observedRoutes.push(currentUrl);
      observedRouteIndex = observedRoutes.length - 1;
    }
    if (isBlockedUrl(currentUrl)) {
      var root = document.documentElement;
      if (root) root.setAttribute("data-dms-only-home", "true");
      location.replace(SAFE_BACK_URL);
      return;
    }
    applyShield();
  }, true);
  window.addEventListener("hashchange", applyShield);
  window.addEventListener("pageshow", applyShield);

  window.__DM_ONLY_APPLY__ = applyShield;
  window.__DM_ONLY_SHIELD__ = true;

  applyShield();
})();
"""
}
