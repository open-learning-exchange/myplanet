import 'dart:convert';

import 'package:drift/drift.dart';

/// Port of `data/room/Converters.kt`.
///
/// The Room schema stores the formerly-`RealmList<String>` fields (`subject`,
/// `level`, `tag`, `languages`, `resourceFor`, `userId`, `rolesList`) as a JSON
/// array in a TEXT column, and shelf membership is queried with `LIKE` against
/// that column. Keeping the identical on-disk encoding here means the same
/// queries port over unchanged.
class StringListConverter extends TypeConverter<List<String>, String>
    with JsonTypeConverter<List<String>, String> {
  const StringListConverter();

  @override
  List<String> fromSql(String fromDb) {
    if (fromDb.isEmpty) return const [];
    final decoded = jsonDecode(fromDb);
    if (decoded is! List) return const [];
    return decoded.map((e) => e.toString()).toList(growable: false);
  }

  @override
  String toSql(List<String> value) => jsonEncode(value);
}
