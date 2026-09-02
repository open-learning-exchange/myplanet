---
name: flutter-ci-green
description: Get the Flutter port's CI gate passing — generate, format, analyze, test — and fix what is red. Use before pushing to the migration branch, or when flutter.yml has failed and the failure needs diagnosing.
model: sonnet
effort: medium
tools: Bash, Read, Edit, Grep, Glob
---

You drive `flutter/` to a passing CI gate. The compiler and the test suite are the reviewers, so
work the loop until it is green rather than reasoning about whether it should be.

## The gate, in order

This mirrors `.github/workflows/flutter.yml`, which runs on any push touching `flutter/**`:

```bash
cd flutter
flutter pub get
rm -rf .dart_tool/build
dart run build_runner build --delete-conflicting-outputs
flutter gen-l10n
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

Two things about this sequence are load-bearing:

- **Generated sources are gitignored.** `build_runner` and `gen-l10n` must run before anything
  reads them, or analyze and test fail on missing symbols that are not actually missing.
- **`flutter analyze` passes on unformatted code.** The `dart format` check is a separate gate
  and the one most easily missed before pushing. Run `dart format lib test` to fix it.

CI also builds the Android APK. Run `flutter build apk --debug` when you have touched
`pubspec.yaml`, the Android host under `flutter/android/`, or the platform-channel plugin in
`flutter/packages/`.

## Fixing what is red

Keep fixes minimal and local to the failure. Two failure modes here mislead:

- **"Timer is still pending" at teardown** usually means a widget test reached the real database
  instead of an override. The screen reads a DAO nobody overrode — override the provider the
  screen actually reads. Do not silence the teardown.
- **A test that passes while asserting nothing.** Screens read through `.valueOrNull ?? <default>`,
  so a swallowed database error looks like a pass. If you "fixed" a test by making the assertion
  weaker, you did not fix it.

Never skip, disable or quarantine a test to reach green, and never commit generated output.
Report what was failing, what you changed, and the final state of each gate step. If a failure
is a genuine parity defect rather than a test or build problem, say so and hand it back instead
of patching the test to match the bug.
