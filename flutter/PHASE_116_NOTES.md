# Phase 116 — the reachability audit

**Status: in progress.** This lane audits, for every screen in `flutter/lib/ui/`,
whether a live path reaches it from an app entry point *and* whether the data its
query needs is ever written in a shape the query can match.

Phase 113 found `TakeExamScreen` unreachable in production — not broken,
unreachable — because `CourseMapper` dropped the step's embedded exam, so the
join `exams.stepId == course_steps.id` could never match real synced data. Green
tests could not see it: every fixture hand-faked the join.

The table, the evidence and the guards land here.
