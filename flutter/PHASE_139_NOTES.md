# Phase 139 — the per-step assessment lock, the survey's return, and an explicit `stepNum`

Lane B of a four-lane round. Three jobs, all from previous rounds'
*Reported, not fixed* lists:

1. `TakeCourseFragment.changeNextButtonState` is unported — the per-step
   next-button lock for `MANDATORY_SURVEY_COURSE_ID` (Phase 128 item 4).
2. A course-step survey `go`es to the submissions screen instead of returning
   to the step, so half of Phase 128's tile-refresh fix is unreachable
   (Phase 128 item 6).
3. A course step's number is derived from `stepIndex` where `?stepNum=` is the
   closer port (Phase 135 item 4).

*In progress — this file is written as the work lands.*
