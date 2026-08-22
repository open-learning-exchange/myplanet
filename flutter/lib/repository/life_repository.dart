import '../data/local/app_database.dart';

/// Port of `repository/LifeRepositoryImpl.kt`.
class LifeRepository {
  LifeRepository(this._dao);

  static const defaultFeatures = [
    'health',
    'achievements',
    'submissions',
    'surveys',
    'references',
    'calendar',
    'personals',
  ];

  final MyLifeDao _dao;

  Stream<List<MyLifeRow>> watch(String userId) => _dao.watchForUser(userId);

  Future<void> seed(String userId) {
    final entries = defaultFeatures.indexed
        .map((entry) {
          final (index, feature) = entry;
          return MyLifeEntriesCompanion.insert(
            id: '${Uri.encodeComponent(userId)}:$feature',
            feature: feature,
            userId: userId,
            weight: index,
          );
        })
        .toList(growable: false);
    return _dao.seedIfEmpty(userId, entries);
  }

  Future<void> setVisibility(String id, {required bool visible}) =>
      _dao.setVisibility(id, visible: visible);

  Future<void> reorder(List<MyLifeRow> rows) =>
      _dao.reorder(rows.map((row) => row.id).toList(growable: false));
}
