import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/view_mode_providers.dart';
import 'package:myplanet/ui/components/list_view_mode.dart';

/// Wraps a widget in the localisation delegates and a [ProviderScope], so
/// screen tests only have to declare the overrides they care about.
///
/// [appDatabaseProvider] is always redirected to an in-memory database, ahead
/// of the caller's [overrides] so a test can still swap in its own. Without it
/// a screen that reads an un-overridden DAO — an unread badge, a filter list —
/// falls through to `AppDatabase.open()`, whose `path_provider` lookup has no
/// platform channel under `flutter test`. Screens tend to read those through
/// `.valueOrNull ?? <default>`, so the failure is swallowed and the test passes
/// while silently exercising nothing.
///
/// If a test fails with "A Timer is still pending even after the widget tree
/// was disposed", that is this backstop firing: the screen opened a drift
/// stream against the fallback database. Override the provider the screen
/// actually reads rather than reaching for `tester.runAsync`.
/// [pushTargets] maps a route path the screen navigates to onto the widget it
/// should land on. Supply it whenever the screen calls `context.push`/`pop` —
/// those are go_router extensions and assert "No GoRouter found in context"
/// under a bare `home:`.
Widget wrapScreen(
  Widget child, {
  List<Override> overrides = const [],
  Locale? locale,
  Map<String, WidgetBuilder> pushTargets = const {},
}) {
  final router = pushTargets.isEmpty
      ? null
      : GoRouter(
          routes: [
            GoRoute(path: '/', builder: (context, _) => child),
            for (final entry in pushTargets.entries)
              GoRoute(
                path: entry.key,
                builder: (context, _) => entry.value(context),
              ),
          ],
        );
  return ProviderScope(
    overrides: [
      appDatabaseProvider.overrideWith((ref) {
        final database = AppDatabase.memory();
        ref.onDispose(database.close);
        return database;
      }),
      courseViewModeProvider.overrideWith(_TestCourseViewModeNotifier.new),
      libraryViewModeProvider.overrideWith(_TestLibraryViewModeNotifier.new),
      ...overrides,
    ],
    child: router == null
        ? MaterialApp(
            locale: locale,
            localizationsDelegates: _delegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: child,
          )
        : MaterialApp.router(
            locale: locale,
            localizationsDelegates: _delegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: router,
          ),
  );
}

const _delegates = <LocalizationsDelegate<Object?>>[
  AppLocalizations.delegate,
  GlobalMaterialLocalizations.delegate,
  GlobalWidgetsLocalizations.delegate,
  GlobalCupertinoLocalizations.delegate,
];

MyLibraryRow buildLibraryRow({
  required String id,
  String? title,
  String? author,
  String? description,
  String? resourceId,
  bool offline = false,
  List<String> subject = const [],
  int createdDate = 0,
}) {
  return MyLibraryRow(
    id: id,
    userId: const [],
    title: title,
    titleNormal: title?.toLowerCase(),
    description: description,
    resourceId: resourceId,
    resourceOffline: offline,
    createdDate: createdDate,
    timesRated: 0,
    resourceFor: const [],
    subject: subject,
    level: const [],
    tag: const [],
    languages: const [],
    isPrivate: false,
    author: author,
  );
}

UserRow buildUserRow({
  required String id,
  String? name,
  String? firstName,
  String? lastName,
  String? email,
  String? dob,
  String? language,
  String? phoneNumber,
  String? birthPlace,
  String? level,
  String? userImage,
  bool isArchived = false,
  bool isUpdated = false,
}) {
  return UserRow(
    id: id,
    name: name,
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    firstName: firstName,
    lastName: lastName,
    email: email,
    dob: dob,
    language: language,
    phoneNumber: phoneNumber,
    birthPlace: birthPlace,
    level: level,
    userImage: userImage,
    isArchived: isArchived,
    isUpdated: isUpdated,
  );
}

CourseRow buildCourseRow({
  required String id,
  String? courseTitle,
  String? gradeLevel,
  String? subjectLevel,
  String? description,
  String? coverFileName,
  List<String> userId = const [],
  int createdDate = 0,
}) {
  return CourseRow(
    id: id,
    userId: userId,
    courseTitle: courseTitle,
    courseTitleNormal: courseTitle?.toLowerCase(),
    gradeLevel: gradeLevel,
    subjectLevel: subjectLevel,
    description: description,
    coverFileName: coverFileName,
    createdDate: createdDate,
  );
}

/// A [CoursesCompanion] counterpart of [buildCourseRow] for tests that write
/// to the in-memory database rather than overriding the stream.
CoursesCompanion buildCourseCompanion({
  required String id,
  String? courseTitle,
  String? gradeLevel,
  String? subjectLevel,
  String? description,
  String? coverFileName,
  List<String> userId = const [],
  int createdDate = 0,
}) {
  return CoursesCompanion(
    id: Value(id),
    couchId: Value(id),
    courseId: Value(id),
    courseTitle: Value(courseTitle),
    courseTitleNormal: Value(courseTitle?.toLowerCase()),
    gradeLevel: Value(gradeLevel),
    subjectLevel: Value(subjectLevel),
    description: Value(description),
    coverFileName: Value(coverFileName),
    userId: Value(userId),
    createdDate: Value(createdDate),
  );
}

CourseStepRow buildStepRow({
  required String id,
  String? stepTitle,
  String? description,
  int noOfResources = 0,
  int stepIndex = 0,
  String courseId = 'course-1',
}) {
  return CourseStepRow(
    id: id,
    courseId: courseId,
    stepTitle: stepTitle,
    description: description,
    noOfResources: noOfResources,
    stepIndex: stepIndex,
  );
}

/// View-mode notifiers that default to list without touching prefs, so screen
/// tests that look for `ListTile`-based list tiles keep working. The real app
/// defaults to grid; tests that exercise grid mode override these providers.
/// `set` is overridden too so tapping the toggle doesn't reach `planetPrefs`.
class _TestCourseViewModeNotifier extends CourseViewModeNotifier {
  @override
  ListViewMode build() => ListViewMode.list;

  @override
  Future<void> set(ListViewMode mode) async => state = mode;
}

class _TestLibraryViewModeNotifier extends LibraryViewModeNotifier {
  @override
  ListViewMode build() => ListViewMode.list;

  @override
  Future<void> set(ListViewMode mode) async => state = mode;
}
