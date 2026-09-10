import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/ui/teams/member_detail_screen.dart';

import '../support/widget_harness.dart';

/// Exercises the member detail screen: the profile fields render, empty
/// fields hide, the visit count and last login come from the team-log and
/// offline-activity providers, and a missing user document shows the
/// unknown-member state.
void main() {
  final key = (teamId: 'team-1', userId: 'alice');

  MemberDetail detail({
    int visitCount = 0,
    int? lastVisit,
    int? lastLogin,
    bool isLeader = false,
    UserRow? user,
  }) => MemberDetail(
    user:
        user ??
        buildUserRow(
          id: 'alice',
          firstName: 'Alice',
          lastName: 'Lovelace',
          email: 'alice@example.org',
          dob: '2001-07-15T00:00:00',
          language: 'en',
          phoneNumber: '555-0100',
          level: '4',
        ),
    visitCount: visitCount,
    lastVisit: lastVisit,
    lastLogin: lastLogin,
    isLeader: isLeader,
  );

  testWidgets('renders the header and every populated field', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const MemberDetailScreen(teamId: 'team-1', userId: 'alice'),
        overrides: [
          memberDetailProvider(
            key,
          ).overrideWith((ref) => Future.value(detail())),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Alice Lovelace'), findsOneWidget);
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('alice@example.org'), findsOneWidget);
    expect(find.text('Date of birth'), findsOneWidget);
    expect(find.text('2001-07-15'), findsOneWidget);
    expect(find.text('Language'), findsOneWidget);
    expect(find.text('en'), findsOneWidget);
    expect(find.text('Phone number'), findsOneWidget);
    expect(find.text('555-0100'), findsOneWidget);
    expect(find.text('Level'), findsOneWidget);
    expect(find.text('4'), findsOneWidget);
    // The visit/login rows sit below the fold of the ListView, so scroll
    // them into view before asserting.
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(find.text('Number of visits'), findsOneWidget);
    expect(find.text('0'), findsOneWidget);
    expect(find.text('Last login'), findsOneWidget);
    expect(find.text('No logout record found'), findsOneWidget);
  });

  testWidgets('shows the leader badge for a leader', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const MemberDetailScreen(teamId: 'team-1', userId: 'alice'),
        overrides: [
          memberDetailProvider(
            key,
          ).overrideWith((ref) => Future.value(detail(isLeader: true))),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Leader'), findsOneWidget);
  });

  testWidgets('hides fields the user did not set', (tester) async {
    final user = buildUserRow(
      id: 'alice',
      name: 'alice',
      firstName: 'Alice',
      // lastName, email, dob, phone, level all null
    );
    await tester.pumpWidget(
      wrapScreen(
        const MemberDetailScreen(teamId: 'team-1', userId: 'alice'),
        overrides: [
          memberDetailProvider(
            key,
          ).overrideWith((ref) => Future.value(detail(user: user))),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The name shows (first name only, no last name).
    expect(find.text('Alice'), findsOneWidget);
    // Email, DOB, phone, level rows are all hidden.
    expect(find.text('Email'), findsNothing);
    expect(find.text('Date of birth'), findsNothing);
    expect(find.text('Phone number'), findsNothing);
    expect(find.text('Level'), findsNothing);
  });

  testWidgets('shows the visit count and formatted last login', (tester) async {
    final lastLogin = DateTime.utc(2026, 8, 1, 14, 30).millisecondsSinceEpoch;
    await tester.pumpWidget(
      wrapScreen(
        const MemberDetailScreen(teamId: 'team-1', userId: 'alice'),
        overrides: [
          memberDetailProvider(key).overrideWith(
            (ref) => Future.value(detail(visitCount: 3, lastLogin: lastLogin)),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(find.text('3'), findsOneWidget);
    expect(find.text('2026-08-01 14:30'), findsOneWidget);
  });

  testWidgets('shows the unknown-member state when the user is not cached', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const MemberDetailScreen(teamId: 'team-1', userId: 'ghost'),
        overrides: [
          memberDetailProvider(key).overrideWith((ref) => Future.value(null)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Unknown member'), findsOneWidget);
  });
}
