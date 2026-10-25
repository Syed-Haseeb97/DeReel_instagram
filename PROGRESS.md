# PROGRESS

## Goal

Fix the reported issue: **Instagram Feed / Explore / Reels are still reachable from DMs Only**.

The gap was in `main`: it only blocked `/`, `/explore`, and `/reels`. It still allowed `/feed`, `/p/` (posts), `/tv/` (IGTV), and individual `/reel/...` pages. A Reel or post shared in a DM could therefore lead into feed-like browsing, and vertical swipes inside a DM Reel could pull in the generic Reels feed.

## Completed

- Inspected the whole repository: Gradle config, manifest, all Kotlin sources, tests, CI, README, and Git branches.
- Identified the escape vectors in the current `main`.
- Expanded route blocking to include:
  - `/feed`
  - `/p/` posts
  - `/tv/` IGTV
  - existing `/`, `/explore`, `/reels`
- Added a DM Reel context guard so a single Reel opened from a DM is allowed, but:
  - generic Reels and feed/Explore/post routes redirect back to the DM Reel or inbox,
  - individual Reels not opened from a DM are redirected to the inbox.
- Integrated the guard into:
  - `shouldOverrideUrlLoading`
  - `doUpdateVisitedHistory`
  - `onPageStarted`
  - new same-document navigation callback
- Added AndroidX WebKit `NavigationListener` support for SPA `pushState` / `replaceState` navigations, with a `runCatching` fallback.
- Hardened `InstagramUiShield`:
  - blocks root (`/`) and feed-like SPA routes,
  - hides Home/Explore/Search/Reels controls with broader selectors,
  - hides feed posts at the root surface,
  - hooks `pushState`, `replaceState`, `popstate`, `hashchange`, and `pageshow`,
  - blocks vertical swipe escapes from individual Reels,
  - keeps the injected style alive with a debounced `MutationObserver`.
- Added/updated unit tests for routes, DM Reel navigation, and shield script contents.
- Updated `README.md` features, architecture, file tree, testing checklist, and known limitations.
- Set up a local build toolchain outside the repo:
  - JDK 17 (Temurin 17.0.20.1)
  - Gradle 9.6.0
  - Android SDK command-line tools, Platform 37.0, Build-Tools 37.0.0, platform-tools
- Created a gitignored `local.properties` pointing at that temporary SDK.
- The first `gradle test` run exposed a **pre-existing build configuration break**: the project applied `org.jetbrains.kotlin.android` while AGP 9.4 has built-in Kotlin, and the Kotlin plugin refuses to apply. This is unrelated to the feed fix.
- Applied the minimal AGP built-in Kotlin migration already used on the remote branches:
  - removed the `org.jetbrains.kotlin.android` plugin from the root and app build files,
  - replaced `android.kotlinOptions` with the top-level `kotlin.compilerOptions` block.
- The second `gradle test` run after the build-config fix **passed**: `BUILD SUCCESSFUL`, all 18 unit tests compiled and ran with 0 failures (routes: 8, DM Reel guard: 6, UI shield: 4).
- `gradle assembleDebug` **passed** (`BUILD SUCCESSFUL`). APK produced at `app/build/outputs/apk/debug/app-debug.apk` (about 11.9 MB).

## Files edited

- `app/src/main/java/com/example/dmsonly/web/InstagramRoutes.kt`
- `app/src/main/java/com/example/dmsonly/web/InstagramUiShield.kt`
- `app/src/main/java/com/example/dmsonly/web/InstagramWebView.kt`
- `app/src/main/java/com/example/dmsonly/web/InstagramWebViewClient.kt`
- `app/src/test/java/com/example/dmsonly/web/InstagramRoutesTest.kt`
- `README.md`
- `build.gradle.kts` (AGP built-in Kotlin migration)
- `app/build.gradle.kts` (AGP built-in Kotlin migration)

## Files created

- `app/src/main/java/com/example/dmsonly/web/DmReelNavigationGuard.kt`
- `app/src/test/java/com/example/dmsonly/web/DmReelNavigationGuardTest.kt`
- `app/src/test/java/com/example/dmsonly/web/InstagramUiShieldTest.kt`
- `local.properties` (gitignored, local-only SDK path)
- `PROGRESS.md` (this file)

## Next step

1. Install `app/build/outputs/apk/debug/app-debug.apk` on an Android device and verify against the live Instagram site:
   - Feed, Explore, Reels, `/p/`, and `/tv/` links redirect to the DM inbox.
   - A Reel shared in a DM opens, but vertical swipes and route changes cannot reach the generic Reels feed.
   - DM threads, login/challenge screens, Stories, and Back navigation still work.
2. Review the final diff.
3. Remove or adjust `local.properties` if the temporary SDK path should not remain on this machine.
4. Commit the changes if desired; nothing has been committed yet.

## Remaining bugs / risks / pending features

- **Build verification is complete:** `gradle test` passed all 18 tests and `gradle assembleDebug` produced the APK successfully. Real-device behavior against the live Instagram site is still unverified.
- The project uses `compileSdk = 37` while the local SDK installed `android-37.0`; Gradle accepted it and auto-installed Build-Tools 36.0.0, so no SDK adjustment was needed.
- The build surfaced a pre-existing deprecation warning for `WebSettings.databaseEnabled` in `InstagramWebView.kt`; it is unrelated to the fix and does not fail the build.
- No real-device verification. The fix still needs testing against Instagram's current web UI.
- Instagram DOM/routes change frequently; CSS selectors and route rules remain best-effort.
- Blocking `/p/` and `/tv/` intentionally prevents opening shared feed posts/IGTV from DMs. This is consistent with the DMs-only goal but may surprise users.
- Root `/` remains blocked. Closing a Story may redirect to the inbox rather than the Home feed; this is intentional to keep the feed inaccessible.
- The pre-existing untracked file `app/src/main/res/drawable/ic_logo.xml` was not created by this work and was left untouched.
- No commit has been made; all changes are in the working tree on `main`.
