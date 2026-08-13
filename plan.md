1. **Repository Additions for DAOs**:
   - `VoicesRepository` and `VoicesRepositoryImpl`:
     - Add `getPendingNewsLogUploads(): List<NewsLog>`
     - Add `markNewsLogUploaded(localId: String, remoteId: String, rev: String): Boolean` (returns `dao.markUploaded != 0`)
   - `ProgressRepository` and `ProgressRepositoryImpl`:
     - Add `getPendingCourseProgressUploads(): List<CourseProgress>`
     - Add `markCourseProgressUploaded(localId: String, remoteId: String, rev: String): Boolean`
   - `ActivitiesRepository` and `ActivitiesRepositoryImpl`:
     - Add `getPendingSearchActivityUploads(): List<SearchActivity>`
     - Add `markSearchActivityUploaded(localId: String, remoteId: String, rev: String): Boolean`
     - Add `getPendingResourceActivityUploads(): List<ResourceActivity>`
     - Add `getPendingResourceActivitySyncUploads(): List<ResourceActivity>`
     - Add `markResourceActivityUploaded(localId: String, remoteId: String, rev: String): Boolean`
   - `SubmissionsRepository` and `SubmissionsRepositoryImpl`:
     - (Already has `getUnuploadedPhotos(): List<Pair<String?, JsonObject>>` and `markPhotoUploaded(photoId: String?, rev: String, id: String)`) Wait, `SubmitPhotos` uses the raw object.
     - Wait, `UploadConfigs` expects `submitPhotosDao.getUnuploaded()` which returns `List<SubmitPhotos>`. So add:
       - `getPendingSubmitPhotosUploads(): List<SubmitPhotos>`
       - `markSubmitPhotosUploaded(localId: String, remoteId: String, rev: String): Boolean`

2. **ApkLogRepository Creation**:
   - Extract ApkLog-related operations from `MainApplication` to `ApkLogRepository` and `ApkLogRepositoryImpl`.
   - `ApkLogRepository.kt` should contain:
     - `getPendingApkLogs(): List<ApkLog>`
     - `markApkLogUploaded(localId: String, rev: String): Boolean`
     - `saveLogToRoom(type: String, error: String, time: String): Boolean`
     - `saveLogsToRoom(pendingLogs: List<CrashLogStore.PendingLog>): Boolean`
     - `buildApkLog(...)` doesn't strictly need to be exposed.
   - `RepositoryModule` needs to bind `ApkLogRepository` to `ApkLogRepositoryImpl`.
   - Inject dependencies into `ApkLogRepositoryImpl` (like `ApkLogDao`, `SharedPrefManager`, `UserSessionManager`).
   - `CoreDependenciesEntryPoint` will provide `apkLogRepository()` instead of `apkLogDao()`.
   - Update `MainApplication` to use `apkLogRepository` through `CoreDependenciesEntryPoint`.

3. **UploadConfigs Update**:
   - Inject `apkLogRepository`, and remove all 6 DAOs (`ApkLogDao`, `NewsLogDao`, `SearchActivityDao`, `ResourceActivityDao`, `SubmitPhotosDao`, `CourseProgressDao`).
   - Use the new repository functions in the corresponding upload configs (`CrashLog`, `NewsActivities`, `SearchActivity`, `ResourceActivities`, `ResourceActivitiesSync`, `CourseProgress`, `SubmitPhotos`).

4. **Pre-commit**: Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
