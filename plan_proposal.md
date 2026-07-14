1. **Add `uploadPersonalResource` to `PersonalsRepository`**
   - Modify `PersonalsRepository.kt` via `replace_with_git_merge_diff`
<<<<<<< SEARCH
    suspend fun uploadPersonalDocument(personal: RealmMyPersonal): Pair<String, String>?
}
=======
    suspend fun uploadPersonalDocument(personal: RealmMyPersonal): Pair<String, String>?
    suspend fun uploadPersonalResource(personal: RealmMyPersonal): String
}
>>>>>>> REPLACE

2. **Implement `uploadPersonalResource` in `PersonalsRepositoryImpl`**
   - Modify `PersonalsRepositoryImpl.kt` via `replace_with_git_merge_diff` to add `FileUploader` behavior inside `uploadPersonalResource`.
<<<<<<< SEARCH
    override suspend fun uploadPersonalDocument(personal: RealmMyPersonal): Pair<String, String>? {
=======
    override suspend fun uploadPersonalResource(personal: RealmMyPersonal): String {
        if (!personal.isUploaded) {
            return try {
                val result = uploadPersonalDocument(personal)
                if (result != null) {
                    val (id, rev) = result
                    val uploader = org.ole.planet.myplanet.services.FileUploader(apiInterface, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
                    uploader.uploadAttachment(id, rev, personal) {}
                    "Personal resource uploaded successfully"
                } else {
                    "Failed to upload personal resource: No response"
                }
            } catch (e: Exception) {
                android.util.Log.e("PersonalsRepo", "Exception in uploadPersonalResource", e)
                "Unable to upload resource: ${e.message}"
            }
        } else {
            return "Resource already uploaded"
        }
    }

    override suspend fun uploadPersonalDocument(personal: RealmMyPersonal): Pair<String, String>? {
>>>>>>> REPLACE

3. **Remove `uploadMyPersonal` from `UploadManager`**
   - Run `replace_with_git_merge_diff` on `UploadManager.kt` to remove `uploadMyPersonal`.
<<<<<<< SEARCH
    suspend fun uploadMyPersonal(personal: RealmMyPersonal): String {
        if (!personal.isUploaded) {
            return withContext(dispatcherProvider.io) {
                try {
                    val result = personalsRepository.uploadPersonalDocument(personal)
                    if (result != null) {
                        val (id, rev) = result
                        uploadAttachment(id, rev, personal) { }
                        "Personal resource uploaded successfully"
                    } else {
                        "Failed to upload personal resource: No response"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in UploadManager", e)
                    "Unable to upload resource: ${e.message}"
                }
            }
        } else {
            return "Resource already uploaded"
        }
    }

    suspend fun uploadTeamTask() {
=======
    suspend fun uploadTeamTask() {
>>>>>>> REPLACE

4. **Add `uploadPersonal` to `PersonalsViewModel`**
   - Modify `PersonalsViewModel.kt` via `replace_with_git_merge_diff`.
<<<<<<< SEARCH
    fun deletePersonalResource(id: String) {
        viewModelScope.launch {
            personalsRepository.deletePersonalResource(id)
        }
    }
}
=======
    fun deletePersonalResource(id: String) {
        viewModelScope.launch {
            personalsRepository.deletePersonalResource(id)
        }
    }

    fun uploadPersonal(personal: RealmMyPersonal) = flow {
        emit("Please wait...")
        emit(personalsRepository.uploadPersonalResource(personal))
    }
}
>>>>>>> REPLACE

5. **Update `PersonalsFragment` to use ViewModel instead of UploadManager**
   - Modify `PersonalsFragment.kt` via `replace_with_git_merge_diff`.
<<<<<<< SEARCH
    @Inject
    lateinit var uploadManager: UploadManager

    private val viewModel: PersonalsViewModel by viewModels()
=======
    private val viewModel: PersonalsViewModel by viewModels()
>>>>>>> REPLACE
<<<<<<< SEARCH
    override fun onUpload(personal: RealmMyPersonal?) {
        pg.setText("Please wait...")
        pg.show()
        if (personal != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = uploadManager.uploadMyPersonal(personal)
                    Utilities.toast(activity, result)
                } catch (e: Exception) {
                    Utilities.toast(activity, "Upload failed: ${e.message}")
                } finally {
                    pg.dismiss()
                }
            }
        }
    }
=======
    override fun onUpload(personal: RealmMyPersonal?) {
        if (personal != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.uploadPersonal(personal).collect { result ->
                    if (result == "Please wait...") {
                        pg.setText(result)
                        pg.show()
                    } else {
                        Utilities.toast(activity, result)
                        pg.dismiss()
                    }
                }
            }
        }
    }
>>>>>>> REPLACE

6. **Create `SubmissionsSyncCoordinator`**
   - Use `run_in_bash_session` to create `SubmissionsSyncCoordinator.kt` via `cat << 'EOF' > ...` to own reachability and upload logic, containing the moved code from `UserInformationFragment.kt` and injecting necessary dependencies (`uploadManager`, `sharedPrefManager`, `serverUrlMapper`, `submissionUploadExecutor`).

7. **Verify `SubmissionsSyncCoordinator.kt` creation**
   - Run `cat ./app/src/main/java/org/ole/planet/myplanet/ui/sync/SubmissionsSyncCoordinator.kt`

8. **Refactor `UserInformationFragment`**
   - Modify `UserInformationFragment.kt` via `replace_with_git_merge_diff` replacing the `uploadManager`, `serverUrlMapper`, and `submissionUploadExecutor` injections with `SubmissionsSyncCoordinator`, replacing the `checkAvailableServer` logic with `submissionsSyncCoordinator.checkAvailableServer(syncStartTime)`, and removing the old code block.

9. **Create `SyncWorkRepository`**
   - Use `run_in_bash_session` to create `SyncWorkRepository.kt` via `cat << 'EOF' > ...` to wrap `WorkManager.enqueueUniqueWork` and return a Flow of the UI State.

10. **Verify `SyncWorkRepository.kt` creation**
    - Run `cat ./app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncWorkRepository.kt`

11. **Refactor `ProcessUserDataActivity`**
    - Modify `ProcessUserDataActivity.kt` via `replace_with_git_merge_diff` removing `WorkManager.getInstance()` raw calls in `uploadLoginData()` and `uploadBulkData()`, and replacing them with `syncWorkRepository.uploadLoginData()` returning state to dismiss `customProgressDialog`.

12. **Run tests**
    - `run_in_bash_session` to run tests.

13. **Pre-commit**
    - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
