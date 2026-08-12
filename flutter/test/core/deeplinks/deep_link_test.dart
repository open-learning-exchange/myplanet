import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/deeplinks/deep_link.dart';

void main() {
  group('public survey links', () {
    test('carries the origin the link arrived from', () {
      final link = parseDeepLink(
        Uri.parse('https://planet.gt/survey/team-1/survey-9'),
        isSignedIn: false,
      );

      expect(
        link,
        const PublicSurveyDeepLink(
          origin: 'https://planet.gt',
          teamId: 'team-1',
          surveyId: 'survey-9',
        ),
      );
    });

    test('keeps a non-default port, as encodedAuthority does', () {
      // Community servers run on http://<ip>:5000; dropping the port would
      // point the screen at a host that is not serving Planet.
      final link = parseDeepLink(
        Uri.parse('http://192.168.1.73:5000/survey/team-1/survey-9'),
        isSignedIn: false,
      );

      expect((link as PublicSurveyDeepLink).origin, 'http://192.168.1.73:5000');
    });

    test('matches the survey segment case-insensitively', () {
      // `segments.firstOrNull().equals("survey", ignoreCase = true)`.
      final link = parseDeepLink(
        Uri.parse('https://planet.gt/Survey/team-1/survey-9'),
        isSignedIn: false,
      );

      expect(link, isA<PublicSurveyDeepLink>());
    });

    test('a signed-in member goes to the surveys section instead', () {
      // `maybeLaunchPublicSurvey` returns false when logged in, and the section
      // branch maps the *third* segment — the survey id — as the content id.
      final link = parseDeepLink(
        Uri.parse('https://planet.gt/survey/team-1/survey-9'),
        isSignedIn: true,
      );

      expect(link, const SectionDeepLink('surveys', 'survey-9'));
    });

    test('an incomplete survey path is not a public survey', () {
      expect(
        parseDeepLink(
          Uri.parse('https://planet.gt/survey/team-1'),
          isSignedIn: false,
        ),
        isNull,
      );
    });
  });

  group('section links', () {
    test('the custom scheme names the section in the authority', () {
      expect(
        parseDeepLink(
          Uri.parse('myplanet://courses/course-1'),
          isSignedIn: true,
        ),
        const SectionDeepLink('courses', 'course-1'),
      );
      expect(
        parseDeepLink(Uri.parse('myplanet://resources'), isSignedIn: false),
        const SectionDeepLink('resources'),
      );
    });

    test('an /app/ path reads the section after the app segment', () {
      expect(
        parseDeepLink(
          Uri.parse('https://planet.gt/app/teams/team-2'),
          isSignedIn: true,
        ),
        const SectionDeepLink('teams', 'team-2'),
      );
    });

    test('a path without an app segment falls back to the first segment', () {
      // `segments.indexOf("app")` is -1 and `appIndex + 1` is 0, so the Kotlin
      // reads the first segment as the section. Reproduced deliberately; the
      // section names are a closed set, so an unrelated path resolves to a
      // section nothing maps.
      expect(
        parseDeepLink(
          Uri.parse('https://planet.gt/courses/course-1'),
          isSignedIn: true,
        ),
        const SectionDeepLink('courses', 'course-1'),
      );
    });

    test('a bare host resolves to nothing', () {
      expect(
        parseDeepLink(Uri.parse('https://planet.gt/'), isSignedIn: true),
        isNull,
      );
      expect(parseDeepLink(Uri.parse('myplanet://'), isSignedIn: true), isNull);
    });

    test('an unrelated scheme is ignored', () {
      expect(
        parseDeepLink(Uri.parse('mailto:someone@planet.gt'), isSignedIn: false),
        isNull,
      );
    });
  });
}
