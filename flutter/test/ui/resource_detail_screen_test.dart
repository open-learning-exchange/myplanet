import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/network_status_provider.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/resources/resource_detail_screen.dart';

import '../support/widget_harness.dart';

void main() {
  late AppDatabase db;

  setUp(() {
    db = AppDatabase.memory();
  });

  tearDown(() => db.close());

  /// Inserts a library row with the given id and userId list, then builds the
  /// screen with the in-memory database and a stubbed session.
  Future<void> pumpScreen(
    WidgetTester tester, {
    required String resourceId,
    required List<String> userIds,
    required String sessionUserId,
    String? mediaType,
    String? resourceLocalAddress,
    bool resourceOffline = false,
  }) async {
    await db
        .into(db.myLibraryTable)
        .insert(
          MyLibraryTableCompanion.insert(
            id: resourceId,
            title: const Value('Test Resource'),
            userId: Value(userIds),
            mediaType: Value(mediaType),
            resourceLocalAddress: Value(resourceLocalAddress),
            resourceOffline: Value(resourceOffline),
          ),
        );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          sessionProvider.overrideWith(() => _StubSession(sessionUserId)),
          ratingSummaryProvider.overrideWith(
            (ref, target) =>
                Stream.value(const RatingSummary(average: 0, total: 0)),
          ),
          networkStatusProvider.overrideWith(() => _DisconnectedNetwork()),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ResourceDetailScreen(resourceId: resourceId),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('shows snackbar when adding to library', (tester) async {
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const [],
      sessionUserId: 'user-a',
    );

    // The resource is not on the shelf — the button says "Add to My Library".
    expect(find.text('Add to My Library'), findsOneWidget);

    await tester.tap(find.text('Add to My Library'));
    await tester.pumpAndSettle();

    // Membership changed (0 -> 1), so the snackbar fires.
    expect(find.text('Added to My Library'), findsOneWidget);
  });

  testWidgets('shows snackbar when removing from library', (tester) async {
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const ['user-a'],
      sessionUserId: 'user-a',
    );

    // The resource is on the shelf — the button says "Remove from My Library".
    expect(find.text('Remove from My Library'), findsOneWidget);

    await tester.tap(find.text('Remove from My Library'));
    await tester.pumpAndSettle();

    // Membership changed (1 -> 0), so the snackbar fires.
    expect(find.text('Removed from My Library'), findsOneWidget);
  });

  testWidgets('stays silent when membership did not change (ef80dda52)', (
    tester,
  ) async {
    // Seed the resource with the user already on the shelf.
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const ['user-a'],
      sessionUserId: 'user-a',
    );

    // Tap remove — changes 1 -> 0, snackbar fires.
    await tester.tap(find.text('Remove from My Library'));
    await tester.pumpAndSettle();
    expect(find.text('Removed from My Library'), findsOneWidget);

    // Dismiss the snackbar so it doesn't interfere with the next assertion.
    await tester.pumpAndSettle();

    // Now the button reads "Add to My Library" because the user was removed.
    expect(find.text('Add to My Library'), findsOneWidget);

    // Simulate a concurrent sync that already re-added the user: write the
    // row back with the user present BEFORE the toggle runs, so
    // setShelfMembership rebuilds the userId list to the same value.
    await db
        .into(db.myLibraryTable)
        .insertOnConflictUpdate(
          MyLibraryTableCompanion.insert(
            id: 'r1',
            title: const Value('Test Resource'),
            userId: const Value(['user-a']),
          ),
        );

    await tester.tap(find.text('Add to My Library'));
    await tester.pumpAndSettle();

    // The userId list was already ['user-a'] before the toggle, and
    // setShelfMembership rebuilds it to the same ['user-a'], so the size
    // does not change and the snackbar does NOT fire.
    expect(find.text('Added to My Library'), findsNothing);
  });

  testWidgets('shows Download for un-downloaded resource with attachment', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const [],
      sessionUserId: 'user-a',
      mediaType: 'pdf',
      resourceLocalAddress: 'doc.pdf',
    );

    expect(find.text('Download'), findsOneWidget);
  });

  testWidgets('shows View for downloaded resource', (tester) async {
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const [],
      sessionUserId: 'user-a',
      mediaType: 'pdf',
      resourceLocalAddress: 'doc.pdf',
      resourceOffline: true,
    );

    expect(find.text('View'), findsOneWidget);
    expect(find.text('Download'), findsNothing);
  });

  testWidgets('hides download button when resource has no attachment', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const [],
      sessionUserId: 'user-a',
      // No mediaType and no resourceLocalAddress
    );

    expect(find.text('Download'), findsNothing);
    expect(find.text('View'), findsNothing);
  });

  testWidgets('treats resourceOffline flag as downloaded for HTML bundle', (
    tester,
  ) async {
    await pumpScreen(
      tester,
      resourceId: 'r1',
      userIds: const [],
      sessionUserId: 'user-a',
      mediaType: 'html',
      resourceOffline: true,
    );

    expect(find.text('View'), findsOneWidget);
    expect(find.text('Download'), findsNothing);
  });
}

class _StubSession extends SessionNotifier {
  _StubSession(this._userId);
  final String _userId;

  @override
  Future<UserRow?> build() async {
    return buildUserRow(id: _userId, name: 'Test User');
  }
}

class _DisconnectedNetwork extends NetworkStatusNotifier {
  @override
  NetworkStatus build() => NetworkStatus.disconnected;
}
