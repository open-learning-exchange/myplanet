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
  TextColumn get age => text().nullable()();
  TextColumn get birthPlace => text().nullable()();
  TextColumn get userImage => text().nullable()();

  /// Set for an account with **no server identity** — a member registered
  /// offline, whose `createMember` document carries no `_id`, so
  /// `applyJsonToUser` takes the plaintext password it typed
  /// (`UserRepositoryImpl.kt:261`, read after `_id = newId`) and
  /// `authenticateUser` compares it directly (`:862`).
  ///
  /// Not guests, which this comment used to say: `buildGuestUserJson` keys a
  /// guest row `guest_<username>`, so its `_id` is non-empty, its password
  /// stays null, and it is never authenticated here at all — guest re-entry
  /// goes back through `showGuestLoginDialog`. Anything that creates a guest
  /// row must leave this column alone and set `couchId` to
  /// `UserMapper.guestIdPrefix + username`, or the row lands in
  /// `UserDao.pendingSyncUsers` and gets POSTed to `_users`, which Kotlin
  /// never does.
  TextColumn get password => text().nullable()();

  BoolColumn get isArchived => boolean().withDefault(const Constant(false))();

  /// Port of `UserEntity.isUpdated` — the dirty flag the user-document upload
  /// path reads. Set `true` by every local profile edit or photo change, then
  /// cleared by [UserDao.markUploaded] once the `_users` PUT succeeds.
  ///
  /// Matches Kotlin's `getPendingSyncUsers` predicate (`_id.isNullOrBlank() ||
  /// isUpdated`): a freshly created local account with no CouchDB id is
  /// pending too, so the uploader sends it even before the first edit.
  BoolColumn get isUpdated => boolean().withDefault(const Constant(false))();

  /// AES key and IV for the user's health records, porting `UserEntity.key`
  /// and `UserEntity.iv`.
  ///
  /// Generated on this device by `ensureUserSecurityKeys` and never uploaded —
  /// the server stores the ciphertext and cannot read it. That is also why
  /// `users` is a preserved table: a sync cannot restore these, and without
  /// them the records they encrypted are lost for good.
  TextColumn get key => text().nullable()();
  TextColumn get iv => text().nullable()();

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
  TextColumn get openWhichFile => text().nullable()();
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

