# Smart IPTV · Android

Native Kotlin / Jetpack Compose IPTV player for Android phones, tablets and TV boxes. Dark interface with mint accents, English and Russian localization. The app includes no channel package, IPTV subscription, search engine for pirated content, or promises of a fixed channel count.

## Implemented

- M3U / M3U8 / M3U Plus by URL and Android document picker; channel names, groups, logos, `tvg-id`, `url-tvg` / `x-tvg-url`, relative URLs, `#EXTGRP`, VLC User-Agent / Referer and URL header suffixes.
- HLS manifests open as **one stream**, not as a list of segments or quality variants.
- Xtream Codes server / username / password: authenticated Live TV, movie VOD, series categories and episode playback. `Xtreme Play` is a competitor service name, not a bundled service in this app.
- Multiple playlists, search, playlist and category filters, favorites, watch history and VOD resume. Refresh replaces content atomically while preserving channel history and favorites. Deleting a playlist removes its associated content and history.
- SQLite catalogue pages of 200 items, debounced Unicode search and indexed filters. Browsing reads channel metadata without decrypting every stream URL. Existing installations migrate in place; encrypted stream details are resolved when playback starts.
- XMLTV / XMLTV.gz guide, matching `tvg-id`, programme schedule, manual guide refresh or custom EPG URL.
- Media3 ExoPlayer: HLS, DASH and progressive media, adaptive quality, audio / subtitle / quality selection, player control lock, brightness, volume, sleep timer, landscape, picture-in-picture, optional background playback with media session notification and audio focus.
- Network recovery waits for connectivity, then makes at most five attempts with 1/2/4/8/16-second delays. Pause, cancellation, sleep timer and channel changes stop recovery. Authentication, missing-stream, codec and DRM failures have separate messages. Previous/next Live TV controls and channel keys navigate within the current playlist/group.
- Playback checkpoints are queued off the UI thread and retain their event time; delayed writes cannot recreate history for deleted channels.
- Google Cast sender with default receiver, device picker, session transfer and expanded TV controller. Requires Google Play services, a compatible receiver on the same network and a receiver-accessible stream. This sends media URLs; it does not mirror the entire phone screen. Local files and custom-header streams require a separate receiver/server implementation and remain local.
- External player chooser for compatible media; local audio, video and image files via Storage Access Framework. No broad storage permission.
- Android TV launcher / banner, landscape support and focusable Compose controls. Phones and TV boxes require Android 8.0+; codec and 4K support depend on device and stream.
- Android Keystore AES-GCM encryption for provider credentials, stream URLs and request headers; disabled device backups; no source credentials in logs or error messages.

## Build

Use Android Studio with **JDK 17**, Android SDK **36** and build tools **35.0.0**. Package/application ID: `com.sultonovmuzafar.smartiptv`.

```bash
./gradlew :core:selfTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Release signing keys are deliberately not stored in the repository.

Core regression tests also run without Android SDK or Gradle:

```bash
bash Scripts/test-core.sh
```

The executable suite checks M3U Plus metadata and headers, duplicate/invalid filtering, relative URLs, HLS master/media distinction, Xtream URL encoding, stable channel IDs, bounded retry policy, XMLTV timezones, external-entity protection and a 20,000-channel catalogue. Android unit tests cover source imports, database migration/rollback/history, paged catalogues with 20,000 and 50,000 channels, error classification and an Android 8 framework activity-launch smoke test.

## Test on a device

1. Complete onboarding → Add source → paste your authorized M3U URL, choose a playlist file or sign in to Xtream.
2. Watch → pick Live TV / Movies / Series / My media, filter a category, search and start playback.
3. Add favorites; reopen the app and confirm favorites and recent history persist. Refresh the source and confirm they remain.
4. For Xtream series, choose a title and episode. Leave VOD playback and reopen it to check resume.
5. Playlists → EPG to fetch a guide; add a custom XMLTV URL if necessary. Use the programme icon beside a live channel.
6. Player → tools: brightness, volume, timer, external player; subtitles icon: audio, quality and captions; lock: only unlock remains active.
7. Settings → background playback / automatic PiP. Test Home, headphone disconnect and another app requesting audio focus.
8. Connect a Chromecast / Google Cast TV, select a receiver and confirm local playback pauses after successful receiver loading. Test remote controls, disconnect and return to device.
9. Install on a TV box; verify D-pad focus, selection, Back, source entry and player controls with the actual remote.
10. During Live and VOD playback, toggle Wi-Fi/network access. Check recovery, VOD position, pause/cancel during recovery, the five-attempt limit, sleep timer and manual retry. Confirm 401/403, 404, codec and DRM failures do not retry indefinitely.
11. Use previous/next and remote channel keys; check group boundaries, wrapping, title/EPG/Cast metadata, rapid changes and return from PiP. Import a large playlist, search Cyrillic names and literal `%` / `_`, scroll through multiple pages and remove a source during playback.

See [QA.md](QA.md) for validation status and remaining device checks. Safety limits are 50 MB downloaded playlists, 100 MB unpacked EPG, 100,000 parsed M3U channels and 250,000 upcoming guide programmes; there is no paid playlist quota.
