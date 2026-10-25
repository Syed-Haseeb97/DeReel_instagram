# Unplug: SPEC

Read this before writing any code. If something here conflicts with a prompt, ask; do not guess.

## 1. Product

Unplug is a native Android social app with a calm, finite feed and focus tools. Users sign up, search and follow people, post, and see a feed that ends ("you're caught up") instead of scrolling forever.

Hackathon goals, in priority order:
1. Never broken: no loading loops, no crashes, every screen has Loading / Empty / Error states.
2. Core social loop works end to end: sign up, search, follow, post, see it in the feed.
3. Focus tools make it distinctive (see section 9).

Out of scope: DMs, stories, reels, video, comments (cut-list item), web version.

## 2. Stack (fixed, do not swap)

- Kotlin, Jetpack Compose, Material 3, minSdk 24, package `com.haseeb.unplug`
- Firebase (Spark plan): Auth (Google + email/password), Firestore, Cloud Messaging, Crashlytics. **No Firebase Storage** (it needs a paid plan).
- Images: Cloudinary unsigned upload via OkHttp (see section 2a)
- Navigation Compose (type-safe routes), Coil (images), Paging 3, OkHttp, Credential Manager (Google sign-in), kotlinx.coroutines/Flow
- **No Hilt/KSP/kapt.** AGP 9 built-in Kotlin makes annotation processors fragile. Use manual DI: one `AppContainer` created in `Application`, passed to ViewModel factories.
- No Cloud Functions (needs paid plan). Everything is client-side plus security rules.

## 2a. Image hosting (Cloudinary)

- Cloud name: `niysidnj`. Unsigned upload preset: `unplug_unsigned` (folder `unplug`, formats jpg/png/webp).
- `ImageUploader` does an HTTPS multipart POST to `https://api.cloudinary.com/v1_1/niysidnj/image/upload` with fields `file` and `upload_preset`, and reads `secure_url` from the JSON response. That URL is what gets stored in Firestore (`photoUrl`, `imageUrl`) and loaded by Coil.
- Cloud name and preset are exposed through BuildConfig fields, read from `local.properties` or environment variables, with placeholders in the repo.
- **Never put a Cloudinary API key or API secret in the app or the repo.** The client cannot delete uploaded images (that needs a signature); that is accepted.
- Presets cannot enforce a file-size limit, so the client compresses to max 1080 px, JPEG ~80, and rejects files over 5 MB before upload.

## 3. Package layout

```
com.haseeb.unplug
  app/        Application, AppContainer, MainActivity
  nav/        routes, NavHost, bottom bar
  core/
    ui/       theme, shared composables (UnplugButton, Avatar, EmptyState, ErrorState, SkeletonList)
    data/     Firebase providers, Result/AppError types
    util/     time formatting, image compression, debounce
  auth/       sign-in, sign-up, AuthRepository, AuthViewModel
  feed/       FeedScreen, FeedViewModel, FeedRepository, PostCard
  search/     SearchScreen, SearchViewModel, UserRepository (search part)
  profile/    ProfileScreen, EditProfile, FollowRepository
  post/       CreatePostScreen, PostRepository, ImageUploader
  activity/   ActivityScreen (new followers, likes)
  focus/      usage tracking, limits, quiet hours, streaks
```

Each slice owns its folder. Do not edit another slice's files except through its public repository interface.

## 4. Architecture rules

