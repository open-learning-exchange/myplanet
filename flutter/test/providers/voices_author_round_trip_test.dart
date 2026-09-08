import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

/// Phase 144, Job 2. A `news` document carries **no** top-level `userId` or
/// `userName`: the nested `user` object is the only author identity on it, and
/// `NewsMapper.fromDoc` reads the local row's `user`, `userId` *and* `userName`
/// back out of that one object. Kotlin sets it on every write path
/// (`News.createNews` → `news.user = gson.toJson(user.serialize())`).
///
/// `VoicesRepository.authorJson` landed with the chat share in Phase 140 and
/// the other three writers never used it, so every ordinary voice post, team
/// post and reply the port authored uploaded with `"user": null` — anonymous
/// on Planet, and stripped of its author on this device at the next sync-in.
///
/// These are round-trip tests on purpose: each drives the writer, takes the
/// document the uploader actually queued, and feeds *that* back through the
/// sync-in path. A fixture that fabricates the document would prove nothing
/// about the pair.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

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

  final ada = buildUserRow(
    id: 'local-ada',
    name: 'ada',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.org',
  ).copyWith(couchId: const Value('org.couchdb.user:ada'));

  Future<ProviderContainer> containerFor({UserRow? user}) async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        serverConfigProvider.overrideWith(() => _TestServerConfigNotifier()),
        sessionProvider.overrideWith(() => _TestSessionNotifier(user ?? ada)),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  /// The document the outbox is actually holding for [id] — what would go on
  /// the wire, not a re-serialization of the row.
  Future<Map<String, dynamic>> queuedDocument(String id) async {
    final rows = await db.outboxDao.forItem(VoicesUploader.type, id);
    expect(rows, hasLength(1), reason: 'nothing was queued for $id');
    return jsonDecode(rows.single.payload) as Map<String, dynamic>;
  }

  /// The sync-in half: the server hands the document straight back, with the
  /// `_id`/`_rev` CouchDB would have stamped on it.
  Future<void> pullBack(
    ProviderContainer container,
    Map<String, dynamic> document, {
    required String docId,
  }) => container.read(voicesRepositoryProvider).cacheDocuments([
    {...document, '_id': docId, '_rev': '1-rev'},
  ]);

  void expectAuthored(Map<String, dynamic> document) {
    final author = document['user'];
    expect(
      author,
      isA<Map<String, dynamic>>(),
      reason:
          'the nested `user` object is the only author identity a news '
          'document carries',
    );
    expect((author as Map<String, dynamic>)['_id'], 'org.couchdb.user:ada');
    expect(author['name'], 'ada');
  }

  Future<void> expectAuthorSurvivesPull(
    ProviderContainer container,
    String id, {
    required String docId,
  }) async {
    final document = await queuedDocument(id);
    expectAuthored(document);

    await pullBack(container, document, docId: docId);

    final row = await container.read(voicesRepositoryProvider).getById(id);
    expect(
      row?.userId,
      'org.couchdb.user:ada',
      reason: 'the pull read the author back out of the document it sent',
    );
    expect(row?.userName, 'ada');
    expect(row?.user, isNotNull);
  }

  test('an ordinary voice post names its author, and keeps it', () async {
    final container = await containerFor();
    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');

    await expectAuthorSurvivesPull(container, id!, docId: 'server-1');
  });

  test('a team voice post names its author, and keeps it', () async {
    final container = await containerFor();
    final id = await container
        .read(voicesActionsProvider)
        .createTeamPost(
          teamId: 'team-1',
          teamName: 'Water',
          message: 'The pump needs parts',
        );

    await expectAuthorSurvivesPull(container, id!, docId: 'server-2');
  });

  test('a reply names its author, and keeps it', () async {
    final container = await containerFor();
    final parent = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');
    final id = await container
        .read(voicesActionsProvider)
        .postReply(parentId: parent!, message: 'Since when?');

    await expectAuthorSurvivesPull(container, id!, docId: 'server-3');
  });

  test('the author object carries no credentials', () async {
    // A voices post is a **public** document. `authorJson` is
    // `UserEntity.serialize()` minus the credential branch, the device trio
    // and the `_attachments` photo; `UserMapper.toDoc` is the `_users` PUT
    // body and must never be reached for here.
    final container = await containerFor(
      user: ada.copyWith(
        password: const Value('hunter2'),
        derivedKey: const Value('deadbeef'),
        salt: const Value('c0ffee'),
      ),
    );
    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');

    final author = (await queuedDocument(id!))['user'] as Map<String, dynamic>;
    for (final forbidden in const [
      'password',
      'derived_key',
      'salt',
      'password_scheme',
      '_attachments',
    ]) {
      expect(
        author.containsKey(forbidden),
        isFalse,
        reason: 'a public news document must not carry $forbidden',
      );
    }
  });

  test('a field the user has not filled in is absent, not null', () async {
    // `News.createNews` serializes with `JsonUtils.gson`, a bare `Gson()` whose
    // `serializeNulls` is off, so `addProperty("middleName", null)` writes
    // nothing at all. `_viewInJson` in the same file already applies that rule
    // — and there it is load-bearing, because `shareToCommunity` back-fills on
    // `!containsKey('name')`. Applying it to one writer and not the other left
    // a counter-example sitting twelve lines away.
    final container = await containerFor(
      user: buildUserRow(
        id: 'local-bare',
        name: 'bare',
      ).copyWith(couchId: const Value('org.couchdb.user:bare')),
    );
    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');

    final author = (await queuedDocument(id!))['user'] as Map<String, dynamic>;
    for (final absent in const [
      'middleName',
      'birthPlace',
      'age',
      'phoneNumber',
      '_rev',
    ]) {
      expect(
        author.containsKey(absent),
        isFalse,
        reason: 'Gson drops a null-valued key; $absent was written as null',
      );
    }
    // The non-nullable columns still go out, because Kotlin writes them too.
    expect(author['type'], 'user');
    expect(author['isUserAdmin'], isFalse);
    expect(author['roles'], isEmpty);
  });

  test('a pulled document with no author clears the local author', () async {
    // The loss this fix closes, demonstrated from the reader's side: the
    // mapper writes `user`, `userId` and `userName` unconditionally, so an
    // author-less document does not merely fail to set them — it erases what
    // the local row had. Same shape as Phase 74's reactions and Phase 56's
    // security data.
    final container = await containerFor();
    final repository = container.read(voicesRepositoryProvider);
    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');
    final document = await queuedDocument(id!);
    expectAuthored(document);
    // Delivered, so the row now carries the server id the pull matches on —
    // the state every uploaded post reaches, and the only one in which a
    // pulled document can overwrite local columns at all.
    await repository.markUploaded(id, 'server-4', '1-rev');

    await pullBack(container, {...document}..remove('user'), docId: 'server-4');

    final row = await repository.getById(id);
    expect(
      row?.userId,
      isNull,
      reason:
          'the mapper writes the author columns unconditionally, so an '
          'author-less document erases what the local row had — which is '
          'exactly what an author-less *upload* got back on the next sync',
    );
    expect(row?.userName, isNull);
    expect(row?.user, isNull);
  });

  test('a session that never resolves does not swallow the post', () async {
    // `VoicesActions` read `ref.read(sessionProvider).valueOrNull` on a
    // provider nothing here watches — `team_voices_screen` never touches
    // `sessionProvider` at all — so the user was null, the early return
    // dropped the composed post with no error and no row, and the app was
    // saved only by the router's `ref.listen`. The `await` also has to sit
    // inside the enclosing `try`, because a future can reject where
    // `valueOrNull` could not.
    final container = await containerFor();
    final id = await container
        .read(voicesActionsProvider)
        .createTeamPost(
          teamId: 'team-1',
          teamName: 'Water',
          message: 'The pump needs parts',
        );

    expect(id, isNotNull, reason: 'the team post was dropped');
    expect(
      (await container.read(voicesRepositoryProvider).getById(id!))?.message,
      'The pump needs parts',
    );
  });

  test('a rejecting session is reported, not thrown', () async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        serverConfigProvider.overrideWith(() => _TestServerConfigNotifier()),
        sessionProvider.overrideWith(_RejectingSessionNotifier.new),
      ],
    );
    addTearDown(container.dispose);

    expect(
      await container.read(voicesActionsProvider).createPost('The well is dry'),
      isNull,
    );
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}

class _TestServerConfigNotifier extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}

class _RejectingSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() async => throw StateError('no session');
}
