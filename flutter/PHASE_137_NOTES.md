# Phase 137 — dependency drift

Lane D of a four-lane round. **Scope: `pubspec.yaml` and `pubspec.lock` only.**
Nothing under `lib/` or `test/` is touched, which is what makes this lane safe
to run beside three lanes editing providers, repositories, screens and the Drift
DAO. Where an upgrade needed a source change, the package was pinned back and
the required change written down here instead.

`flutter_riverpod`, `riverpod` and `go_router` were out of scope by instruction
and are now pinned exactly, so no resolution — direct or transitive — can drag
them. The brief for the round that does take them is at the end of this file.

---

## 1. What "89 packages behind" actually decomposes into

`CLAUDE.md` has quoted a raw count for several rounds ("84 a few rounds ago",
"89 now") without decomposing it. The raw count answers a question nobody is
asking. `flutter pub outdated` reports three genuinely different situations, and
only one of them is work anybody can do today.

**Before this phase — 89 behind latest:**

| Bucket | Count | What it means |
|---|---:|---|
| **Locked, but upgradable now** | 53 | `pubspec.lock` is behind what the *existing* constraints already allow. `flutter pub upgrade` takes them. No pubspec edit, no code change, no decision. |
| **Constrained below a resolvable version** | 18 | Needs a `pubspec.yaml` constraint change. This is the bucket that contains actual judgement — majors, coupled clusters, API breaks. |
| **Not reachable at any constraint** | 18 | The newest release needs a newer Dart/Flutter SDK than the pinned 3.44.8, or is held there by a parent that is itself blocked. No constraint you can write reaches it. |

**After this phase — 38 behind latest:**

| Bucket | Count | Change |
|---|---:|---|
| Locked, but upgradable now | **0** | All 53 taken. |
| Constrained below a resolvable version | **13** | 5 of the 18 closed; the remaining 13 are documented below with a reason each. |
| Not reachable at any constraint | **25** | Grew by 7 — see the next paragraph, this is not a regression. |

**Why the third bucket grew, and why that is correct.** Taking a lock-only
upgrade frequently just moves a package from bucket 1 to bucket 3. `analyzer`
was "locked at 10.0.1, upgradable to 13.0.0, latest 14.3.0": taking 13.0.0 moved
it out of bucket 1 and into bucket 3, because 14.3.0 needs an SDK this repo
cannot have. Same for `dart_style` (3.1.7 → 3.1.9, latest 3.1.13) and five
others.

The practical consequence, which is the useful thing to take away: **on a pinned
Flutter SDK the headline number has a floor, and 38 is close to it.** 25 of the
remaining 38 are unreachable until `flutter.yml` and
`.claude/hooks/session-start.sh` move off 3.44.8 together, and 2 of those 25
(`js`, `flutter_secure_storage_macos`) are discontinued packages that will never
move at all. Quoting the raw number as debt overstates it by roughly a factor of
three. The number worth tracking is **bucket 2**, which is 13.

---

## 2. What was taken

One commit per upgrade or coherent group, so a bisect can attribute a
regression. The gate — `dart run build_runner build`, `dart format
--output=none --set-exit-if-changed lib test`, `flutter analyze`, `flutter test`
— was run to completion after **every** one of these, and was green each time:
462 files formatted with 0 changed, no analyzer issues, 2398 tests passing.

| Commit | What | Kind |
|---|---|---|
| `a52e007` | pin `flutter_riverpod` 2.6.1, `go_router` 14.8.1 | pubspec |
| `9dbaae8` | 33 runtime dependencies | lock only |
| `3197c37` | `drift` 2.34.3 → 2.34.4, `sqlite3` 3.5.0 → 3.5.2 | lock only |
| `c1dd525` | build/analysis toolchain, 19 packages | lock only |
| `8bb58f2` | `package_info_plus` ^8.1.1 → ^9.0.0 | **major**, pubspec |
| `a6fc8ae` | `flutter_map` ^7.0.2 → ^8.3.2, `latlong2` ^0.9.1 → ^0.10.1 | **major**, pubspec |
| `9f8c18e` | `proj4dart` 3.0.0, `mgrs_dart` 3.0.0, `unicode` 1.1.9 | **major**, lock only |

Three of these deserve a note.

