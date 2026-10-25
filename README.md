# DeReel

DeReel is an Android utility built around an embedded Instagram WebView. Instagram sign-in takes place in the WebView. Stories, direct messages, profiles, Search, and Explore are available; the Home feed and general Reels routes are redirected to the DM inbox. A Reel opened from a DM is guarded against navigation into the general feed. Instagram can change its web client, so route behavior should be rechecked after site changes.

## Build

Install JDK 17 or later and Android SDK 37, then run:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release signing

Release builds use the debug signing config when `keystore.properties` is absent. To create a release key locally:

```sh
keytool -genkeypair -v -keystore dereel-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dereel
```

Create a local, untracked `keystore.properties` file with these entries (use the passwords and alias chosen for your key):

```properties
storeFile=dereel-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=dereel
keyPassword=YOUR_KEY_PASSWORD
```

Keep the keystore and its passwords private. Do not commit either file.
