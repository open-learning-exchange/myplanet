import 'dart:io';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// `MyLibraryDao.getTeamPrivate` — the second arm of
/// `TeamsRepositoryImpl.getTeamResources` (`:318-323`), which the port did not
/// have.
///
/// Both conjuncts are mutation-tested, and the first run said neither was
/// pinned — wrongly. The mutation script matched a source string `dart format`
/// had since reflowed, so the edit silently did nothing and the suite passed
/// on **unmutated** code. A mutation harness needs its own assertion that the
/// pattern was found; "still green" otherwise means "nothing was mutated" as
/// readily as it means "nothing is pinned".
void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> resource(
    String id, {
    required bool isPrivate,
    String? privateFor,
  }) => db.myLibraryDao.upsertAll([
    MyLibraryTableCompanion.insert(
      id: id,
      isPrivate: Value(isPrivate),
      privateFor: privateFor == null ? const Value.absent() : Value(privateFor),
    ),
  ]);

  test('a team private resource is found by its team', () async {
    await resource('mine', isPrivate: true, privateFor: 'T');
    // Load-bearing decoys, one per conjunct. A *public* resource carrying a
    // stale `privateFor` catches dropping the `isPrivate` test — which is not
    // hypothetical: `MyLibraryMapper._privateFor` leaves the column absent
    // rather than clearing it when a document stops being private.
    await resource('public-but-tagged', isPrivate: false, privateFor: 'T');
    await resource('other-team', isPrivate: true, privateFor: 'U');
    await resource('private-untagged', isPrivate: true);

    final rows = await db.myLibraryDao.getTeamPrivate('T');
    expect(rows.map((r) => r.id), ['mine']);

    // The Kotlin statement on the same rows.
    final raw = await db
        .customSelect(
          'SELECT id FROM my_library WHERE is_private = 1 AND private_for = ?1',
          variables: [Variable<String>('T')],
        )
        .get();
    expect(raw.map((r) => r.read<String>('id')), ['mine']);
  });

  group('teamResourcesProvider does not yet union the private arm', () {
    /// An exemption with an expiry date. `lib/providers/teams_provider.dart`
    /// is another lane's file this round, so `teamResourcesProvider` is still
    /// the link arm alone and a team's private resources are in no view of the
    /// app.
    ///
    /// **To retire this group:** have `teamResourcesProvider` union
    /// `myLibraryDao.getTeamPrivate(teamId)` with the rows it already yields,
    /// de-duplicated by `id` (`distinctBy { it.id }` in the Kotlin), then
    /// delete this group. The test below fails and says so.
    test('the exemption is still earned', () {
      final text = File('lib/providers/teams_provider.dart').readAsStringSync();
      // Call-anchored, not text: a doc comment naming the method must not
      // satisfy or break this.
      expect(
        RegExp(r'\.getTeamPrivate\(').hasMatch(text),
        isFalse,
        reason:
            'teamResourcesProvider now calls getTeamPrivate, so this exemption '
            'has expired: delete this group.',
      );
      expect(
        RegExp(r'\.watchResourceLinks\(').hasMatch(text),
        isTrue,
        reason:
            'teamResourcesProvider no longer builds the team Resources tab '
            'from watchResourceLinks. Whatever it does now, this exemption no '
            'longer describes it: re-read it and delete or rewrite this group.',
      );
    });
  });
}
