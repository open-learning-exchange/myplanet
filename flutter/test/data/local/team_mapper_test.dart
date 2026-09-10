import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/team_mapper.dart';

void main() {
  test('maps the team catalog fields used by the list and detail screens', () {
    final row = TeamMapper.fromDoc({
      '_id': 'team-1',
      '_rev': '2-rev',
      'teamId': 'shared-id',
      'name': 'Gardeners',
      'description': 'Grow together',
      'type': 'team',
      'public': true,
      'createdBy': 'alice',
      'createdDate': 42,
      'courses': [
        'plain',
        {'_id': 'embedded'},
        'plain',
      ],
      'isLeader': true,
      'resourceId': 'resource-1',
      'title': 'Linked resource',
    });

    expect(row, isNotNull);
    expect(row!.id.value, 'team-1');
    expect(row.name.value, 'Gardeners');
    expect(row.isPublic.value, isTrue);
    expect(row.createdDate.value, 42);
    expect(row.courses.value, ['plain', 'embedded']);
    expect(row.isLeader.value, isTrue);
    expect(row.resourceId.value, 'resource-1');
    expect(row.title.value, 'Linked resource');
  });

  test('rejects design and id-less documents', () {
    expect(TeamMapper.fromDoc({'name': 'missing'}), isNull);
    expect(TeamMapper.fromDoc({'_id': '_design/index'}), isNull);
  });
}
