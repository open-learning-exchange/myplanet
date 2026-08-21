import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/team_surveys_provider.dart';
import 'package:myplanet/providers/teams_provider.dart';
import 'package:myplanet/repository/surveys_repository.dart';
import 'package:myplanet/ui/teams/team_surveys_screen.dart';

import '../support/widget_harness.dart';

class _MockSurveysRepository extends Mock implements SurveysRepository {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

SurveyRow _survey({String id = 's1', String? name, String? description}) =>
    SurveyRow(
      id: id,
      name: name,
      description: description,
      createdDate: 0,
      updatedDate: 0,
      adoptionDate: 0,
      totalMarks: 0,
      isFromNation: false,
      teamShareAllowed: false,
    );

UserRow _user() => UserRow(
  id: 'user-1',
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  firstName: 'Ada',
  lastName: 'Lovelace',
  isArchived: false,
  isUpdated: false,
);

TeamRow _leaderMembership(String teamId) => TeamRow(
  id: teamId,
  courses: const [],
  createdDate: 0,
  limit: 0,
  isPublic: false,
  isLeader: true,
  beginningBalance: 0,
  sales: 0,
  otherIncome: 0,
  wages: 0,
  otherExpenses: 0,
  startDate: 0,
  endDate: 0,
  updatedDate: 0,
  date: 0,
  amount: 0,
  isUpdated: false,
);

void main() {
  testWidgets('shows both empty sections when no surveys exist', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const TeamSurveysScreen(teamId: 'team-1'),
        overrides: [
          teamOwnedSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => const []),
          teamAdoptableSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => const []),
          teamProvider('team-1').overrideWith((ref) async => null),
          sessionProvider.overrideWith(() => _TestSessionNotifier(null)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No surveys available'), findsOneWidget);
    expect(find.text('No surveys available to adopt'), findsOneWidget);
  });

  testWidgets('renders owned surveys with a send button', (tester) async {
    final owned = [
      _survey(id: 's1', name: 'Climate Survey', description: 'Annual'),
      _survey(id: 's2', name: null, description: null),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const TeamSurveysScreen(teamId: 'team-1'),
        overrides: [
          teamOwnedSurveysProvider('team-1').overrideWith((ref) async => owned),
          teamAdoptableSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => const []),
          teamProvider('team-1').overrideWith((ref) async => null),
          sessionProvider.overrideWith(() => _TestSessionNotifier(null)),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Climate Survey'), findsOneWidget);
    expect(find.text('Annual'), findsOneWidget);
    // A null name falls back to the sentence-case placeholder.
    expect(find.text('Untitled survey'), findsOneWidget);
    expect(find.byIcon(Icons.send), findsNWidgets(2));
    expect(find.byIcon(Icons.chevron_right), findsNWidgets(2));
  });

  testWidgets('a non-leader sees the adopt button disabled', (tester) async {
    final adoptable = [_survey(id: 'a1', name: 'Adoptable Survey')];

    await tester.pumpWidget(
      wrapScreen(
        const TeamSurveysScreen(teamId: 'team-1'),
        overrides: [
          teamOwnedSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => const []),
          teamAdoptableSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => adoptable),
          teamProvider('team-1').overrideWith((ref) async => null),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value(const <String, TeamRow>{}),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    final adoptButton = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(adoptButton.onPressed, isNull);
  });

  testWidgets('a leader can adopt, which shows the adopted snackbar', (
    tester,
  ) async {
    final adoptable = [_survey(id: 'a1', name: 'Adoptable Survey')];
    final repo = _MockSurveysRepository();
    when(
      () => repo.adoptSurvey(
        surveyId: any(named: 'surveyId'),
        userId: any(named: 'userId'),
        teamId: any(named: 'teamId'),
        teamName: any(named: 'teamName'),
        isTeam: any(named: 'isTeam'),
      ),
    ).thenAnswer((_) async {});

    await tester.pumpWidget(
      wrapScreen(
        const TeamSurveysScreen(teamId: 'team-1'),
        overrides: [
          teamOwnedSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => const []),
          teamAdoptableSurveysProvider(
            'team-1',
          ).overrideWith((ref) async => adoptable),
          teamProvider('team-1').overrideWith((ref) async => null),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          teamMembershipsProvider.overrideWith(
            (ref) => Stream.value({'team-1': _leaderMembership('team-1')}),
          ),
          surveysRepositoryProvider.overrideWithValue(repo),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();

    verify(
      () => repo.adoptSurvey(
        surveyId: 'a1',
        userId: 'user-1',
        teamId: 'team-1',
        teamName: null,
        isTeam: true,
      ),
    ).called(1);
    expect(find.text('Survey adopted'), findsOneWidget);
  });
}
