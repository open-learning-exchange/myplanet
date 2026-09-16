import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/deeplinks/deep_link.dart';
import 'package:myplanet/core/notifications/notification_config.dart';
import 'package:myplanet/core/notifications/notification_tap.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/deep_link_provider.dart';
import 'package:myplanet/providers/notification_tap_provider.dart';
import 'package:myplanet/ui/notifications/notification_destination.dart';
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
/// Five rules over the route table:
///
/// 1. **Every `Routes` constant resolves to the route it names.** Not merely
///    to *some* route — to the one whose pattern is that constant.
/// 2. **No registered route is shadowed by an earlier sibling.** Exhaustive
///    over the table, including routes no `Routes` constant names.
/// 3. **Every navigation location in `lib/` resolves.** This catches a
///    `context.push` whose target drifted away from the route table.
/// 4. **No navigation carries an unsubstituted `:param`.** go_router matches a
///    placeholder against its own literal text, so this one fails silently.
/// 5. **Every registered route is navigated to from somewhere.** A route
///    nothing links to is a screen the user cannot reach; the allowlist is the
///    set of deliberate exceptions, each with its reason.
///
/// **Rules 1 and 2 are the Phase 157 hardening, and they exist because the
/// first four were green while a screen was unreachable.** Every rule here
/// used to ask `_matches` — whether *some* route serves a location — and never
/// which one. `/life/feedback/create` matched, as the **detail** route, because
/// `:feedbackId` was declared above `create` and go_router takes the first
/// match; so `FeedbackDetailScreen(feedbackId: 'create')` rendered "Feedback
/// not found", filing feedback was impossible from either of its two buttons,
/// and this file said nothing. The blind spot was the width of every literal
/// sibling declared after a path parameter.
///
/// And two over the entry points that do not go through `context.go` at all —
/// the deep link, and (Phase 130) the **system-tray notification tap**. A tray
/// tap is the same failure class arriving from outside the widget tree: the
/// port raised notifications for four phases and tapping one did nothing,
/// which no route test could see because there was no navigation call to scan
/// for. So these two exercise the real handler rather than the source text.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late ProviderContainer container;
  late GoRouter router;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final database = AppDatabase.memory();
    container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        appDatabaseProvider.overrideWithValue(database),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(database.close);
    router = container.read(routerProvider);
  });

  test('every Routes constant resolves to the route it names', () {
    // The assertion is `fullPath`, not `isError`. A constant must resolve to
    // the route whose pattern *is* that constant; resolving to some other
    // route is the shape that made `Routes.feedbackCreate` dead.
    final wrong = <String, String>{};
    _routeConstants().forEach((name, path) {
      final won = _winningPattern(router, _fillParams(path));
      if (won != _normalize(path)) {
        wrong[name] = '$path resolves to ${won ?? 'no route at all'}';
      }
    });

    expect(
      wrong,
      isEmpty,
      reason:
          'These Routes constants do not resolve to the route they name, so a '
          'navigation through them opens a different screen, or none:\n'
          '${wrong.entries.map((e) => '  Routes.${e.key}: ${e.value}').join('\n')}',
    );
  });

  test('no registered route is shadowed by an earlier sibling', () {
    // Rule one over the *table* rather than over the constants, so a route
    // declared inline with no `Routes` constant is covered too — it is
    // shadowed in exactly the same way and rule one would never look at it.
    //
    // For each registered pattern, build the most ordinary location that
    // pattern serves — its own text, with every `:param` filled by a stand-in
    // no literal segment in the table uses — and ask which route wins. Anything
    // but itself means an earlier sibling swallows every such location, and the
    // screen behind it cannot be opened at all.
    //
    // Note what this deliberately does *not* flag: a literal sibling declared
    // *above* a `:param` shadows that param only for its own one value, which
    // is the whole point of declaring it there. Filling with a stand-in asks
    // about the other values, which is the question that matters.
    final shadowed = <String>[];
    for (final path in _registeredPaths(router)) {
      final won = _winningPattern(router, _fillParams(path));
      if (won != path) shadowed.add('$path is served by ${won ?? 'no route'}');
    }

    expect(
      shadowed,
      isEmpty,
      reason:
          'These routes never win a match, so the screens behind them are '
          'unreachable however correct the screen and its own tests are. '
          'go_router takes the *first* match, so a literal segment has to be '
          'declared above a sibling `:param` — as `chat/new` is above '
          '`:chatId` and `feedback/create` above `:feedbackId`:\n'
          '${shadowed.map((e) => '  $e').join('\n')}',
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
      //
      // `/server` is deliberately **not** here. It used to be — it was a pure
      // redirect target, reachable only by clearing the persisted
      // configuration so `redirect` would fire, which is how three ported and
      // green code paths behind it came to be dead. `Routes.changeServer`
      // navigates there now, so the entry point is real and the exception is
      // no longer owed.
      //
      // **This rule does not guard that entry point, and an earlier version of
      // this comment claimed it did.** Mutation-tested: replacing the login
      // screen's navigation with an empty callback leaves every test in this
      // file green, because `server_config_screen.dart`'s own
      // `context.go(Routes.server)` is a bare `Routes` constant in a
      // navigating layer and rule three counts it as reached. That is rule
      // three's documented permissiveness, not a hole to plug here. The guard
      // is `server_change_navigation_test.dart`, where the same mutation fails
      // four tests.
      '/onboarding': 'redirect target on a first launch',
      '/login': 'redirect target when there is no session',
      '/home': 'initialLocation, and the redirect target once signed in',
      // Built by DeepLinkHandler.publicSurveyLocation, covered by the deep-link
      // test below.
      '/survey/:teamId/:surveyId': 'deep-link entry point',
      // Kotlin shows the respondent profile as a dialog over the survey and the
      // port keeps that shape, so its callers build the screen with
      // `Navigator.push` and there is no location to navigate to. There are
      // **two** of them since Phase 132 — `PublicSurveyScreen` for a deep
      // link and `TakeSurveyScreen` for a team survey, which is the pair
      // Kotlin's `showUserInfoDialog` serves — so the route is still spare,
      // and now spare with two live builders rather than one. Either give it
      // a caller or delete it: a parsed-but-unreachable route is how the
      // `teamId` it reads sat unread for three phases.
      '/exam/user-info/:submissionId':
          'PublicSurveyScreen and TakeSurveyScreen build it with '
          'Navigator.push',
    };

    final reached = _navigationSites()
        .where((s) => s.fromCall || _indirectNavigators.containsKey(s.file))
        .map((s) => s.location)
        .toSet();
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

  test('every query parameter a route reads is supplied by a navigation', () {
    // The reachability class turned on its side, and the one shape the other
    // rules structurally cannot see.
    //
    // Rules one, two and five compare the port's route table with the port's
    // own navigations, so they catch a route with no pusher. They cannot catch
    // a route that *is* pushed, resolves correctly, and reads an argument
    // nobody ever passes — the screen opens, and the feature the argument
    // carried is missing. A parameter read but never supplied is the same
    // fingerprint as a mapper with no caller: plumbing laid for a call nobody
    // wrote, which Phase 119 found four of.
    //
    // Both supplier shapes count, because the port uses both: a literal
    // `'…?tab=requests'`, and `Uri(queryParameters: {'origin': …})`, which is
    // how the public-survey deep link passes the one value its route cannot
    // recover. Reading only the first reports `origin` as dead, which it is
    // not.
    const knownMissingEntryPoint = <String, String>{
      // `feedback/create` reads both, and nothing in `lib/` passes either:
      // every feedback the port can file is `title: "Question regarding /"`,
      // `url: "/"`, with `state` and `item` null.
      //
      // The missing writer is the teams list's per-row feedback button —
      // `item_team_list.xml:59` (an `ImageView` with no `visibility`
      // attribute), bound unconditionally at `TeamsAdapter.kt:80-82`, and
      // `TeamFragment.kt:299-305`'s `getBundle` is what supplies the pair:
      // `state` = `"${team.type}s"` (or `"teams"`), `item` = `team._id`.
      // `FeedbackRepositoryImpl.kt:46-54` turns them into the report's title,
      // url, state and item, so Planet can file it against that team.
      //
      // `lib/ui/teams/` belongs to another lane this round, so this is
      // reported rather than fixed. Delete these two entries with the button.
      'item': 'the teams-list per-row feedback button is not ported',
      'state': 'the teams-list per-row feedback button is not ported',
    };

    final router_ = _stripComments(
      File('lib/ui/router.dart').readAsStringSync(),
    );
    final read = RegExp(
      r"queryParameters\['(\w+)'\]",
    ).allMatches(router_).map((m) => m.group(1)!).toSet();

    final supplied = <String>{};
    for (final file
        in Directory('lib')
            .listSync(recursive: true)
            .whereType<File>()
            .where((f) => f.path.endsWith('.dart'))
            .where((f) => !f.path.endsWith('ui/router.dart'))) {
      final source = _stripComments(file.readAsStringSync());
      // Per *literal*, then every parameter within it. Scanning the file with
      // one `'[^']*[?&](\w+)=' `is wrong in a way worth recording, because it
      // looked right and was green on the cases that had a single parameter:
      // `[^']*` is greedy, so in `'…?url=$u&title=$t'` it runs to the end and
      // backtracks to the *last* `[?&]`, reporting `title` and never `url`.
      // It read exactly one parameter per literal — the last — and the two it
      // dropped, `url` and `stepId`, are both genuinely supplied.
      for (final m in RegExp(r"'([^']*)'").allMatches(source)) {
        for (final param in RegExp(r'[?&](\w+)=').allMatches(m.group(1)!)) {
          supplied.add(param.group(1)!);
        }
      }
      for (final m in RegExp(
        r'queryParameters\s*:\s*\{([^}]*)\}',
      ).allMatches(source)) {
        for (final key in RegExp(r"'(\w+)'\s*:").allMatches(m.group(1)!)) {
          supplied.add(key.group(1)!);
        }
      }
    }

    final dead = read
        .where((name) => !supplied.contains(name))
        .where((name) => !knownMissingEntryPoint.containsKey(name))
        .toList();

    expect(
      dead,
      isEmpty,
      reason:
          'These query parameters are read by a route builder and passed by no '
          'navigation in lib/, so the screen always sees null and whatever the '
          'parameter carried is silently missing. Either supply it at the call '
          "site or record it in this test's `knownMissingEntryPoint` map with "
          'the entry point it is waiting on:\n${dead.map((n) => '  $n').join('\n')}',
    );

    // The exception map must not outlive its reason — the shelf-life rule.
    final resurrected = knownMissingEntryPoint.keys
        .where(supplied.contains)
        .toList();
    expect(
      resurrected,
      isEmpty,
      reason:
          'These parameters are supplied now, so their entry point landed. '
          "Delete them from this test's `knownMissingEntryPoint` map:\n"
          '${resurrected.map((n) => '  $n').join('\n')}',
    );
  });

  test('every indirect-navigator exemption is still load-bearing', () {
    // An exemption nobody needs is an exemption nobody re-reads, and this file
    // has already been bitten once by an entry that had outlived its reason
    // (the `/server` note below, which described a mutation result it did not
    // cause). So each entry has to still be carrying a route that would
    // otherwise be reported unreachable.
    //
    // This fails in the *useful* direction: if a file's destinations gain
    // ordinary `context.go` call sites, its entry becomes dead weight and this
    // says so, rather than leaving a widening nobody can account for.
    final callSites = _navigationSites()
        .where((s) => s.fromCall)
        .map((s) => s.location)
        .toSet();
    final registered = _registeredPaths(router);

    final idle = <String>[];
    for (final file in _indirectNavigators.keys) {
      final carries = _navigationSites()
          .where((s) => !s.fromCall && s.file == file)
          .map((s) => s.location)
          .where(
            (loc) => registered.any(
              (path) =>
                  _pathsMatch(path, loc) &&
                  !callSites.any((c) => _pathsMatch(path, c)),
            ),
          );
      if (carries.isEmpty) idle.add(file);
    }

    expect(
      idle,
      isEmpty,
      reason:
          'These files are exempted from rule five but no longer carry a route '
          'that needs the exemption — every destination they name is also '
          'reached from a real navigation call. Drop them from '
          '`_indirectNavigators` rather than leaving a widening with no live '
          'reason:\n${idle.map((f) => '  $f').join('\n')}',
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
      // `context.go(notificationDestinationLocation(destination))`. The switch
      // it used to inline moved to `notification_destination.dart` — where the
      // tray tap shares it — so the arms are now walked *exhaustively* over
      // `NotificationDestinationKind` by the notification-tap test below,
      // which is a stronger guard than rule three's `Routes.x` scan was.
      'lib/ui/notifications/notifications_screen.dart':
          'notificationDestinationLocation(destination)',
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

  test('every notification destination resolves to a registered route', () {
    // Exhaustive over the enum, so a new kind cannot be added without an arm
    // (that would not compile) and an arm cannot name a path the router does
    // not serve. This is what the extraction bought: the mapping used to be a
    // `switch` inside a private widget method, reachable only by the scanner's
    // "a `Routes.x` mentioned anywhere in this file counts as reached" rule —
    // which cannot tell a live arm from a dead one.
    final unresolved = <String>[];
    for (final kind in NotificationDestinationKind.values) {
      final location = notificationDestinationLocation(
        // Values for the two id-carrying kinds; the rest ignore them.
        NotificationDestination(kind, teamId: 'team-1', voiceId: 'voice-1'),
      );
      if (!_matches(router, _normalize(location))) {
        unresolved.add('$kind -> $location');
      }
    }

    expect(
      unresolved,
      isEmpty,
      reason:
          'These notification destinations name locations no route matches, so '
          'both the bell row and a system-tray tap land on the error page:\n'
          '${unresolved.map((entry) => '  $entry').join('\n')}',
    );
  });

  test('every notification type the port declares has a resolver arm', () async {
    // `NotificationTypes` is the set of `NotificationUtils.TYPE_*` values the
    // port produces, and `resolveFor`'s `default: return null` swallows
    // anything it has no arm for — silently, which is the failure class this
    // whole file guards.
    //
    // The risk is not theoretical. The resolver is a port of the bell row's
    // click handler (`NotificationsFragment.handleNotificationClick`), which
    // has **no** `survey` or `course` arm, and those are two of Kotlin's six
    // `TYPE_*` values. Add a survey tray notification and its payload decodes
    // cleanly, its type is `'survey'`, and the tap does nothing — with the
    // tray-tap test below still green, because that one drives only
    // `NotificationConfig.task`.
    //
    // Dart has no reflection over static constants, so the hand-written list
    // is reconciled against the source file: a constant added there and not
    // here fails first, with a message saying to add it.
    const declared = <String>{NotificationTypes.task};
    final inSource = RegExp(r"static const \w+ = '([^']*)';")
        .allMatches(
          _stripComments(
            File(
              'lib/core/notifications/notification_config.dart',
            ).readAsStringSync(),
          ).split('class NotificationTypes').last,
        )
        .map((match) => match.group(1)!)
        .toSet();
    expect(
      inSource,
      declared,
      reason:
          'NotificationTypes gained or lost a constant; add it to this test so '
          'its resolver arm is checked',
    );

    final database = container.read(appDatabaseProvider);
    final resolver = NotificationDestinationResolver(
      taskDao: database.teamTaskDao,
      teamDao: database.teamDao,
    );
    for (final type in declared) {
      // A non-blank relatedId, because several arms require one and returning
      // null for a blank id is deliberate rather than a missing arm.
      final destination = await resolver.resolveFor(
        type: type,
        relatedId: 'related-1',
      );
      expect(
        destination,
        isNotNull,
        reason:
            'the port can raise a "$type" notification and the resolver has no '
            'arm for it, so tapping one does nothing',
      );
      final location = notificationDestinationLocation(destination!);
      expect(_matches(router, _normalize(location)), isTrue, reason: location);
    }
  });

  test(
    'a system-tray tap on the notification the port raises navigates',
    () async {
      // The reachability guard for an entry point outside the route table.
      //
      // The port has exactly one producer of an OS notification —
      // `TaskDeadlineNotifier`, via `NotificationConfig.task` — so this drives
      // the tap that producer's own notification delivers, through the real
      // payload codec and the real handler, and asserts it reaches a location
      // the router serves. Before Phase 130 every piece of that chain was
      // missing: no `payload` on the notification, no response callback, no
      // handler, and `markNotificationAsRead` with no caller in `lib/`.
      //
      // A fixture that hand-built a `NotificationTap` would prove nothing about
      // the producer, which is the Phase 113 lesson — "every fixture fabricated
      // the join, and that was the symptom". So the payload comes from
      // `NotificationTapPayload.forConfig` over the config the notifier builds.
      final database = container.read(appDatabaseProvider);
      await database.teamTaskDao.upsertAll([
        TeamTasksCompanion.insert(
          id: 'task-42',
          teamId: 'team-9',
          title: const Value('Read chapter 3'),
          assignee: const Value('user-1'),
        ),
      ]);

      final tap = NotificationTap(
        payload: NotificationTapPayload.forConfig(
          NotificationConfig.task(
            taskId: 'task-42',
            taskTitle: 'Read chapter 3',
            deadlineLabel: 'Wed 19, August 2026',
            urgent: true,
          ),
        ),
      );

      final location = await container
          .read(notificationTapHandlerProvider)
          .handle(tap);

      expect(location, isNotNull, reason: 'the tap navigates nowhere');
      expect(_matches(router, _normalize(location!)), isTrue, reason: location);
    },
  );

  test(
    'every action the notification offers is one the handler knows',
    () async {
      // The other half of the same chain, and the half most likely to rot: the
      // buttons are strings the OS holds while the app is dead, so a rename on
      // one side and not the other is silent. `notificationTapFrom` drops an
      // action id it does not recognise, so an unknown one is not a crash — it
      // is a button that does nothing.
      final config = NotificationConfig.task(
        taskId: 'task-42',
        taskTitle: 'Read chapter 3',
        deadlineLabel: 'Wed 19, August 2026',
        urgent: true,
      );
      final payload = NotificationTapPayload.forConfig(config).encode();

      final actions = notificationActionsFor(config);
      expect(
        actions,
        isNotEmpty,
        reason: 'createTaskNotification is actionable',
      );
      for (final action in actions) {
        expect(
          notificationTapFrom(
            NotificationResponse(
              notificationResponseType:
                  NotificationResponseType.selectedNotificationAction,
              actionId: action.id,
              payload: payload,
            ),
          ),
          isNotNull,
          reason:
              'the notification offers "${action.title}" (${action.id}) and the '
              'handler does not recognise it, so tapping it does nothing',
        );
      }
    },
  );

  test('the platform wiring a tap depends on is present', () {
    // The three links in the chain that no behavioural test can exercise,
    // because each is an argument to a plugin call and
    // `FlutterLocalNotificationsPlugin` has a private constructor — it cannot
    // be faked or subclassed from here. Asserted on the source text instead,
    // which is what four of the rules above already do, and named as such
    // rather than left as an assumed-covered gap.
    //
    // Every one of the three was missing before Phase 130, and each on its own
    // is enough to make a tap do nothing at all:
    //
    //   * no `payload` — the tap arrives knowing *that* a notification was
    //     tapped and nothing about which one;
    //   * no response callback at `initialize` — the plugin has nowhere to
    //     deliver a tap that arrives while the app is running;
    //   * no `NotificationTapScope` in the widget tree — nobody asks for the
    //     launch tap or listens to the stream, so the handler is dead code.
    //
    // `DeepLinkScope` is checked alongside it: it is the same exposure (a
    // scope silently dropped from `app.dart`'s builder disables its whole
    // entry point) and nothing else guarded it.
    final presenter = _stripComments(
      File(
        'lib/core/notifications/notification_presenter.dart',
      ).readAsStringSync(),
    );
    expect(
      presenter,
      contains('payload: NotificationTapPayload.forConfig(config).encode()'),
      reason: 'the shown notification carries no id for a tap to act on',
    );
    expect(
      presenter,
      // The *value*, not the parameter name. `onDidReceiveNotificationResponse:
      // null,` contains the name and drops every tap that arrives while the app
      // is running — the second audit pass injected exactly that and watched
      // this rule stay green.
      contains('onDidReceiveNotificationResponse: _responses.add'),
      reason: 'the plugin has nowhere to deliver a tap',
    );
    expect(
      presenter,
      contains('notificationActionsFor(config)'),
      reason: 'the notification offers none of its Kotlin action buttons',
    );

    final app = _stripComments(File('lib/app.dart').readAsStringSync());
    for (final scope in const ['NotificationTapScope', 'DeepLinkScope']) {
      expect(
        app,
        contains('$scope('),
        reason:
            '$scope is not mounted in app.dart, so its entry point is dead '
            'however correct the handler behind it is',
      );
    }

    // A fourth link, and the least obvious one: `initialize` is where the
    // plugin accepts the response handler, and the only thing that reaches it
    // in the UI isolate is `main.dart` asking for the notification permission.
    // Move that request to a screen — a plausible refactor, since that is where
    // a permission prompt usually belongs — and every warm tap is dropped for
    // the whole process, with nothing else here to notice. (The *launch* tap
    // survives: `getNotificationAppLaunchDetails` goes through the handler
    // Android registers in `onAttachedToEngine`, independent of Dart's
    // `initialize`.)
    expect(
      _stripComments(File('lib/main.dart').readAsStringSync()),
      // The *call*, not the declaration — which is still in the file after the
      // call is removed, and is what a first cut of this assertion matched.
      contains('unawaited(_requestNotificationPermission());'),
      reason:
          'nothing else in the UI isolate calls the presenter, so without this '
          'the plugin is never initialized and no running-app tap is delivered',
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

/// Files that navigate to a location they *compute*, so their destinations sit
/// loose in the file rather than in a `context.push`/`go` argument.
///
/// Rule five counts a location as reached when it is read out of a navigation
/// call — or when it is a loose mention **in one of these files**. Everywhere
/// else, a loose `Routes.x` no longer counts.
///
/// **This replaces a blanket permissiveness, and the replacement is measured
/// rather than guessed.** The scanner deliberately reads locations from
/// anywhere in `lib/`, because for rules three and four breadth is strength: a
/// broken target is worth checking wherever it is written. For rule five
/// breadth is the opposite — every extra site is one more thing counted as
/// *reached* — so a `Routes.x` in a dead branch, a `case` label or an unused
/// helper used to satisfy it, in any of 66 files.
///
/// Classifying every reachability witness in the tree by where it came from
/// gives: **every** loose mention that rule five leans on is in one of the
/// three files below, and **no** route is reached by a loose mention alone —
/// all 63 non-exempt routes have a real call site today. So the narrowing
/// costs nothing now, and what it buys is that the next dead branch naming a
/// `Routes` constant does not quietly satisfy this rule.
///
/// These three stay because their indirection is real, not sloppy — and each
/// already has a stronger, exhaustive guard in this same file, named here so
/// the exemption is reviewable rather than inherited:
const _indirectNavigators = <String, String>{
  // Thirteen destinations collected into a list and handed over as
  // `context.go(route)`. Also in `declared` below, for the same indirection.
  'lib/ui/dashboard/dashboard_drawer.dart':
      'a list of destinations, navigated as a variable',
  // `notification_destination.dart` is deliberately **not** here, and the
  // honesty test below is how that was settled rather than by taste. Its six
  // destinations are every one of them also pushed from an ordinary screen, so
  // exempting it widened the rule while carrying nothing; the enum is walked
  // exhaustively by "every notification destination resolves to a registered
  // route" either way. Should a tray notification ever become the only way to
  // reach a screen, rule five will say so and the entry comes back with that
  // as its reason.
  //
  // `deepLinkRoute(section)`. Guarded by "every deep-link section resolves to
  // a registered route", which drives the real function.
  'lib/providers/deep_link_provider.dart':
      'a section-to-route map; driven directly above',
};

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
  const _NavSite(this.file, this.line, this.location, {this.fromCall = false});
  final String file;
  final int line;
  final String location;

  /// Whether this location was read out of a `context.push`/`go`/… argument,
  /// as opposed to a `Routes.x` or `'/…'` sitting loose in the file.
  ///
  /// Only rule five reads it, and only because the two kinds answer different
  /// questions there — see the `_indirectNavigators` note.
  final bool fromCall;

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
    bool record(int offset, String? raw, {bool fromCall = false}) {
      if (raw == null) return false;
      final resolution = _resolve(raw, constants);
      final location = resolution.location;
      if (location == null || !location.startsWith('/')) return false;
      final site = _NavSite(
        file.path,
        _lineOf(source, offset),
        location,
        fromCall: fromCall,
      );
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
        found |= record(open + 1 + hit.start, hit.group(1), fromCall: true);
      }
      for (final hit in bareConstant.allMatches(argument)) {
        found |= record(
          open + 1 + hit.start,
          constants[hit.group(1)!],
          fromCall: true,
        );
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

/// The pattern of the route that actually serves [location], or null when none
/// does.
///
/// `RouteMatchList.fullPath` is go_router's own answer to *which* route matched
/// — "the full path pattern that matches the uri", `go_router-18.0.1`
/// `lib/src/match.dart:541` — so this is the supported question rather than an
/// inference from the widget it builds.
///
/// **Verified empirically against the pinned 18.0.1, not read off the source.**
/// With `create` declared below `:feedbackId`, `/life/feedback/create` reports
/// `/life/feedback/:feedbackId`; with the order swapped it reports itself.
/// Ordering semantics are the thing this whole file now leans on, so they were
/// probed against the version that ships.
String? _winningPattern(GoRouter router, String location) {
  final match = router.configuration.findMatch(Uri.parse(location));
  return match.isError ? null : match.fullPath;
}

/// Whether *any* route serves [location].
///
/// Deliberately still the weak question, and only where the strong one has no
/// answer: a navigation site's intended pattern is not recoverable from its
/// text, so all rule three can ask is that the location goes somewhere. Rules
/// one and two ask the strong question of the route table itself, which is
/// where the shadowing lives.
bool _matches(GoRouter router, String location) =>
    _winningPattern(router, location) != null;

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
