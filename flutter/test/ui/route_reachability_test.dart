import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/deeplinks/deep_link.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/deep_link_provider.dart';
import 'package:myplanet/ui/router.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Reachability guards — the failure class Phase 113 found the hard way.
///
/// `TakeExamScreen` was not broken, it was *unreachable*: its route was fine,
/// its tests were green, and no live path led to it. Nothing in the suite could
/// see that, because a screen test builds its screen directly and a repository
/// test builds its own rows. Both halves of the app — the route table and the
/// navigation calls — were only ever exercised one at a time.
///
/// These tests exercise them together, and they read the source rather than
/// enumerate a hand-written list, so a route or a `context.push` added later is
/// covered without anyone remembering to come back here.
///
/// Four rules:
///
/// 1. **Every `Routes` constant resolves to a registered route.** A constant
///    naming a path the router does not serve is a screen nobody can open.
/// 2. **Every navigation location in `lib/` resolves.** This catches a
///    `context.push` whose target drifted away from the route table.
/// 3. **No navigation carries an unsubstituted `:param`.** go_router matches a
///    placeholder against its own literal text, so this one fails silently.
/// 4. **Every registered route is navigated to from somewhere.** A route
///    nothing links to is a screen the user cannot reach; the allowlist is the
///    set of deliberate exceptions, each with its reason.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late ProviderContainer container;
  late GoRouter router;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final database = AppDatabase.memory();
    container = ProviderContainer(
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        appDatabaseProvider.overrideWithValue(database),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(database.close);
    router = container.read(routerProvider);
  });

  test('every Routes constant resolves to a registered route', () {
    final unresolved = <String, String>{};
    _routeConstants().forEach((name, path) {
      if (!_matches(router, _fillParams(path))) unresolved[name] = path;
    });

    expect(
      unresolved,
      isEmpty,
      reason:
          'These Routes constants name locations the router does not serve, so '
          'every navigation through them lands on the error page:\n'
          '${unresolved.entries.map((e) => '  Routes.${e.key} = ${e.value}').join('\n')}',
    );
  });

  test('every navigation location in lib/ resolves to a registered route', () {
    final broken = _navigationSites()
        .where((site) => !_matches(router, _fillParams(site.location)))
        .toList();

    expect(
      broken,
      isEmpty,
      reason:
          'These navigation calls target a location no route matches:\n'
          '${broken.map((s) => '  $s').join('\n')}',
    );
  });

  test('no navigation pushes an unsubstituted route pattern', () {
    // The Phase 113 defect-C shape: `'${Routes.exam}/${exam.id}'` concatenates
    // the *pattern* `/courses/exam/:examId`, and `context.push(Routes.chat)`
    // pushes one whole. go_router happily matches `:examId` against the literal
    // text `:examId`, so the screen opens with a path parameter whose value is
    // the placeholder's own name — a lookup that silently finds nothing.
    final offenders = _navigationSites()
        .where((site) => site.location.contains(':'))
        .toList();

    expect(
      offenders,
      isEmpty,
      reason:
          'These navigations carry a `:param` placeholder into a real '
          'location:\n${offenders.map((s) => '  $s').join('\n')}',
    );
  });

  test('every registered route is reachable from a navigation', () {
    /// Routes with no `context.push`/`go` in `lib/`, and why that is correct.
    const allowed = <String, String>{
      // Reached by the router's own redirect rather than by a navigation.
      '/onboarding': 'redirect target on a first launch',
      '/server': 'redirect target when no server is configured',
      '/login': 'redirect target when there is no session',
      '/home': 'initialLocation, and the redirect target once signed in',
      // Built by DeepLinkHandler.publicSurveyLocation, covered by the deep-link
      // test below.
      '/survey/:teamId/:surveyId': 'deep-link entry point',
      // Kotlin shows the respondent profile as a dialog over the survey and the
      // port keeps that shape, so `PublicSurveyScreen` builds the screen with
      // `Navigator.push` and there is no location to navigate to. The route is
      // a spare entry point kept for a future in-app caller.
      '/exam/user-info/:submissionId':
          'PublicSurveyScreen builds it with Navigator.push',
    };

    final reached = _navigationSites().map((s) => s.location).toSet();
    final unreachable = _registeredPaths(router)
        .where((path) => !allowed.containsKey(path))
        .where((path) => !reached.any((r) => _pathsMatch(path, r)))
        .toList();

    expect(
      unreachable,
      isEmpty,
      reason:
          'These routes are registered but nothing in lib/ navigates to them, '
          'so the screens behind them cannot be opened. Either add the entry '
          "point or record the route in this test's `allowed` map with its "
          'reason:\n${unreachable.map((p) => '  $p').join('\n')}',
    );
  });

  test('every deep-link section resolves to a registered route', () {
    // `deepLinkRoute` is the port of `DashboardActivity`'s
    // `when (fragmentToOpen)`. A section whose route stopped matching would
    // send an incoming link to the error page, with nothing inside the app to
    // say the link itself was fine.
    for (final section in const [
      'feedbackList',
      'courses',
      'resources',
      'teams',
      'surveys',
    ]) {
      final route = deepLinkRoute(section);
      expect(route, isNotNull, reason: 'deepLinkRoute("$section")');
      expect(
        _matches(router, _fillParams(route!)),
        isTrue,
        reason: 'deep link section "$section" -> $route',
      );
    }
  });

  test('a public-survey deep link resolves to its route', () {
    final location = DeepLinkHandler.publicSurveyLocation(
      const PublicSurveyDeepLink(
        teamId: 'team-1',
        surveyId: 'survey-1',
        origin: 'https://planet.example.org',
      ),
    );
    expect(_matches(router, location), isTrue, reason: location);
  });
}

