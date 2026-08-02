import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';

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
Widget wrapScreen(
  Widget child, {
  List<Override> overrides = const [],
  Locale? locale,
}) {
  return ProviderScope(
    overrides: [
      appDatabaseProvider.overrideWith((ref) {
        final database = AppDatabase.memory();
        ref.onDispose(database.close);
        return database;
      }),
      ...overrides,
    ],
    child: MaterialApp(
      locale: locale,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: child,
    ),
  );
}

MyLibraryRow buildLibraryRow({
  required String id,
  String? title,
  String? author,
  bool offline = false,
  List<String> subject = const [],
}) {
  return MyLibraryRow(
    id: id,
    userId: const [],
    title: title,
    titleNormal: title?.toLowerCase(),
    resourceOffline: offline,
    createdDate: 0,
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

CourseRow buildCourseRow({
  required String id,
  String? courseTitle,
  String? gradeLevel,
  String? subjectLevel,
  String? description,
  List<String> userId = const [],
}) {
  return CourseRow(
    id: id,
    userId: userId,
    courseTitle: courseTitle,
    courseTitleNormal: courseTitle?.toLowerCase(),
    gradeLevel: gradeLevel,
    subjectLevel: subjectLevel,
    description: description,
    createdDate: 0,
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
