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
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/repository/voices_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

/// Phase 144. Found by the ground-truth `parity-auditor` pass, not by the two
/// jobs this lane opened on.
///
/// `VoicesFragment.btnSubmit` (`ui/voices/VoicesFragment.kt:137-148`) builds
/// **four** keys before it calls `createNews`:
///
/// ```kotlin
/// map["viewInId"] = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
/// map["viewInSection"] = "community"
/// map["messageType"] = "sync"
/// map["messagePlanetCode"] = user?.planetCode ?: ""
/// ```
///
/// The port's community composer called `createPost(message)` with the message
/// alone, so `_viewInJson(id: null, …)` returned `"[]"` and
/// [VoicesRepository.isVisibleToUser] fell through its `viewIn == null ||
/// viewIn.isEmpty` guard to `false` — `viewableBy` is null on a locally
/// composed post, so the shortcut above it does not fire either. The post was
/// written, listed nowhere, and uploaded as a document with no audience at all,
/// because `serialize` omits `viewIn` for an empty list.
///
/// That is the Phase 113 shape: the writer could not produce a value the
/// reader's predicate matches. Every fixture in the existing feed tests
/// hand-built its rows through `cacheDocuments`, which is why the pair was
/// never asked about.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;

  setUpAll(() => registerFallbackValue(<String, dynamic>{}));

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    api = MockPlanetApi();
  });
  tearDown(() => db.close());

  final ada = buildUserRow(
    id: 'local-ada',
    name: 'ada',
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

  Future<Map<String, dynamic>> queuedDocument(String id) async {
    final rows = await db.outboxDao.forItem(VoicesUploader.type, id);
    expect(rows, hasLength(1), reason: 'nothing was queued for $id');
    return jsonDecode(rows.single.payload) as Map<String, dynamic>;
  }

  test('a composed community post appears in the community feed', () async {
    final container = await containerFor(
      user: ada.copyWith(
        planetCode: const Value('learning'),
        parentCode: const Value('earth'),
      ),
    );

    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');

    final feed = await container
        .read(voicesRepositoryProvider)
        .watchCommunityFeed(
          communityViewerIdentifier(
            planetCode: 'learning',
            parentCode: 'earth',
          ),
        )
        .first;
    expect(
      feed.map((row) => row.id),
      contains(id),
      reason:
          'the author cannot see their own community post — the writer '
          'produces no value `isVisibleToUser` can match',
    );
  });

  test('the composed post addresses the community on the wire', () async {
    final container = await containerFor(
      user: ada.copyWith(
        planetCode: const Value('learning'),
        parentCode: const Value('earth'),
      ),
    );

    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');
    final document = await queuedDocument(id!);

    expect(
      document['viewIn'],
      [
        {'_id': 'learning@earth', 'section': 'community'},
      ],
      reason:
          'a document with no viewIn is invisible in Planet\'s community view '
          'too, and a pull-back cannot repair it',
    );
    // `"sync"` is the literal `VoicesFragment` writes, and it is what
    // distinguishes a community post from a `"team"` one.
    expect(document['messageType'], 'sync');
    expect(document['messagePlanetCode'], 'learning');
  });

  test('a user with no planet codes still addresses the community', () async {
    // Kotlin interpolates both halves unguarded, so a user missing either
    // writes `"@"`, `"learning@"` or `""@earth"` — and `isVisibleToUser`
    // treats an empty or `"@"` id as the planet-wide wildcard on both sides.
    // A guest-shaped account must not silently lose its post instead.
    final container = await containerFor();

    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');

    final feed = await container
        .read(voicesRepositoryProvider)
        .watchCommunityFeed(
          communityViewerIdentifier(planetCode: null, parentCode: null),
        )
        .first;
    expect(feed.map((row) => row.id), contains(id));
    expect((await queuedDocument(id!))['viewIn'], [
      {'_id': '@', 'section': 'community'},
    ]);
  });

  test('a composed post counts as a community voice', () async {
    // Two other readers key on the same `viewIn` community entry, and both
    // were wrong for a port-composed post before the writer was fixed.
    //
    // `getCommunityVoiceDates` is the challenge dialog's "post five community
    // voices" counter (Phase 81, port of `getCommunityVoiceDates`), and it
    // filters on `isCommunityNews` — so a user could post five voices from the
    // port's own community screen and the challenge would still read zero.
    //
    // The other is `VoicesAdapter.canShare`, which is
    // `news?.isCommunityNews != true` (`VoicesAdapter.kt:699`): the port was
    // offering a "share to community" action on a post that Kotlin already
    // treats as being in the community. `voices_screen.dart:121` reads it.
    final container = await containerFor(
      user: ada.copyWith(
        planetCode: const Value('learning'),
        parentCode: const Value('earth'),
      ),
    );
    final repository = container.read(voicesRepositoryProvider);

    final id = await container
        .read(voicesActionsProvider)
        .createPost('The well is dry');

    expect(
      await repository.getCommunityVoiceDates(0, 1 << 62, null),
      hasLength(1),
      reason: 'the challenge task could not be completed from this screen',
    );
    expect(
      VoicesRepository.isCommunityNews((await repository.getById(id!))!),
      isTrue,
    );
  });

  test('a team post carries no planet code it cannot know', () async {
    // `TeamsVoicesFragment.kt:78` writes `team?.teamPlanetCode ?: ""` — the
    // **team's** planet, not the author's. The port's `Teams` table has no
    // such column (reported against `tables.dart`, which this lane does not
    // own), and `MyTeam.kt:86` reads it with `JsonUtils.getString`, so a
    // document that omits the field yields `""` in Kotlin too. `''` is
    // therefore the faithful interim value; `user.planetCode` is a *claim*
    // about a team that may have been created on another planet, and it
    // disagreed with the port's own chat-share writer, which already sends
    // `''` for exactly this reason (`chat_repository.dart:306-310`).
    final container = await containerFor(
      user: ada.copyWith(planetCode: const Value('learning')),
    );

    final id = await container
        .read(voicesActionsProvider)
        .createTeamPost(
          teamId: 'team-1',
          teamName: 'Water',
          message: 'The pump needs parts',
        );

    expect((await queuedDocument(id!))['messagePlanetCode'], '');
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
