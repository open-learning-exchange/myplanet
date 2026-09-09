import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Pins the exclusion of `courses_progress` from the interactive courses sync.
///
/// It is a source-text assertion rather than a behavioural one on purpose: the
/// failure mode is somebody re-adding the call because the method looks
/// uncalled, and that reads as tidying up rather than as a regression. On
/// planet.learning the table is 114,219 documents — 572 `_all_docs` pages with
/// a deepening `skip` — so inline it cannot finish and takes the courses area
/// down with it. Kotlin keeps it in `HeavyTableSyncWorker`, resumable via a
/// persisted checkpoint; the port has no such scheduler yet.
void main() {
  test('the interactive courses sync does not pull courses_progress', () {
    final source = File(
      'lib/providers/courses_providers.dart',
    ).readAsStringSync();
    final notifier = source.substring(
      source.indexOf('class CourseSyncNotifier'),
    );
    final body = notifier.substring(0, notifier.indexOf('\n}'));

    expect(
      body.contains('syncCourseProgress'),
      isFalse,
      reason:
          'CourseSyncNotifier.runSync must not call syncCourseProgress. '
          'courses_progress is a heavy table (114k+ docs on planet.learning) '
          'that Kotlin runs in HeavyTableSyncWorker with a resumable '
          'checkpoint. Inline it never completes and fails the courses sync. '
          'Port the background worker instead of re-adding the call.',
    );
    // The pulls that do belong here are still wired.
    expect(body.contains('syncCertifications'), isTrue);
    expect(body.contains('courses.sync('), isTrue);
  });
}
