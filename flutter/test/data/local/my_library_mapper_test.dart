import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/my_library_mapper.dart';

void main() {
  const couchDbUrl = 'https://satellite:1234@planet.example.org:443';

  group('normalizeTitle', () {
    test('folds diacritics and lower-cases, so "cafe" matches "Café"', () {
      expect(MyLibraryMapper.normalizeTitle('Café Résumé'), 'cafe resume');
      expect(MyLibraryMapper.normalizeTitle('ÑOÑO'), 'nono');
      expect(MyLibraryMapper.normalizeTitle('Plain Title'), 'plain title');
    });
  });

  group('fromDoc', () {
    test('maps the scalar fields off a resources document', () {
      final row = MyLibraryMapper.fromDoc({
        '_id': 'res-1',
        '_rev': '3-xyz',
        'title': 'Álgebra Básica',
        'description': 'An intro',
        'author': 'Ada',
        'year': '2021',
        'mediaType': 'pdf',
        'createdDate': 1700000000000,
        'timesRated': 4,
      }, couchDbUrl: couchDbUrl);

      expect(row, isNotNull);
      expect(row!.id.value, 'res-1');
      expect(row.couchId.value, 'res-1');
      expect(row.rev.value, '3-xyz');
      expect(row.title.value, 'Álgebra Básica');
      expect(row.titleNormal.value, 'algebra basica');
      expect(row.description.value, 'An intro');
      expect(row.author.value, 'Ada');
      expect(row.mediaType.value, 'pdf');
      expect(row.createdDate.value, 1700000000000);
      expect(row.timesRated.value, 4);
    });

    test('derives the attachment address from the first non-nested key', () {
      final row = MyLibraryMapper.fromDoc({
        '_id': 'res-1',
        'title': 'Doc',
        '_attachments': {
          'chapter one.pdf': {'content_type': 'application/pdf'},
        },
      }, couchDbUrl: couchDbUrl);

      // The stored URL must not carry the satellite PIN.
      expect(
        row!.resourceRemoteAddress.value,
        'https://planet.example.org/resources/res-1/chapter one.pdf',
      );
      expect(row.resourceRemoteAddress.value, isNot(contains('1234')));
      expect(row.resourceRemoteAddress.value, isNot(contains('satellite')));
      expect(row.resourceLocalAddress.value, 'chapter one.pdf');
    });

    test('skips nested attachment keys', () {
      final row = MyLibraryMapper.fromDoc({
        '_id': 'res-1',
        'title': 'Doc',
        '_attachments': {
          'thumbs/small.png': {'content_type': 'image/png'},
          'main.epub': {'content_type': 'application/epub+zip'},
        },
      }, couchDbUrl: couchDbUrl);

      expect(row!.resourceLocalAddress.value, 'main.epub');
    });

    test(
      'merges list fields with what is already stored, without duplicates',
      () {
        final row = MyLibraryMapper.fromDoc(
          {
            '_id': 'res-1',
            'title': 'Doc',
            'subject': ['math', 'science'],
          },
          couchDbUrl: couchDbUrl,
          existingSubject: const ['math', 'history'],
        );

        expect(row!.subject.value, containsAll(['math', 'science', 'history']));
        expect(row.subject.value.length, 3);
      },
    );

    test('preserves the shelf membership it was given', () {
      final row = MyLibraryMapper.fromDoc(
        {'_id': 'res-1', 'title': 'Doc'},
        couchDbUrl: couchDbUrl,
        existingUserIds: const ['user-1'],
      );

      expect(row!.userId.value, ['user-1']);
    });

    test('defaults missing fields rather than throwing', () {
      final row = MyLibraryMapper.fromDoc({
        '_id': 'res-1',
      }, couchDbUrl: couchDbUrl);

      expect(row, isNotNull);
      expect(row!.title.value, '');
      expect(row.description.value, isNull);
      expect(row.createdDate.value, 0);
      expect(row.subject.value, isEmpty);
      expect(row.resourceRemoteAddress.value, isNull);
    });

    test('has no attachment address when the CouchDB URL is unknown', () {
      final row = MyLibraryMapper.fromDoc({
        '_id': 'res-1',
        'title': 'Doc',
        '_attachments': {
          'main.epub': {'content_type': 'application/epub+zip'},
        },
      }, couchDbUrl: '');

      // Better an absent URL than the unusable 'http:///resources/...'.
      expect(row!.resourceRemoteAddress.value, isNull);
      expect(row.resourceLocalAddress.value, isNull);
    });

    test('returns null for empty, design and id-less documents', () {
      expect(MyLibraryMapper.fromDoc(const {}, couchDbUrl: couchDbUrl), isNull);
      expect(
        MyLibraryMapper.fromDoc(const {
          '_id': '_design/resources',
        }, couchDbUrl: couchDbUrl),
        isNull,
      );
      expect(
        MyLibraryMapper.fromDoc(const {
          'title': 'no id',
        }, couchDbUrl: couchDbUrl),
        isNull,
      );
    });
  });

  group('credentialFreeBase', () {
    test('strips the satellite credentials', () {
      // The default :443 normalizes away; the non-default case is covered below.
      expect(
        MyLibraryMapper.credentialFreeBase(couchDbUrl),
        'https://planet.example.org',
      );
    });

    test('trims trailing slashes', () {
      expect(
        MyLibraryMapper.credentialFreeBase('https://host/'),
        'https://host',
      );
    });

    test('keeps a non-default port, as local community servers use', () {
      expect(
        MyLibraryMapper.credentialFreeBase(
          'http://satellite:0000@10.0.0.5:5000',
        ),
        'http://10.0.0.5:5000',
      );
    });

    test('returns null for empty or hostless input', () {
      expect(MyLibraryMapper.credentialFreeBase(''), isNull);
      expect(MyLibraryMapper.credentialFreeBase('not a url'), isNull);
    });
  });
}