- MVVM: Composable -> ViewModel -> Repository (interface + Firestore implementation).
- ViewModels expose a single `StateFlow<UiState>`; Composables only read state and call ViewModel functions.
- **No automatic reload loops.** Data loads once on entry, on pull-to-refresh, and on explicit Retry. Never trigger a reload from a state change that a reload itself causes.
- Every screen that loads data uses this state shape:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>   // always shows a Retry button
}
```

- Every network call has a timeout (10 s) and maps exceptions to `Error` with a human message. No raw exception text in UI.
- Spinners show only for `Loading`; they must always end in Content, Empty or Error.
- Repositories return `Result<T>`; no throwing across layers.
- All strings in `strings.xml`. All colors and typography from the theme.

## 5. Data model (Firestore)

`users/{uid}`
- `username` (string, unique, 3-20 chars, `[a-z0-9_.]`)
- `usernameLower` (string, same as username lowercased; used for search)
- `displayName` (string, max 40)
- `photoUrl` (string, nullable)
- `bio` (string, max 160)
- `streak` (number), `lastActiveDate` (string `yyyy-MM-dd`)
- `createdAt` (timestamp)

`usernames/{usernameLower}` -> `{ uid }` (reserves usernames; created in the same transaction as the user doc)

`posts/{postId}`
- `authorId`, `authorUsername`, `authorPhotoUrl` (denormalized for fast feed rendering)
- `text` (max 500), `imageUrl` (nullable), `createdAt`

`follows/{followerId}_{followeeId}`
- `followerId`, `followeeId`, `createdAt`

`likes/{postId}_{uid}` -> `{ postId, uid, createdAt }`

`reports/{id}` -> `{ reporterId, targetType, targetId, reason, createdAt }`
`blocks/{blockerId}_{blockedId}` -> `{ blockerId, blockedId, createdAt }`

**Counts** (followers, following, posts, likes) come from Firestore `count()` aggregate queries. Do not store counter fields.

## 6. Key queries

- **Search:** `users` where `usernameLower >= q` and `usernameLower <= q + "\uf8ff"`, limit 20. Lowercase and trim the query first. Debounce 300 ms. Ignore queries under 2 characters.
- **Everyone feed:** `posts` orderBy `createdAt` desc, limit 10, cursor with `startAfter`.
- **Following feed:** get the current user's followee ids (latest 30 follows), then `posts` where `authorId in ids` orderBy `createdAt` desc, limit 10, cursor with `startAfter`. Needs a composite index.
- **Is following:** get `follows/{me}_{them}`.
- **Follow/unfollow:** write or delete that single doc. Nothing else to keep in sync.
- Filter out posts from blocked users client-side.

Home has two tabs: **Following** and **Everyone**. When Following is empty, show a clear empty state with a button to go to Search.

## 7. Security rules (must exist in `firestore.rules`)

- Read: any signed-in user can read `users`, `posts`, `follows`, `likes`, `usernames`.
- `users/{uid}`: create and update only by `uid`; validate field types and sizes; `username` cannot change after creation except through the rename flow.
- `posts`: create only if `authorId == request.auth.uid` and text length <= 500; update and delete only by the author.
- `follows`: create only if `followerId == request.auth.uid` and the doc id equals `followerId_followeeId` and `followerId != followeeId`; delete only by `followerId`.
- `likes`: same pattern, keyed by `uid`.
- `reports`, `blocks`: create only by the signed-in user as reporter or blocker; no reads of others' data.
- Default deny everything else.
- No Storage rules (images are on Cloudinary). Validate that `photoUrl` and `imageUrl` are strings starting with `https://res.cloudinary.com/niysidnj/`.

## 8. Screens

1. **Auth:** Google button, email/password, username picker on first sign-in.
2. **Home:** Following / Everyone tabs, `PostCard`, pull-to-refresh, "you're caught up" footer, skeleton loading.
3. **Search:** search bar always visible at the top, results list with avatar, username, Follow/Unfollow button, recent searches, empty and error states.
4. **Profile:** avatar, display name, bio, counts, post grid, Follow button or Edit Profile, overflow menu (block, report, sign out).
5. **Create post:** text plus optional image (photo picker, compress to max 1080 px, JPEG ~80), upload progress, failure with retry.
6. **Activity:** new followers and likes.

Bottom bar: Home, Search, Post, Activity, Profile.

## 9. Focus features (build after the core loop works; pick three)

- Daily limit with a gentle end-of-feed screen
- Streaks (days active, shown on profile)
- Quiet hours (schedule model already exists in `focus/`)
- Weekly insight card (time in app, posts made)
- Streak nudge notification via FCM from the client

## 10. Quality bar (every slice)

- Builds clean: `./gradlew testDebugUnitTest assembleDebug lintDebug`
- ViewModels have unit tests for the main states (Loading -> Content, Empty, Error -> Retry)
- Works in airplane mode without crashing (shows Error with Retry)
- Survives rotation and process death
- No hard-coded strings or colors, no secrets committed
- `google-services.json`, keystores and `keystore.properties` are never committed. `app/google-services.json` is gitignored, shared manually, and restored in CI from the `GOOGLE_SERVICES_JSON` secret (base64)

## 11. Workflow

- Branch per slice: `feature/<slice>`; small PRs into `main`; rebase before opening.
- Commit messages: `feat(feed): ...`, `fix(search): ...`.
- Read this file and `AGENTS.md` first. When a task is ambiguous, ask instead of inventing.
- Do not add libraries not listed in section 2 without asking.

## 12. Agent prompt template

```
Read SPEC.md and AGENTS.md. Implement the <slice> slice only, in its own package.
Branch: feature/<slice>. Follow the UiState and no-auto-reload rules.
Deliver: code, unit tests for the ViewModel, and a short note of what is done and what is not.
Run ./gradlew testDebugUnitTest assembleDebug lintDebug and fix failures before finishing.
Do not touch other slices' files.
```
