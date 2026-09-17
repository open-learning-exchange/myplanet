import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/sync/heavy_table_sync.dart';

/// Pins the exclusion of `courses_progress` from the interactive courses sync.
///
/// It is a source-text assertion rather than a behavioural one on purpose: the
/// failure mode is somebody re-adding the call because the method looks
/// uncalled, and that reads as tidying up rather than as a regression. On
/// planet.learning the table is 114,219 documents — 572 `_all_docs` pages with
/// a deepening `skip` — so inline it cannot finish and takes the courses area
/// down with it.
///
/// **The pull is not missing any more, and that does not retire this guard.**
/// `HeavyTableSync` now owns it, walking the table in a background task from a
/// persisted `heavy_sync_skip_courses_progress` checkpoint, which is where
/// Kotlin has it too (`HeavyTableSyncWorker.ALL_HEAVY_TABLES`). What the guard
/// says has therefore changed from "the port cannot do this yet" to "this is
/// not the place it is done": an inline call would still have no checkpoint,
/// so re-adding one would break the courses area again exactly as before. The
/// second half of the file pins the other end of that claim — the table really
/// is walked by the worker — so the guard cannot pass by both call sites being
/// absent.
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
          'checkpoint, and HeavyTableSync is the port of that worker. Inline '
          'the walk has no checkpoint, never completes, and fails the courses '
          'sync with it. Leave it to the worker.',
    );
    // The pulls that do belong here are still wired.
    expect(body.contains('syncCertifications'), isTrue);
    expect(body.contains('courses.sync('), isTrue);
  });

  test('the heavy-table worker is where courses_progress is walked', () {
    // The other half of the claim above. Without this, deleting the pull
    // entirely would leave the guard green while Planet's grading stopped
    // reaching the handset — which is precisely the state the stopgap that
    // removed the inline call left the port in.
    expect(HeavyTableSync.tables, contains('courses_progress'));
    expect(HeavyTableSync.pageSizeFor('courses_progress'), 200);
  });
}
