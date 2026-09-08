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
        reason: 'Kotlin never records this, and re-POSTs the clone forever',
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

  test('a server-authored adopted survey is not swept for upload', () async {
    // `sourceSurveyId` is not a local-authorship marker: the courses walk reads
    // it off a server-embedded survey, so an adopted copy Planet published
    // carries it *and* a rev. Sweeping on the first clause alone would re-POST
    // somebody else's document.
    final mapped = SurveyMapper.fromDoc(const {
      '_id': 'survey-9',
      '_rev': '4-server',
      'type': 'surveys',
      'name': 'Adopted elsewhere',
      'sourceSurveyId': 'survey-1',
      'teamId': 'team-9',
      'questions': <Map<String, dynamic>>[],
    })!;
    await database.surveyDao.upsertAll(
      [mapped.survey],
      {mapped.survey.id.value: mapped.questions},
    );

    final pending = await database.surveyDao.pendingAdoptedSurveys();
    expect(pending.map((row) => row.id), [cloneId]);
  });

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
}
