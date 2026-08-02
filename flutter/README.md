# myPlanet — Flutter port

A Flutter/Dart port of the myPlanet Android app in [`../app`](../app). The Kotlin app is
unchanged and still the shipping app; this directory is an in-progress migration.

**Read [`../docs/kotlin-to-flutter-migration.md`](../docs/kotlin-to-flutter-migration.md) first** —
it covers scope, the technology mapping, what is deliberately not improved, and what remains.

## Status

Phases 1–4 provide server configuration, online/offline login, resources, courses, shelf
write-back, the dashboard shell, and calendar. **3 of 28 UI packages are ported**, offline-first,
against the real CouchDB API.

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
