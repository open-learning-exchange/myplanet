1. **Fix Dead Code in `SettingsActivity.kt`**
   - Remove unused `dispatcherProvider` injection.
   - Remove unused imports `kotlinx.coroutines.withContext` and `org.ole.planet.myplanet.utils.DownloadUtils.downloadAllFiles`.
   - Remove cosmetic noise (double blank lines).

2. **Refactor Event Bus in `SettingsViewModel.kt`**
   - Replace `MutableSharedFlow` with `Channel` for one-shot events (`clearDataEvent`, `clearRetryQueueEvent`, `retryQueueDetailsEvent`, `downloadCompleteEvent`). Use `receiveAsFlow()` to expose them.
   - This ensures events are buffered and not dropped if emitted while the UI is not in the `STARTED` state.

3. **Fix Behavioral Regression: Stale `isProcessing` snapshot**
   - Wait, `isProcessing` is part of `retryQueueDetailsEvent`. Instead of using a stale snapshot inside the button click, let's inject `retryQueue` back or trigger a ViewModel function `triggerRetryNow()` which checks `retryQueue.isCurrentlyProcessing()` and either triggers `RetryQueueWorker.triggerImmediateRetry` or emits a "retry already in progress" toast event. Actually, since the event is handled in the UI, checking `retryQueue.isCurrentlyProcessing()` requires the `retryQueue` instance. We can expose an `isCurrentlyProcessing()` method on the ViewModel and use it inside the click listener.

4. **Consistency in `viewModelScope.launch`**
   - Ensure all `launch` calls explicitly use `dispatcherProvider.io` for consistency with `clearAllData`.
