import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;
  late SurveysRepository surveys;

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

  setUp(() async {
    database = AppDatabase.memory();
    final api = MockPlanetApi();
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
  });
  tearDown(() => database.close());

  test('the team-adoption marker is not swept for upload', () async {
    await surveys.adoptSurvey(
      surveyId: 'survey-1',
      userId: 'user-1',
      userName: 'Ada',
      teamId: 'team-1',
      isTeam: true,
      teamName: 'Team One',
    );
    final marker = (await database.submissionDao.byTeam('team-1')).single;
    expect(
      marker.status,
      '',
      reason: 'the marker as `createMappedSubmission` writes it',
    );
    expect(marker.isUpdated, isTrue);

    expect(
      await submissions.pendingUploads(),
      isEmpty,
      reason:
          "Kotlin's sweep is `status = 'complete'` — it has no config that "
          'selects a bookkeeping row whose status is blank',
    );
  });

  /// Phase 134 named this row alongside the marker as one the port should stop
  /// sending. **Checked, and it is wrong** — kept as a guard so the claim is
  /// not re-seeded. `createDraft` is the submissions screen's New-submission
  /// button and `submissions_screen.dart:172-194` runs `queuePending` *and*
  /// `drain` on the next lines: the learner typed a title and an answer and
  /// pressed Save. Having no Kotlin writer makes the document shape port-only,
  /// not the intent absent, and excluding it would have made that button write
  /// to the device and nothing else, silently.
  test('a deliberate free-form submission is still swept for upload', () async {
    await submissions.createDraft(
      userId: 'user-1',
      type: 'submission',
      title: 'My note',
      answers: const [SubmissionDraftAnswer(value: 'something')],
    );
    expect(await submissions.pendingUploads(), hasLength(1));
  });

  /// A null status is not a blank one here, and the asymmetry is deliberate —
  /// see the predicate. The sync-in stores null for a document that omits
  /// `status`, and `pendingUploads` was status-blind before Phase 138, so a
  /// row like this is what the port has always carried.
  test('a row with no status at all is still swept for upload', () async {
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 'bare',
        userId: const Value('user-1'),
        isUpdated: const Value(true),
      ),
    ]);
    expect((await submissions.pendingUploads()).single.id, 'bare');
  });

  test('a finished survey sheet is still swept for upload', () async {
    final survey = (await database.surveyDao.getById('survey-1'))!;
    await submissions.createSurveyDraft(
      survey: survey,
      questions: await database.surveyDao.questionsFor('survey-1'),
      userId: 'user-1',
      answers: const {},
    );
    expect(await submissions.pendingUploads(), hasLength(1));
  });
}
