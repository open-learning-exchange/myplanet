# Real-time Sync Usage Guide

## Overview
The real-time sync system updates your UI automatically as data is fetched during sync operations. This provides immediate feedback to users instead of waiting for the entire sync to complete.

## How to Use

### Method 1: Using RealtimeSyncMixin (Recommended)

```kotlin
class MyLibraryFragment : BaseRecyclerFragment<RealmMyLibrary>(), RealtimeSyncMixin {
    
    private lateinit var syncHelper: RealtimeSyncHelper
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup real-time sync
        syncHelper = RealtimeSyncHelper(this, this)
        syncHelper.setupRealtimeSync()
    }
    
    override fun onDestroyView() {
        syncHelper.cleanup()
        super.onDestroyView()
    }
    
    // Implement RealtimeSyncMixin
    override fun getWatchedTables(): List<String> {
        return listOf("resources", "library")
    }
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        when (table) {
            "resources", "library" -> {
                // Refresh your data source
                refreshLibraryData()
                
                // Show user feedback
                if (update.newItemsCount > 0) {
                    Utilities.toast(requireActivity(), "Found ${update.newItemsCount} new resources")
                }
            }
        }
    }
    
    override fun getRecyclerView(): RecyclerView? = recyclerView
    
    override fun shouldAutoRefresh(table: String): Boolean {
        // Only refresh if user is actively viewing the list
        return isResumed && view != null
    }
    
    private fun refreshLibraryData() {
        // Update your data and adapter
        val newData = mRealm?.where(RealmMyLibrary::class.java)?.findAll()?.toList()
        adapter.updateData(newData)
    }
}
```

### Method 2: Extending BaseRealtimeFragment

```kotlin
class MyLibraryFragment : RealtimeLibraryFragment() {
    
    override fun getUpdatedResourceList(): List<RealmMyLibrary> {
        return mRealm?.where(RealmMyLibrary::class.java)
            ?.findAll()
            ?.toList() ?: emptyList()
    }
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        super.onDataUpdated(table, update)
        
        // Custom logic when data updates
        if (update.newItemsCount > 0) {
            showNewItemsSnackbar(update.newItemsCount)
        }
    }
}
```

## Configuration

### Enable Improved Sync
```kotlin
// In your settings or preferences
settings.edit { 
    putBoolean("useImprovedSync", true) 
}
```

### Table Monitoring
The system automatically monitors these tables:
- `resources` - Resource documents
- `library` - Library/shelf data  
- `courses` - Course content
- `teams` - Team information
- `meetups` - Meetup data
- `news` - News articles
- `feedback` - User feedback
- And more...

## Benefits

1. **Immediate Updates**: UI refreshes as soon as new data arrives
2. **Progress Feedback**: Users see sync progress in real-time
3. **Better UX**: No waiting for entire sync to complete
4. **Efficient**: Uses DiffUtil for minimal UI updates
5. **Configurable**: Control refresh frequency and conditions

## Performance Notes

- Auto-refresh has a 1-second throttle to prevent excessive updates
- Uses DiffUtil for efficient RecyclerView updates
- Background sync operations don't block UI thread
- Circuit breaker prevents sync failures from affecting UI