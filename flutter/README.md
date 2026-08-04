# myPlanet — Flutter port

A Flutter/Dart port of the myPlanet Android app in [`../app`](../app). The Kotlin app is
unchanged and still the shipping app; this directory is an in-progress migration.

**Read [`../docs/kotlin-to-flutter-migration.md`](../docs/kotlin-to-flutter-migration.md) first** —
it covers scope, the technology mapping, what is deliberately not improved, and what remains.

## Status

Phases 1–27 provide server configuration, online/offline login, resources, courses, shelf
write-back, the dashboard shell, calendar, first-launch onboarding, an offline-editable user
profile, persisted appearance settings, safe server details, an offline dictionary, reactive
notifications, personalized My life/reference navigation, offline personal items, course
ratings with upload, offline submission creation/durable upload/list/detail/question-aware answer review/PDF export,
offline events/meetups, individual surveys, voices/discussions, teams/enterprises management,
AI chat conversations, user feedback/review system, community/nation tabs, graded course exams,
resource viewer with download path, My health with AES-256-CBC encryption, and offline maps.
**27 of 28 UI packages have a screen**, offline-first, against the real CouchDB API.

Phase 13 added the durable write-back path — an `outbox` table replacing `RetryQueue`, drained
on app resume by `OutboxDrainer` instead of by `WorkManager`. Writes made offline survive
process death and go out on the next launch; what they do *not* do is send while the app is
closed. See [the migration tracker](../docs/kotlin-to-flutter-migration.md) for why that trade is
the right one here and what is still open.

Phase 15 completes offline events: meetup persistence and paginated CouchDB sync,
list/detail/create/edit and date/time screens, search/sort, join/leave shelf write-back, and
durable outbox uploads are now Dart/Drift code.

Phase 16 adds individual surveys: the exams catalog and embedded questions sync into Drift,
users can search, sort, answer text and choice questions offline, and completed responses enter
the durable submissions outbox.

Phase 24 adds My health: the health profile form and examination form with AES-256-CBC
encryption matching the Kotlin's `AndroidDecrypter` scheme, preserving `users.key`/`users.iv`
across schema upgrades.

Phase 25 adds offline maps, storage management, become-a-member, shared components, and the
ratings upload path:
- **maps**: `OfflineMapsScreen` using `flutter_map` with OpenStreetMap tiles, porting the Kotlin's
  OSMDroid-based `OfflineMapsActivity`
- **storage**: `StorageBreakdownScreen` and `StorageCategoryDetailScreen` for per-category file
  sizes and deletion
- **user**: `BecomeMemberScreen` with offline account creation (server sync pending)
- **ratings**: `RatingsUploader` completes the ratings write-back path that existed in Kotlin
  but had no Flutter caller

Phase 26 adds chat and feedback sync-in:
- **chat**: `ChatRepository.sync()` fetches `chat_history` from CouchDB, calling the previously
  uncalled `insertChatHistoryFromSync`
- **feedback**: `FeedbackRepository.sync()` fetches `feedback` from CouchDB, calling the previously
  uncalled `insertFromJson`

Phase 27 adds team voices:
- **team voices**: `TeamVoicesScreen` shows discussion posts scoped to a specific team, with
  `teamVoicesProvider` filtering by team ID in `viewIn`, and `createTeamPost()` for composing
  team-specific posts

## Getting started

```bash
flutter pub get

# Generated sources (Drift, gen-l10n) are gitignored — build them first.
dart run build_runner build
flutter gen-l10n

flutter analyze
flutter test
flutter run
```

## Layout

```
lib/
├── core/          # Pure Dart — no package:flutter imports
│   ├── config/    # ServerConfig
│   ├── crypto/    # PBKDF2 + the AndroidDecrypter credential check
│   ├── network/   # NetworkResult
│   ├── prefs/     # SharedPrefManager / SecurePrefs equivalent
│   ├── sync/      # ServerUrlMapper, AdaptiveBatchProcessor
│   └── utils/     # UrlUtils, JsonUtils, VersionUtils
├── data/
│   ├── api/       # Dio client (replaces the Retrofit ApiInterface)
│   └── local/     # Drift tables, DAOs, CouchDB document mappers
├── repository/    # Configurations, User, Resources, Courses, Shelf
├── providers/     # Riverpod graph (replaces the Hilt modules)
├── ui/            # Screens + go_router
└── l10n/          # .arb files (replaces res/values-{lang}/strings.xml)
```

`lib/core/` must stay free of `package:flutter` imports so it is testable without a widget
binding. Every ported file names its Kotlin counterpart in its doc comment.

## Configuration

Server mirror mappings are supplied at build time rather than committed (see the
committed-secrets note in `../CLAUDE.md`):

```bash
flutter run --dart-define=PLANET_SERVER_MAPPINGS=http://a.example=https://a-clone.example
```

## CI

`.github/workflows/flutter.yml` runs format, analyze, test, and a debug APK build. It is
path-filtered to `flutter/**`, so it does not run for Kotlin-only changes and does not affect
the existing `build.yml` / `test.yml`.
