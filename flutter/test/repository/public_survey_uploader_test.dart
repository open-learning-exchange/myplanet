import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/public_survey_uploader.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late SubmissionsRepository submissions;
  late SurveysRepository surveys;
  late PublicSurveyUploader uploader;
  late OutboxRepository outbox;

  const origin = 'http://192.168.1.73:5000';
  const endpoint = '$origin/api/public/surveys/team-1/survey-9/submissions';

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    outbox = OutboxRepository(database.outboxDao);
    submissions = SubmissionsRepository(
      api,
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
    );
    surveys = SurveysRepository(
      api,
      database.surveyDao,
      database.examDao,
      submissions,
    );
    uploader = PublicSurveyUploader(api, surveys, submissions, outbox);
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: PublicSurveyUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: endpoint,
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  /// Builds the answer sheet the way `PublicSurveyScreen` does — draft first,
  /// then the respondent profile — so the body under test is the real one.
  Future<String> seedAnswerSheet() async {
    await database.surveyDao.upsertAll(
      [SurveysCompanion.insert(id: 'survey-9', name: const Value('Health'))],
      {
        'survey-9': [
          SurveyQuestionsCompanion.insert(
            id: 'survey-9:q1',
            surveyId: 'survey-9',
            position: 0,
            questionId: const Value('q1'),
            body: const Value('How are you?'),
            type: const Value('input'),
          ),
        ],
      },
    );
    final survey = (await database.surveyDao.getById('survey-9'))!;
    final questions = await database.surveyDao.questionsFor('survey-9');
    final id = await submissions.createSurveyDraft(
      survey: survey,
      questions: questions,
      userId: 'public_1',
      answers: {
        'survey-9:q1': const SubmissionDraftAnswer(
          questionId: 'q1',
          value: 'Fine',
        ),
      },
    );
    await submissions.markSubmissionComplete(id, {'name': 'Ada', 'age': 40});
    return id;
  }

  test('the endpoint is the public API and carries no credentials', () {
    expect(
      PublicSurveyUploader.endpointFor(
        baseUrl: origin,
        teamId: 'team-1',
        surveyId: 'survey-9',
      ),
      endpoint,
    );
  });

  test('a queued answer sheet stores the body verbatim', () async {
    final id = await seedAnswerSheet();

    expect(
      await uploader.queue(
        baseUrl: origin,
        teamId: 'team-1',
        surveyId: 'survey-9',
        submissionId: id,
      ),
      isTrue,
    );

    final entry = await database.outboxDao.findOpen(
      PublicSurveyUploader.type,
      id,
    );
    final body = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(body['answers'], ['Fine']);
    expect((body['user'] as Map)['name'], 'Ada');
    // The origin the deep link arrived from, not the configured server: this
    // respondent may never configure one.
    expect(entry.endpoint, endpoint);
  });

  test('an answer sheet already delivered is not queued again', () async {
    final id = await seedAnswerSheet();
    await submissions.markPublicSubmitted(id);

    expect(
      await uploader.queue(
        baseUrl: origin,
        teamId: 'team-1',
        surveyId: 'survey-9',
        submissionId: id,
      ),
      isFalse,
    );
    expect(
      await database.outboxDao.findOpen(PublicSurveyUploader.type, id),
      isNull,
    );
  });

  test('a link with no origin queues nothing', () async {
    final id = await seedAnswerSheet();

    expect(
      await uploader.queue(
        baseUrl: '',
        teamId: 'team-1',
        surveyId: 'survey-9',
        submissionId: id,
      ),
      isFalse,
    );
  });

  test('a successful drain records the delivery', () async {
    final id = await seedAnswerSheet();
    when(
      () => api.sendJsonDynamic(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
      ),
    ).thenAnswer((_) async => const NetworkSuccess<dynamic>({'ok': true}));

    final result = await uploader.handler(rowFor(id), {}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final row = await database.submissionDao.getById(id);
    // Nothing else marks it: the public API is not a CouchDB insert, so there is
    // no revision. Without this the same sheet could be queued twice.
    expect(row?.uploaded, isTrue);
    expect(row?.isUpdated, isFalse);
  });

  test('the anonymous post carries no Authorization header', () async {
    final id = await seedAnswerSheet();
    when(
      () => api.sendJsonDynamic(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
      ),
    ).thenAnswer((_) async => const NetworkSuccess<dynamic>({'ok': true}));

    // The drain hands every handler the configured credential; attaching a
    // Planet Basic header to the public API is not what the Kotlin does and
    // would leak it to whatever host the link named.
    await uploader.handler(rowFor(id), {}, 'Basic c2F0ZWxsaXRlOjEyMzQ=');

    final captured = verify(
      () => api.sendJsonDynamic(
        captureAny(),
        body: any(named: 'body'),
        method: captureAny(named: 'method'),
      ),
    ).captured;
    expect(captured.first, endpoint);
    expect(captured.last, 'POST');
  });

  test('a server error leaves the sheet undelivered for a retry', () async {
    final id = await seedAnswerSheet();
    when(
      () => api.sendJsonDynamic(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
      ),
    ).thenAnswer((_) async => const NetworkError<dynamic>(503, 'unavailable'));

    final result = await uploader.handler(rowFor(id), {}, null);

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((result as NetworkError<Map<String, dynamic>>).code, 503);
    final row = await database.submissionDao.getById(id);
    expect(row?.uploaded, isFalse);
  });

  test('a transport failure stays retryable', () async {
    final id = await seedAnswerSheet();
    when(
      () => api.sendJsonDynamic(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
      ),
    ).thenAnswer(
      (_) async => const NetworkException<dynamic>('SocketException'),
    );

    final result = await uploader.handler(rowFor(id), {}, null);

    // The drainer classifies an exception as retryable and a <500 code as
    // permanent, so the distinction has to survive the cast.
    expect(result, isA<NetworkException<Map<String, dynamic>>>());
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
