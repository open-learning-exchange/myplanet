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

/// Port of `model/MyCourse.kt` (`@Entity(tableName = "courses")`).
@DataClassName('CourseRow')
class Courses extends Table {
  @override
  String get tableName => 'courses';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get courseId => text().nullable()();

  /// Shelf membership — the users who have added this course. Queried with
  /// `LIKE` on the JSON column, as `MyCourseDao` does.
  TextColumn get userId => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();

  TextColumn get courseTitle => text().nullable()();

  /// Diacritic-folded, lower-cased `courseTitle`; what search filters on.
  TextColumn get courseTitleNormal => text().nullable()();

  TextColumn get description => text().nullable()();
  TextColumn get languageOfInstruction => text().nullable()();
  TextColumn get method => text().nullable()();
  TextColumn get gradeLevel => text().nullable()();
  TextColumn get subjectLevel => text().nullable()();
  IntColumn get memberLimit => integer().nullable()();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  TextColumn get coverFileName => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/RemovedLog.kt` (`@Entity(tableName = "removed_log")`).
///
/// Records that a user deliberately dropped a document from their shelf. The
/// shelf upload merges local ids with whatever the server already holds, so
/// without this a "leave" would be silently re-added by the next merge.
@DataClassName('RemovedLogRow')
class RemovedLogs extends Table {
  @override
  String get tableName => 'removed_log';

  TextColumn get id => text()();

  /// `courses`, `resources`, … — matches the CouchDB table the doc belongs to.
  TextColumn get type => text()();
  TextColumn get docId => text()();
  TextColumn get userId => text()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/CourseStep.kt` (`@Entity(tableName = "course_steps")`).
@DataClassName('CourseStepRow')
@TableIndex(name: 'course_steps_course_id', columns: {#courseId})
class CourseSteps extends Table {
  @override
  String get tableName => 'course_steps';

  TextColumn get id => text()();
  TextColumn get courseId => text().nullable()();
  TextColumn get stepTitle => text().nullable()();
  TextColumn get description => text().nullable()();
  IntColumn get noOfResources => integer().withDefault(const Constant(0))();

  /// Not in the Kotlin entity, which relies on insertion order. Drift makes no
  /// ordering promise without an ORDER BY, so the position within the course is
  /// stored explicitly.
  IntColumn get stepIndex => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {id};
}
