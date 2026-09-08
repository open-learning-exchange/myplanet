# Phase 136 — an adopted survey clone loses its course

Lane C of a four-lane round. Branch `claude/adopted-survey-course-key-j3v7`,
based on `claude/kotlin-flutter-dart-migration-d3gmrd`.

Brief: `SurveysRepositoryImpl.createMappedSurvey` copies `courseId` and
`stepId` onto an adopted team clone; the port's `adoptSurvey` does not, and
Phase 132's Send fix turned that omission into Phase 125's writer/reader key
disagreement.

_In progress — notes are written as the work lands._
