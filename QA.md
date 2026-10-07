# Validation · 2026-10-07

Validated with JDK 17, Android SDK 36, Build Tools 35.0.0, Gradle 8.13 / AGP 8.13.0.

| Check | Result |
| --- | --- |
| `:core:selfTest` | PASS: 30 assertions, including 20,000-channel import |
| `:app:testDebugUnitTest` | PASS: 5 tests, 0 failures, 0 errors |
| `:app:assembleDebug` | PASS: debug APK generated |
| `:app:lintDebug` | PASS: 0 errors; non-blocking style, dependency-version and resource warnings remain |
| EN / RU localization | 108 strings in each locale; no missing referenced keys |
| Gradle distribution | Official SHA-256 verified and pinned in wrapper |

The source-client tests use MockWebServer and verify authenticated Xtream Live / VOD / Series import, expired-account rejection, encoded credentials, provider-specific live output formats, episode ordering and extensions, playlist redirects, redacted access errors and payload limits.

Core tests verify quoted M3U Plus metadata, relative URLs, logos and EPG, duplicate and unsafe URL filtering, VLC headers, HLS manifests, Xtream URL construction, XMLTV timezone conversion and protection against external XML entity access.

No Android emulator, physical phone, TV box, real provider account or Cast receiver was available for playback testing. Build/test success is not a claim that a provider's codecs, DRM, account limits or receiver network access work on every device. Before release, complete the device checklist in README.md, especially D-pad navigation, playback, audio focus, background notification, picture-in-picture, sleep timer and real Cast handoff.

Known scope limits:

- Google Cast sends a supported remote media URL; whole-screen mirroring is not implemented.
- Default Cast receiver cannot receive local files or streams with custom request headers.
- XMLTV EPG refresh is manual. Imported playlist EPG URLs are detected automatically, but guide downloads are initiated from Playlists.
- Release signing, billing, advertising, analytics and provider subscriptions are not configured.
- The app contains no preloaded channel package. Add your own authorized playlist or provider account.
