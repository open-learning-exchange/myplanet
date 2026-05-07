1. **Fix outer iteration limits and inner chunking in `ResourcesRepository` and `SyncManager`.**
   - In `SyncManager`, since we want the repository to handle bulk insertions logic and not be constrained by caller batching logic, we can remove the batching for `"resources"` inside `processShelfDataOptimizedSync`. Wait, `processShelfDataOptimizedSync` has a `for` loop that steps by `batchSize`. We can't trivially extract `"resources"` from the outer batch logic without refactoring the whole method or keeping it separate.
   - Alternatively, if we remove `documents.chunked(chunkSize)` in `batchInsertMyLibrary`, it correctly represents "batch insert this list inside a single transaction".

2. **Complete the refactor for `meetups`, `courses`, and `teams`.**
   - Move `insertMyCourses` to `CoursesRepository` (`batchInsertMyCourses`).
   - Move `insert` for meetups to `EventsRepository` (`batchInsertMeetups`).
   - Update `insertMyTeam` in `TeamsRepository` or add `batchInsertMyTeam`.
