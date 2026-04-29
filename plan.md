1. **Create `PhotoUploadDelegate`:** Extract the `uploadSubmitPhotos` logic from `UploadManager` into a new class `PhotoUploadDelegate`. It should receive its dependencies (`submissionsRepository`, `apiInterface`, `dispatcherProvider`, `FileUploader` instance if needed) via its constructor. We'll inject `PhotoUploadDelegate` into `UploadManager`.
2. **Refactor `UploadManager`:** Modify `UploadManager` to use `PhotoUploadDelegate` for uploading submitted photos (`uploadSubmitPhotos`). Remove the existing `uploadSubmitPhotos` code from `UploadManager`.
3. **Verify:** Run the `UploadManagerTest` and create `PhotoUploadDelegateTest` if needed to ensure no behavior change.
4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
5. **Submit PR.**
