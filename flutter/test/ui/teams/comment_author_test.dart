import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/ui/teams/inline_comments.dart';

import '../../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

/// Phase 147, Job 3 (`PHASE_144_NOTES.md` item 2).
///
/// An inline comment is a `News` row like any other, so the same rule holds:
/// a `news` document carries **no** top-level `userId` or `userName`, the
/// nested `user` object is its only author identity, and `NewsMapper.fromDoc`
/// reads all three local columns back out of that one object — so a document
/// without it does not merely fail to set them, it **erases** what the row
/// had. `addComment` wrote no `user` at all, and the sweep
/// (`PHASE_144_NOTES.md` Job 1) now delivers comments reliably rather than
/// incidentally, so the anonymous document reaches CouchDB on a schedule.
///
/// Comments are a port-original from unmerged issue #15112 (Phase 74) — there
/// is no `TeamsRepositoryImpl.addComment` in the Kotlin to check against, so
/// the ground truth here is the port's own three other writers, all of which
/// pass `authorJson`.
void main() {
  late AppDatabase db;
  late VoicesRepository repository;

  setUp(() {
    db = AppDatabase.memory();
    repository = VoicesRepository(_MockPlanetApi(), db.newsDao);
  });
  tearDown(() => db.close());

  final ada = buildUserRow(
    id: 'local-ada',
    name: 'ada',
    firstName: 'Ada',
    lastName: 'Lovelace',
  ).copyWith(couchId: const Value('org.couchdb.user:ada'));

  Future<NewsRow> commentFromScreen(WidgetTester tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const Scaffold(
          body: InlineComments(parentId: 'task-1', teamId: 'team-1'),
        ),
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          voicesRepositoryProvider.overrideWithValue(repository),
          commentsForParentProvider.overrideWith(
            (ref, parentId) => Stream.value(const []),
          ),
          sessionProvider.overrideWith(() => _TestSessionNotifier(ada)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The input only exists once the thread is expanded.
    await tester.tap(find.byIcon(Icons.expand_more));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'the valve is stuck');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    final rows = await db.newsDao.getAll();
    expect(rows, hasLength(1), reason: 'the comment was never written');
    return rows.single;
  }

  testWidgets('a comment names its author', (tester) async {
    final row = await commentFromScreen(tester);

    expect(
      row.user,
      isNotNull,
      reason:
          'the nested `user` object is the only author identity a news '
          'document carries',
    );
  });

  testWidgets('the author survives the round trip through the server', (
    tester,
  ) async {
    final row = await commentFromScreen(tester);

    // The document the uploader would send, handed straight back with the
    // `_id`/`_rev` CouchDB would have stamped on it. A fixture that
    // fabricated the document would prove nothing about the pair.
    await repository.markUploaded(row.id, 'server-1', '1-rev');
    await repository.cacheDocuments([
      {...VoicesRepository.serialize(row), '_id': 'server-1', '_rev': '2-rev'},
    ]);

    final pulled = await repository.getById(row.id);
    expect(
      pulled?.userId,
      'org.couchdb.user:ada',
      reason: 'the pull reads the author back out of the document it sent',
    );
    expect(pulled?.userName, 'ada');
    expect(pulled?.user, isNotNull);
  });

  testWidgets('the comment is filed under the id its author object carries', (
    tester,
  ) async {
    // `authorJson` writes the user's `couchId` as the object's `_id`, and
    // `NewsMapper.fromDoc` copies that into `userId` on the way back in. A
    // comment written with the *local* id would therefore have its `userId`
    // silently rewritten by the first sync — the two halves have to name the
    // same user, which is what the other three writers already do
    // (`user.couchId ?? user.id`).
    final row = await commentFromScreen(tester);

    expect(row.userId, 'org.couchdb.user:ada');
  });
}
