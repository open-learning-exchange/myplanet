import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/voice_images.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/repository/voices_uploader.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 147, Job 1 (`PHASE_142_NOTES.md` item 2).
///
/// The port POSTed a voice's news JSON and **never uploaded the image bytes at
/// all** — so upstream `a182edd`, which changes the mime type Kotlin sends
/// when PUTting one, had nothing to align to. The gap underneath the fix was
/// the whole feature.
///
/// Kotlin's shape, from `UploadManager.kt:303-361`: a voice's image is **not**
/// an attachment on the news document. Per image it POSTs a `resources`
/// document (`createImage`, `:126-142`), PUTs the bytes onto *that*
/// (`:321-331`), appends `![](resources/<id>/<name>)` to the outgoing message
/// and records `{resourceId, filename, markdown}` in the post's `images`
/// array — and only then POSTs the news document, which is why the order is
/// load-bearing rather than incidental.
///
/// **These are pair tests.** The composer writes the bytes, the row points at
/// them, and the uploader has to find them again; Phase 100's
/// verification-photo bug was that pair disagreeing about the key, and it
/// failed silently because the attachment step swallowed a missing file. So
/// every test here drives the real writer and the real reader against a real
/// temporary directory.
void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late VoicesRepository voices;
  late OutboxRepository outbox;
  late VoicesUploader uploader;
  late Directory tempDir;
  var idCounter = 0;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUpAll(() => registerFallbackValue(<String, dynamic>{}));

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    idCounter = 0;
    voices = VoicesRepository(
      api,
      database.newsDao,
      createId: () => 'local-${++idCounter}',
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = VoicesUploader(api, voices, outbox, testDeviceIdentity);

    tempDir = Directory.systemTemp.createTempSync('voice_images_test');
    VoiceImages.baseDirectory = () async => tempDir;
    addTearDown(() {
      VoiceImages.baseDirectory = getApplicationDocumentsDirectoryUnavailable;
      if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
    });
  });
  tearDown(() => database.close());

  final bytes = Uint8List.fromList([137, 80, 78, 71, 13, 10, 26, 10]);

  /// Every POST succeeds; records what was sent, in order.
  List<({String url, Map<String, dynamic> body})> stubPosts() {
    final sent = <({String url, Map<String, dynamic> body})>[];
    var rev = 0;
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(
          named:
              'auth'
              'Header',
        ),
      ),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      final body = invocation.positionalArguments[1] as Map<String, dynamic>;
      sent.add((url: url, body: body));
      rev++;
      return NetworkSuccess<Map<String, dynamic>>({
        'id': url.contains('/resources') ? 'resource-$rev' : 'news-$rev',
        'rev': '$rev-abc',
      });
    });
    return sent;
  }

  List<({String url, String? contentType, String? ifMatch, List<int> bytes})>
  stubAttachments({bool succeed = true}) {
    final puts =
        <
          ({String url, String? contentType, String? ifMatch, List<int> bytes})
        >[];
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer((invocation) async {
      puts.add((
        url: invocation.positionalArguments[0] as String,
        contentType: invocation.namedArguments[#contentType] as String?,
        ifMatch: invocation.namedArguments[#ifMatch] as String?,
        bytes: invocation.namedArguments[#bytes] as List<int>,
      ));
      return succeed
          ? NetworkSuccess<Map<String, dynamic>>({'ok': true, 'rev': '9-att'})
          : const NetworkError<Map<String, dynamic>>(413, 'too large');
    });
    return puts;
  }

  Future<String> seedPostWithImage({String filename = 'well.png'}) =>
      voices.createPost(
        message: 'the well is dry',
        userId: 'org.couchdb.user:ada',
        userName: 'ada',
        planetCode: 'learning',
        parentCode: 'earth',
        attachments: [VoiceImageAttachment(bytes: bytes, filename: filename)],
      );

  Future<OutboxRow> queuedFor(String id) async {
    await uploader.queuePending(config: config, userId: 'user-1');
    final rows = await database.outboxDao.forItem(VoicesUploader.type, id);
    expect(rows, hasLength(1));
    return rows.single;
  }

  Future<void> drainOnce(String id) async {
    final operation = await queuedFor(id);
    await uploader.handler(
      operation,
      jsonDecode(operation.payload) as Map<String, dynamic>,
      'Basic dGVzdA==',
    );
  }

  test(
    'the composer writes the bytes where the uploader looks for them',
    () async {
      final id = await seedPostWithImage();

      // The row's own record of the pending image.
      final pending = await voices.pendingImagesFor(id);
      expect(pending, hasLength(1));
      expect(pending.single.fileName, 'well.png');

      // And the uploader resolves the same key back to a real file. This is the
      // pair: one side minted the id and wrote the bytes under it, and the other
      // side finds them from the row alone.
      final file = await VoiceImages.existingFileFor(
        newsId: id,
        filename: pending.single.fileName,
      );
      expect(file, isNotNull);
      expect(await file!.readAsBytes(), bytes);
    },
  );

  test('the bytes are PUT onto a resources document, not onto news', () async {
    final posts = stubPosts();
    final puts = stubAttachments();
    final id = await seedPostWithImage();

    await drainOnce(id);

    expect(posts, hasLength(2));
    expect(
      posts.first.url,
      'https://planet.example/db/resources',
      reason:
          'a voice image is its own `resources` document, not an '
          'attachment on the post (UploadManager.kt:311)',
    );
    expect(posts.last.url, 'https://planet.example/db/news');

    expect(puts, hasLength(1));
    expect(
      puts.single.url,
      'https://planet.example/db/resources/resource-1/well.png',
    );
    expect(puts.single.bytes, bytes);
    expect(
      puts.single.ifMatch,
      '1-abc',
      reason:
          'the rev goes in `If-Match`, not a `?rev=` query '
          '(FileUploader.getHeaderMap)',
    );
  });

  test('the resource document is the one createImage builds', () async {
    final posts = stubPosts();
    stubAttachments();
    final id = await seedPostWithImage();

    await drainOnce(id);

    final resource = posts.first.body;
    expect(resource['title'], 'well.png');
    expect(resource['filename'], 'well.png');
    expect(resource['private'], isTrue);
    expect(resource['mediaType'], 'image');
    expect(resource['privateFor'], isEmpty);
    // Read off the post rather than a live session: the drain is headless and
    // can outlive the composer, and the row already records who wrote it.
    expect(resource['addedBy'], 'org.couchdb.user:ada');
    expect(resource['sourcePlanet'], 'learning');
    expect(resource['resideOn'], 'earth');
  });

  test('a blank planet code is sent, not omitted', () async {
    // `createImage` guards with `?.let`, so a **null** code omits the key and
    // an empty one is still written. An `isNotEmpty` test would drop a key
    // Kotlin sends for a user whose code is blank rather than absent.
    final posts = stubPosts();
    stubAttachments();
    final id = await voices.createPost(
      message: 'the well is dry',
      userId: 'org.couchdb.user:ada',
      userName: 'ada',
      planetCode: '',
      parentCode: '',
      attachments: [VoiceImageAttachment(bytes: bytes, filename: 'well.png')],
    );

    await drainOnce(id);

    final resource = posts.first.body;
    expect(resource.containsKey('resideOn'), isTrue);
    expect(resource['resideOn'], '');
    expect(resource.containsKey('sourcePlanet'), isTrue);
    expect(resource['sourcePlanet'], '');
    // `addDocumentOrigin()` plus the two device names, which is exactly what
    // `createImage` writes (UploadManager.kt:135-138).
    expect(resource['app'], isNotNull);
    expect(resource.containsKey('androidId'), isTrue);
    expect(resource.containsKey('deviceName'), isTrue);
    expect(resource.containsKey('customDeviceName'), isTrue);
  });

  test('the news document carries the markdown and the images array', () async {
    final posts = stubPosts();
    stubAttachments();
    final id = await seedPostWithImage();

    await drainOnce(id);

    final news = posts.last.body;
    expect(
      news['message'],
      'the well is dry\n![](resources/resource-1/well.png)',
      reason:
          'one `\\n` per image, appended to the original message '
          '(UploadManager.kt:340)',
    );
    expect(news['images'], [
      {
        'resourceId': 'resource-1',
        'filename': 'well.png',
        'markdown': '![](resources/resource-1/well.png)',
      },
    ]);
  });

  test('two images are appended in order, one line each', () async {
    final posts = stubPosts();
    final puts = stubAttachments();
    final id = await voices.createPost(
      message: 'two shots',
      userId: 'org.couchdb.user:ada',
      userName: 'ada',
      attachments: [
        VoiceImageAttachment(bytes: bytes, filename: 'a.png'),
        VoiceImageAttachment(bytes: bytes, filename: 'b.jpg'),
      ],
    );

    await drainOnce(id);

    expect(puts.map((p) => p.url), [
      'https://planet.example/db/resources/resource-1/a.png',
      'https://planet.example/db/resources/resource-2/b.jpg',
    ]);
    expect(
      posts.last.body['message'],
      'two shots\n'
      '![](resources/resource-1/a.png)\n'
      '![](resources/resource-2/b.jpg)',
    );
  });

  test('two picks with the same name do not share one slot', () async {
    // Keyed on `<newsId>/<fileName>`, so two `photo.jpg` picks would land in
    // the same file and both `imageUrls` entries would resolve to the second
    // one's bytes — one image lost, silently. Kotlin cannot reach this: its
    // entries carry two different absolute source paths.
    final posts = stubPosts();
    final puts = stubAttachments();
    final first = Uint8List.fromList([1, 1, 1]);
    final second = Uint8List.fromList([2, 2, 2]);
    final id = await voices.createPost(
      message: 'two shots of the same pump',
      userId: 'org.couchdb.user:ada',
      userName: 'ada',
      attachments: [
        VoiceImageAttachment(bytes: first, filename: 'photo.jpg'),
        VoiceImageAttachment(bytes: second, filename: 'photo.jpg'),
      ],
    );

    await drainOnce(id);

    expect(puts.map((p) => p.bytes), [first, second]);
    expect(puts.map((p) => p.url), [
      'https://planet.example/db/resources/resource-1/photo.jpg',
      'https://planet.example/db/resources/resource-2/photo-2.jpg',
    ]);
    // The extension survives the disambiguation, so MIME detection still works.
    expect(puts.map((p) => p.contentType), ['image/jpeg', 'image/jpeg']);
    expect(
      posts.last.body['message'],
      'two shots of the same pump\n'
      '![](resources/resource-1/photo.jpg)\n'
      '![](resources/resource-2/photo-2.jpg)',
    );
  });

  test('two names that reduce to one slot do not collide', () async {
    // The slot is `<newsId>/<_segment(name)>`, and `_segment` takes the
    // basename so a path separator cannot escape the directory. That means two
    // *different* picked names can reduce to the same file — so the
    // de-duplication has to key on the stored name, not the raw one. Keying on
    // the raw name let these two through and the first image was lost.
    final posts = stubPosts();
    final puts = stubAttachments();
    final first = Uint8List.fromList([1, 1, 1]);
    final second = Uint8List.fromList([2, 2, 2]);
    final id = await voices.createPost(
      message: 'two folders, one name',
      userId: 'org.couchdb.user:ada',
      userName: 'ada',
      attachments: [
        VoiceImageAttachment(bytes: first, filename: 'a/photo.jpg'),
        VoiceImageAttachment(bytes: second, filename: 'b/photo.jpg'),
      ],
    );

    await drainOnce(id);

    expect(puts.map((p) => p.bytes), [first, second]);
    expect(puts.map((p) => p.url), [
      'https://planet.example/db/resources/resource-1/photo.jpg',
      'https://planet.example/db/resources/resource-2/photo-2.jpg',
    ]);
    // And the attachment name is the sanitised one, so the separator never
    // reaches the URL as an extra path segment.
    expect(
      posts.last.body['message'],
      'two folders, one name\n'
      '![](resources/resource-1/photo.jpg)\n'
      '![](resources/resource-2/photo-2.jpg)',
    );
  });

  test('the mime type is detected from the name, as a182edd made it', () async {
    stubPosts();
    final puts = stubAttachments();

    await drainOnce(await seedPostWithImage(filename: 'chart.png'));
    expect(puts.single.contentType, 'image/png');

    // `a182edd` swapped byte-sniffing through a `file://` connection for
    // `FileUtils.getMimeType(fileName) ?: "application/octet-stream"`, so an
    // unrecognised extension falls back rather than reading the file.
    puts.clear();
    await drainOnce(await seedPostWithImage(filename: 'notes.qqq'));
    expect(puts.single.contentType, 'application/octet-stream');
  });

  test(
    'a delivered post keeps its images and drops its pending bytes',
    () async {
      stubPosts();
      stubAttachments();
      final id = await seedPostWithImage();

      await drainOnce(id);

      final row = await voices.getById(id);
      expect(row?.docId, 'news-2');
      expect(
        row?.imageUrls,
        isEmpty,
        reason: 'clearing `imageUrls` is what marks the attachments delivered',
      );
      expect(jsonDecode(row!.images!), hasLength(1));
      expect(
        row.isEdited,
        isFalse,
        reason:
            'the message and images the handler derived are not a local '
            'edit; treating them as one would re-queue the post forever',
      );
      // The slot is unreachable once `imageUrls` is cleared, so it is removed.
      expect(
        await VoiceImages.existingFileFor(newsId: id, filename: 'well.png'),
        isNull,
      );
    },
  );

  test('a transient attachment failure is retried, not abandoned', () async {
    // `OutboxDrainer` classifies `code >= 500` and a transport
    // `NetworkException` as retryable and everything else as permanent. The
    // first cut wrapped every image failure in `NetworkError(null, ...)`,
    // whose `(code ?? 0) < 500` reads as **permanent** — so one dropped
    // connection mid-attachment abandoned the post, its text included, after
    // a single attempt. The handler now returns the underlying result.
    stubPosts();
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(503, 'busy'),
    );

    final id = await seedPostWithImage();
    final operation = await queuedFor(id);
    final result = await uploader.handler(
      operation,
      jsonDecode(operation.payload) as Map<String, dynamic>,
      'Basic dGVzdA==',
    );

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect(
      (result as NetworkError<Map<String, dynamic>>).code,
      503,
      reason: 'the drainer needs the real code to know this is retryable',
    );
  });

  test('cleanup cannot fail a post the server already accepted', () async {
    // `markUploaded` deletes the delivered slot, and the handler awaits it
    // **after** the news document has been accepted. Anything escaping that
    // cleanup fails the outbox row for a post that is already on the server,
    // and the next drain POSTs a second copy — the exact duplicate the
    // handler's id/rev guard exists to prevent.
    //
    // Not hypothetical: `getApplicationDocumentsDirectory` on an engine with
    // no `path_provider` channel throws a `FlutterError`, which is an `Error`
    // and not an `Exception`, and headless WorkManager engines are where this
    // drains. An `on Exception` catch let it straight through, and the
    // pre-existing `markUploaded` test in `voices_repository_test.dart` is
    // what caught it.
    //
    // Driven at `markUploaded` rather than through the handler because a
    // `baseDirectory` that throws for the whole drain fails earlier, in the
    // attachment *read* — which is correct there (nothing was uploaded, so a
    // retry is right) and would not exercise this.
    final id = await seedPostWithImage();
    VoiceImages.baseDirectory = () async => throw StateError('no channel');

    await expectLater(
      voices.markUploaded(
        id,
        'news-1',
        '1-rev',
        images: const [
          {'a': 1},
        ],
      ),
      completes,
    );
    final row = await voices.getById(id);
    expect(row?.docId, 'news-1');
    expect(row?.imageUrls, isEmpty);
  });

  test('a refused attachment leaves the post queued with its image', () async {
    final posts = stubPosts();
    stubAttachments(succeed: false);
    final id = await seedPostWithImage();

    final operation = await queuedFor(id);
    final result = await uploader.handler(
      operation,
      jsonDecode(operation.payload) as Map<String, dynamic>,
      'Basic dGVzdA==',
    );

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect(
      posts.map((p) => p.url),
      ['https://planet.example/db/resources'],
      reason:
          'the news document must not go up referencing an attachment '
          'that was refused — Kotlin discards the PUT response unchecked '
          '(UploadManager.kt:327-331) and uploads it anyway',
    );
    final row = await voices.getById(id);
    expect(row?.docId, isNull);
    expect(
      row?.imageUrls,
      hasLength(1),
      reason: 'the pending image survives so the retry can send it',
    );
  });

  test(
    'a post whose bytes vanished is refused, not silently truncated',
    () async {
      stubPosts();
      stubAttachments();
      final id = await seedPostWithImage();
      // Storage management cleared the slot between composing and draining.
      await VoiceImages.deleteFor(id);

      final operation = await queuedFor(id);
      final result = await uploader.handler(
        operation,
        jsonDecode(operation.payload) as Map<String, dynamic>,
        'Basic dGVzdA==',
      );

      expect(result, isA<NetworkError<Map<String, dynamic>>>());
      verifyNever(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: any(named: 'ifMatch'),
        ),
      );
    },
  );

  test(
    'a post with no images uploads without touching the resources db',
    () async {
      final posts = stubPosts();
      stubAttachments();
      final id = await voices.createPost(
        message: 'no pictures',
        userId: 'org.couchdb.user:ada',
        userName: 'ada',
      );

      await drainOnce(id);

      expect(posts, hasLength(1));
      expect(posts.single.url, 'https://planet.example/db/news');
      verifyNever(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          contentType: any(named: 'contentType'),
          ifMatch: any(named: 'ifMatch'),
        ),
      );
    },
  );

  test('re-sending a delivered post does not strip its images', () async {
    // **The one Kotlin bug in this path that a naive port would inherit.**
    // `UploadManager.kt:345` overwrites the serialized `images` with an array
    // rebuilt from `imageUrls` on every sweep — and `markNewsUploaded` has
    // just cleared `imageUrls`, so the *second* sweep of an already-delivered
    // post sends `images: []`, stripping the image metadata off the server
    // document and, via the write-back, off the local row too. The resource
    // document and its attachment survive on the server with nothing pointing
    // at them, and the image stops rendering in the app.
    //
    // Driven the way a user reaches it: upload, then edit, which is the only
    // thing that puts a delivered post back in `pendingUploads`.
    final posts = stubPosts();
    stubAttachments();
    final id = await seedPostWithImage();
    await drainOnce(id);
    expect((posts.last.body['images'] as List), hasLength(1));

    await voices.editPost(newsId: id, message: 'the pump is broken');
    posts.clear();
    await drainOnce(id);

    expect(
      posts.single.url,
      'https://planet.example/db/news',
      reason: 'nothing pending, so no second resource document',
    );
    expect(
      posts.single.body['images'],
      [
        {
          'resourceId': 'resource-1',
          'filename': 'well.png',
          'markdown': '![](resources/resource-1/well.png)',
        },
      ],
      reason: 'the second send must carry the images the first established',
    );
  });
}

/// The production default, restored after a test swaps in a temp directory —
/// calling it under `flutter test` throws, which is the point: a test that
/// forgets to override is loud rather than writing into a real app directory.
Future<Directory> getApplicationDocumentsDirectoryUnavailable() =>
    throw UnsupportedError('VoiceImages.baseDirectory was not overridden');
