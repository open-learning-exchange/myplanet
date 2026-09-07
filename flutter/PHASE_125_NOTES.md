# Phase 125 — the mandatory-survey key (`parentId`)

Lane A. In progress.

Target: `PHASE_123_NOTES.md` §"Found, not fixed" item 0 — every port writer
stores a course-attached survey's `parentId` as the bare survey id while
`hasUnfinishedSurveys` looks for `surveyId@courseId`, so a learner who has
completed the attached survey is still told they have not, and
`MANDATORY_SURVEY_COURSE_ID` cannot be finished.
