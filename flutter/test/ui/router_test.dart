import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/router.dart';

/// A public survey is the one screen reachable with no session, no onboarding
/// and possibly no configured server, so it is also the one screen whose route
/// arguments come from outside the app.
void main() {
  test('a deep link supplies its own origin', () {
    // Port of `"${uri.scheme}://${uri.encodedAuthority}"` in
    // `OnboardingActivity.maybeLaunchPublicSurvey` — the survey has to be
    // fetched from the host the link pointed at, not from wherever this
    // install happens to be configured.
    expect(
      publicSurveyBaseUrl(
        Uri.parse('https://uriur.planet.gt/survey/team-1/survey-1'),
        'https://planet.example.org',
      ),
      'https://uriur.planet.gt',
    );
  });

  test('a non-default port stays in the base URL', () {
    expect(
      publicSurveyBaseUrl(Uri.parse('https://planet.gt:8443/survey/t/s'), null),
      'https://planet.gt:8443',
    );
  });

  test('a location without an origin falls back instead of throwing', () {
    // `Uri.origin` throws `StateError` on a relative URI rather than returning
    // an empty string, so reading it unguarded took down the very route it was
    // building. go_router hands the builder a relative location whenever the
    // navigation came from inside the app, and depending on the platform, for
    // an incoming link too.
    final uri = Uri.parse('/survey/team-1/survey-1');
    expect(() => uri.origin, throwsStateError);

    expect(
      publicSurveyBaseUrl(uri, 'https://planet.example.org'),
      'https://planet.example.org',
    );
  });

  test('no origin and no configured server yields an empty base URL', () {
    // An anonymous respondent on a fresh install: there is genuinely nowhere
    // to fetch from, and the screen shows its load-failure state.
    expect(publicSurveyBaseUrl(Uri.parse('/survey/t/s'), null), '');
  });

  test('the origin parameter rescues a path-only location', () {
    // The case the deep-link plugin exists for: the platform handed over a
    // location with no scheme or host, and `DeepLinkHandler` preserved the real
    // origin as a query parameter. Before this, a respondent with no configured
    // server could not load the survey at all.
    expect(
      publicSurveyBaseUrl(
        Uri.parse('/survey/t/s?origin=http%3A%2F%2F192.168.1.73%3A5000'),
        null,
      ),
      'http://192.168.1.73:5000',
    );
  });

  test('the origin parameter beats the configured server', () {
    // A member whose device is configured against one planet can still answer a
    // survey hosted by another; the link names which.
    expect(
      publicSurveyBaseUrl(
        Uri.parse('/survey/t/s?origin=https%3A%2F%2Fplanet.gt'),
        'https://planet.example.org',
      ),
      'https://planet.gt',
    );
  });

  test('a junk origin parameter falls through rather than being trusted', () {
    expect(
      publicSurveyBaseUrl(
        Uri.parse('/survey/t/s?origin=not-a-url'),
        'https://planet.example.org',
      ),
      'https://planet.example.org',
    );
  });
}
