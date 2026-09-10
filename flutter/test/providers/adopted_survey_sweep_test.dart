import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/background_entrypoint.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/adopted_surveys_uploader.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/submissions_uploader.dart';
import 'package:myplanet/repository/surveys_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **The wiring, not the pieces.** Phase 138's implementation audit found that
/// every test for the adopted-survey upload built `AdoptedSurveysUploader`
/// directly and called `uploader.handler(...)` by hand — so deleting the sweep
/// from both sync paths, or deleting the handler's registration in
/// `outboxDrainerProvider`, left the whole suite green while the clone was
/// destroyed again. That is the Phase 113 shape exactly: the writer exists, the
/// reader exists, and nothing proves anything calls them.
///
/// These drive a real `ProviderContainer` from both sync paths and assert the
/// end state on the row — a rev recorded and the local-authorship flag cleared
/// — which is reachable only if the sweep ran *and* the drain dispatched to
/// this uploader's own handler rather than the drainer's generic replay branch.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  const courseDoc = {
    '_id': 'course-1',
    'courseTitle': 'Clean water',
    'steps': [
      {
        'stepTitle': 'First',
        'survey': {
          '_id': 'survey-1',
          'type': 'surveys',
          'name': 'Water needs',
          'teamShareAllowed': true,
          'questions': [
            {'id': 's1', 'title': 'How was it?', 'type': 'input'},
          ],
        },
      },
    ],
  };
  const cloneId = 'survey-1_team-1';

  late AppDatabase db;
  late MockPlanetApi api;

  setUpAll(() {
    registerFallbackValue(config);
    registerFallbackValue(<String, dynamic>{});
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    api = MockPlanetApi();
  });
  tearDown(() => db.close());

  /// A team's adopted clone, minted by the production `adoptSurvey` over a
  /// course document shaped like the server's.
  Future<void> seedAdoptedClone() async {
    final submissions = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );
    final parsed = CourseMapper.fromDoc(courseDoc)!;
    await db.courseDao.upsertAll([parsed.course], parsed.steps);
    for (final mapping in SurveyMapper.fromCourseDoc(
      courseDoc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await db.surveyDao.upsertAll(
        [mapping.survey],
        {mapping.survey.id.value: mapping.questions},
      );
    }
    await SurveysRepository(
      api,
      db.surveyDao,
      db.examDao,
      submissions,
    ).adoptSurvey(
      surveyId: 'survey-1',
      userId: 'org.couchdb.user:ada',
      userName: 'Ada',
      teamId: 'team-1',
      isTeam: true,
      teamName: 'Team One',
    );
  }

  /// See `pending_submissions_sweep_test.dart`: `outboxDrainerProvider` builds
  /// every registered handler and several reach `planetPrefsProvider`, which is
  /// `UnimplementedError` by default — the Phase 75 harness trap.
  Future<ProviderContainer> containerFor() async {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(config),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(buildUserRow(id: 'org.couchdb.user:ada')),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  void acceptPosts() {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': cloneId,
        'rev': '1-published',
      }),
    );
  }

  test('syncAll publishes an adopted clone', () async {
    await seedAdoptedClone();
    final container = await containerFor();
    acceptPosts();

    expect(
      await db.outboxDao.forItem(AdoptedSurveysUploader.type, cloneId),
      isEmpty,
      reason: 'nothing has queued it — the state the gap leaves behind',
    );

    await container.read(dashboardSyncProvider.notifier).syncAll();

    final clone = (await db.surveyDao.getById(cloneId))!;
    expect(clone.rev, '1-published');
    expect(
      clone.needsSync,
      isFalse,
      reason:
          'only this uploader\'s own handler clears the flag — the '
          'drainer\'s generic replay branch would POST and report success '
          'without ever calling SurveyDao.markUploaded',
    );
    expect(await db.surveyDao.pendingAdoptedSurveys(), isEmpty);
  });

  test('the headless sweep publishes it too', () async {
    await seedAdoptedClone();
    final container = await containerFor();
    acceptPosts();

    await sweepPendingSubmissions(
      container,
      config: config,
      userId: 'org.couchdb.user:ada',
    );

    expect(
      await db.outboxDao.forItem(AdoptedSurveysUploader.type, cloneId),
      hasLength(1),
      reason: 'the headless sweep queues; `drainOutbox` sends',
    );
  });

  test('a throwing adopted sweep does not skip the submissions sweep', () async {
    // The audit's finding: both sweeps shared one `try`, so this arm throwing
    // — `PlatformDeviceIdentitySource.read` rethrows on an engine with no
    // channel and no primed cache, and a headless engine is exactly that case
    // — skipped the submissions safety net Phase 134 added. Kotlin wraps each
    // arm in its own `runCatching` (`UserDataWorker:47-48`).
    //
    // The identity source fails its **first** read only, which is what makes
    // this test able to distinguish the two shapes: under one shared `try` the
    // adopted arm's throw skips the submissions arm and nothing is queued;
    // under separate `try`s the submissions arm runs, gets a working identity,
    // and queues the stranded sheet.
    await seedAdoptedClone();
    final submissions = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );
    final sheetId = await submissions.createDraft(
      userId: 'org.couchdb.user:ada',
      type: 'survey',
      title: 'Water access',
      answers: const [],
    );
    await submissions.markSubmissionComplete(sheetId, {
      '_id': 'org.couchdb.user:ada',
      'name': 'ada',
    });

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(_FailsFirstRead()),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(config),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(buildUserRow(id: 'org.couchdb.user:ada')),
        ),
      ],
    );
    addTearDown(container.dispose);

    await sweepPendingSubmissions(
      container,
      config: config,
      userId: 'org.couchdb.user:ada',
    );

    expect(
      await db.outboxDao.forItem(SubmissionsUploader.type, sheetId),
      hasLength(1),
      reason:
          'the submissions sweep must be reached even when the adopted '
          'sweep throws',
    );
    expect(
      await db.outboxDao.forItem(AdoptedSurveysUploader.type, cloneId),
      isEmpty,
      reason: 'the arm that threw queued nothing, as intended',
    );
  });
}

/// Throws on its first read and works afterwards — see the test that uses it.
class _FailsFirstRead implements DeviceIdentitySource {
  var _reads = 0;

  @override
  Future<DeviceIdentity> read() async {
    if (_reads++ == 0) throw StateError('no platform channel');
    return testDeviceIdentity.read();
  }
}

class _TestServerConfigNotifier extends ServerConfigNotifier {
  _TestServerConfigNotifier(this._config);

  final ServerConfig? _config;

  @override
  ServerConfig? build() => _config;
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}
