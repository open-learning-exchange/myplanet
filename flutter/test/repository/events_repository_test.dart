import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/events_repository.dart';

void main() {
  late AppDatabase database;
  late EventsRepository repository;
  late MockPlanetApi api;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = EventsRepository(
      api,
      database.meetupDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(1234),
      createId: () => 'local-meetup',
    );
  });
  tearDown(() => database.close());

  test('maps and watches server meetups for a team', () async {
    expect(
      await repository.cacheDocuments([
        {
          '_id': 'meetup-1',
          '_rev': '1-a',
          'title': 'Study group',
          'startDate': 20,
          'endDate': 30,
          'day': ['Monday'],
          'link': {'teams': 'team-1'},
        },
      ]),
      1,
    );

    final rows = await repository.watchForTeam('team-1').first;
    expect(rows.single.title, 'Study group');
    expect(rows.single.day, '["Monday"]');
    expect(rows.single.meetupIdRev, '1-a');
  });

  test(
    'preserves local edits during refresh and exposes pending upload',
    () async {
      await repository.cacheDocuments([
        {
          '_id': 'meetup-1',
          'title': 'Old',
          'link': {'teams': 'team-1'},
        },
      ]);
      expect(
        await repository.update(
          'meetup-1',
          title: ' Local title ',
          description: ' Local description ',
          startDate: 1,
          endDate: 2,
          startTime: '09:00',
          endTime: '10:00',
          location: ' Room 1 ',
          link: ' https://example.test ',
          recurring: 'weekly',
        ),
        isTrue,
      );
      await repository.cacheDocuments([
        {
          '_id': 'meetup-1',
          'title': 'Server title',
          'link': {'teams': 'team-1'},
        },
      ]);

      final row = await repository.getById('meetup-1');
      expect(row?.title, 'Local title');
      expect(row?.updated, isTrue);
      expect(await repository.pendingUploads(), hasLength(1));
    },
  );

  test('toggles attendance only when a user is available', () async {
    await repository.cacheDocuments([
      {'_id': 'meetup-1', 'title': 'Meetup'},
    ]);
    expect(
      (await repository.toggleAttendance('meetup-1', null))?.userId,
      isNull,
    );
    expect(
      (await repository.toggleAttendance('meetup-1', 'user-1'))?.userId,
      'user-1',
    );
    expect(
      (await repository.toggleAttendance('meetup-1', 'user-1'))?.userId,
      '',
    );
  });

  test('a joined meetup is not left when there is no signed-in user', () async {
    await repository.cacheDocuments([
      {'_id': 'meetup-1', 'title': 'Meetup'},
    ]);
    await repository.toggleAttendance('meetup-1', 'user-1');

    // The Kotlin's old guard wrote userId = '' here; 2a49db978 made a
    // missing user a no-op in both directions.
    expect(
      (await repository.toggleAttendance('meetup-1', null))?.userId,
      'user-1',
    );
    expect(
      (await repository.toggleAttendance('meetup-1', ''))?.userId,
      'user-1',
    );
  });

  test('a server refresh does not un-join the user', () async {
    // The `meetups` document says nothing about whether *this* user joined,
    // so a sync that writes `userId` from it wipes the attendance marker —
    // and `meetupsOnShelf` then drops the meetup from the shelf push.
    await repository.cacheDocuments([
      {'_id': 'meetup-1', 'title': 'Study group'},
    ]);
    await repository.toggleAttendance('meetup-1', 'user-1');
    expect((await repository.getById('meetup-1'))?.userId, 'user-1');

    await repository.cacheDocuments([
      {'_id': 'meetup-1', 'title': 'Study group renamed'},
    ]);

    final row = await repository.getById('meetup-1');
    expect(row?.title, 'Study group renamed');
    expect(row?.userId, 'user-1', reason: 'attendance is local, not server');
  });

  test('a refresh preserves the empty marker that means "left"', () async {
    await repository.cacheDocuments([
      {'_id': 'meetup-1', 'title': 'Study group'},
    ]);
    await repository.toggleAttendance('meetup-1', 'user-1');
    await repository.toggleAttendance('meetup-1', 'user-1');
    expect((await repository.getById('meetup-1'))?.userId, '');

    await repository.cacheDocuments([
      {'_id': 'meetup-1', 'title': 'Study group'},
    ]);

    // '' is distinct from null: it means left, not never-joined.
    expect((await repository.getById('meetup-1'))?.userId, '');
  });

  test('creates and serializes a pending offline meetup', () async {
    final id = await repository.create(
      title: ' Community lesson ',
      description: 'Bring a book',
      startDate: 100,
      endDate: 200,
      startTime: '09:00',
      endTime: '10:00',
      location: 'Library',
      link: 'https://example.test',
      recurring: 'weekly',
      creator: 'Ada',
      teamId: 'team-1',
      sourcePlanet: 'earth',
    );

    expect(id, 'local-meetup');
    final row = await repository.getById(id!);
    expect(row?.createdDate, 1234);
    expect(row?.updated, isTrue);
    final payload = EventsRepository.serialize(row!);
    expect(payload.containsKey('_id'), isFalse);
    expect(payload['link'], {'teams': 'team-1'});
    // `Meetup.serialize` pushes the raw column via `addProperty`, so `sync`
    // reaches CouchDB as a JSON string rather than an object. Reproduced
    // deliberately: both apps write this database.
    expect(payload['sync'], '{"type":"local","planetCode":"earth"}');
  });

  test('omits link entirely when the meetup belongs to no team', () async {
    final id = await repository.create(
      title: 'Solo meetup',
      description: '',
      startDate: 0,
      endDate: 0,
      startTime: '',
      endTime: '',
      location: '',
      link: '',
      recurring: 'none',
      creator: 'Ada',
    );
    final payload = EventsRepository.serialize(
      (await repository.getById(id!))!,
    );

    // Kotlin guards with `if (!meetup.link.isNullOrEmpty())`; writing an
    // explicit null would put a key in the document that Kotlin never writes.
    expect(payload.containsKey('link'), isFalse);
    expect(payload['sync'], isNull);
  });

  test('sync walks CouchDB pages and removes stale server rows', () async {
    await repository.cacheDocuments([
      {'_id': 'stale', 'title': 'Old'},
    ]);
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments.single as String;
      if (url.endsWith('limit=0')) {
        return NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
      }
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'fresh', 'title': 'Fresh meetup'},
          },
        ],
      });
    });
    const config = ServerConfig(
      serverUrl: 'https://planet.example',
      couchDbUrl: 'https://satellite:1234@planet.example:443',
      pin: '1234',
    );

    final result = await repository.sync(config: config);

    expect(result, isA<SyncComplete>());
    expect((result as SyncComplete).savedCount, 1);
    expect(await repository.getById('stale'), isNull);
    expect((await repository.getById('fresh'))?.title, 'Fresh meetup');
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