// ---------------------------------------------------------------------------
// Source scanning
// ---------------------------------------------------------------------------

/// `static const String name = '/path';` in `lib/ui/router.dart`.
Map<String, String> _routeConstants() {
  final source = _stripComments(File('lib/ui/router.dart').readAsStringSync());
  final pattern = RegExp(r"static const String (\w+)\s*=\s*'([^']*)'\s*;");
  return {
    for (final match in pattern.allMatches(source))
      match.group(1)!: match.group(2)!,
  };
}

class _NavSite {
  const _NavSite(this.file, this.line, this.location);
  final String file;
  final int line;
  final String location;

  @override
  String toString() => '$file:$line -> $location';
}

/// Every in-app location `lib/` navigates to that can be resolved statically.
///
/// Two rules, which between them reach every navigation in the tree:
///
/// - the argument of a `context.push`/`go`/`replace`/`pushReplacement` call,
///   whether that is a string literal or a bare `Routes.x`;
/// - any string literal interpolating a `Routes.x` constant, wherever it sits.
///   This second rule reaches the locations built inside a `switch` and handed
///   to `context.go` through a variable — the shape a notification tap uses in
///   `notifications_screen.dart`.
///
/// A location built from a variable that is not a `Routes` constant cannot be
/// resolved statically and is skipped; the tree has none today.
List<_NavSite> _navigationSites() {
  final constants = _routeConstants();
  final sites = <_NavSite>[];

  // `context\s*\.\s*push` rather than `context.push`: a call with an `extra:`
  // argument is wrapped by the formatter onto its own line.
  final navCall = RegExp(
    r"context\s*\.\s*(?:push|go|replace|pushReplacement)\(\s*(?:'([^']*)'|Routes\.(\w+))",
    dotAll: true,
  );
  final interpolated = RegExp(r"'([^']*\$\{Routes\.\w+\}[^']*)'");

  final files = Directory('lib')
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'))
      // The router declares patterns rather than navigating to them.
      .where((f) => !f.path.endsWith('ui/router.dart'));

  for (final file in files) {
    final source = _stripComments(file.readAsStringSync());

    void record(RegExpMatch match, String? raw) {
      final resolved = raw == null ? null : _resolve(raw, constants);
      if (resolved == null || !resolved.startsWith('/')) return;
      sites.add(
        _NavSite(file.path, _lineOf(source, match.start), _normalize(resolved)),
      );
    }

    for (final match in navCall.allMatches(source)) {
      final constant = match.group(2);
      record(
        match,
        match.group(1) ?? (constant == null ? null : constants[constant]),
      );
    }
    for (final match in interpolated.allMatches(source)) {
      record(match, match.group(1));
    }
  }
  return sites;
}

