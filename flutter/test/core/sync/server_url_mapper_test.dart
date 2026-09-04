import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/sync/server_url_mapper.dart';

void main() {
  group('parseMappings', () {
    test('parses primary=alternative pairs', () {
      final mappings = ServerUrlMapper.parseMappings(
        'http://a.example=https://a-clone.example,http://b.example=https://b-clone.example',
      );
      expect(mappings, {
        'http://a.example': 'https://a-clone.example',
        'http://b.example': 'https://b-clone.example',
      });
    });

    test('ignores blank and malformed entries', () {
      final mappings = ServerUrlMapper.parseMappings(
        ',http://a.example=https://a-clone.example, ,no-separator,=leading,trailing=',
      );
      expect(mappings, {'http://a.example': 'https://a-clone.example'});
    });

    test('returns empty for an empty definition', () {
      expect(ServerUrlMapper.parseMappings(''), isEmpty);
    });
  });

  group('extractBaseUrl', () {
    test('omits the port when it is the scheme default', () {
      expect(
        ServerUrlMapper.extractBaseUrl('http://a.example:80'),
        'http://a.example',
      );
      expect(
        ServerUrlMapper.extractBaseUrl('https://a.example:443/ml'),
        'https://a.example',
      );
    });

    test('keeps a non-default port', () {
      expect(
        ServerUrlMapper.extractBaseUrl('http://a.example:5000/ml'),
        'http://a.example:5000',
      );
    });

    test('returns null without a scheme or host', () {
      expect(ServerUrlMapper.extractBaseUrl('a.example'), isNull);
      expect(ServerUrlMapper.extractBaseUrl(''), isNull);
    });
  });

  group('processUrl', () {
    final mapper = ServerUrlMapper(
      mappings: const {'http://a.example': 'https://a-clone.example'},
    );

    test('finds the alternative for a known server, ignoring the path', () {
      final mapping = mapper.processUrl('http://a.example/ml');
      expect(mapping.primaryUrl, 'http://a.example/ml');
      expect(mapping.alternativeUrl, 'https://a-clone.example');
      expect(mapping.extractedBaseUrl, 'http://a.example');
    });

    test('returns no alternative for an unknown server', () {
      final mapping = mapper.processUrl('http://unknown.example');
      expect(mapping.alternativeUrl, isNull);
    });

    test(
      'defaults to an empty table when nothing is defined at build time',
      () {
        expect(
          ServerUrlMapper().processUrl('http://a.example').alternativeUrl,
          isNull,
        );
      },
    );
  });
}