**The toolchain commit (`c1dd525`) was the risky one.** `dart_style` and
`drift_dev` between them decide the layout of every generated file under `lib/`,
and CI gates on `dart format --set-exit-if-changed lib test`, which does not
exclude generated output. `analyzer` 10 → 13 (with `_fe_analyzer_shared` 93 →
100) and `dart_style` 3.1.7 → 3.1.9 could therefore have produced a tree-wide
reformat that looks like a correct commit and is not. It was verified by
deleting `.dart_tool/build`, regenerating from scratch, and formatting: 462
files, 0 changed. This also takes `build_daemon` off 4.1.3, which is a
**retracted** version. `drift_dev` could not move on its own — it wanted the
`analyzer` bump first, which is why it is here rather than with the drift
runtime commit.

**`schemaVersion` is untouched, still 46.** No lane was allocated a bump this
round. `drift` 2.34.3 → 2.34.4 is a patch: no DDL change, no migration step, and
regenerating confirms the output still matches.

**`sqlite3` 3.5.0 → 3.5.2 moves the download.** Its build hook fetches a
prebuilt binary and verifies a sha256 with no retry of its own, and the expected
hash table lives inside the package and moves with the version. Nothing in this
repo pins those hashes, so this is a version move only — but if a future CI run
goes red with a hash mismatch (as run `34129995451` did on 3.5.0, where the
fetched bytes hashed to `8e8052a0…`, matching no released version), that is the
download, not the commit. Re-run it.

**`flutter_map` 8 has no test behind it.** There is no widget test for
`lib/ui/maps/offline_maps_screen.dart` — it is the one upgrade in this branch
whose only evidence is `flutter analyze` rather than analyze *plus* a passing
test. Analyze does type-check the entire surface the screen uses (`FlutterMap`,
`MapOptions.initialCenter` / `initialZoom` / `onPositionChanged`, `TileLayer`,
`MapController`), so this is not blind, but it is thinner than everything else
here and worth a manual look the next time anyone runs the app.

---

## 3. What was not taken, and why

### 3a. The win32-6 cluster — blocked on four `lib/` files

`file_picker` 12, `flutter_secure_storage` 11 and `package_info_plus` 10 are
**one upgrade, not three.** All three sit on `win32 ^6`, and every package still
on `win32 ^5` blocks all of them:

```
package_info_plus >=10.0.0        -> win32 ^6.0.0
file_picker 11.0.2                -> win32 ^5.9.0
flutter_secure_storage 9.x        -> flutter_secure_storage_windows 3.1.2 -> win32 ^5
```

This is why `package_info_plus` stops at 9 in this branch: 9.0.1 is the highest
version that does not require `win32 ^6`. Resolving all three together succeeds,
and `flutter analyze` then reports exactly these errors:

| File | Line | Error | Cause |
|---|---:|---|---|
| `lib/core/prefs/planet_prefs.dart` | 33 | `The named parameter 'encryptedSharedPreferences' isn't defined` | `AndroidOptions.encryptedSharedPreferences` removed in `flutter_secure_storage` 10; the encrypted implementation became the only one. |
| `lib/core/system/file_pick.dart` | 45–50 | `The getter 'files' isn't defined for the type 'List<PlatformFile>'` (×2) | `FilePicker.pickFiles` returns `List<PlatformFile>` in v12, not `FilePickerResult?`. `.files` and the null check are both gone. |
| `lib/ui/personals/personals_screen.dart` | 312–317 | same, ×2 | same |
| `lib/ui/resources/add_resource_screen.dart` | 107–109 | `The getter 'paths' isn't defined for the type 'List<PlatformFile>'` (×2) | same, via `.paths` |

Plus two deprecation infos: `allowMultiple` is deprecated in favour of a
separate `pickFile` for the single-file case
(`lib/core/system/file_pick.dart:47`, `lib/ui/personals/personals_screen.dart:314`).

`lib/ui/teams/team_reports_screen.dart` uses `FilePicker.saveFile` and is
**unaffected** — that API did not change.

So the whole cluster is four files and roughly a dozen lines, all of it
mechanical. It is out of scope here only because it is source, not pubspec. It
is a good candidate for a small follow-up lane on its own, and it closes five of
the thirteen remaining bucket-2 entries plus `win32` and four
`flutter_secure_storage_*` transitives — nine of the thirteen, in one change.

One caution for whoever takes it: the secure-storage change is not just a
parameter deletion. `PlanetPrefs` is where the server PIN, the derived key and
the user's password live, and Phase 56 already has a scar from a credential
column being wiped. Confirm that dropping `encryptedSharedPreferences: true`
reads back values written by the 9.x implementation, or that the failure mode is
"prompt for the password again" rather than "locked out of offline login".

