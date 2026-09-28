# Duplicate Remover (Android)

An offline Android app (Kotlin + Jetpack Compose, Material 3) that scans the device for
**exact duplicate files** — photos, audio, videos and documents — using SHA-256 content
hashing, keeps one original safe per group, and deletes the selected copies after the
system delete confirmation.

- 100% on-device & private — no INTERNET permission, no analytics, no ads, no Firebase
- Quick Scan (MediaStore) + Deep Scan (full storage crawl, needs All Files Access)
- Modern scoped-storage delete flow (`MediaStore.createDeleteRequest`, batched)

## Open & run

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (latest stable),
Android SDK with API 36.

1. Open Android Studio → **Open** → select this folder.
2. Let it sync Gradle (it will generate the missing Gradle wrapper on import).
3. Run on an emulator or a physical device (physical device recommended for real scans).

> No API keys, no `google-services.json`, no `.env` file needed. The app is fully offline.

## Build a release for Play Store

```bash
# Unsigned release AAB (sign it afterwards with your upload key):
./gradlew bundleRelease

# ...or let Gradle sign it directly by providing the upload keystore:
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=*****
export KEY_ALIAS=upload
export KEY_PASSWORD=*****
./gradlew bundleRelease
```

The signed `.aab` is at `app/build/outputs/bundle/release/`.

> First Play upload ever? Create the app in Play Console with package
> `com.aistudio.duplicateremover.jkwlq` (or rename `applicationId` in
> `app/build.gradle.kts` **before** the first upload — it can never change after).

## Before publishing — Play Store checklist

- [ ] **Privacy policy URL:** publish the policy text (see `PrivacyPolicyDialog.kt`)
      on a public page (e.g. GitHub Pages), then set that URL in
      Play Console → App content → Privacy policy. Update `PRIVACY_POLICY_URL` too.
- [ ] **All Files Access declaration:** Play Console → App content →
      "All files access" — submit the declaration + short demo video showing why
      document/archive duplicate cleaning needs it.
- [ ] **Photos & Videos declaration:** required permission declaration in Play Console.
- [ ] **Data safety form:** declare **"No data collected / shared"** (the app is
      offline; nothing leaves the device).
- [ ] **Test on Android 10 → 16**, incl. permission deny flows and 100+ file deletes.
- [ ] Add store listing assets (icon, feature graphic, screenshots).

## Fixes applied (Sept 2026 audit)

1. **100+ file delete bug fixed** — selections larger than 100 media files are now
   deleted in sequential system-confirmed batches instead of silently skipping the rest
   (`MainActivity`: `mediaBatchQueue` / `launchNextMediaBatch`).
2. **Android 14+ partial photo access** (`READ_MEDIA_VISUAL_USER_SELECTED`) handled —
   "Select photos and videos" grants are accepted instead of nagging forever.
3. **In-app Privacy Policy** added (Settings → About → Privacy Policy).
4. **Removed unused dependencies** — Firebase AI/App Check, Room, Retrofit, Moshi,
   OkHttp + the broken KSP plugin (`2.3.5` never resolves) are gone; APK/AAB is much smaller.
5. **Release logs stripped** — file names/URIs are only logged in debug builds.
6. **Signing fixed** — debug uses the default debug keystore; release signing only
   applies when your upload keystore exists (builds no longer fail without keys).
7. Removed obsolete `requestLegacyExternalStorage`, dead `DeleteHelper.kt`, and the
   hardcoded version string in Settings (now reads the real `versionName`).
8. Manifest now declares `READ_MEDIA_VISUAL_USER_SELECTED` for Android 14+.

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # permissions, delete pipeline, navigation
├── MainViewModel.kt         # scan + selection state
├── DeleteDebugger.kt        # debug-only deletion diagnostics
├── scanner/ScannerService.kt# Quick/Deep scan + SHA-256 duplicate detection
├── model/                   # FileItem, DuplicateGroup, Category
├── ui/                      # Compose screens + dialogs
└── util/                    # settings, scan history, storage info
```
