import 'package:drift/drift.dart';

import 'converters.dart';

/// Port of `model/UserEntity.kt` (`@Entity(tableName = "users")`).
///
/// Only the columns the login slice reads or writes are carried over; the
/// remaining profile fields land here as the other packages are ported.
@DataClassName('UserRow')
class Users extends Table {
  @override
  String get tableName => 'users';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get name => text().nullable()();
  TextColumn get rolesList => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  BoolColumn get userAdmin => boolean().withDefault(const Constant(false))();
  IntColumn get joinDate => integer().withDefault(const Constant(0))();
  TextColumn get firstName => text().nullable()();
  TextColumn get lastName => text().nullable()();
  TextColumn get middleName => text().nullable()();
  TextColumn get email => text().nullable()();
  TextColumn get planetCode => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  TextColumn get phoneNumber => text().nullable()();
  TextColumn get passwordScheme => text().nullable()();
  TextColumn get iterations => text().nullable()();
  TextColumn get derivedKey => text().nullable()();
  TextColumn get salt => text().nullable()();
  TextColumn get level => text().nullable()();
  TextColumn get language => text().nullable()();
  TextColumn get gender => text().nullable()();
  TextColumn get dob => text().nullable()();
  TextColumn get userImage => text().nullable()();

  /// Only ever set for guest users, which have an empty `_id` and are compared
  /// in plaintext (see `UserRepositoryImpl.authenticateUser`).
  TextColumn get password => text().nullable()();

  BoolColumn get isArchived => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/MyLibrary.kt` (`@Entity(tableName = "my_library")`).
@DataClassName('MyLibraryRow')
class MyLibraryTable extends Table {
  @override
  String get tableName => 'my_library';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();

  /// Shelf membership. Queried with `LIKE '%"<userId>"%'`, exactly as
  /// `MyLibraryDao` does against the Room JSON column.
  TextColumn get userId => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();

  TextColumn get title => text().nullable()();

  /// Diacritic-stripped, lower-cased `title`; the column search sorts and
  /// filters on. Populated by `MyLibraryMapper.normalizeTitle`.
  TextColumn get titleNormal => text().nullable()();

  TextColumn get description => text().nullable()();
  TextColumn get resourceId => text().nullable()();
  TextColumn get resourceRemoteAddress => text().nullable()();
  TextColumn get resourceLocalAddress => text().nullable()();
  BoolColumn get resourceOffline =>
      boolean().withDefault(const Constant(false))();
  TextColumn get downloadedRev => text().nullable()();
  TextColumn get filename => text().nullable()();
  TextColumn get averageRating => text().nullable()();
  TextColumn get uploadDate => text().nullable()();
  TextColumn get year => text().nullable()();
  TextColumn get addedBy => text().nullable()();
  TextColumn get publisher => text().nullable()();
  TextColumn get linkToLicense => text().nullable()();
  TextColumn get openWith => text().nullable()();
  TextColumn get articleDate => text().nullable()();
  TextColumn get kind => text().nullable()();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  TextColumn get language => text().nullable()();
  TextColumn get author => text().nullable()();
  TextColumn get mediaType => text().nullable()();
  TextColumn get resourceType => text().nullable()();
  TextColumn get medium => text().nullable()();
  IntColumn get timesRated => integer().withDefault(const Constant(0))();

  TextColumn get resourceFor => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get subject => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get level => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get tag => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get languages => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();

  BoolColumn get isPrivate => boolean().withDefault(const Constant(false))();
  TextColumn get privateFor => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}
