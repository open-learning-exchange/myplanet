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
import 'package:myplanet/providers/chat_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/chat_repository.dart';
import 'package:myplanet/repository/voices_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

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

/// The share *write* path, end to end against a real database: the Kotlin's
/// `ChatHistoryFragment` gate, `shareChatToVoices`, and the enqueue the port
/// adds in place of the Kotlin's full-table upload sweep.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
  });
  tearDown(() => db.close());

  Future<ProviderContainer> containerFor({UserRow? user}) async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(MockPlanetApi()),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(config),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            user ?? buildUserRow(id: 'org.couchdb.user:ada', name: 'ada'),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  ChatRow chat({String? docId = 'chat_1'}) => ChatRow(
    id: 'c1',
    docId: docId,
    rev: '2-rev',
    title: 'Tech Discussion',
    user: 'ada',
    aiProvider: 'openai',
    conversations: '[{"query":"q","response":"r"}]',
    lastUsed: 0,
    isUploaded: false,
  );

  const team = ChatShareTarget(id: 'team_1', name: 'Bricklayers');

  group('the share targets', () {
    Future<void> seedTeams() => db.teamDao.upsertAll([
      TeamsCompanion.insert(
        id: 'team_1',
        name: const Value('Bricklayers'),
        type: const Value('team'),
        teamType: const Value('local'),
      ),
      TeamsCompanion.insert(
        id: 'team_2',
        name: const Value('Bakers'),
        type: const Value('team'),
      ),
      TeamsCompanion.insert(
        id: 'ent_1',
        name: const Value('Cooperative'),
        type: const Value('enterprise'),
      ),
      TeamsCompanion.insert(
        id: 'team_3',
        name: const Value('Archived'),
        type: const Value('team'),
        status: const Value('archived'),
      ),
      TeamsCompanion.insert(
        id: 'mem_1',
        docType: const Value('membership'),
        teamId: const Value('team_1'),
        userId: const Value('org.couchdb.user:ada'),
      ),
      TeamsCompanion.insert(
        id: 'mem_2',
        docType: const Value('membership'),
        teamId: const Value('ent_1'),
        userId: const Value('org.couchdb.user:ada'),
      ),
    ]);

    test('offers only the teams and enterprises the user belongs to', () async {
      await seedTeams();
      final container = await containerFor();

      final targets = await container.read(chatShareTargetsProvider.future);
      expect(targets.teams.map((t) => t.id), ['team_1']);
      expect(targets.enterprises.map((t) => t.id), ['ent_1']);
      // `teamType` is what becomes `messageType` on the wire.
      expect(targets.teams.single.teamType, 'local');
      expect(targets.teams.single.name, 'Bricklayers');
    });

    // `getShareableTeams` short-circuits an empty membership set to
    // `emptyList()` rather than falling through to the whole catalog.
    test('a user who belongs to nothing is offered nothing', () async {
      await seedTeams();
      final container = await containerFor(
        user: buildUserRow(id: 'org.couchdb.user:bob', name: 'bob'),
      );

      final targets = await container.read(chatShareTargetsProvider.future);
      expect(targets.teams, isEmpty);
      expect(targets.enterprises, isEmpty);
    });

    test('the community is synthesized when no team document exists', () async {
      final prefs = PlanetPrefs(await SharedPreferences.getInstance());
      await prefs.setCommunityName('lc');
      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetApiProvider.overrideWithValue(MockPlanetApi()),
          planetPrefsProvider.overrideWithValue(prefs),
          serverConfigProvider.overrideWith(
            () => _TestServerConfigNotifier(config.copyWith(parentCode: 'pc')),
          ),
          sessionProvider.overrideWith(
            () => _TestSessionNotifier(
              buildUserRow(id: 'org.couchdb.user:ada', name: 'ada'),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final targets = await container.read(chatShareTargetsProvider.future);
      // `"<communityName>@<parentCode>"`, where communityName is the
      // configuration `code` rather than a display name.
      expect(targets.community?.id, 'lc@pc');
      expect(targets.community?.name, 'lc');
    });

    test(
      'no community name or parent code means no community target',
      () async {
        final container = await containerFor();
        final targets = await container.read(chatShareTargetsProvider.future);
        expect(targets.community, isNull);
      },
    );
  });

  test('a share writes the voices row and queues it for upload', () async {
    final container = await containerFor();

    final outcome = await container
        .read(chatShareActionsProvider)
        .share(
          chat: chat(),
          target: team,
          section: ChatShareSection.teams,
          note: 'worth a read',
        );

    expect(outcome, ChatShareOutcome.shared);

    final rows = await db.newsDao.getAll();
    expect(rows, hasLength(1));
    expect(rows.single.newsId, 'chat_1');
    expect(rows.single.message, 'worth a read');
    expect(rows.single.chat, isTrue);

    // The Kotlin uploads news by sweeping the whole table on every sync; the
    // port has no such sweep, so a share that did not enqueue would sit on the
    // device until some unrelated voices action rescued it.
    final queued = await db.outboxDao.forItem(
      VoicesUploader.type,
      rows.single.id,
    );
    expect(queued, hasLength(1));
    final payload = jsonDecode(queued.single.payload) as Map<String, dynamic>;
    expect(payload['chat'], isTrue);
    expect((payload['news'] as Map)['_id'], 'chat_1');
  });

  test('a second share of the same chat and target is refused', () async {
    final container = await containerFor();
    final actions = container.read(chatShareActionsProvider);

    await actions.share(
      chat: chat(),
      target: team,
      section: ChatShareSection.teams,
      note: '',
    );
    final second = await actions.share(
      chat: chat(),
      target: team,
      section: ChatShareSection.teams,
      note: '',
    );

    expect(second, ChatShareOutcome.alreadyShared);
    expect(await db.newsDao.getAll(), hasLength(1));
  });

  test('the same chat can still reach a different destination', () async {
    final container = await containerFor();
    final actions = container.read(chatShareActionsProvider);

    await actions.share(
      chat: chat(),
      target: team,
      section: ChatShareSection.teams,
      note: '',
    );
    final second = await actions.share(
      chat: chat(),
      target: const ChatShareTarget(id: 'team_2', name: 'Bakers'),
      section: ChatShareSection.teams,
      note: '',
    );

    expect(second, ChatShareOutcome.shared);
    expect(await db.newsDao.getAll(), hasLength(2));
  });

  // `ChatHistoryFragment:166-170` drops the tap when either id is empty, and
  // says nothing at all. The gate is kept; the silence is not.
  test('a chat the server has never seen is not shared', () async {
    final container = await containerFor();

    final outcome = await container
        .read(chatShareActionsProvider)
        .share(
          chat: chat(docId: null),
          target: team,
          section: ChatShareSection.teams,
          note: '',
        );

    expect(outcome, ChatShareOutcome.unavailable);
    expect(await db.newsDao.getAll(), isEmpty);
  });

  test('a share with no destination is not shared', () async {
    final container = await containerFor();

    final outcome = await container
        .read(chatShareActionsProvider)
        .share(
          chat: chat(),
          target: null,
          section: ChatShareSection.community,
          note: '',
        );

    expect(outcome, ChatShareOutcome.unavailable);
    expect(await db.newsDao.getAll(), isEmpty);
  });

  test('the destination map sees the share on the next read', () async {
    final container = await containerFor(
      user: buildUserRow(
        id: 'org.couchdb.user:ada',
        name: 'ada',
      ).copyWith(planetCode: const Value('lc')),
    );

    expect(
      await container.read(sharedChatDestinationsProvider.future),
      isEmpty,
    );

    await container
        .read(chatShareActionsProvider)
        .share(
          chat: chat(),
          target: team,
          section: ChatShareSection.teams,
          note: '',
        );

    expect(await container.read(sharedChatDestinationsProvider.future), {
      'chat_1': {'team_1'},
    });
  });
}