### 3b. `sqlite3_flutter_libs` 0.6.0+eol — refused, and this one is a trap

`0.6.0+eol` is not a normal release. Its changelog: *"Deprecate this package.
Starting from versions 3.x of the `sqlite3` package, `sqlite3_flutter_libs` is
no longer necessary. This version removes all code from this package."* The port
is already on `sqlite3` 3.5.2, so on paper the migration applies.

It resolves. It would almost certainly pass the gate. **That is the problem.**
`flutter.yml` runs `analyze` and `test` and never builds an APK for the port,
and `flutter test` runs on the Dart VM against the host's SQLite — so nothing in
CI, and nothing in this container, exercises the Android native build this
change is entirely about. A green run here would be evidence of nothing.

This container has no Android SDK at all (`flutter doctor`: *"Unable to locate
Android SDK"*), so I cannot produce the one piece of evidence that matters.
Refused and left at `^0.5.26`. Whoever takes it needs `flutter build apk` to
pass *and* the app to actually open a database on a device, before and after.
Worth doing — it is the upstream-recommended direction and removes a build-script
dependency — but not on analyze-and-test evidence.

### 3c. SDK-pinned — `build_runner`, `intl`, `meta`

```
build_runner >=2.15.2 -> analyzer >=13.3.0 -> meta ^1.18.3
flutter_test from sdk -> meta 1.18.0
```

Flutter 3.44.8 pins `meta` at 1.18.0, so `build_runner` stops at 2.15.1 and
`intl` at 0.20.2. Nothing to do at the constraint level; these move when the SDK
moves, and the SDK is pinned in two places that must change together
(`.github/workflows/flutter.yml` `flutter-version:` and
`.claude/hooks/session-start.sh`). The same pin is why 25 packages sit in bucket
3.

### 3d. The two pinned by instruction

`flutter_riverpod` and `go_router` — section 4.

---

## 4. Brief for the solo round: Riverpod 3 and go_router 18

Both were measured, not guessed: each was resolved in this container, analyzed,
and (for go_router) run against the full suite, then reverted. Neither is
committed.

### go_router 14.8.1 → 18.0.1 — **this is a one-line change**

The finding that matters, and it contradicts how `CLAUDE.md` frames this:

```
flutter analyze  -> No issues found!
flutter test     -> All tests passed!  (2398)
```

Zero source changes. Zero test changes. Zero failures. The constraint edit is
the entire diff.

Why it is that cheap, from the changelogs against the port's actual usage:

- **18.0.0** raises the floor to Flutter 3.44 / Dart 3.12 — which is *exactly*
  what is pinned here (3.44.8 / Dart 3.12.2). It migrates to the `material_ui`
  and `cupertino_ui` packages and breaks no routing API.
- **17.0.0** makes `ShellRoute` navigator changes notify `GoRouter`'s observers
  by default, adding `notifyRootObserver`. The port registers no router
  observer, so the changed default is unobservable here.
- **16.0.0** changes `GoRouteData` method signatures and **requires
  `go_router_builder >= 3.0.0`** — the port has no `go_router_builder` and zero
  `TypedGoRoute` (confirmed by grep), so this whole breaking change misses it.
- **15.0.0** makes URLs case-sensitive by default (`caseSensitive`, default
  `true`). Every one of the port's 78 `GoRoute` paths is lowercase and every
  navigation goes through `context.go`/`push` with those same literals, so the
  new default changes nothing. **This is the one to re-check if a route is ever
  added with a capital letter in it.**

Port surface, for the record: 78 `GoRoute`, 4 `ShellRoute`, 3
`StatefulShellRoute`, 1 `redirect`, 1 `refreshListenable`, 9 `GoRouterState`,
29 `state.pathParameters`, 18 `state.uri`, 1 `state.extra`, 24 `context.go`,
86 `context.push`, 19 `context.pop`, in a 683-line `lib/ui/router.dart`.

**Recommendation: take it.** It does not need a solo round, it does not need to
wait for Riverpod, and it does not need to be bundled with anything. It was left
out of this branch only because the lane brief named it out of scope, and a lane
that quietly relitigates its own boundary is worse than a lane that leaves an
easy win on the table with the evidence attached. Any integrator can apply it in
one line and re-run the gate.

### flutter_riverpod 2.6.1 → 3.4.3 — **261 analyzer errors across 81 files**

Resolved and analyzed in this container. `261` errors, `81` files (61 under
`lib/`, 20 under `test/`), and they account for themselves exactly:

| Errors | Cause | Fix |
|---:|---|---|
| **164** | `AsyncValue.valueOrNull` is gone; renamed to `.value` | search-and-replace |
| **78** | `StateProvider` (20), `StateNotifierProvider` (4), `StateNotifier` base class, and the `state` / `mounted` / `dispose` members that come with it moved to `package:riverpod/legacy.dart` | add one import per file |
| **14** | `Override` is no longer exported from `riverpod.dart`; it lives in `package:riverpod/misc.dart` | add one import per file |
| **2** | `Ref` is now a `sealed class` and cannot be implemented outside its library — `test/ui/notification_tap_scope_test.dart:233` (`_NoRef`) and `test/providers/challenge_provider_test.dart:170` (`_DummyRef`) | rewrite both fakes |
| **2** | `AsyncValue` is sealed, so a `switch` over it must be exhaustive — `lib/providers/events_provider.dart:34`, `lib/providers/surveys_provider.dart:34` | add a wildcard or the missing case |
| **1** | `double` where `int` is expected — `lib/ui/user/profile_screen.dart:347` | one line |

Concentration: `lib/providers/health_provider.dart` alone carries 40 (it holds
the port's only `StateNotifier` subclasses), then `lib/ui/dashboard/home_screen.dart`
16, `test/providers/health_provider_test.dart` 10,
`lib/providers/notifications_provider.dart` 10, `lib/providers/teams_provider.dart` 9.

Scale of the surface being migrated: **262 top-level provider declarations**
across 38 files (254 of them in `lib/providers/`), 521 `ref.watch`, 388
`ref.read`, 5 `ref.listen`, 23 `ref.invalidate`, 22 `ref.onDispose`, 71
`ConsumerWidget` and 42 `ConsumerStatefulWidget`, and — the number that decides
how long this takes — **627 `overrideWith` and 121 `overrideWithValue`** across
the test tree. The port uses no `riverpod_generator` and no `@riverpod`
annotations, so there is no codegen migration; every provider is hand-declared,
which makes the work tedious rather than subtle.

**The compile errors are the easy part. Four things will not show up in
`flutter analyze` and are where the round will actually be spent:**

1. **Providers now retry on failure automatically**, with exponential backoff
   (200ms initial, up to 6.4s), configurable per container or per provider via
   `retry`. This port has its own deliberate retry semantics in several places —
   `RetryQueue`, the outbox drainer, `saveKeyIv`'s explicit 3 attempts, and the
   sync walks' `hadBatchFailure` rule that decides whether a `deleteNotIn`
   cleanup is safe to run. A silent framework retry underneath any of those
   changes behaviour that four separate phases were spent getting right.
   **Decide the `retry` policy explicitly, per provider, rather than inheriting
   the default.**
2. **Errors are wrapped in `ProviderException`.** Only 6 `throwsA` sites in the
   test tree, so the blast radius looks small — but the port's failure paths
   mostly branch on a caught error rather than assert on a thrown one, and those
   `catch` blocks will now see a wrapper.
3. **Refs and Notifiers cannot be touched after disposal**, and `Ref.mounted`
   is the new guard. The port has 98 `await …future` sites and a documented
   habit (four phases' worth) of awaiting a provider inside a screen's `try`.
   Every one of those is an async gap that now needs a mounted check.
4. **Subscriptions pause when the widget is not visible** (via `TickerMode`).
   The dashboard's sync-center providers and the notification bell are watched
   from tabs that are frequently off-screen.

Also worth flagging, because it cuts against a rule this project relies on:
renaming `valueOrNull` → `value` **destroys the grep signal** behind
`CLAUDE.md`'s own rule — *"when you touch a screen, grep it for `.valueOrNull`
before anything else"*. That rule exists because five separate defects were a
screen reading a provider it never watches. After the rename, the tell is
`.value`, which is far noisier. Update the rule in `CLAUDE.md` in the same round,
or the next instance of that bug gets harder to find, not easier.

**Recommendation: still a solo round, and still not urgent.** Nothing is broken
at 2.6.1. But the two-line summary for whoever schedules it is: *the mechanical
part is 261 errors that decompose into four search-and-replaces, and the real
work is auditing 262 providers against automatic retry, error wrapping, stricter
disposal and visibility-based pausing.* Take go_router first and separately — it
is free, and bundling it into the Riverpod round only makes that round's bisect
worse.
