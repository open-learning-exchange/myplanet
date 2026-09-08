import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/adopted_surveys_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// **Ported, tested, green, and destroyed** — the reachability class at its
/// worst, reported by Phase 136 and closed here.
///
/// `SurveysRepository.adoptSurvey` mints a team's private copy of a shared
/// survey. Kotlin uploads it (`ExamDao.getPendingAdoptedSurveys()` ->
/// `UploadConfigs.AdoptedSurveys`, endpoint `exams`) and never prunes that
/// table at all; the port had neither half, so `SurveyDao.deleteNotIn` deleted
/// the clone and its questions on the next surveys sync and orphaned every
/// answer sheet its members had filled in.
///
/// Nothing here fabricates a join. The source survey comes out of
/// `SurveyMapper.fromCourseDoc` reading a course document shaped like the
/// server's, the clone is produced by the production `adoptSurvey`, and the
/// re-pull feeds the uploader's own payload back through the production
/// mappers.

void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;
  late SurveysRepository surveys;
  late OutboxRepository outbox;
  late AdoptedSurveysUploader uploader;
  late MockPlanetApi api;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
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
          'sourcePlanet': 'nation',
          'questions': [
            {'id': 's1', 'title': 'How was it?', 'type': 'input'},
          ],
        },
      },
    ],
  };

  const cloneId = 'survey-1_team-1';

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    submissions = SubmissionsRepository(
      api,
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
      teamDao: database.teamDao,
    );
    surveys = SurveysRepository(
      api,
      database.surveyDao,
      database.examDao,
      submissions,
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = AdoptedSurveysUploader(
      api,
      database.surveyDao,
      outbox,
      testDeviceIdentity,
    );

    final parsed = CourseMapper.fromDoc(courseDoc)!;
    await database.courseDao.upsertAll([parsed.course], parsed.steps);
    for (final mapping in SurveyMapper.fromCourseDoc(
      courseDoc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await database.surveyDao.upsertAll(
        [mapping.survey],
        {mapping.survey.id.value: mapping.questions},
      );
    }
    await surveys.adoptSurvey(
      surveyId: 'survey-1',
      userId: 'user-1',
      userName: 'Ada',
      teamId: 'team-1',
      isTeam: true,
      teamName: 'Team One',
    );
  });
  tearDown(() => database.close());

  test('a clone survives the surveys walk that has never seen it', () async {
    expect(await database.surveyDao.getById(cloneId), isNotNull);

    // The keep set a real `exams` walk produces before the clone has ever been
    // uploaded: the server knows the source survey and nothing else.
    await database.surveyDao.deleteNotIn(['survey-1']);

    expect(
      await database.surveyDao.getById(cloneId),
      isNotNull,
      reason: 'the clone is locally authored and in no walk keep set',
    );
    expect(await database.surveyDao.questionsFor(cloneId), isNotEmpty);
  });

  test(
    "a member's answer sheet still resolves its survey after a sync",
    () async {
      await submissions.createBulkSurveySubmissions(cloneId, const [
        'member-1',
      ]);
      final sheet = await database.submissionDao.latestPendingByUserAndParent(
        'member-1',
        '$cloneId@course-1',
      );
      expect(sheet, isNotNull, reason: 'Send writes the sheet under the clone');

      await database.surveyDao.deleteNotIn(['survey-1']);

      final parentSurveyId = sheet!.parentId!.split('@').first;
      expect(
        await database.surveyDao.getById(parentSurveyId),
        isNotNull,
        reason: 'an answer sheet whose survey is gone cannot be reviewed',
      );
    },
  );

  /// Sends the queued clone, as a drain would.
  Map<String, dynamic> decoded(OutboxRow row) =>
      jsonDecode(row.payload) as Map<String, dynamic>;

  Future<void> drain() async {
    final operation = (await outbox.due()).single;
    when(
      () => api.postJsonObject(
        operation.endpoint,
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': operation.itemId,
        'rev': '1-abc',
      }),
    );
    final result = await uploader.handler(operation, decoded(operation), null);
    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
  }

  test('the clone is swept for upload and the source survey is not', () async {
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final operation = (await outbox.due()).single;
    expect(operation.itemId, cloneId);
    expect(operation.endpoint, endsWith('/exams'));
    final payload = decoded(operation);
    expect(payload['_id'], cloneId);
    expect(payload.containsKey('_rev'), isFalse);
    expect(payload['sourceSurveyId'], 'survey-1');
    expect(payload['teamId'], 'team-1');
    expect((payload['questions'] as List), hasLength(1));
    // `serializeExam`'s closing `addDocumentOrigin()`.
    expect(payload['app'], isNotNull);
    expect(payload['androidId'], isNotNull);
  });

  test(
    'the uploaded document is typed, so a re-pull files it as a survey',
    () async {
      await uploader.queuePending(config: config, userId: 'user-1');
      final payload = decoded((await outbox.due()).single);

      // `StepExam.serializeExam` writes `type` (`StepExam.kt:81`); the port's
      // `surveyParentDocument` omits it, which is right for a submission's
      // embedded parent and wrong on the wire. Without it `SurveyMapper` refuses
      // the document, `ExamMapper` accepts it, and the clone comes back as a
      // graded course test while the surveys walk's keep set never learns its id.
      expect(payload['type'], 'surveys');
      expect(SurveyMapper.fromDoc(payload), isNotNull);
      expect(
        ExamMapper.fromDoc(payload),
        isNull,
        reason: 'a survey must not be re-pulled into the exams table',
      );
    },
  );

  test(
    'after the upload the clone is named by the walk and keeps its course',
    () async {
      await submissions.createBulkSurveySubmissions(cloneId, const [
        'member-1',
      ]);
      await uploader.queuePending(config: config, userId: 'user-1');
      final payload = decoded((await outbox.due()).single);
      await drain();

      expect(
        (await database.surveyDao.getById(cloneId))!.rev,
        '1-abc',
        reason: 'the published clone is handed back to the walk that names it',
      );
      expect(
        await database.surveyDao.pendingAdoptedSurveys(),
        isEmpty,
        reason: 'a published clone is out of the sweep',
      );

      // The next surveys walk: the server now has the document, so the page
      // carries it and the keep set names it. Fed back through the production
      // mapper rather than hand-written.
      final mapped = SurveyMapper.fromDoc({...payload, '_rev': '1-abc'})!;
      await database.surveyDao.upsertAll(
        [mapped.survey],
        {mapped.survey.id.value: mapped.questions},
      );
      await database.surveyDao.deleteNotIn(['survey-1', cloneId]);

      final clone = (await database.surveyDao.getById(cloneId))!;
      expect(clone.teamId, 'team-1');
      expect(clone.sourceSurveyId, 'survey-1');
      expect(
        clone.courseId,
        'course-1',
        reason:
            'the wire format carries no courseId, so the re-pull must not '
            'overwrite the join `adoptSurvey` authored',
      );
      expect(await database.surveyDao.questionsFor(cloneId), hasLength(1));

      final sheet = await database.submissionDao.latestPendingByUserAndParent(
        'member-1',
        '$cloneId@course-1',
      );
      expect(
        await database.surveyDao.getById(sheet!.parentId!.split('@').first),
        isNotNull,
      );
    },
  );

  test('a published clone the server stops naming is pruned', () async {
    await uploader.queuePending(config: config, userId: 'user-1');
    await drain();

    // The exemption is scoped to `rev IS NULL`, so it lapses on publication —
    // a walk that no longer names a document it once did is reporting a
    // deletion, and Kotlin's insert-only walk is the reason to spare an
    // *unpublished* clone, not every clone forever.
    await database.surveyDao.deleteNotIn(['survey-1']);
    expect(await database.surveyDao.getById(cloneId), isNull);
  });

  /// **The shape that made `rev IS NULL` the wrong predicate.** Kotlin can ask
  /// `_rev IS NULL` because `JsonUtils.getString` returns `''` for a missing
  /// key, so only `createMappedSurvey`'s explicit null satisfies it. The port's
  /// mappers use `getStringOrNull`, and a course document's embedded survey
  /// carries no `_rev` of its own — every embedded-survey fixture in this tree
  /// omits it — so `rev IS NULL` was true for another team's private copy and
  /// the sweep POSTed it to `exams` under this user's credentials.
  test(
    "another team's course-embedded copy is neither swept nor spared",
    () async {
      const otherCourse = {
        '_id': 'course-9',
        'courseTitle': "Another planet's course",
        'steps': [
          {
            'stepTitle': 'One',
            'survey': {
              '_id': 'survey-embedded',
              'type': 'surveys',
              'name': "Another team's copy",
              'sourceSurveyId': 'survey-1',
              'teamId': 'someone-elses-team',
              'questions': <Map<String, dynamic>>[],
            },
          },
        ],
      };
      final parsed = CourseMapper.fromDoc(otherCourse)!;
      await database.courseDao.upsertAll([parsed.course], parsed.steps);
      for (final mapping in SurveyMapper.fromCourseDoc(
        otherCourse,
        stepIdFor: CourseMapper.stepIdFor,
      )) {
        await database.surveyDao.upsertAll(
          [mapping.survey],
          {mapping.survey.id.value: mapping.questions},
        );
      }
      final embedded = (await database.surveyDao.getById('survey-embedded'))!;
      expect(embedded.rev, isNull, reason: 'the fixture shape this is about');
      expect(embedded.sourceSurveyId, 'survey-1');

      expect(
        (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
        [cloneId],
        reason: 'the port must not publish a document it did not author',
      );

      // And the prune exemption must not spare it either: once the courses walk
      // stops naming that step, `releaseStepJoinsForCourse` nulls its `stepId`
      // and `courseId` and it is an ordinary cache row again.
      await database.surveyDao.releaseStepJoinsForCourse('course-9', const {});
      await database.surveyDao.deleteNotIn(['survey-1']);
      expect(await database.surveyDao.getById('survey-embedded'), isNull);
      expect(await database.surveyDao.getById(cloneId), isNotNull);
    },
  );

  /// A public-API survey lands through `SurveyMapper.fromDoc` too, and the
  /// port's own public-survey fixtures carry no `_rev` either.
  test('a rev-less cache row is not swept for upload', () async {
    final mapped = SurveyMapper.fromDoc(const {
      '_id': 'survey-public',
      'type': 'surveys',
      'name': 'From the public API',
      'sourceSurveyId': 'survey-1',
      'questions': <Map<String, dynamic>>[],
    })!;
    await database.surveyDao.upsertAll(
      [mapped.survey],
      {mapped.survey.id.value: mapped.questions},
    );
    expect(
      (await database.surveyDao.pendingAdoptedSurveys()).map((row) => row.id),
      [cloneId],
    );
  });

  test(
    'a second handset in the team keys member sheets the same way',
    () async {
      await uploader.queuePending(config: config, userId: 'user-1');
      final payload = decoded((await outbox.due()).single);
      await drain();
      await submissions.createBulkSurveySubmissions(cloneId, const [
        'member-1',
      ]);

      // Device B has never seen this row, so `_presentOrAbsent` cannot preserve
      // anything: whatever the wire carries is all it gets.
      final b = AppDatabase.memory();
      addTearDown(b.close);
      final bSubmissions = SubmissionsRepository(
        api,
        b.submissionDao,
        b.submitPhotosDao,
        b.surveyDao,
        b.examDao,
        teamDao: b.teamDao,
      );
      final mapped = SurveyMapper.fromDoc({...payload, '_rev': '1-abc'})!;
      await b.surveyDao.upsertAll(
        [mapped.survey],
        {mapped.survey.id.value: mapped.questions},
      );
      final onB = (await b.surveyDao.getById(cloneId))!;
      expect(
        onB.courseId,
        'course-1',
        reason:
            'without this B keys the same survey bare and A keys it '
            'compound — one member, two pending sheets',
      );
      expect(
        onB.needsSync,
        isFalse,
        reason: "a row B pulled is not B's to publish",
      );

      await bSubmissions.createBulkSurveySubmissions(cloneId, const [
        'member-2',
      ]);
      final aKey = (await database.submissionDao.latestPendingByUserAndParent(
        'member-1',
        '$cloneId@course-1',
      ))!.parentId;
      final bKey = (await b.submissionDao.latestPendingByUserAndParent(
        'member-2',
        '$cloneId@course-1',
      ))!.parentId;
      expect(aKey, bKey);
    },
  );

  test(
    'publishing a clone does not block an outsider from finishing the course',
    () async {
      // The learner outside the adopting team has answered the *course's* survey.
      await submissions.createSurveyDraft(
        survey: (await database.surveyDao.getById('survey-1'))!,
        questions: await database.surveyDao.questionsFor('survey-1'),
        userId: 'outsider',
      );
      expect(
        await submissions.hasUnfinishedSurveys('course-1', 'outsider'),
        isFalse,
      );

      // Phase 136 keyed the clone skip on `rev == null`, which was exact while
      // nothing ever published a clone. Publishing it is what this phase adds,
      // and `rev` stops discriminating the moment it does: the clone carries the
      // source's `courseId`, so `getByCourseId` returns it to every learner on
      // the course, and nobody outside `team-1` has — or can have — a sheet
      // keyed `"$cloneId@course-1"`.
      //
      // Kotlin cannot reach this: `getSurveysByCourseId` filters
      // `getByCourseIdAndType(courseId, "survey")` — **singular**
      // (`SubmissionsRepositoryImpl.kt:367-379`) — while a clone copies the
      // source's `type` and every list adoption is reachable from is
      // `"surveys"` (`ExamDao.kt:29-31`), so a Kotlin clone is never in that
      // result set at all, rev or no rev.
      await uploader.queuePending(config: config, userId: 'user-1');
      await drain();

      expect(
        await submissions.hasUnfinishedSurveys('course-1', 'outsider'),
        isFalse,
        reason:
            'a team\'s private copy is never the course\'s to demand, '
            'published or not',
      );
    },
  );

  test(
    'a server-authored adopted step survey still gates after this change',
    () async {
      // The counter-example Phase 136 was pinning: an adopted copy that Planet
      // itself published into a course step. It carries `sourceSurveyId` *and* a
      // `stepId`, it has a Take Survey button, and it must still be demanded.
      const adoptedStepCourse = {
        '_id': 'course-2',
        'courseTitle': 'Adopted elsewhere',
        'steps': [
          {
            'stepTitle': 'Only',
            'survey': {
              '_id': 'survey-2',
              '_rev': '3-server',
              'type': 'surveys',
              'name': 'Published adopted survey',
              'sourceSurveyId': 'survey-1',
              'questions': [
                {'id': 's2', 'title': 'Well?', 'type': 'input'},
              ],
            },
          },
        ],
      };
      final parsed = CourseMapper.fromDoc(adoptedStepCourse)!;
      await database.courseDao.upsertAll([parsed.course], parsed.steps);
      for (final mapping in SurveyMapper.fromCourseDoc(
        adoptedStepCourse,
        stepIdFor: CourseMapper.stepIdFor,
      )) {
        await database.surveyDao.upsertAll(
          [mapping.survey],
          {mapping.survey.id.value: mapping.questions},
        );
      }

      expect(
        await submissions.hasUnfinishedSurveys('course-2', 'outsider'),
        isTrue,
      );
    },
  );

  test('a send already on the wire is not re-enqueued', () async {
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final operation = (await outbox.due()).single;
    // What a drain does before it sends (`OutboxDrainer._send`).
    expect(await outbox.markInProgress(operation.id), isTrue);

    // Re-enqueueing over an in-flight row would reset it to `pending`, so the
    // send that succeeds moments later deletes nothing (`markCompleted` is
    // `deleteIfInProgress`) and the next drain POSTs the same `_id` again — a
    // 409, which the drainer classifies permanent, abandoning a row whose
    // document does exist and leaving the local rev unrecorded.
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 0);
    expect(
      await outbox.isInFlight(AdoptedSurveysUploader.type, operation.itemId),
      isTrue,
      reason: 'the claimed row is still the one on the wire',
    );
  });

  test(
    'a response naming a different document is not recorded as a round trip',
    () async {
      await uploader.queuePending(config: config, userId: 'user-1');
      final operation = (await outbox.due()).single;
      when(
        () => api.postJsonObject(
          operation.endpoint,
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          'id': 'some-other-doc',
          'rev': '1-abc',
        }),
      );

      // The payload names its own `_id`, so the two are the same string in every
      // real exchange. If they ever differ, the document is unreachable from
      // every local key that embeds this row's id, and recording a rev against
      // the local id would claim a round trip that did not happen — and would
      // drop the clone out of the prune exemption at the same time.
      expect(
        await uploader.handler(operation, decoded(operation), null),
        isA<NetworkError<Map<String, dynamic>>>(),
      );
      expect((await database.surveyDao.getById(cloneId))!.rev, isNull);
    },
  );

  /// **The two-leader race, and why a later walk does not rescue it.** The
  /// port's clone id is deterministic (`'<surveyId>_<teamId>'`) where Kotlin's
  /// is a UUID, so two leaders adopting the same survey for the same team
  /// before either syncs converge on one document — better than Kotlin's two,
  /// but it makes a 409 ordinary. `OutboxDrainer` treats any `code < 500` as
  /// permanent, and a re-pull leaves `needsSync` set (a mapper's companion
  /// omits the column and Drift writes only the columns present), so without
  /// recovery the loser re-POSTs and abandons one outbox row every sync, for
  /// the life of the install.
  test(
    'a 409 adopts the winning document instead of abandoning the row',
    () async {
      await uploader.queuePending(config: config, userId: 'user-1');
      final operation = (await outbox.due()).single;
      when(
        () => api.postJsonObject(
          operation.endpoint,
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async =>
            const NetworkError<Map<String, dynamic>>(409, 'Document conflict'),
      );
      // The header is asserted, not `any`: `outbox.endpoint` is stored
      // credential-free on purpose, so an unauthenticated read of a CouchDB
      // document is a 401 and this recovery would never fire.
      when(
        () => api.getJsonObject(
          '${operation.endpoint}/$cloneId',
          authHeader: 'Basic x',
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': cloneId,
          '_rev': '1-winner',
        }),
      );

      expect(
        await uploader.handler(operation, decoded(operation), 'Basic x'),
        isA<NetworkSuccess<Map<String, dynamic>>>(),
      );
      final clone = (await database.surveyDao.getById(cloneId))!;
      expect(clone.rev, '1-winner');
      expect(clone.needsSync, isFalse);
      expect(await database.surveyDao.pendingAdoptedSurveys(), isEmpty);
    },
  );

  test('a 409 whose document cannot be read stays a failure', () async {
    await uploader.queuePending(config: config, userId: 'user-1');
    final operation = (await outbox.due()).single;
    when(
      () => api.postJsonObject(
        operation.endpoint,
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          const NetworkError<Map<String, dynamic>>(409, 'Document conflict'),
    );
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(503, 'Unavailable'),
    );

    // Clearing the flag with no rev in hand would drop the clone out of the
    // prune exemption while it is still, as far as this device knows,
    // unpublished — the destruction this whole file is about.
    expect(
      await uploader.handler(operation, decoded(operation), 'Basic x'),
      isA<NetworkError<Map<String, dynamic>>>(),
    );
    final clone = (await database.surveyDao.getById(cloneId))!;
    expect(clone.needsSync, isTrue);
    expect(clone.rev, isNull);
  });
}
