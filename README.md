# Smart IPTV · Android

Native Kotlin / Jetpack Compose IPTV player for Android phones, tablets and TV boxes. Dark interface with mint accents and localization in 12 languages. The app includes no channel package, IPTV subscription, search engine for pirated content, or promises of a fixed channel count.

## Free product model

All player features are free. The product has no Premium/Pro tier, paywall, app subscription, in-app purchase, trial period or five-minute viewing allowance. Playback, playlists, favorites, EPG, background playback, picture-in-picture, Cast and external-player integration are available without an app purchase. There is no paid playlist or channel quota.

Keep this model in future development unless the user explicitly changes the product direction. Google Play Billing and paid-access controls are outside the current product scope. File/parser safety limits protect app stability and are unrelated to payment. IPTV provider access and any provider account requirements remain separate from the free player.

## Implemented

- M3U / M3U8 / M3U Plus by URL and Android document picker; channel names, groups, logos, `tvg-id`, `url-tvg` / `x-tvg-url`, relative URLs, `#EXTGRP`, VLC User-Agent / Referer and URL header suffixes.
- HLS manifests open as **one stream**, not as a list of segments or quality variants.
- Xtream Codes server / username / password: authenticated Live TV, movie VOD, series categories and episode playback. `Xtreme Play` is a competitor service name, not a bundled service in this app.
- Multiple playlists, search, playlist and category filters, favorites, watch history and VOD resume. Refresh replaces content atomically while preserving channel history and favorites. Deleting a playlist removes its associated content and history.
- Illustrated three-step onboarding explains where playlists come from, browsing/favorites/resume and Google Cast. Source connection has separate method, details and preview steps; channel/category counts and sample titles are shown before confirmation. Cancelled drafts are not saved. Individual stream compatibility is checked at playback, not implied by a successful playlist import.
- Continue watching shows up to eight movies/videos/episodes with a saved position. After import, the new source and its available content type are selected automatically. Empty library, favorites, history and filtered results have distinct guidance.
- SQLite catalogue pages of 200 items, debounced Unicode search and indexed filters. Browsing reads channel metadata without decrypting every stream URL. Existing installations migrate in place; encrypted stream details are resolved when playback starts.
- XMLTV / XMLTV.gz guide, matching `tvg-id`, programme schedule, manual guide refresh or custom EPG URL.
- Media3 ExoPlayer: HLS, DASH and progressive media, adaptive quality, audio / subtitle / quality selection, player control lock, brightness, volume, sleep timer, landscape, picture-in-picture, optional background playback with media session notification and audio focus.
- Network recovery waits for connectivity, then makes at most five attempts with 1/2/4/8/16-second delays. Pause, cancellation, sleep timer and channel changes stop recovery. Authentication, missing-stream, codec and DRM failures have separate messages. Previous/next Live TV controls and channel keys navigate within the current playlist/group.
- Playback checkpoints are queued off the UI thread and retain their event time; delayed writes cannot recreate history for deleted channels.
- Google Cast sender with default receiver, device picker, session transfer and expanded TV controller. Requires Google Play services, a compatible receiver on the same network and a receiver-accessible stream. This sends media URLs; it does not mirror the entire phone screen. Local files and custom-header streams require a separate receiver/server implementation and remain local.
- External player chooser for compatible media; local audio, video and image files via Storage Access Framework. No broad storage permission.
- Android TV launcher / banner, landscape support and focusable Compose controls. Phones and TV boxes require Android 8.0+; codec and 4K support depend on device and stream.
- Adaptive side navigation on TV and wide tablets, larger TV type, visible focus outlines and initial focus after layout attachment. D-pad activation is covered in local Compose tests; real remote/receiver behavior still requires hardware. Player control lock blocks channel keys and focuses the unlock action on TV.
- Android Keystore AES-GCM encryption for provider credentials, stream URLs and request headers; disabled device backups; no source credentials in logs or error messages.

## Localization

Complete app resources (166 strings and 2 plural resources per locale) are available in English, Russian, Spanish, German, French, Brazilian Portuguese, Italian, Japanese, Korean, Simplified Chinese, Turkish and Arabic. Onboarding artwork labels, source connection, library, settings, playback tools, accessibility labels and errors use the same resources. Provider titles, categories and programme descriptions retain their original language.

The interface follows the device language. On Android 13+, choose a different app language in Android Settings → Apps → Smart IPTV → Language. `locale_config.xml` declares only fully translated app locales; English is the fallback. There is no custom in-app language selector. Android 8–12 follow the system language.

Arabic uses mirrored layouts and six quantity forms; URL and EPG input remain left-to-right. Dates, times, resume positions and track-language names use the active app locale, including when it differs from the system language. Settings text can scroll, and onboarding artwork leaves space for long translated labels.

`python3 Scripts/check-localization.py` checks resource parity, numbered format arguments, required plural forms and the declared languages. The same check runs in CI and `Scripts/validate.sh`. Runtime tests verify packaged resource selection for all 12 locales, including Simplified Chinese in China/Singapore and Arabic quantities. UI tests cover German on a 320 dp phone, Japanese, Arabic source entry and right-to-left TV navigation. Preview renders are included in the CI UI artifact. Translations have not been reviewed by native speakers; real-device font and large-text checks remain on the release checklist.

## Public playlists for manual QA

