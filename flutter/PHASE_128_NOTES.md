# Phase 128 — the step tile's Redo survey / Retake test labels

Lane A of a three-lane round. Work in progress.

Ported from `CourseStepFragment.hideTestIfNoQuestion` (`:241-262`), whose
label swap reads `getCourseStepData`'s `hasExam`/`hasSurvey` —
`submissionsRepository.hasSubmission(stepExams[0].id, step.courseId, userId,
"exam"/"survey")` (`CoursesRepositoryImpl.kt:529-556`).
