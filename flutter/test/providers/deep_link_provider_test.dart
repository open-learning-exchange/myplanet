import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/deeplinks/deep_link.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/deep_link_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/router.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late PlanetPrefs prefs;

  UserRow user() => UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  Future<ProviderContainer> containerFor({UserRow? current}) async {
    final container = ProviderContainer(
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        sessionProvider.overrideWith(() => _TestSessionNotifier(current)),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);
    return container;
  }

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
  });

  test('a public-survey link carries its origin into the location', () async {
    final container = await containerFor();

    final location = await container
        .read(deepLinkHandlerProvider)
        .handle(Uri.parse('https://planet.gt/survey/team-1/survey-9'));

    // The origin is the one thing the route cannot recover for itself, so it
    // travels as a query parameter — and `publicSurveyBaseUrl` reads it back.
    expect(location, '/survey/team-1/survey-9?origin=https%3A%2F%2Fplanet.gt');
    expect(
      publicSurveyBaseUrl(Uri.parse(location!), null),
      'https://planet.gt',
    );
  });

  test('a section link before sign-in is stored, not navigated', () async {
    final container = await containerFor();

    final location = await container
        .read(deepLinkHandlerProvider)
        .handle(Uri.parse('myplanet://courses/course-1'));

    expect(location, isNull);
    expect(prefs.pendingDeepLinkSection, 'courses');
    expect(prefs.pendingDeepLinkId, 'course-1');
  });

  test('the stored link is applied once and then cleared', () async {
    final container = await containerFor();
    await container
        .read(deepLinkHandlerProvider)
        .handle(Uri.parse('myplanet://teams/team-2'));

    final signedIn = await containerFor(current: user());
    final handler = signedIn.read(deepLinkHandlerProvider);

    expect(await handler.takePendingLocation(), Routes.teams);
    // Clearing on read is what stops the link reopening on every later launch.
    expect(await handler.takePendingLocation(), isNull);
    expect(prefs.pendingDeepLinkSection, '');
  });

  test('a signed-in section link navigates immediately', () async {
    final container = await containerFor(current: user());

    final location = await container
        .read(deepLinkHandlerProvider)
        .handle(Uri.parse('https://planet.gt/app/resources'));

    expect(location, Routes.resources);
    // Nothing is stored: there is no login to survive.
    expect(prefs.pendingDeepLinkSection, '');
  });

  test('a section nothing maps is dropped without storing anything', () async {
    final container = await containerFor();

    final location = await container
        .read(deepLinkHandlerProvider)
        .handle(Uri.parse('https://planet.gt/app/enterprises/e-1'));

    expect(location, isNull);
    expect(prefs.pendingDeepLinkSection, '');
  });

  test('a link without an id clears an id left by an earlier one', () async {
    final container = await containerFor();
    final handler = container.read(deepLinkHandlerProvider);

    await handler.handle(Uri.parse('myplanet://courses/course-1'));
    await handler.handle(Uri.parse('myplanet://courses'));

    // `else prefData.removeKey(DEEP_LINK_ID_KEY)` — inheriting the old id would
    // open the wrong course.
    expect(prefs.pendingDeepLinkId, isNull);
  });

  test('a survey link opened by a member goes to the surveys screen', () async {
    final container = await containerFor(current: user());

    final location = await container
        .read(deepLinkHandlerProvider)
        .handle(Uri.parse('https://planet.gt/survey/team-1/survey-9'));

    // Not the anonymous public screen: `maybeLaunchPublicSurvey` bails when
    // logged in.
    expect(location, Routes.surveys);
  });

  test('deepLinkRoute covers exactly the Kotlin sections', () {
    expect(deepLinkRoute('feedbackList'), Routes.feedback);
    expect(deepLinkRoute('courses'), Routes.courses);
    expect(deepLinkRoute('resources'), Routes.resources);
    expect(deepLinkRoute('teams'), Routes.teams);
    expect(deepLinkRoute('surveys'), Routes.surveys);
    expect(deepLinkRoute('dashboard'), isNull);
    // Not an alias for `feedbackList` — the Kotlin does not accept it either.
    expect(deepLinkRoute('feedback'), isNull);
  });

  test('the public-survey location omits an empty origin', () {
    expect(
      DeepLinkHandler.publicSurveyLocation(
        const PublicSurveyDeepLink(origin: '', teamId: 't', surveyId: 's'),
      ),
      '/survey/t/s',
    );
  });
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}