Debug builds offer **Add a source → Try a public playlist → Russia / United States / Relax → preview → Add to my library**. Selecting a sample downloads the M3U using the normal source importer. The preview shows channel/category counts and sample titles; nothing is saved until confirmation. Back, cancellation and failed downloads do not save a source. A failed download leaves the URL form available for correction/retry. There is no automatic download at app launch or playback of preview entries.

The sample catalogue lives in `app/src/debug/java/.../PublicPlaylistCatalog.kt`. The release source set supplies an empty catalogue, so the public-playlist button and predefined URLs are excluded from the release variant. CI compiles both variants and uploads the release catalogue class with test reports. The rest of the player remains fully free.

The upstream project is [iptv-org/iptv](https://github.com/iptv-org/iptv); its [playlist directory](https://github.com/iptv-org/iptv/blob/master/PLAYLISTS.md) lists these URLs:

| Sample | M3U URL |
| --- | --- |
| Russia | https://iptv-org.github.io/iptv/countries/ru.m3u |
| United States | https://iptv-org.github.io/iptv/countries/us.m3u |
| Relax | https://iptv-org.github.io/iptv/categories/relax.m3u |

These are community-maintained external sources; individual channels can change, fail or have region restrictions. Playlist download/parse success does not establish playback or Cast compatibility. Use content you are authorized to access. Automated tests serve the repository's small synthetic `app/src/test/resources/public-playlist.m3u` through MockWebServer, without calling GitHub or any live channel. They cover deferred download, preview-before-save, cancellation, retry, localized selection, TV remote navigation and hiding the entry when the catalogue is empty.

## Build

Use Android Studio with **JDK 17**, Android SDK **36** and build tools **35.0.0**. Package/application ID: `com.sultonovmuzafar.smartiptv`.

```bash
python3 Scripts/check-localization.py
./gradlew :core:selfTest :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Release signing keys are deliberately not stored in the repository.

Core regression tests also run without Android SDK or Gradle:

```bash
bash Scripts/test-core.sh
```

The executable suite checks M3U Plus metadata and headers, duplicate/invalid filtering, relative URLs, HLS master/media distinction, Xtream URL encoding, stable channel IDs, bounded retry policy, XMLTV timezones, external-entity protection and a 20,000-channel catalogue. Android unit tests cover source imports, database migration/rollback/history, paged catalogues with 20,000 and 50,000 channels, error classification and an Android 8 framework activity-launch smoke test. Additional tests exercise draft cancellation, confirmation, import destinations, bounded resume results, onboarding and D-pad menu/action activation. Native Robolectric renders are written to `app/build/reports/ui/` and uploaded by CI as `smart-iptv-ui-preview`.

## Test on a device

1. Complete onboarding → choose a source method → paste your authorized M3U URL, choose a playlist file or enter Xtream details → check source → review the preview → Add to my library. Go back/cancel at preview and confirm that nothing is added.
2. Watch → pick Live TV / Movies / Series / My media, filter a category, search and start playback.
3. Add favorites; reopen the app and confirm favorites and recent history persist. Refresh the source and confirm they remain.
4. For Xtream series, choose a title and episode. Leave VOD playback and open Continue watching to check resume. Finish a title and confirm it is no longer offered as unfinished.
5. Playlists → EPG to fetch a guide; add a custom XMLTV URL if necessary. Use the programme icon beside a live channel.
6. Player → tools: brightness, volume, timer, external player; subtitles icon: audio, quality and captions; lock: only unlock remains active.
7. Settings → background playback / automatic PiP. Test Home, headphone disconnect and another app requesting audio focus.
8. Connect a Chromecast / Google Cast TV, select a receiver and confirm local playback pauses after successful receiver loading. Test remote controls, disconnect and return to device.
9. Install on a TV box; verify D-pad focus, selection, Back, source entry and player controls with the actual remote. Check onboarding action focus, side-menu navigation, focus outlines and unlocking with the remote after locking the player.
10. During Live and VOD playback, toggle Wi-Fi/network access. Check recovery, VOD position, pause/cancel during recovery, the five-attempt limit, sleep timer and manual retry. Confirm 401/403, 404, codec and DRM failures do not retry indefinitely.
11. Use previous/next and remote channel keys; check group boundaries, wrapping, title/EPG/Cast metadata, rapid changes and return from PiP. Import a large playlist, search Cyrillic names and literal `%` / `_`, scroll through multiple pages and remove a source during playback.
12. Switch through all supported languages; on Android 13+ change the app language while keeping the device in English. Check onboarding, import, errors, settings, EPG dates, subtitle/audio selection and notification controls. In Arabic, check right-to-left navigation, mixed-language titles, URL editing and quantities 0/1/2/3/11/100. Repeat with large system text and on a physical TV remote.
13. In a debug build, open the public-playlist picker. Select Relax, inspect the preview, go back twice and confirm that the library remains unchanged. Select a list again, confirm the import, then test a reachable channel, favorites, search, refresh and Cast on your device. Check an offline/failed download and retry. Release builds should offer only the normal source connection methods.

See [QA.md](QA.md) for validation status and remaining device checks. Safety limits are 50 MB downloaded playlists, 100 MB unpacked EPG, 100,000 parsed M3U channels and 250,000 upcoming guide programmes; there is no paid playlist quota.
