# Phase 135 — DAO predicates: user-scoped exam grading, and five unescaped `LIKE`s

Lane B of a four-lane round. Branch `claude/dao-predicates-q5w9`, off
`claude/kotlin-flutter-dart-migration-d3gmrd`. Sole owner of
`lib/data/local/app_database.dart` this round, which is why the two jobs are
one lane: **one file, one owner.**

Two jobs, two commits:

1. Port `ed5609f` — `AND userId IS :userId` on
   `CourseProgressDao.updatePassedByCourseAndStep`, threaded through
   `updateCourseProgress`, and wire the caller the port never had.
2. Escape the five interpolated `LIKE` patterns Phase 131 listed.

*(In progress — findings below are filled in as they are established.)*

## Reported, not fixed

*(to be filled in)*
