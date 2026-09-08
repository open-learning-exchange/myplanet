# Phase 132 — plumbing the team through the port's answer sheets

Lane B of a three-lane round. Branch `claude/survey-team-context-m2p6`.

Closes the reachability half of Phase 129 item 1: `createSurveyDraft(teamId:)`
and `startExamSession(teamId:)` exist and are tested, and **no caller passes a
team**, so every answer sheet the port writes is team-less where Kotlin's
carries the team.

Notes are written as the work proceeds; this file is a stub until the first
findings land.

## Reported, not fixed

_(pending)_
