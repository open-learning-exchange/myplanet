import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/user_mapper.dart';

/// Pins the `_attachments` -> `userImage` port of `UserEntity.addImageUrl`.
/// The Kotlin reads the *first* attachment key as the profile-photo name; the
/// doc itself never carries a top-level `userImage` field, so a stray value
/// there must not shadow the attachment.
void main() {
  group('UserMapper.fromDoc userImage', () {
    test('takes the first attachment key as the image name', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        '_attachments': {
          'ada.png': {'content_type': 'image/png', 'length': 1234},
          'thumb.png': {'content_type': 'image/png', 'length': 100},
        },
      });

      expect(companion.userImage.value, 'ada.png');
    });

    test('is null when there are no attachments', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
      });

      expect(companion.userImage.value, isNull);
    });

    test('ignores a top-level userImage field in favour of attachments', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        'userImage': 'should-be-ignored',
        '_attachments': {
          'real.png': {'content_type': 'image/png'},
        },
      });

      expect(companion.userImage.value, 'real.png');
    });

    test('is null for an empty attachments object', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        '_attachments': <String, dynamic>{},
      });

      expect(companion.userImage.value, isNull);
    });
  });
}
