import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/submit_photos_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/submit_photos_uploader.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:path_provider/path_provider.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

const config = ServerConfig(
  serverUrl: 'https://planet.example',
  couchDbUrl: 'https://satellite:1234@planet.example:443',
  pin: '1234',
);

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late SubmissionsRepository repository;
  late OutboxRepository outbox;
  late SubmitPhotosUploader uploader;
  late Directory tmpRoot;

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SubmissionsRepository(
      api,
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = SubmitPhotosUploader(
      api,
      repository,
      outbox,
      testDeviceIdentity,
    );
    registerFallbackValue(<String, dynamic>{});

    tmpRoot = await Directory.systemTemp.createTemp('submit_photos_test');
    SubmitPhotosFiles.baseDirectory = () async => tmpRoot;
  });
  tearDown(() async {
    SubmitPhotosFiles.baseDirectory = getApplicationDocumentsDirectory;
    await database.close();
    if (await tmpRoot.exists()) await tmpRoot.delete(recursive: true);
  });

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: SubmitPhotosUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: 'https://planet.example/db/submissions',
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  Future<String> seedPhoto({String? photoLocation}) =>
      repository.addSubmissionPhoto(
        submissionId: 'sub-1',
        examId: 'exam-1',
        courseId: 'course-1',
        memberId: 'user-1',
        photoLocation: photoLocation,
      );

  test('the endpoint is credential-free and points at the submissions db', () {
    expect(
      SubmitPhotosUploader.endpointFor(config),
      'https://planet.example/db/submissions',
    );
  });

  test(
    'queuePending enqueues each unuploaded photo with device identity',
    () async {
      await seedPhoto();
      expect(await uploader.queuePending(config: config), 1);
      expect(await uploader.queuePending(config: config), 1);

      final operation = (await outbox.due()).single;
      expect(operation.uploadType, SubmitPhotosUploader.type);
      expect(operation.endpoint, 'https://planet.example/db/submissions');
      final queuedDoc =
          operation.payload; // serialized by the outbox as a JSON string
      for (final field in testDeviceFields.entries) {
        expect(queuedDoc, contains(field.key));
      }
    },
  );

  test(
    'a successful upload marks the row uploaded and PUTs the attachment',
    () async {
      final id = await seedPhoto(photoLocation: '/tmp/capture.jpg');
      await SubmitPhotosFiles.write(
        photoId: id,
        filename: 'capture.jpg',
        bytes: [1, 2, 3, 4],
      );

      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async =>
            NetworkSuccess<Map<String, dynamic>>({'id': id, 'rev': '2-b'}),
      );
      when(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: any(named: 'ifMatch'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );

      final result = await uploader.handler(rowFor(id), {}, 'auth');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      final survivor = await repository.photoById(id);
      expect(survivor?.uploaded, isTrue);
      expect(survivor?.couchId, id);
      expect(survivor?.rev, '2-b');

      final captured = verify(
        () => api.uploadAttachment(
          captureAny(),
          bytes: captureAny(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: captureAny(named: 'contentType'),
          ifMatch: captureAny(named: 'ifMatch'),
        ),
      ).captured;
      expect(captured[0], contains('/submissions/$id/capture.jpg'));
      expect(captured[1], [1, 2, 3, 4]);
      expect(captured[2], 'image/jpeg');
      expect(captured[3], '2-b');
    },
  );

  test('a response without an id/rev is not treated as uploaded', () async {
    final id = await seedPhoto(photoLocation: '/tmp/capture.jpg');

    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor(id), {}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((await repository.photoById(id))?.uploaded, isFalse);
  });

  test('a failed upload leaves the photo local and retryable', () async {
    final id = await seedPhoto(photoLocation: '/tmp/capture.jpg');

    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(500, 'nope'),
    );

    await uploader.handler(rowFor(id), {}, 'auth');

    expect((await repository.photoById(id))?.uploaded, isFalse);
  });

  test('a photo with no attachment file succeeds as a document', () async {
    final id = await seedPhoto(photoLocation: null);

    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'id': id, 'rev': '2-b'}),
    );

    final result = await uploader.handler(rowFor(id), {}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    verifyNever(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    );
  });
}
