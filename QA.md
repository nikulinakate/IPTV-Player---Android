# Validation · 2026-10-07

Validated with JDK 17, Android SDK 36, Build Tools 35.0.0, Gradle 8.13 / AGP 8.13.0.

| Check | Result |
| --- | --- |
| `:core:selfTest` | PASS: 42 assertions, including 20,000-channel import, stable IDs and bounded retry policy |
| `:app:testDebugUnitTest` | PASS: 42 tests, 0 failures, 0 errors |
| `:app:assembleDebug` | PASS: debug APK generated |
| `:app:lintDebug` | PASS: 0 errors; non-blocking style, dependency-version and resource warnings remain |
| Localization: 12 languages | PASS: 166 strings and 2 plural resources in each locale; no missing referenced keys; numbered parameters, required plural forms and declared languages match |
| Gradle distribution | Official SHA-256 verified and pinned in wrapper |

Product decision: the app is fully free. Source/dependency review found no billing SDK, Premium/Pro tier, paywall, paid entitlement or viewing-time allowance. The five-minute trial previously discussed is not implemented and is excluded from the product scope. References to subscriptions in stream-access errors refer to the external IPTV provider account.

The source-client tests use MockWebServer and verify authenticated Xtream Live / VOD / Series import, expired-account rejection, encoded credentials, provider-specific live output formats, episode ordering and extensions, playlist redirects, redacted access errors and payload limits.

Core tests verify quoted M3U Plus metadata, relative URLs, logos and EPG, duplicate and unsafe URL filtering, VLC headers, HLS manifests, Xtream URL construction, XMLTV timezone conversion and protection against external XML entity access.

Robolectric tests exercise SQLite paging with no secret reads, single-channel resolution, favorites and resume across refresh, transactional rollback, v1-to-v2 migration, Unicode/literal-wildcard search, group-scoped channel navigation and delayed checkpoints after source deletion. Playback tests distinguish network failures from permanent HTTP/format/DRM failures and verify retry limits and redacted UI state. The main activity launches under a simulated Android 8 framework; this does not test device rendering, codecs or Cast.

Product-flow tests verify that draft preparation/cancellation does not write a source, late cancelled results do not replace a newer draft, repeated confirmation does not duplicate a source, validation errors can be corrected and local/video imports select the correct destination. Resume queries return at most eight metadata-only unfinished videos/movies/episodes and exclude Live TV, images and completed titles.

Compose tests exercise onboarding forward/back/final confirmation, source-preview confirmation and D-pad activation of onboarding actions and the TV side menu. Phone library/resume placement and Russian final onboarding CTA visibility are also checked. Preview images are rendered with Robolectric native graphics via the Compose host view at 411×891 phone and 1280×720 TV configurations. These are framework renders for layout inspection, not emulator/device captures. CI uploads them as `smart-iptv-ui-preview`.

Localization covers EN, RU, ES, DE, FR, PT-BR, IT, JA, KO, ZH-Hans, TR and AR. Runtime resource tests resolve the final onboarding action in all 12 packaged locales, check Chinese selection in China/Singapore, Arabic zero/one/two/few/many/other quantities and locale-aware resume digits. Additional native UI tests verify German onboarding at 320×640, Japanese artwork/actions, Arabic onboarding direction, left-to-right URL input inside the Arabic form, localized source preview and D-pad selection in the mirrored TV rail. Visible CTA lines are checked for height overflow, truncation and line width rather than the paragraph's reserved layout width. Preview renders for these cases were visually inspected. Native-speaker translation review and real-device large-text/font checks remain outstanding.

Catalogue measurements from one successful local test run:

| Channels | Parse M3U | Import SQLite | First 200 items | Search | Catalogue secret reads |
| --- | --- | --- | --- | --- | --- |
| 20,000 | 463 ms | 2,940 ms | 71 ms | 33 ms | 0 |
| 50,000 | 436 ms | 1,512 ms | 62 ms | 133 ms | 0 |

These are desktop Robolectric SQLite measurements with an injected test vault. They exclude network download and real Android Keystore work, and are not phone performance guarantees. JVM/database warm-up affects the comparison. Tests assert catalogue correctness rather than fragile timing thresholds.

A downloaded Google/Shaka HLS initialization fragment plus its first segment passed local `ffprobe` inspection as H.264, 192×144. This checks the sample container only; Android playback still needs a device. The GitHub SDK-install step was updated to use the runner's installed command-line tools instead of requesting the obsolete `tools` package.

No Android emulator, physical phone, TV box, real provider account or Cast receiver was available for playback testing. Build/test success is not a claim that a provider's codecs, DRM, account limits or receiver network access work on every device. Before release, complete the device checklist in README.md, especially D-pad navigation, playback, audio focus, background notification, picture-in-picture, sleep timer and real Cast handoff.

Public-playlist QA adds an optional debug-only source picker for Russia, United States and Relax from iptv-org. It uses the normal import/preview/confirmation pipeline. Release sources define an empty sample catalogue. Six new UI scenarios exercise the real source importer with a synthetic M3U served by MockWebServer: no download before choosing, confirmation-only persistence, preview cancellation, download retry, TV navigation, Arabic labels and the hidden entry for an empty catalogue. Validation of this change is pending the GitHub Actions run; previous Android results above describe the preceding localization commit.

Known scope limits:

- Google Cast sends a supported remote media URL; whole-screen mirroring is not implemented.
- Default Cast receiver cannot receive local files or streams with custom request headers.
- XMLTV EPG refresh is manual. Imported playlist EPG URLs are detected automatically, but guide downloads are initiated from Playlists.
- Release signing, advertising, analytics and provider services are not configured. Billing, paywalls and paid tiers are excluded by the free product model.
- The app contains no preloaded channel package. Add your own authorized playlist or provider account.
