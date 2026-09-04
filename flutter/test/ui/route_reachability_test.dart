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

  test('every navigation this cannot read is a declared exception', () {
    // The blind spots, named rather than dropped. Phase 116's second pass
    // injected a broken target into three call sites this scanner could not
    // read and watched the suite stay green; two of the three are now read,
    // and what remains is listed here so it is reviewed rather than assumed
    // empty.
    const declared = <String, String>{
      // The location arrives as a `String` parameter. Its thirteen real
      // destinations are `Routes` constants in the same file, which rule three
      // reads.
      'lib/ui/dashboard/dashboard_drawer.dart': 'context.go(route)',
      // Built in a switch and handed over as a variable; the arms themselves
      // are read by rules two and three.
      'lib/ui/notifications/notifications_screen.dart': 'context.go(path)',
      // `'${Routes.addHealth}$patientQuery'` — the query string is built in a
      // local, so the prefix is checked and the suffix is not.
      'lib/ui/health/my_health_screen.dart': 'a query string in a local',
    };

    final undeclared = _unresolvedNavigations()
        .where((site) => !declared.containsKey(site.file))
        .toList();

    expect(
      undeclared,
      isEmpty,
      reason:
          'These call sites build a location this scanner cannot read, so no '
          'rule above covers them. Either make the target readable at the call '
          "site or add the file to this test's `declared` map with the reason:"
          '\n${undeclared.map((s) => '  $s').join('\n')}',
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

/// `/`-leading string literals in the navigating layers that are not in-app
/// locations. `/db` is CouchDB's path suffix; `/` is a path separator.
const _notALocation = <String>{'/', '/db'};

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
/// Three rules, which between them reach every navigation in the tree:
///
/// - the argument of a `context.push`/`go`/`replace`/`pushReplacement` call,
///   whether that is a string literal or a bare `Routes.x`;
/// - any string literal interpolating a `Routes.x` constant, wherever it sits;
/// - any bare `Routes.x` reference, wherever it sits.
///
/// The last two exist because a navigation argument is often not a literal at
/// the call. `dashboard_drawer.dart` collects thirteen destinations into a list
/// and calls `context.go(route)`; `notifications_screen.dart` builds a location
/// in a `switch` and hands it over as a variable, and two of its seven arms are
/// a bare constant rather than an interpolated string; `public_survey_screen`
/// passes a ternary of two constants. A rule that only read the call site saw
/// none of those four — this scanner did not, until Phase 116's second pass
/// injected a broken target into each and watched it stay green.
///
/// The cost of rule three is that a `Routes` constant merely *mentioned* in
/// `lib/` counts as reached, which makes rule 4 (below) slightly permissive.
/// That is the right trade: rule 4 guards against forgetting an entry point,
/// while rules 2 and 3 guard against a live navigation going nowhere, and the
/// second failure is the one that reaches a user.
///
/// A location this cannot resolve is recorded in [_unresolvedNavigations]
/// rather than dropped, because a silently skipped call site is how this class
/// survives.
List<_NavSite> _navigationSites() => _scan().sites;

/// Locations a call site builds that cannot be read from the source.
///
/// Kept as a channel rather than dropped on the floor: a silently skipped call
/// site looks exactly like a call site with nothing wrong, which is how a dead
/// navigation survives a guard that scans for them.
List<_NavSite> _unresolvedNavigations() => _scan().unresolved;

class _Scan {
  const _Scan(this.sites, this.unresolved);
  final List<_NavSite> sites;
  final List<_NavSite> unresolved;
}

_Scan _scan() {
  final constants = _routeConstants();
  final sites = <_NavSite>[];
  final unresolved = <_NavSite>[];

  // The *whole* argument list, taken by balancing parentheses. Reading only the
  // first token missed `context.go(session != null ? Routes.resources : ...)`
  // and every other shape where the location is not the literal that follows
  // the paren.
  final navCall = RegExp(
    r'context\s*\.\s*(?:push|go|replace|pushReplacement)\s*(?:<[^>]*>)?\(',
  );
  final literal = RegExp(r"'([^']*)'");
  final interpolated = RegExp(r"'([^']*\$\{Routes\.\w+\}[^']*)'");
  // A bare `Routes.x` not already inside an interpolation.
  final bareConstant = RegExp(r'(?<!\$\{)\bRoutes\.(\w+)\b');

  final files = Directory('lib')
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'))
      // The router declares patterns rather than navigating to them.
      .where((f) => !f.path.endsWith('ui/router.dart'));

  for (final file in files) {
    final source = _stripComments(file.readAsStringSync());
    final navigatingLayer =
        file.path.startsWith('lib/ui/') ||
        file.path.startsWith('lib/providers/');

    /// Records one location; returns whether it turned out to be one.
    bool record(int offset, String? raw) {
      if (raw == null) return false;
      final resolution = _resolve(raw, constants);
      final location = resolution.location;
      if (location == null || !location.startsWith('/')) return false;
      final site = _NavSite(file.path, _lineOf(source, offset), location);
      sites.add(site);
      if (resolution.partial) unresolved.add(site);
      return true;
    }

    for (final match in navCall.allMatches(source)) {
      final open = match.end - 1;
      final close = _matchingParen(source, open);
      if (close == -1) continue;
      final argument = source.substring(open + 1, close);

      // Every location the argument could evaluate to: each string literal and
      // each `Routes` constant in it. A ternary contributes both branches,
      // which is what we want — either may be navigated to.
      var found = false;
      for (final hit in literal.allMatches(argument)) {
        found |= record(open + 1 + hit.start, hit.group(1));
      }
      for (final hit in bareConstant.allMatches(argument)) {
        found |= record(open + 1 + hit.start, constants[hit.group(1)!]);
      }
      if (!found) {
        // A location handed over in a variable: `context.go(route)`. Its real
        // targets are assigned elsewhere in the file, where rules two and three
        // pick them up.
        unresolved.add(
          _NavSite(file.path, _lineOf(source, match.start), argument.trim()),
        );
      }
    }
    for (final match in interpolated.allMatches(source)) {
      record(match.start, match.group(1));
    }
    for (final match in bareConstant.allMatches(source)) {
      record(match.start, constants[match.group(1)!]);
    }
    // Rule four: any '/'-leading string literal, wherever it sits.
    //
    // This is what reaches a destination written as a raw literal and handed to
    // `context.go` through a variable or a data structure — the shape
    // `dashboard_drawer.dart` uses for its thirteen entries. Rule three reads
    // those only while they stay `Routes` constants; replace one with a typo'd
    // literal and nothing else here would see it.
    //
    // Scoped to the layers that navigate. A repository, a mapper or a core
    // utility builds URLs, disk paths and regexes out of '/'-leading strings
    // and never calls `context.go`, so reading those is all false positives —
    // four of them, measured. Within `ui/` and `providers/`, essentially every
    // such literal is an in-app location, and the few that are not are named
    // in [_notALocation] rather than inferred.
    if (navigatingLayer) {
      for (final match in literal.allMatches(source)) {
        final text = match.group(1)!;
        if (!text.startsWith('/') || _notALocation.contains(text)) continue;
        record(match.start, text);
      }
    }
  }
  return _Scan(sites, unresolved);
}

/// Substitutes `${Routes.x}` with the constant and reduces the rest to a path
/// the router can be asked about. Returns null when a `Routes` name does not
/// resolve, which only happens if this scanner and the router disagree.
class _Resolution {
  const _Resolution(this.location, {this.partial = false});
  final String? location;

  /// True when something had to be thrown away to reach a location, so the
  /// answer is the prefix rather than the whole thing.
  final bool partial;
}

_Resolution _resolve(String raw, Map<String, String> constants) {
  const unresolvable = '\u0000';
  var out = raw.replaceAllMapped(
    RegExp(r'\$\{Routes\.(\w+)\}'),
    (m) => constants[m.group(1)!] ?? unresolvable,
  );
  if (out.contains(unresolvable)) return const _Resolution(null);

  // Cut the query first, so an interpolated query *value* never has to be
  // resolved: `'${Routes.addResource}?edit=${resource.id}'` navigates to
  // `/resources/add`.
  final query = out.indexOf('?');
  if (query != -1) out = out.substring(0, query);

  // An interpolation following a '/' is one path segment's worth of runtime
  // value — an id, a tab name — and stands in as a single segment, because a
  // route matches on segment count and on its literal segments, and an id never
  // contains a '/'.
  out = out.replaceAll(RegExp(r'/(?:\$\{[^}]*\}|\$\w+)'), '/x');

  // Anything still interpolated is glued to the end of a literal segment
  // rather than forming one, which in this tree is always an optional query
  // string built elsewhere (`'${Routes.addHealth}$patientQuery'`). The prefix
  // is still worth checking, but the answer is partial: whatever that variable
  // holds is not read here, so a suffix that is *not* a query would go unseen.
  final glued = RegExp(r'\$\{[^}]*\}|\$\w+');
  if (glued.hasMatch(out)) {
    return _Resolution(out.replaceAll(glued, ''), partial: true);
  }
  return _Resolution(out);
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

/// The index of the ')' closing the '(' at [open].
int _matchingParen(String source, int open) {
  var depth = 0;
  for (var i = open; i < source.length; i++) {
    if (source[i] == '(') depth++;
    if (source[i] == ')') {
      depth--;
      if (depth == 0) return i;
    }
  }
  return -1;
}

int _lineOf(String source, int offset) =>
    '\n'.allMatches(source.substring(0, offset)).length + 1;

/// Strips `//` and `/* */` comments, so a route named in prose is not mistaken
/// for a navigation.
///
/// **String-aware, and that is not fussiness.** A naive `indexOf('//')` cuts
/// `route.startsWith('http://')` down to an unbalanced quote, and that exact
/// line sits one above the `/web-view` push this phase fixed
/// (`services_screen.dart:60-63`) — so the truncation silently swallowed the
/// navigation below it. Twelve other lines in `lib/` truncate the same way.
///
/// Newlines are preserved, including a multi-line block comment's, so the line
/// numbers in a failure message point at the real source.
String _stripComments(String source) {
  final out = StringBuffer();
  var quote = '';
  for (var i = 0; i < source.length; i++) {
    final char = source[i];
    if (quote.isNotEmpty) {
      out.write(char);
      if (char == r'\' && i + 1 < source.length) {
        out.write(source[++i]);
      } else if (char == quote) {
        quote = '';
      }
      continue;
    }
    if (char == "'" || char == '"') {
      quote = char;
      out.write(char);
      continue;
    }
    if (char == '/' && i + 1 < source.length) {
      // A line comment: drop to the newline, which is kept so line numbers and
      // the newline-counting in [_lineOf] stay accurate.
      if (source[i + 1] == '/') {
        while (i < source.length && source[i] != '\n') {
          i++;
        }
        out.write('\n');
        continue;
      }
      // A block comment: drop it but keep its newlines, for the same reason.
      if (source[i + 1] == '*') {
        final end = source.indexOf('*/', i + 2);
        final stop = end == -1 ? source.length : end + 2;
        out.write('\n' * '\n'.allMatches(source.substring(i, stop)).length);
        i = stop - 1;
        continue;
      }
    }
    out.write(char);
  }
  return out.toString();
}
