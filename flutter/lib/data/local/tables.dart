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
  TextColumn get relatedId => text().nullable()();
  TextColumn get title => text().nullable()();
  TextColumn get link => text().nullable()();
  BoolColumn get isRead => boolean().withDefault(const Constant(false))();
  IntColumn get createdAt => integer()();
  IntColumn get priority => integer().withDefault(const Constant(0))();
  BoolColumn get isFromServer => boolean().withDefault(const Constant(false))();
  BoolColumn get needsSync => boolean().withDefault(const Constant(false))();

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
  TextColumn get choices => text()
      .map(const StringListConverter())
      .withDefault(const Constant('[]'))();
  BoolColumn get required => boolean().withDefault(const Constant(false))();
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

  @override
  Set<Column> get primaryKey => {id};
}

/// Port of `model/Feedback.kt` — stores user feedback/issues with priority,
/// type, status, and embedded message list.
@DataClassName('FeedbackRow')
@TableIndex(name: 'feedback_owner', columns: {#owner})
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
