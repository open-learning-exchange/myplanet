# Quick Reference: 10 Refactoring Tasks

## Overview
Repository boundary reinforcement tasks focusing on moving data logic from UI/service layers into repositories. All tasks are granular, low-risk, and independently implementable.

---

## Task List

| # | Task | Priority | Estimated Lines | Files Affected |
|---|------|----------|-----------------|----------------|
| 1 | Make mRealm private in BaseResourceFragment | High | ~5 | 1 base + audit |
| 2 | Add getExaminationById to HealthRepository | Medium | ~20 | 3 files |
| 3 | Remove deprecated userModel from UserSessionManager | Medium | ~10 | 1 + audit usages |
| 4 | Remove redundant withContext(Dispatchers.Main) | Low | ~30 | 5 fragments |
| 5 | Simplify download suggestions with repository | Medium | ~15 | 2 files |
| 6 | Add saveHealthExamination to HealthRepository | High | ~50 | 3 files |
| 7 | Replace Dispatchers.Default with IO | Low | ~6 | 3 files |
| 8 | Verify TeamsRepository.isMember pattern | Low | ~0 | Documentation |
| 9 | Remove mRealm transaction check from VoicesFragment | Medium | ~10 | 1 file |
| 10 | Document adapter state management pattern | Low | ~0 | Documentation |

---

## Task Categories

### 🔴 Data Layer Violations (Critical)
- **Task 1**: Remove mRealm exposure (affects 20+ fragments)
- **Task 2**: Move health query to repository
- **Task 9**: Remove transaction check from UI

### 🟡 Repository Enhancements (Important)
- **Task 5**: Simplify download suggestion flow
- **Task 6**: Add save method to HealthRepository
- **Task 8**: Verify team membership pattern

### 🟢 Code Quality & Patterns (Nice to Have)
- **Task 3**: Remove deprecated property
- **Task 4**: Remove redundant dispatcher switches (53 instances)
- **Task 7**: Fix dispatcher types (10 instances)
- **Task 10**: Document adapter patterns

---

## Implementation Priority

### Week 1 (High Priority)
1. Task 1: BaseResourceFragment mRealm (protects boundaries)
2. Task 6: Health save operations (completes HealthRepository)
3. Task 2: Health examination query (cleans up direct access)

### Week 2 (Medium Priority)
4. Task 5: Download suggestions (simplifies BaseResourceFragment)
5. Task 9: VoicesFragment transaction check (removes smell)
6. Task 3: Deprecated userModel (prevents new violations)

### Week 3 (Low Priority - Quality)
7. Task 4: Dispatcher.Main cleanup (5 fragments)
8. Task 7: Dispatcher.Default fixes (3 files)
9. Task 8: Verify team membership (documentation)
10. Task 10: Document adapter pattern (documentation)

---

## Quick Stats

- **Total PRs Estimated**: 10 (one per task)
- **Code Changes**: ~150 lines total across all tasks
- **Files Modified**: ~15 unique files
- **Documentation Tasks**: 2
- **Audit Tasks**: 2
- **Code Tasks**: 6

---

## Dependencies

- No inter-task dependencies
- All tasks can be implemented in parallel
- Merge order doesn't matter

---

## Success Metrics

✅ Zero direct Realm access in UI layer
✅ All data queries go through repositories
✅ Consistent dispatcher usage patterns
✅ No deprecated patterns in active use
✅ Documented best practices for adapters

---

## Getting Started

1. Read full task details in `REFACTORING_TASKS.md`
2. Pick a task based on priority and complexity preference
3. Follow the task stub steps for implementation
4. Create PR with reference to task number
5. Repeat!

---

**Note**: Each task is designed to be completed in < 2 hours and reviewed in < 30 minutes.
