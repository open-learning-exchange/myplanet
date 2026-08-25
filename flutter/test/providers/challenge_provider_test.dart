import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/challenge_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/progress_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

/// The ChallengeEvaluator's `courseStatusString` and `evaluate` gating are
/// pure logic over repository results. This test covers the gating (guest,
/// window, server) and the status string without exercising the dialog widget
/// itself, which is covered by the dialog's own widget test.
void main() {
  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  /// A minimal container with the database and a session, so the evaluator
  /// can read its repositories without a server config.
  Future<ProviderContainer> containerFor({
    String userId = 'user-ada',
    bool isGuest = false,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            isGuest ? buildUserRow(id: 'guest_ada') : buildUserRow(id: userId),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);
    return container;
  }

  group('ChallengeEvaluator gating', () {
    test('a guest never sees the challenge', () async {
      final container = await containerFor(isGuest: true);
      final evaluator = container.read(challengeEvaluatorProvider);
      final data = await evaluator.evaluate(
        userId: 'guest_ada',
        isGuest: true,
        serverUrl: 'https://planet.gt',
        now: () => DateTime(2024, 12, 15),
      );
      expect(data, isNull);
    });

    test('a non-participating server returns null', () async {
      final container = await containerFor();
      final evaluator = container.read(challengeEvaluatorProvider);
      final data = await evaluator.evaluate(
        userId: 'user-ada',
        isGuest: false,
        serverUrl: 'https://example.org',
        now: () => DateTime(2024, 12, 15),
      );
      expect(data, isNull);
    });

    test('a date outside the window returns null', () async {
      final container = await containerFor();
      final evaluator = container.read(challengeEvaluatorProvider);
      final before = await evaluator.evaluate(
        userId: 'user-ada',
        isGuest: false,
        serverUrl: 'https://planet.gt',
        now: () => DateTime(2024, 11, 1),
      );
      final after = await evaluator.evaluate(
        userId: 'user-ada',
        isGuest: false,
        serverUrl: 'https://planet.gt',
        now: () => DateTime(2025, 2, 1),
      );
      expect(before, isNull);
      expect(after, isNull);
    });

    test('a participating server in window returns data', () async {
      final container = await containerFor();
      final evaluator = container.read(challengeEvaluatorProvider);
      final data = await evaluator.evaluate(
        userId: 'user-ada',
        isGuest: false,
        serverUrl: 'https://planet.gt',
        now: () => DateTime(2024, 12, 15),
      );
      expect(data, isNotNull);
      expect(data!.voiceCount, 0);
      expect(data.allVoiceCount, 0);
      // No progress, no course name — the status is the empty name.
      expect(data.courseStatus, '');
      expect(data.hasValidSync, isFalse);
    });
  });

  group('ChallengeEvaluator course status', () {
    test('null progress returns the course name alone', () {
      final evaluator = _evaluator();
      expect(evaluator.courseStatusString(null, 'My Course'), 'My Course');
    });

    test('incomplete progress returns name with current/max', () {
      final evaluator = _evaluator();
      final progress = CourseProgressSummary(max: 10, current: 5);
      expect(
        evaluator.courseStatusString(progress, 'My Course'),
        'My Course 5/10',
      );
    });

    test('completed progress appends the terminado marker', () {
      final evaluator = _evaluator();
      final progress = CourseProgressSummary(max: 10, current: 10);
      expect(
        evaluator.courseStatusString(progress, 'My Course'),
        'My Course terminado',
      );
    });

    test('a null course name falls back to empty', () {
      final evaluator = _evaluator();
      final progress = CourseProgressSummary(max: 10, current: 3);
      expect(evaluator.courseStatusString(progress, null), ' 3/10');
    });
  });

  group('hasShownChallengeCongratsProvider', () {
    Future<ProviderContainer> congratsContainer() async {
      SharedPreferences.setMockInitialValues({});
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetPrefsProvider.overrideWithValue(prefs),
        ],
      );
      addTearDown(container.dispose);
      return container;
    }

    test('defaults to false', () async {
      final container = await congratsContainer();
      expect(container.read(hasShownChallengeCongratsProvider), isFalse);
    });

    test('setShown flips the state and persists', () async {
      final container = await congratsContainer();
      await container
          .read(hasShownChallengeCongratsProvider.notifier)
          .setShown();
      expect(container.read(hasShownChallengeCongratsProvider), isTrue);
    });
  });
}

/// A tiny container-less evaluator for the pure `courseStatusString` tests.
ChallengeEvaluator _evaluator() => ChallengeEvaluator(_DummyRef());

class _DummyRef implements Ref {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}