/// Port of `data/room/entity/DictionaryEntity.kt`.
@DataClassName('DictionaryRow')
@TableIndex(name: 'dictionary_word_normalized', columns: {#wordNormalized})
class DictionaryEntries extends Table {
  @override
  String get tableName => 'dictionary';

  TextColumn get id => text()();
  TextColumn get code => text().withDefault(const Constant(''))();
  TextColumn get language => text().withDefault(const Constant(''))();
  TextColumn get advanceCode => text().withDefault(const Constant(''))();
  TextColumn get word => text().withDefault(const Constant(''))();
  TextColumn get wordNormalized => text()();
  TextColumn get meaning => text().withDefault(const Constant(''))();
  TextColumn get definition => text().withDefault(const Constant(''))();
  TextColumn get synonym => text().withDefault(const Constant(''))();
  TextColumn get antonym => text().withDefault(const Constant(''))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/AppNotification.kt` / `data/room/entity/NotificationEntity`.
@DataClassName('NotificationRow')
@TableIndex(name: 'notifications_user_created', columns: {#userId, #createdAt})
class Notifications extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text()();
  TextColumn get message => text().withDefault(const Constant(''))();
  TextColumn get type => text().withDefault(const Constant('notification'))();
  TextColumn get subType => text().nullable()();
  TextColumn get relatedId => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get link => text().nullable()();
  BoolColumn get isRead => boolean().withDefault(const Constant(false))();
  IntColumn get createdAt => integer()();
  IntColumn get priority => integer().withDefault(const Constant(0))();
  BoolColumn get isFromServer => boolean().withDefault(const Constant(false))();
  BoolColumn get needsSync => boolean().withDefault(const Constant(false))();

  /// The CouchDB `_rev` of the notification document, set when a server
  /// notification is pulled. Read-state upload PUTs the doc back, so it needs
  /// a rev to avoid a 409 conflict; `getPendingSyncNotifications` filters on
  /// `rev IS NOT NULL` so a locally-authored row (no rev yet) is not sent.
  TextColumn get rev => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/MyLife.kt`.
@DataClassName('MyLifeRow')
@TableIndex(name: 'my_life_user_weight', columns: {#userId, #weight})
class MyLifeEntries extends Table {
  @override
  String get tableName => 'my_life';

  TextColumn get id => text().named('_id')();
  TextColumn get feature => text()();
  TextColumn get userId => text()();
  TextColumn get title => text().nullable()();
  BoolColumn get isVisible => boolean().withDefault(const Constant(true))();
  IntColumn get weight => integer()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Personal.kt`.
@DataClassName('PersonalRow')
@TableIndex(name: 'my_personal_user', columns: {#userId})
class PersonalEntries extends Table {
  @override
  String get tableName => 'my_personal';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  BoolColumn get isUploaded => boolean().withDefault(const Constant(false))();
  TextColumn get title => text()();
  TextColumn get titleNormalized => text()();
  TextColumn get description => text().nullable()();
  IntColumn get date => integer()();
  TextColumn get userId => text()();
  TextColumn get userName => text().nullable()();
  TextColumn get path => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/RetryOperation.kt` (`@Entity(tableName = "retry_operation")`).
///
/// The durable half of the Kotlin write-back path. `RetryQueue` and
/// `RetryQueueWorker` split one job in two: this table is what actually
/// survives process death, and `WorkManager` only decides *when* to drain it.
/// That split is why the missing `WorkManager` equivalent does not cost
/// durability here — the queue ports as-is, and only the trigger is replaced
/// (see [OutboxDrainer]).
@DataClassName('OutboxRow')
@TableIndex(
  name: 'outbox_status_next_attempt',
  columns: {#status, #nextAttemptAt},
)
@TableIndex(name: 'outbox_item', columns: {#uploadType, #itemId})
class OutboxEntries extends Table {
  @override
  String get tableName => 'outbox';

  TextColumn get id => text()();

  /// `shelf`, `personals`, … — the Kotlin `uploadType`.
  TextColumn get uploadType => text()();

  /// The local row this operation belongs to. Kotlin's `itemId`; combined with
  /// [uploadType] it is what makes a re-enqueue update rather than duplicate.
  TextColumn get itemId => text()();

  /// The request body, serialized. Kotlin's `serializedPayload`.
  TextColumn get payload => text()();

  TextColumn get endpoint => text()();
  TextColumn get httpMethod => text().withDefault(const Constant('POST'))();

  /// `pending` | `in_progress` | `completed` | `abandoned`.
  TextColumn get status => text().withDefault(const Constant('pending'))();

  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  IntColumn get maxAttempts => integer().withDefault(const Constant(5))();

  IntColumn get createdAt => integer()();
  IntColumn get lastAttemptAt => integer().withDefault(const Constant(0))();
  IntColumn get nextAttemptAt => integer().withDefault(const Constant(0))();

  TextColumn get errorMessage => text().nullable()();
  IntColumn get httpCode => integer().nullable()();
  TextColumn get userId => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Rating.kt`.
@DataClassName('RatingRow')
@TableIndex(name: 'rating_item_type', columns: {#item, #type})
@TableIndex(name: 'rating_user', columns: {#userId})
class Ratings extends Table {
  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  IntColumn get time => integer()();
  TextColumn get title => text().nullable()();
  TextColumn get userId => text()();
  BoolColumn get isUpdated => boolean().withDefault(const Constant(true))();
  IntColumn get rate => integer()();
  TextColumn get item => text()();
  TextColumn get comment => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  TextColumn get planetCode => text().nullable()();
  TextColumn get type => text()();

  /// Port of `Rating.createdOn` — despite the name it is not a timestamp.
  /// `RatingsRepositoryImpl.setRatingData` assigns the rater's `parentCode`
  /// to it, and `insertRatingsFromSync` copies the document's own value
  /// verbatim; `Rating.serializeRating` sends it straight back out.
  TextColumn get createdOn => text().nullable()();

  /// Port of `Rating.user` — the rater's serialized user document, stored as
  /// a JSON string exactly as the Kotlin stores it
  /// (`gson.toJson(resolvedUser.serialize())` locally, the document's own
  /// embedded `user` object on the way in). `serializeRating` parses it back
  /// into the `user` field of the uploaded document, which is how Planet
  /// attributes a rating.
  TextColumn get user => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Submission.kt`.
@DataClassName('SubmissionRow')
@TableIndex(
  name: 'submissions_user_updated',
  columns: {#userId, #lastUpdateTime},
)
class Submissions extends Table {
  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get parentId => text().nullable()();
  TextColumn get type => text().nullable()();
  TextColumn get userId => text().nullable()();
  TextColumn get user => text().nullable()();
  IntColumn get startTime => integer().withDefault(const Constant(0))();
  IntColumn get lastUpdateTime => integer().withDefault(const Constant(0))();
  IntColumn get grade => integer().withDefault(const Constant(0))();
  TextColumn get status => text().nullable()();
  BoolColumn get uploaded => boolean().withDefault(const Constant(false))();
  TextColumn get sender => text().nullable()();
  TextColumn get source => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  TextColumn get parent => text().nullable()();
  TextColumn get teamId => text().nullable()();
  BoolColumn get isUpdated => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Answer.kt`. Answers are authored with a submission and are
/// cached separately, matching Room's `answers` table in the Kotlin app.
@DataClassName('SubmissionAnswerRow')
@TableIndex(name: 'submission_answers_submission', columns: {#submissionId})
class SubmissionAnswers extends Table {
  TextColumn get id => text()();
  TextColumn get submissionId => text()();
  TextColumn get examId => text().nullable()();
  TextColumn get questionId => text().nullable()();
  TextColumn get value => text().nullable()();
  TextColumn get valueChoices => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  IntColumn get mistakes => integer().withDefault(const Constant(0))();
  BoolColumn get isPassed => boolean().withDefault(const Constant(false))();
  IntColumn get grade => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Embedded exam/survey question metadata from `model/ExamQuestion.kt`.
@DataClassName('SubmissionQuestionRow')
@TableIndex(name: 'submission_questions_submission', columns: {#submissionId})
class SubmissionQuestions extends Table {
  TextColumn get id => text()();
  TextColumn get submissionId => text()();
  TextColumn get header => text().nullable()();
  TextColumn get body => text().nullable()();
  TextColumn get type => text().nullable()();
  TextColumn get correctChoices => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get choices => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get marks => text().nullable()();
  IntColumn get position => integer()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Meetup.kt`. Meetups are server-cached documents that may
/// also carry local edits until the generic meetup uploader is ported.
@DataClassName('MeetupRow')
@TableIndex(name: 'meetup_remote_id', columns: {#meetupId})
@TableIndex(name: 'meetup_team_id', columns: {#teamId})
@TableIndex(name: 'meetup_user_id', columns: {#userId})
class Meetups extends Table {
  TextColumn get id => text()();
  TextColumn get userId => text().nullable()();
  TextColumn get meetupId => text().nullable()();
  TextColumn get meetupIdRev => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get description => text().nullable()();
  IntColumn get startDate => integer().withDefault(const Constant(0))();
  IntColumn get endDate => integer().withDefault(const Constant(0))();
  TextColumn get recurring =>
      text().nullable().withDefault(const Constant('none'))();
  TextColumn get day => text().nullable()();
  TextColumn get startTime => text().nullable()();
  TextColumn get endTime => text().nullable()();
  TextColumn get category => text().nullable()();
  TextColumn get meetupLocation => text().nullable()();
  TextColumn get meetupLink => text().nullable()();
  TextColumn get creator => text().nullable()();
  TextColumn get link => text().nullable()();
  TextColumn get teamId => text().nullable()();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  IntColumn get recurringNumber => integer().withDefault(const Constant(10))();
  TextColumn get sync => text().nullable()();
  TextColumn get sourcePlanet => text().nullable()();
  BoolColumn get updated => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of survey-shaped rows from `model/StepExam.kt` (`exams` database).
@DataClassName('SurveyRow')
@TableIndex(name: 'surveys_created_date', columns: {#createdDate})
@TableIndex(name: 'surveys_course_id', columns: {#courseId})
class Surveys extends Table {
  TextColumn get id => text()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get name => text().nullable()();
  TextColumn get description => text().nullable()();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  IntColumn get updatedDate => integer().withDefault(const Constant(0))();
  IntColumn get adoptionDate => integer().withDefault(const Constant(0))();
  TextColumn get createdBy => text().nullable()();
  IntColumn get totalMarks => integer().withDefault(const Constant(0))();
  TextColumn get passingPercentage => text().nullable()();
  TextColumn get sourcePlanet => text().nullable()();
  BoolColumn get isFromNation => boolean().withDefault(const Constant(false))();
  TextColumn get teamId => text().nullable()();
  BoolColumn get teamShareAllowed =>
      boolean().withDefault(const Constant(false))();
  TextColumn get sourceSurveyId => text().nullable()();
  TextColumn get courseId => text().nullable()();
  TextColumn get stepId => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Questions embedded in an `exams` CouchDB survey document.
@DataClassName('SurveyQuestionRow')
@TableIndex(name: 'survey_questions_survey', columns: {#surveyId, #position})
class SurveyQuestions extends Table {
  TextColumn get id => text()();
  TextColumn get surveyId => text()();
  TextColumn get questionId => text().nullable()();
  TextColumn get header => text().nullable()();
  TextColumn get body => text().nullable()();
  TextColumn get type => text().nullable()();

  /// The `{id, text}` pairs as CouchDB stores them — see [ExamChoice]. This
  /// used to be a [StringListConverter], which put every choice object through
  /// `toString()` and stored the Dart literal `{id: water, text: Water}`: the
  /// label was unusable and the id was gone. The SQL column is unchanged
  /// (`TEXT NOT NULL DEFAULT '[]'`), so the swap needs no schema bump; a row
  /// written by an earlier build decodes as a choice whose text is that
  /// literal, until the next surveys sync rewrites it.
  TextColumn get choices => text()
      .map(const ExamChoiceListConverter())
      .withDefault(const Constant('[]'))();
  BoolColumn get required => boolean().withDefault(const Constant(false))();
  IntColumn get position => integer()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/StepExam.kt` - graded course exams with correct answers.
@DataClassName('ExamRow')
@TableIndex(name: 'exams_step_id', columns: {#stepId})
@TableIndex(name: 'exams_course_id', columns: {#courseId})
class Exams extends Table {
  TextColumn get id => text()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get stepId => text().nullable()();
  TextColumn get courseId => text().nullable()();
  TextColumn get name => text().nullable()();
  TextColumn get description => text().nullable()();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  IntColumn get updatedDate => integer().withDefault(const Constant(0))();
  IntColumn get adoptionDate => integer().withDefault(const Constant(0))();
  TextColumn get createdBy => text().nullable()();
  IntColumn get totalMarks => integer().withDefault(const Constant(0))();
  TextColumn get passingPercentage => text().nullable()();
  TextColumn get sourcePlanet => text().nullable()();
  BoolColumn get isFromNation => boolean().withDefault(const Constant(false))();
  TextColumn get teamId => text().nullable()();
  BoolColumn get teamShareAllowed =>
      boolean().withDefault(const Constant(false))();
  TextColumn get sourceSurveyId => text().nullable()();
  IntColumn get noOfQuestions => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/ExamQuestion.kt` - exam questions with correct answers for grading.
@DataClassName('ExamQuestionRow')
@TableIndex(name: 'exam_questions_exam', columns: {#examId, #position})
class ExamQuestions extends Table {
  TextColumn get id => text()();
  TextColumn get examId => text()();
  TextColumn get header => text().nullable()();
  TextColumn get body => text().nullable()();
  TextColumn get type => text().nullable()();

  /// Lowercased choice **ids** that count as correct. Answers record the id of
  /// the selected choice, so grading compares ids to ids.
  TextColumn get correctChoices => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get marks => text().nullable()();

  /// The `{id, text}` pairs as CouchDB stores them — see [ExamChoice] for why
  /// this cannot be a plain string list.
  TextColumn get choices => text()
      .map(const ExamChoiceListConverter())
      .withDefault(const Constant('[]'))();
  BoolColumn get hasOtherOption =>
      boolean().withDefault(const Constant(false))();
  IntColumn get scaleMax => integer().withDefault(const Constant(9))();
  IntColumn get position => integer()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/News.kt` — voices/discussion posts, stored in the CouchDB
/// `news` database.
///
/// A reply is an ordinary row whose [replyTo] points at its parent, so a thread
/// is a self-join rather than a nested structure — matching the Kotlin, where
/// `deleteNews` has to walk replies recursively to remove a post.
///
/// [imageUrls] holds *local* attachment paths awaiting upload, while [images]
/// caches the server's `images` array as raw JSON; `markNewsUploaded` clears
/// the former and fills the latter, which is what moves an attachment from
/// "pending" to "on the server".
@DataClassName('NewsRow')
@TableIndex(name: 'news_reply_to', columns: {#replyTo})
@TableIndex(name: 'news_user_id', columns: {#userId})
@TableIndex(name: 'news_time', columns: {#time})
class NewsEntries extends Table {
  @override
  String get tableName => 'news';

  TextColumn get id => text()();
  TextColumn get docId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get userId => text().nullable()();
  TextColumn get user => text().nullable()();
  TextColumn get message => text().nullable()();
  TextColumn get docType => text().nullable()();
  TextColumn get viewableBy => text().nullable()();
  TextColumn get viewableId => text().nullable()();
  TextColumn get avatar => text().nullable()();
  TextColumn get replyTo => text().nullable()();
  TextColumn get userName => text().nullable()();
  TextColumn get messagePlanetCode => text().nullable()();
  TextColumn get messageType => text().nullable()();
  IntColumn get updatedDate => integer().withDefault(const Constant(0))();
  IntColumn get time => integer().withDefault(const Constant(0))();
  TextColumn get createdOn => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  TextColumn get imageUrls => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get images => text().nullable()();
  TextColumn get labels => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get viewIn => text().nullable()();
  TextColumn get newsId => text().nullable()();
  TextColumn get newsRev => text().nullable()();
  TextColumn get newsUser => text().nullable()();
  TextColumn get aiProvider => text().nullable()();
  TextColumn get newsTitle => text().nullable()();
  TextColumn get conversations => text().nullable()();
  IntColumn get newsCreatedDate => integer().withDefault(const Constant(0))();
  IntColumn get newsUpdatedDate => integer().withDefault(const Constant(0))();
  BoolColumn get chat => boolean().withDefault(const Constant(false))();
  BoolColumn get isEdited => boolean().withDefault(const Constant(false))();
  IntColumn get editedTime => integer().withDefault(const Constant(0))();
  TextColumn get sharedBy => text().nullable()();
  TextColumn get reactions => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/MyTeam.kt` for the team catalog vertical slice.
@DataClassName('TeamRow')
@TableIndex(name: 'teams_team_id', columns: {#teamId})
@TableIndex(name: 'teams_type', columns: {#type, #docType})
class Teams extends Table {
  TextColumn get id => text().named('_id')();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get teamId => text().nullable()();
  TextColumn get userId => text().nullable()();
  TextColumn get name => text().nullable()();
  TextColumn get description => text().nullable()();
  TextColumn get resourceId => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get type => text().nullable()();
  TextColumn get docType => text().nullable()();
  TextColumn get teamType => text().nullable()();
  TextColumn get status => text().nullable()();
  TextColumn get services => text().nullable()();
  TextColumn get rules => text().nullable()();
  TextColumn get createdBy => text().nullable()();

  /// Route for community services - links to other teams or external URLs.
  TextColumn get route => text().nullable()();
  TextColumn get courses => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  IntColumn get limit => integer().withDefault(const Constant(0))();
  BoolColumn get isPublic => boolean().withDefault(const Constant(false))();
  BoolColumn get isLeader => boolean().withDefault(const Constant(false))();
  IntColumn get beginningBalance => integer().withDefault(const Constant(0))();
  IntColumn get sales => integer().withDefault(const Constant(0))();
  IntColumn get otherIncome => integer().withDefault(const Constant(0))();
  IntColumn get wages => integer().withDefault(const Constant(0))();
  IntColumn get otherExpenses => integer().withDefault(const Constant(0))();
  IntColumn get startDate => integer().withDefault(const Constant(0))();
  IntColumn get endDate => integer().withDefault(const Constant(0))();
  IntColumn get updatedDate => integer().withDefault(const Constant(0))();

  /// For transactions: the transaction date.
  IntColumn get date => integer().withDefault(const Constant(0))();

  /// For transactions: the transaction amount (positive = credit, negative = debit).
  IntColumn get amount => integer().withDefault(const Constant(0))();

  /// The name of a binary attachment (a transaction or finance-report receipt
  /// image). Port of `MyTeam.imageName` / `getFirstAttachmentName`: the bytes
  /// live under `team_attachments/<docId>/<imageName>` (see `TeamAttachments`),
  /// and the name is what the upload write-back and the in-app preview resolve
  /// the file from. Null for every document without an attachment.
  TextColumn get imageName => text().nullable()();

  /// Set by every local write. The `teams` database mixes the server catalog
  /// with documents the user authors offline — a join request, a membership,
  /// a financial report, a resource link — and they are otherwise
  /// indistinguishable from cached rows, which is how the stale-row cleanup
  /// came to delete all of them.
  BoolColumn get isUpdated => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/TeamTask.kt`. Locally-created rows remain authoritative until
/// the durable outbox adopts the CouchDB id/revision.
@DataClassName('TeamTaskRow')
@TableIndex(name: 'team_tasks_team_id', columns: {#teamId})
class TeamTasks extends Table {
  TextColumn get id => text()();
  TextColumn get docId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get description => text().nullable()();
  TextColumn get teamId => text()();
  TextColumn get assignee => text().nullable()();
  IntColumn get deadline => integer().withDefault(const Constant(0))();
  IntColumn get completedTime => integer().withDefault(const Constant(0))();
  TextColumn get status => text().withDefault(const Constant('active'))();
  BoolColumn get completed => boolean().withDefault(const Constant(false))();
  BoolColumn get isUpdated => boolean().withDefault(const Constant(false))();

  /// Whether the deadline notification for this task has already been shown.
  ///
  /// `TeamTask.isNotified` in the Kotlin, and the reason its deadline worker can
  /// run every 15 minutes without re-notifying: the flag, not the OS, is what
  /// makes a notification once-only. It is never uploaded — the column exists on
  /// neither the CouchDB document nor `TeamTask.serialize` — so a synced row
  /// arrives with it false and each device notifies its own assignee once.
  BoolColumn get isNotified => boolean().withDefault(const Constant(false))();

  /// Port of `TeamTask.sync` and `TeamTask.link` — two JSON sub-objects the
  /// Kotlin stores as strings (`gson.toJson(JsonUtils.getJsonObject(…))`) and
  /// re-emits verbatim (`TeamTask.serialize`: `gson.fromJson(task.sync, …)`).
  ///
  /// They are stored rather than rebuilt because `upsertTask` fills them in
  /// **only when blank**, and it has exactly one caller — `createTask`. So a
  /// task that came from the server keeps the server's `sync` (whose
  /// `planetCode` names the planet that authored it, not this device's) and
  /// the server's `link` for the rest of its life, through every later edit.
  /// Rebuilding them from `teamId` and the session's planet code, which is
  /// what the port did before these columns existed, re-stamps someone else's
  /// task as locally authored here on the first edit.
  ///
  /// A document with no `link`/`sync` key stores the string `"{}"`, because
  /// `JsonUtils.getJsonObject` returns an empty object for a missing key — not
  /// null, so `upsertTask` would not treat it as blank either.
  TextColumn get sync => text().nullable()();
  TextColumn get link => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/ChatHistory.kt` — stores chat conversations with the AI
/// assistant. The embedded conversation list is stored as JSON.
@DataClassName('ChatRow')
@TableIndex(name: 'chat_user', columns: {#user})
class ChatEntries extends Table {
  @override
  String get tableName => 'chat_history';

  TextColumn get id => text()();
  TextColumn get docId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get user => text().nullable()();
  TextColumn get aiProvider => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get createdDate => text().nullable()();
  TextColumn get updatedDate => text().nullable()();
  IntColumn get lastUsed => integer().withDefault(const Constant(0))();

  /// Embedded list of conversations, stored as JSON.
  /// Each conversation has `query` and `response` fields.
  TextColumn get conversations => text().nullable()();

  /// Whether this chat has been uploaded to the server.
  BoolColumn get isUploaded => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Feedback.kt` — stores user feedback/issues with priority,
/// type, status, and embedded message list.
@DataClassName('FeedbackRow')
@TableIndex(name: 'feedback_owner', columns: {#owner})
@TableIndex(name: 'feedback_open_time', columns: {#openTime})
@TableIndex(name: 'feedback_is_uploaded', columns: {#isUploaded})
class FeedbackEntries extends Table {
  @override
  String get tableName => 'feedback';

  TextColumn get id => text()();
  TextColumn get rev => text().named('_rev').nullable()();

  TextColumn get title => text().nullable()();
  TextColumn get source => text().nullable()();
  TextColumn get status => text().nullable()();
  TextColumn get priority => text().nullable()();
  TextColumn get owner => text().nullable()();
  IntColumn get openTime => integer().withDefault(const Constant(0))();
  TextColumn get type => text().nullable()();
  TextColumn get url => text().nullable()();
  TextColumn get parentCode => text().nullable()();

  /// Whether this feedback has been uploaded to the server.
  BoolColumn get isUploaded => boolean().withDefault(const Constant(false))();

  /// Embedded list of messages/replies, stored as JSON.
  /// Each message has `message`, `user`, and `time` fields.
  TextColumn get messages => text().nullable()();

  TextColumn get item => text().nullable()();
  TextColumn get state => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/HealthExamination.kt` (`@Entity(tableName = "health_examinations")`).
///
/// HealthExamination stores health examination records for users.
@DataClassName('HealthExaminationRow')
@TableIndex(name: 'health_examinations_user_id', columns: {#userId})
class HealthExaminations extends Table {
  @override
  String get tableName => 'health_examinations';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();

  /// The user this examination belongs to.
  TextColumn get userId => text().nullable()();

  /// Vital signs.
  RealColumn get temperature => real().withDefault(const Constant(0.0))();
  IntColumn get pulse => integer().withDefault(const Constant(0))();
  TextColumn get bp => text().nullable()();
  RealColumn get height => real().withDefault(const Constant(0.0))();
  RealColumn get weight => real().withDefault(const Constant(0.0))();
  TextColumn get vision => text().nullable()();
  TextColumn get hearing => text().nullable()();

  /// Examination details stored as JSON string.
  TextColumn get conditions => text().nullable()();

  BoolColumn get selfExamination =>
      boolean().withDefault(const Constant(false))();
  TextColumn get planetCode => text().nullable()();
  BoolColumn get hasInfo => boolean().withDefault(const Constant(false))();
  TextColumn get profileId => text().nullable()();
  TextColumn get creatorId => text().nullable()();
  TextColumn get gender => text().nullable()();
  IntColumn get age => integer().withDefault(const Constant(0))();
  IntColumn get date => integer().withDefault(const Constant(0))();

  /// Encrypted examination data containing notes, diagnosis, medications, etc.
  TextColumn get data => text().nullable()();

  /// Whether this record has been modified locally and needs syncing.
  BoolColumn get isUpdated => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/CourseProgress.kt` (`@Entity(tableName = "course_progress")`).
///
/// One row per (course, user, step) recording whether the user *passed* that
/// step. Unlike most tables here it is a *mixed* authority: rows the user
/// authored on-device (a step viewed or an exam passed offline) carry no `_id`
/// and survive a schema upgrade, while rows pulled from the `courses_progress`
/// CouchDB database are a cache the next sync refills. See
/// [AppDatabase.localAuthorityTables] for why it is preserved.
@DataClassName('CourseProgressRow')
@TableIndex(name: 'course_progress_user', columns: {#userId})
@TableIndex(name: 'course_progress_course', columns: {#courseId})
@TableIndex(
  name: 'course_progress_course_user_step',
  columns: {#courseId, #userId, #stepNum},
)
class CourseProgress extends Table {
  @override
  String get tableName => 'course_progress';

  /// Locally-minted UUID for rows authored here; the CouchDB `_id` once the
  /// server has acknowledged the upload. Kotlin's `getPendingUploads` keys off
  /// `_id IS NULL` to tell them apart, and so does the uploader.
  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get createdOn => text().nullable()();
  IntColumn get createdDate => integer().withDefault(const Constant(0))();
  IntColumn get updatedDate => integer().withDefault(const Constant(0))();

  /// 1-based step position within the course — matches
  /// `CourseProgress.stepNum` and the `stepNumber` the take-course view passes.
  IntColumn get stepNum => integer().withDefault(const Constant(0))();
  BoolColumn get passed => boolean().withDefault(const Constant(false))();
  TextColumn get userId => text().nullable()();
  TextColumn get courseId => text().nullable()();
  TextColumn get parentCode => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/OfflineActivity.kt` (`@Entity(tableName = "offline_activity")`).
///
/// The device's own record of the user's offline sessions, read by the
/// dashboard's offline-login count and the activity chart and carried to the
/// server's `login_activities` database by `ActivitiesUploader`.
///
/// Preserved across a schema bump (see [AppDatabase.localAuthorityTables]): a
/// row that has not been uploaded yet exists nowhere else, and the port does not
/// sync `login_activities` back in, so a drop is permanent for the rest.
@DataClassName('OfflineActivityRow')
@TableIndex(name: 'offline_activity_user_name', columns: {#userName})
@TableIndex(name: 'offline_activity_type', columns: {#type})
@TableIndex(name: 'offline_activity_login_time', columns: {#loginTime})
class OfflineActivities extends Table {
  @override
  String get tableName => 'offline_activity';

  /// Locally-minted id. `_id`/`_rev` stay null until the upload lands — the
  /// Kotlin sets them explicitly to null when logging a login, and
  /// `getPendingLoginUploads` selects exactly the rows where `_rev` is still
  /// null.
  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get userName => text().nullable()();
  TextColumn get userId => text().nullable()();

  /// `login` for the rows this slice writes — `UserSessionManager.KEY_LOGIN`.
  TextColumn get type => text().nullable()();
  TextColumn get description => text().nullable()();

  /// The user's planet code, not a timestamp, matching the Kotlin's
  /// `createdOn = planetCode`.
  TextColumn get createdOn => text().nullable()();
  TextColumn get parentCode => text().nullable()();

  /// Nullable in the Kotlin entity and nullable here: `ActivitiesFragment`
  /// filters logins with `mapNotNull { it.loginTime }`, so a row without one
  /// must be representable.
  IntColumn get loginTime => integer().nullable()();
  IntColumn get logoutTime => integer().nullable()();
  TextColumn get androidId => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/ResourceActivity.kt` (`@Entity(tableName = "resource_activity")`).
///
/// One row per resource open or download, plus one per completed sync. The
/// `type` separates them and decides where the row is posted:
/// `visit`/`download` go to the `resource_activities` database
/// (`UploadConfigs.ResourceActivities`) and `sync` goes to `admin_activities`
/// (`UploadConfigs.ResourceActivitiesSync`) — the Kotlin's two configs differ
/// only in that predicate and that endpoint.
///
/// Preserved across a schema bump (see [AppDatabase.localAuthorityTables]).
/// The test is "can a sync restore this?", and here it cannot in either
/// direction: an un-uploaded row exists nowhere else, and neither app syncs
/// `resource_activities` or `admin_activities` back in — they are write-only
/// telemetry databases.
@DataClassName('ResourceActivityRow')
@TableIndex(name: 'resource_activity_type', columns: {#type})
@TableIndex(name: 'resource_activity_user', columns: {#user})
class ResourceActivities extends Table {
  @override
  String get tableName => 'resource_activity';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();

  /// The user's *name*, not id — the Kotlin's column is `user` and
  /// `logResourceOpen` stores `model?.name` in it. Every read keys on the name.
  TextColumn get user => text().nullable()();

  /// `visit` (`KEY_RESOURCE_OPEN`), `download` (`KEY_RESOURCE_DOWNLOAD`), or
  /// `sync` (`recordSyncActivity`).
  TextColumn get type => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get resourceId => text().nullable()();

  /// The user's planet code, as on [OfflineActivities] — not a timestamp.
  TextColumn get createdOn => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  IntColumn get time => integer().withDefault(const Constant(0))();
  TextColumn get androidId => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/CourseActivity.kt` (`@Entity(tableName = "course_activity")`).
///
/// One `visit` row per course opened in the take-course view, posted to the
/// `course_activities` database (`UploadConfigs.CourseActivities`). Preserved
/// for the same reason as [ResourceActivities].
@DataClassName('CourseActivityRow')
@TableIndex(name: 'course_activity_course_id', columns: {#courseId})
@TableIndex(name: 'course_activity_type', columns: {#type})
class CourseActivities extends Table {
  @override
  String get tableName => 'course_activity';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();

  /// The user's name, matching [ResourceActivities.user] — `logCourseVisit`
  /// takes what it calls a `userId` and looks the user up by *name*
  /// (`getUserByName`), then stores that same string here.
  TextColumn get user => text().nullable()();
  TextColumn get type => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get courseId => text().nullable()();
  TextColumn get createdOn => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  IntColumn get time => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/TeamNotification.kt` (`@Entity(tableName = "team_notification")`).
///
/// One row per team per notification type, holding the chat count as of the
/// last time the user looked. The dashboard's chat badge is
/// `lastCount < currentCount`, so this is the "seen" watermark.
///
/// Deliberately **not** preserved across a schema bump, matching the Kotlin —
/// Room drops this table too under `fallbackToDestructiveMigration`. Losing a
/// watermark row makes `getTeamNotifications` read `notification == null`,
/// which suppresses the badge until the user next opens that team's voices;
/// nothing the user authored is lost.
@DataClassName('TeamNotificationRow')
@TableIndex(name: 'team_notification_type', columns: {#type})
class TeamNotifications extends Table {
  @override
  String get tableName => 'team_notification';

  TextColumn get id => text()();
  TextColumn get type => text().nullable()();

  /// The team's `_id` for the `chat` type.
  TextColumn get parentId => text().nullable()();
  IntColumn get lastCount => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Certification.kt` (`@Entity(tableName = "certification")`).
///
/// Read-only sync data; `courseIds` stores the certification's course-id array
/// as a JSON string, and a `LIKE` substring match mirrors the Kotlin's
/// `CertificationDao.countByCourseId`. A completed course whose id appears in a
/// certification is shown with a tinted star on the dashboard.
@DataClassName('CertificationRow')
class Certifications extends Table {
  @override
  String get tableName => 'certification';

  TextColumn get id => text().named('_id')();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get name => text().nullable()();

  /// JSON array string of course ids — kept verbatim to match the `LIKE` query.
  TextColumn get courseIds => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Durable resource-file work owned by this device.
///
/// This is intentionally separate from the upload outbox: a download has no
/// JSON payload or CouchDB write, but needs the same process-death guarantee.
@DataClassName('DownloadQueueRow')
class DownloadQueueEntries extends Table {
  @override
  String get tableName => 'download_queue';

  TextColumn get resourceId => text()();
  IntColumn get createdAt => integer()();

  @override
  Set<Column> get primaryKey => {resourceId};
}

/// Port of `model/SubmitPhotos.kt` (`@Entity(tableName = "submit_photos")`).
///
/// A captured exam verification photo is authored on this device — it exists
/// nowhere else until the `SubmitPhotosUploader` delivers it — so the table is
/// locally authored and preserved across a schema bump. The row carries only
/// the metadata CouchDB needs to file it (`submissionId`/`courseId`/`examId`)
/// and the local path to the bytes, which are PUT separately as an attachment
/// once the document is acknowledged — the same two-step shape as `teams`.
/// Kotlin's `uniqueId` (the device identity it uploads as `macAddress`) is
/// layered onto the document by the uploader at queue time rather than
/// persisted here, matching how every other Flutter uploader adds telemetry.
@DataClassName('SubmitPhotosRow')
class SubmitPhotosTable extends Table {
  @override
  String get tableName => 'submit_photos';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get submissionId => text().nullable()();
  TextColumn get courseId => text().nullable()();
  TextColumn get examId => text().nullable()();
  TextColumn get memberId => text().nullable()();
  TextColumn get date => text().nullable()();

  /// Local path to the captured JPEG. The bytes are never serialized into the
  /// document; they are PUT as a CouchDB attachment after the POST succeeds.
  TextColumn get photoLocation => text().nullable()();

  BoolColumn get uploaded => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/SearchActivity.kt` (`@Entity(tableName =
/// "search_activity")`) — one row per filtered search the user runs on the
/// courses or resources list. Locally authored (the Kotlin writes the row from
/// `CoursesFragment.onPause` / `ResourcesFragment.onPause` via
/// `*Repository.saveSearchActivity`) and preserved across a schema bump: a
/// row whose `_rev` is empty has not yet reached `search_activities`, and
/// dropping it would silently lose the analytics event.
@DataClassName('SearchActivityRow')
class SearchActivities extends Table {
  @override
  String get tableName => 'search_activity';

  TextColumn get id => text()();
  TextColumn get couchId =>
      text().named('_id').withDefault(const Constant(''))();
  TextColumn get rev => text().named('_rev').withDefault(const Constant(''))();
  // The Dart getter is `searchText` because `text` clashes with the `text()`
  // builder on drift's `Table` base class; `.named('text')` keeps the SQL
  // column name the Kotlin schema uses.
  TextColumn get searchText =>
      text().named('text').withDefault(const Constant(''))();
  TextColumn get type => text().withDefault(const Constant(''))();
  IntColumn get time => integer().withDefault(const Constant(0))();
  TextColumn get user => text().withDefault(const Constant(''))();
  // The Kotlin persists the filter JSON via Gson; the port stores the same
  // serialized string and parses it back at upload time. The Dart getter is
  // named `filterJson` because `filter` clashes with a member on drift's
  // `Table` base class; `.named('filter')` keeps the SQL column name the
  // Kotlin schema uses.
  TextColumn get filterJson =>
      text().named('filter').withDefault(const Constant(''))();
  TextColumn get createdOn => text().withDefault(const Constant(''))();
  TextColumn get parentCode => text().withDefault(const Constant(''))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/TeamLog.kt` (`@Entity(tableName = "team_log")`) — one row
/// per `teamVisit` a user makes to a team's detail screen. Locally authored
/// (the Kotlin logs the visit from `TeamDetailFragment.onViewCreated` via
/// `TeamsRepositoryImpl.logTeamVisit`) and preserved across a schema bump:
/// the `uploaded` flag is the only durable record that the visit has not yet
/// reached `team_activities`, and dropping the row would silently lose an
/// action the user took.
@DataClassName('TeamLogRow')
class TeamLogTable extends Table {
  @override
  String get tableName => 'team_log';

  TextColumn get id => text()();
  TextColumn get couchId => text().named('_id').nullable()();
  TextColumn get rev => text().named('_rev').nullable()();
  TextColumn get teamId => text().nullable()();
  TextColumn get user => text().nullable()();
  TextColumn get type => text().nullable()();
  TextColumn get teamType => text().nullable()();
  TextColumn get createdOn => text().nullable()();
  TextColumn get parentCode => text().nullable()();
  IntColumn get time => integer().nullable()();
  BoolColumn get uploaded => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/TagEntity.kt` (`@Entity(tableName = "tag")`) — the cache of
/// the `tags` CouchDB database. One table holds three row shapes at once:
/// collection definitions (a non-empty [name], an empty [attachedTo]),
/// child tags (a name whose [attachedTo] lists a parent's id), and link rows
/// attaching a tag to a resource or course ([db] is `resources` or `courses`,
/// [linkId] the resource/course id, [tagId] the tag's id). Pure cache: the
/// app never writes tags locally, so sync refills with upserts and prunes
/// with `deleteNotIn`, and the table is not preserved across schema bumps.
class Tags extends Table {
  @override
  String get tableName => 'tag';

  TextColumn get id => text()();
  TextColumn get couchId =>
      text().named('_id').withDefault(const Constant(''))();
  TextColumn get rev => text().named('_rev').withDefault(const Constant(''))();
  TextColumn get name => text().withDefault(const Constant(''))();
  TextColumn get linkId => text().withDefault(const Constant(''))();
  TextColumn get tagId => text().withDefault(const Constant(''))();
  TextColumn get attachedTo => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  TextColumn get docType => text().withDefault(const Constant(''))();
  TextColumn get db => text().withDefault(const Constant(''))();
  BoolColumn get isAttached => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Achievement.kt` (`@Entity(tableName = "achievements")`).
///
/// One row per user, keyed `"$userId@$planetCode"` — the way
/// `UserRepositoryImpl.initializeAchievement` derives it. The achievements
/// and references lists are locally authored and preserved across a schema
/// bump: until the `AchievementsUploader` delivers the document, a bump
/// would silently erase the user's work.
///
/// `achievementsJson`/`referencesJson`/`linksJson`/`otherInfoJson` carry the
/// JSON arrays the Kotlin stores through Room's `Converters` and
/// `Achievement.serialize` re-parses at upload time — the upload document
/// and the rendered list both read the same fields.
@DataClassName('AchievementRow')
class Achievements extends Table {
  @override
  String get tableName => 'achievements';

  /// `"${user.id}@${user.planetCode}"`.
  TextColumn get id => text()();
  TextColumn get purpose => text().withDefault(const Constant(''))();
  TextColumn get goals => text().withDefault(const Constant(''))();
  TextColumn get achievementsHeader => text().withDefault(const Constant(''))();
  BoolColumn get sendToNation => boolean().withDefault(const Constant(false))();
  TextColumn get achievementsJson =>
      text().named('achievements').withDefault(const Constant('[]'))();
  TextColumn get referencesJson =>
      text().named('references').withDefault(const Constant('[]'))();
  TextColumn get linksJson =>
      text().named('links').withDefault(const Constant('[]'))();
  TextColumn get otherInfoJson =>
      text().named('otherInfo').withDefault(const Constant('[]'))();
  TextColumn get dateSortOrder => text().withDefault(const Constant('none'))();
  TextColumn get createdOn => text().withDefault(const Constant(''))();
  TextColumn get username => text().withDefault(const Constant(''))();
  TextColumn get parentCode => text().withDefault(const Constant(''))();
  TextColumn get couchId => text().named('_id')();
  TextColumn get rev => text().named('_rev')();
  BoolColumn get uploaded => boolean().withDefault(const Constant(false))();
  TextColumn get resumeFileName => text()();

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/UserChallengeActions.kt`. One row per challenge action a
/// user completes (currently only `"sync"`). Locally authored and never
/// synced back in — `user_challenge_actions` has no CouchDB counterpart — so
/// it belongs in [localAuthorityTables] and survives a schema bump.
class UserChallengeActions extends Table {
  @override
  String get tableName => 'user_challenge_actions';

  /// UUID minted at insert, matching the Kotlin's
  /// `UUID.randomUUID().toString()`.
  TextColumn get id => text()();
  TextColumn get userId => text().nullable()();
  TextColumn get actionType => text().nullable()();
  TextColumn get resourceId => text().nullable()();
  IntColumn get time => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {id};
}
