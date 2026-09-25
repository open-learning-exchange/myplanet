import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/core/utils/url_utils.dart';

/// A sync failure is shown on screen and pasted into bug reports, and this
/// project's CouchDB URIs embed `satellite:<PIN>@`. The sync centre printed a
/// real server PIN until this existed.
void main() {
  group('redactCredentials', () {
    test('hides the password and keeps the user', () {
      expect(
        UrlUtils.redactCredentials(
          'uri = https://satellite:1983@planet.learning.ole.org/db/x',
        ),
        'uri = https://satellite:***@planet.learning.ole.org/db/x',
      );
    });

    test('redacts every URL in one message', () {
      final redacted = UrlUtils.redactCredentials(
        'https://satellite:1983@a.example.org and http://satellite:4242@b.example.org',
      );
      expect(redacted.contains('1983'), isFalse);
      expect(redacted.contains('4242'), isFalse);
      expect(redacted.contains('satellite:***@'), isTrue);
    });

    test('leaves a credential-free URL untouched', () {
      const clean = 'uri = https://planet.learning.ole.org/versions';
      expect(UrlUtils.redactCredentials(clean), clean);
    });

    test('does not mangle an ordinary colon or an at-sign', () {
      const text = 'Error at 10:30 for user@example.org';
      expect(UrlUtils.redactCredentials(text), text);
    });

    test('handles an empty password', () {
      expect(
        UrlUtils.redactCredentials('https://satellite:@host/db'),
        'https://satellite:***@host/db',
      );
    });
  });

  group('describeNetworkFailure never leaks a PIN', () {
    test('a transport error carrying the credentialed URI is redacted', () {
      final result = NetworkException<Object?>(
        Exception(
          'HttpException: Software caused connection abort, uri = '
          'https://satellite:1983@planet.learning.ole.org/db/courses_progress/'
          '_all_docs?include_docs=true&limit=200&skip=23600',
        ),
      );

      final described = describeNetworkFailure(result);

      expect(described.contains('1983'), isFalse);
      expect(described.contains('satellite:***@'), isTrue);
      // The useful part survives.
      expect(described.contains('courses_progress'), isTrue);
    });

    test('a server status message is passed through', () {
      expect(
        describeNetworkFailure(NetworkError<Object?>(503, 'unavailable')),
        contains('503'),
      );
    });
  });
}
