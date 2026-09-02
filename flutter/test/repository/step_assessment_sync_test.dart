import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/repository/courses_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 113. A course step's test and survey are written by the **courses**
/// walk, which is the only walk that knows the step id; the **exams** walk
/// writes the same documents from the `exams` database and owns the
/// `deleteNotIn` prune for both tables.
///
/// The two therefore have to agree, and the port syncs them in that order
/// (`DashboardSyncArea.courses` before `.surveys`). These tests pin the
/// agreement, because a change to either mapper's accept rule would break it
/// silently: the step exam would be written, pruned minutes later in the same
/// sync pass, and the Take Test button would go back to never appearing.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late CoursesRepository courses;
  late SurveysRepository surveys;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    courses = CoursesRepository(
      api,
      db.courseDao,
      db.removedLogDao,
      db.examDao,
      db.surveyDao,
    );
    surveys = SurveysRepository(
      api,
      db.surveyDao,
      db.examDao,
      SubmissionsRepository(
        api,
        db.submissionDao,
        db.submitPhotosDao,
        db.surveyDao,
      ),
    );
  });
  tearDown(() => db.close());

  // The exam and survey the course document embeds are also documents of the
  // `exams` database — the embedded objects carry a CouchDB `_rev`, which is
  // meaningless on anything unstored, and Kotlin's course walk used to prefetch
  // the existing rows by those ids before writing them.
  final examDoc = {
    '_id': 'exam-1',
    '_rev': '3-abc',
    'type': 'courses',
    'name': 'Step two test',
    'questions': [
      {'id': 'q1', 'title': 'Which is wet?', 'type': 'input'},
    ],
  };
  final surveyDoc = {
    '_id': 'survey-1',
    '_rev': '2-def',
    'type': 'surveys',
    'name': 'Step two survey',
    'questions': [
      {'id': 's1', 'title': 'How was it?', 'type': 'input'},
    ],
  };
  final courseDoc = {
    '_id': 'course-1',
    'courseTitle': 'Water',
    'steps': [
      {'stepTitle': 'Intro'},
      {'stepTitle': 'Assessment', 'exam': examDoc, 'survey': surveyDoc},
    ],
  };

  void stubCourses() {
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 1}),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {'id': 'course-1', 'doc': courseDoc},
        ],
      }),
    );
  }

  void stubExamsDatabase(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        any(that: contains('/exams/_all_docs?limit=0')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final doc in docs) {'id': doc['_id'], 'doc': doc},
        ],
      }),
    );
  }

  test('the exams walk keeps the step exam the courses walk wrote', () async {
    stubCourses();
    await courses.sync(config: config);

    final stepId = CourseMapper.stepIdFor('course-1', 1);
    expect((await db.examDao.getByStepIds([stepId])).single.id, 'exam-1');

    // Now the exams-database walk, which prunes everything it did not see.
    stubExamsDatabase([examDoc, surveyDoc]);
    expect(await surveys.sync(config: config), isA<SyncComplete>());

    final exam = (await db.examDao.getByStepIds([stepId])).single;
    expect(exam.id, 'exam-1');
    expect(
      exam.stepId,
      stepId,
      reason:
          'the exams walk must not clear the join it has no way to know about',
    );
    expect(exam.courseId, 'course-1');
    expect(
      (await db.surveyDao.getByStepId(stepId)).single.id,
      'survey-1',
      reason: 'the step survey survives the same prune',
    );
  });

  test('a course test is not pruned as an unknown document type', () async {
    // The regression this guards: while `ExamMapper.fromDoc` required
    // `type == "exam"`, a `type: "courses"` document was skipped, so its id
    // never entered the walk's keep set and `deleteNotIn` deleted the row.
    stubCourses();
    await courses.sync(config: config);
    stubExamsDatabase([examDoc, surveyDoc]);
    await surveys.sync(config: config);

    expect(await db.examDao.getById('exam-1'), isNotNull);
    expect(await db.examDao.questionsFor('exam-1'), hasLength(1));
  });

  test('a derived-id step assessment survives the exams walk prune', () async {
    // The friendly fixture above hides this: there, the embedded copy and
    // the standalone document are the same object, so the id is always in
    // the exams walk's keep set. An embedded exam with no `_id` of its own
    // takes Kotlin's `ifBlank { "$courseId-$stepId-$examKey" }` fallback,
    // which by construction can never be an `_all_docs` id — so the courses
    // area wrote the row and the surveys area deleted it in the same pass.
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 1}),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'id': 'course-2',
            'doc': {
              '_id': 'course-2',
              'courseTitle': 'Local',
              'steps': [
                {
                  'stepTitle': 'Assessment',
                  'exam': {
                    'name': 'Unpublished test',
                    'questions': [
                      {'id': 'q1', 'title': 'One?', 'type': 'input'},
                    ],
                  },
                  'survey': {
                    'name': 'Unpublished survey',
                    'questions': [
                      {'id': 's1', 'title': 'How?', 'type': 'input'},
                    ],
                  },
                },
              ],
            },
          },
        ],
      }),
    );
    await courses.sync(config: config);

    final stepId = CourseMapper.stepIdFor('course-2', 0);
    expect(
      (await db.examDao.getByStepIds([stepId])).single.id,
      'course-2-course-2:0-exam',
    );

    // An exams database that has never heard of either.
    stubExamsDatabase([examDoc]);
    await surveys.sync(config: config);

    expect(
      (await db.examDao.getByStepIds([stepId])).single.id,
      'course-2-course-2:0-exam',
      reason: 'a locally derived id can never be in the exams walk keep set',
    );
    expect(await db.surveyDao.getByStepId(stepId), hasLength(1));
  });

  test('removing a step releases the exam it used to carry', () async {
    // The port's step id is positional, so deleting a step shifts every later
    // step down one slot. Without the release the old exam would reappear
    // under whatever step inherited `course-1:1`.
    stubCourses();
    await courses.sync(config: config);
    expect(
      (await db.examDao.getByStepIds([
        CourseMapper.stepIdFor('course-1', 1),
      ])).single.id,
      'exam-1',
    );

    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'id': 'course-1',
            'doc': {
              '_id': 'course-1',
              'courseTitle': 'Water',
              'steps': [
                {'stepTitle': 'Intro'},
              ],
            },
          },
        ],
      }),
    );
    await courses.sync(config: config);

    expect(
      await db.examDao.getByStepIds([CourseMapper.stepIdFor('course-1', 0)]),
      isEmpty,
      reason: 'the surviving step must not inherit the removed step exam',
    );
    expect(await db.surveyDao.getByStepId('course-1:0'), isEmpty);
    // The row itself is kept, detached, so the exams walk can prune it.
    expect((await db.examDao.getById('exam-1'))?.stepId, isNull);
  });

  test(
    'the courses walk does not wipe questions the exams walk wrote',
    () async {
      // `ExamDao.upsertAll` deletes an exam's questions before reinserting the
      // map entry, so an embedded copy carrying no `questions` array would
      // empty an exam the other walk had filled.
      stubExamsDatabase([examDoc, surveyDoc]);
      await surveys.sync(config: config);
      expect(await db.examDao.questionsFor('exam-1'), hasLength(1));

      when(
        () => api.getJsonObject(
          '$dbUrl/courses/_all_docs?limit=0',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 1}),
      );
      when(
        () => api.getJsonObject(
          '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          'rows': [
            {
              'id': 'course-1',
              'doc': {
                '_id': 'course-1',
                'courseTitle': 'Water',
                'steps': [
                  {'stepTitle': 'Intro'},
                  {
                    'stepTitle': 'Assessment',
                    'exam': {'_id': 'exam-1', 'type': 'courses'},
                  },
                ],
              },
            },
          ],
        }),
      );
      await courses.sync(config: config);

      expect(await db.examDao.questionsFor('exam-1'), hasLength(1));
    },
  );
}
