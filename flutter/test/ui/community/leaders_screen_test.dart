import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/community/leaders_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/widget_harness.dart';

final _leadersJson = jsonEncode({
  'docs': [
    {
      '_id': 'org.couchdb.user:alice',
      'name': 'alice',
      'firstName': 'Alice',
      'lastName': 'Alda',
      'email': 'alice@example.org',
    },
    {'name': 'bobjoe', 'email': 'bob@example.org'},
  ],
});

/// Seeded prefs that report [communityLeaders]; mirrors the `_prefs()`
/// helper in `home_screen_test.dart`, with leaders pre-populated.
Future<PlanetPrefs> _prefsWithLeaders(String json) async {
  SharedPreferences.setMockInitialValues({'communityLeaders': json});
  return PlanetPrefs(await SharedPreferences.getInstance());
}

void main() {
  testWidgets('shows the empty state when no leaders are cached', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const LeadersScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(await _prefsWithLeaders('')),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No leaders available'), findsOneWidget);
    expect(find.byType(LeaderCard), findsNothing);
  });

  testWidgets('renders a card per leader with the display name', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const LeadersScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(
            await _prefsWithLeaders(_leadersJson),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // A leader with firstName/lastName shows the composed name.
    expect(find.text('Alice Alda'), findsOneWidget);
    // A leader with only a name falls back to it as the display name.
    expect(find.text('bobjoe'), findsOneWidget);
    expect(find.byType(LeaderCard), findsNWidgets(2));
    // The avatar shows the first letter of the display name.
    expect(find.text('A'), findsOneWidget);
    expect(find.text('B'), findsOneWidget);
  });

  testWidgets('a leader with no email hides the email line', (tester) async {
    final json = jsonEncode({
      'docs': [
        {'name': 'lonely', 'firstName': 'Lonely', 'lastName': 'Wolf'},
      ],
    });

    await tester.pumpWidget(
      wrapScreen(
        const LeadersScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(await _prefsWithLeaders(json)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Lonely Wolf'), findsOneWidget);
    // No email text rendered for a leader without one.
    expect(find.textContaining('@'), findsNothing);
  });

  testWidgets('tapping a leader navigates to the member detail route', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const LeadersScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(
            await _prefsWithLeaders(_leadersJson),
          ),
        ],
        pushTargets: {
          '/life/teams/community/members/org.couchdb.user%3Aalice': (_) =>
              const Scaffold(body: Text('member detail target')),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Alice Alda'));
    await tester.pumpAndSettle();

    // The member detail screen is pushed.
    expect(find.text('member detail target'), findsOneWidget);
  });

  testWidgets('malformed leaders JSON falls back to the empty state', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const LeadersScreen(),
        overrides: [
          planetPrefsProvider.overrideWithValue(
            await _prefsWithLeaders('not valid json'),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The parser swallows the FormatError and returns an empty list.
    expect(find.text('No leaders available'), findsOneWidget);
  });
}
