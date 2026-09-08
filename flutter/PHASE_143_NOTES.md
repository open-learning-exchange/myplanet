# Phase 143 — the schema bump destroys an unpublished survey clone, and `submissions` outlives it

Lane B of a four-lane round. The brief is Phase 138's own *Reported, not fixed*
entry — written by the lane that created the expectation and deliberately did
not close it.

**Status: in progress.**

## The hole

Phase 138 stopped `SurveyDao.deleteNotIn` destroying an adopted team survey
clone on the next sync. `surveys` and `survey_questions` are still not in
`localAuthorityTables`, while `submissions`, `submission_answers` and
`submission_questions` all are — so a schema bump on a handset that has adopted
but not published drops the clone and its questions and *keeps* the members'
answer sheets. The same orphaning, reachable by an upgrade instead of a sync.

Notes are written as the work lands; see *Reported, not fixed* at the end.
