# myPlanet Refactoring - Quick Reference

## 15 Granular Tasks Summary

### Repository Naming (12 tasks) - Plural → Singular

| # | Current Name | New Name | Files Affected |
|---|--------------|----------|----------------|
| 1 | TeamsRepository | TeamRepository | 2 files + DI + UI |
| 2 | ActivitiesRepository | ActivityRepository | 2 files + DI + UI |
| 3 | EventsRepository | EventRepository | 2 files + DI + UI |
| 4 | VoicesRepository | VoiceRepository | 2 files + DI + UI |
| 5 | PersonalsRepository | PersonalRepository | 2 files + DI + UI |
| 6 | NotificationsRepository | NotificationRepository | 2 files + DI + UI |
| 7 | RatingsRepository | RatingRepository | 2 files + DI + UI |
| 8 | TagsRepository | TagRepository | 2 files + DI + UI |
| 9 | ResourcesRepository | ResourceRepository | 2 files + DI + UI |
| 10 | CoursesRepository | CourseRepository | 2 files + DI + UI |
| 11 | SubmissionsRepository | SubmissionRepository | 2 files + DI + UI |
| 12 | SurveysRepository | SurveyRepository | 2 files + DI + UI |

### Package Organization (3 tasks)

| # | Task | Files Affected | Action |
|---|------|----------------|--------|
| 13 | Organize Callbacks | 33 callback files | Move to feature subdirs (chat/, teams/, sync/, library/, dashboard/) |
| 14 | Consolidate Managers | 5 manager files | Move to services/managers/ |
| 15 | Group Workers | 6 worker files | Move to services/workers/ |

## Task Characteristics

✅ **Safe for Parallel Execution**
- All tasks touch different files
- No overlapping changes
- Minimal merge conflict risk

✅ **Non-Functional Changes Only**
- Rename files/classes
- Move files to new packages
- Update imports
- NO logic changes

✅ **Build Verification Required**
- Compile after each task
- Update DI modules
- Update imports

## Detailed Plan

See **REFACTORING_PLAN.md** for:
- Complete rationale for each task
- File citations with line numbers
- Step-by-step execution instructions
- GitHub URL references