/// Substitutes `${Routes.x}` with the constant and reduces the rest to a path
/// the router can be asked about. Returns null when a `Routes` name does not
/// resolve, which only happens if this scanner and the router disagree.
String? _resolve(String raw, Map<String, String> constants) {
  const unresolvable = '\u0000';
  var out = raw.replaceAllMapped(
    RegExp(r'\$\{Routes\.(\w+)\}'),
    (m) => constants[m.group(1)!] ?? unresolvable,
  );
  if (out.contains(unresolvable)) return null;

  // Cut the query first, so an interpolated query *value* never has to be
  // resolved: `'${Routes.addResource}?edit=${resource.id}'` navigates to
  // `/resources/add`.
  final query = out.indexOf('?');
  if (query != -1) out = out.substring(0, query);

  // An interpolation following a '/' is one path segment's worth of runtime
  // value \u2014 an id, a tab name \u2014 and stands in as a single segment, because a
  // route matches on segment count and on its literal segments, and an id never
  // contains a '/'.
  out = out.replaceAll(RegExp(r'/(?:\$\{[^}]*\}|\$\w+)'), '/x');

  // Anything still interpolated is glued to the end of a literal segment rather
  // than forming one, which in this tree is always an optional query string
  // built elsewhere (`'${Routes.addHealth}$patientQuery'`). Dropping it leaves
  // the path without the query, which is the location being navigated to.
  return out.replaceAll(RegExp(r'\$\{[^}]*\}|\$\w+'), '');
}

/// Drops the query string and any trailing slash.
String _normalize(String location) {
  final query = location.indexOf('?');
  var path = query == -1 ? location : location.substring(0, query);
  if (path.length > 1 && path.endsWith('/')) {
    path = path.substring(0, path.length - 1);
  }
  return path;
}

/// Replaces `:param` segments with a stand-in so a pattern can be matched.
String _fillParams(String path) =>
    _normalize(path).replaceAll(RegExp(r':[A-Za-z_]\w*'), 'x');

bool _matches(GoRouter router, String location) =>
    !router.configuration.findMatch(Uri.parse(location)).isError;

/// Full paths of every route in the table, patterns included.
List<String> _registeredPaths(GoRouter router) {
  final paths = <String>[];
  void walk(List<RouteBase> routes, String parent) {
    for (final route in routes) {
      var full = parent;
      if (route is GoRoute) {
        full = route.path.startsWith('/')
            ? route.path
            : '$parent/${route.path}'.replaceAll('//', '/');
        paths.add(full);
      }
      walk(route.routes, full);
    }
  }

  walk(router.configuration.routes, '');
  return paths;
}

/// Whether a concrete location reaches a route pattern: same segment count,
/// with every literal segment equal.
bool _pathsMatch(String pattern, String location) {
  final expected = pattern.split('/');
  final actual = location.split('/');
  if (expected.length != actual.length) return false;
  for (var i = 0; i < expected.length; i++) {
    if (expected[i].startsWith(':')) continue;
    if (expected[i] != actual[i]) return false;
  }
  return true;
}

int _lineOf(String source, int offset) =>
    '\n'.allMatches(source.substring(0, offset)).length + 1;

/// Strips `//` and `/* */` comments, so a route named in prose is not mistaken
/// for a navigation. Offsets are preserved within a line so line numbers in the
/// failure message stay accurate.
String _stripComments(String source) => source
    .replaceAll(RegExp(r'/\*.*?\*/', dotAll: true), '')
    .split('\n')
    .map((line) {
      final index = line.indexOf('//');
      return index == -1 ? line : line.substring(0, index);
    })
    .join('\n');
