1. **Remove `showDownloadDialog`, `addToLibrary`, `addAllToLibrary`, `createListView`** from `BaseResourceFragment`.
2. **Remove `removeFromShelf`** from `BaseResourceFragment`.
3. **Remove `getLibraries`** from `BaseResourceFragment` companion object.
4. **Remove `receiver` (ACTION_NETWORK_CHANGED) and its logic** from `BaseResourceFragment`. This means removing `"ACTION_NETWORK_CHANGED" -> receiver.onReceive(requireContext(), intent)` from `registerReceiver()`.
5. **Move the logic to a new object `DownloadDialogHelper`** in `org.ole.planet.myplanet.utils` or `org.ole.planet.myplanet.base`. Wait, since `showDownloadDialog` needs `Fragment` extension methods (like `lifecycleScope`, `isAdded`, `activity`), maybe I can just define it as a top-level extension function on `Fragment`? Or better, an extension function on `BaseResourceFragment`? If it's an extension function on `BaseResourceFragment`, it will still have `RealmMyLibrary` references in the file, but NOT in `BaseResourceFragment` itself. Wait, the prompt says "Clean up the RealmMyLibrary references from BaseResourceFragment." If we put the extension function in `BaseResourceFragment.kt`, the references are still in the file. But we can put it in a new file `DownloadDialogExtensions.kt`.

Let's see: `DownloadDialog` needs:
- `configurationsRepository`
- `resourcesRepository`
- `profileDbHandler`
- `trackDownloadUrls()` (from `BaseResourceFragment`)
- `showProgressDialog()` (from `BaseResourceFragment`)
- `showNotConnectedToast()` (from `BaseResourceFragment`)

So if I make an extension function on `BaseResourceFragment`:
```kotlin
fun BaseResourceFragment.showDownloadDialog(dbMyLibrary: List<RealmMyLibrary?>) { ... }
```
But wait, we want to clean up `RealmMyLibrary` references from `BaseResourceFragment` entirely. We can make `showDownloadDialog` an extension on `BaseResourceFragment` but put it in a separate file, e.g., `DownloadDialogExtensions.kt` in `org.ole.planet.myplanet.base`.
Wait, the prompt says: "Move it to the specific fragments that actually require it (e.g., ResourcesFragment)".
If `ResourcesFragment` is the main one that requires it, but `BaseDashboardFragment`, `BaseRecyclerFragment`, `BaseContainerFragment` also require it... `ResourcesFragment` inherits from `BaseRecyclerFragment`.
Ah! If we move `showDownloadDialog` down to `BaseRecyclerFragment`, and also to `BaseDashboardFragment` (or `BaseContainerFragment`), it's completely out of `BaseResourceFragment`.
Actually, does `BaseContainerFragment` need it? `BaseContainerFragment` has `setResourceButton` which calls `showDownloadDialog`.
Does `BaseDashboardFragment` need it? It has `showResourceDownloadDialog` which calls `showDownloadDialog(libraryList)`, and `showUserResourceDialog` which calls `showDownloadDialog`.
Does `BaseRecyclerFragment` need it? It calls it in `onViewCreated`.

So if I just put it in an interface `LibraryDownloader` and have `BaseContainerFragment` and `BaseRecyclerParentFragment` implement it?
Kotlin Interfaces can have default method bodies!
```kotlin
interface LibraryDownloader {
    fun showDownloadDialog(dbMyLibrary: List<RealmMyLibrary?>)
    //...
}
```
But wait, it needs access to `fragmentActivity`, `lifecycleScope`, `resourcesRepository`, etc. These are properties of `BaseResourceFragment`. If `LibraryDownloader` is implemented by `BaseContainerFragment` (which extends `BaseResourceFragment`), it can access them if we cast `this` to `BaseResourceFragment` or just define properties in the interface.

Actually, the easiest way is to put `showDownloadDialog`, `addToLibrary`, `addAllToLibrary`, `createListView` into `BaseContainerFragment` and `BaseRecyclerParentFragment`. Yes, it's a bit of duplication, but it fulfills "Move it to the specific fragments that actually require it". Or even better, just define a helper class `DownloadDialogHelper` that both use.

Let's create `DownloadDialogHelper.kt`:
```kotlin
package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.ui.components.CheckboxListView

object DownloadDialogHelper {
    fun showDownloadDialog(
        fragment: BaseResourceFragment,
        dbMyLibrary: List<RealmMyLibrary?>,
        resourcesRepository: ResourcesRepository,
        configurationsRepository: ConfigurationsRepository,
        profileDbHandler: UserSessionManager,
        onStartDownload: (List<String>) -> Unit
    ) {
        if (!fragment.isAdded) return
        val librariesForDialog = dbMyLibrary
        if (librariesForDialog.isEmpty()) return

        fragment.activity?.let { fragmentActivity ->
            val inflater = fragmentActivity.layoutInflater
            val rootView = fragmentActivity.findViewById<ViewGroup>(android.R.id.content)
            val convertView = inflater.inflate(R.layout.my_library_alertdialog, rootView, false)

            var lv: CheckboxListView? = null
            var downloadSuggestionDialog: AlertDialog? = null

            val alertDialogBuilder = AlertDialog.Builder(fragmentActivity, R.style.AlertDialogTheme)
            alertDialogBuilder.setView(convertView)
                .setTitle(R.string.download_suggestion)
                .setPositiveButton(R.string.download_selected) { _: DialogInterface?, _: Int ->
                    fragment.lifecycleScope.launch {
                        if (configurationsRepository.checkServerAvailability()) {
                            lv?.selectedItemsList?.let { selectedItems ->
                                val userId = profileDbHandler.getUserModel()?.id
                                if (userId != null) {
                                    val resourceIds = selectedItems.mapNotNull { index ->
                                        librariesForDialog.getOrNull(index)?.resourceId
                                    }
                                    resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
                                    Utilities.toast(fragmentActivity, fragment.getString(R.string.added_to_my_library))
                                }

                                val selectedLibraries = selectedItems.mapNotNull { index -> librariesForDialog.getOrNull(index) }
                                val filtered = selectedLibraries.filterNotNull()
                                if (resourcesRepository.downloadResources(filtered)) {
                                    onStartDownload(filtered.mapNotNull { lib -> lib.resourceRemoteAddress })
                                }
                            }
                        } else {
                            fragment.showNotConnectedToast()
                        }
                    }
                }
                .setNeutralButton(R.string.download_all) { _: DialogInterface?, _: Int ->
                    fragment.lifecycleScope.launch {
                        if (configurationsRepository.checkServerAvailability()) {
                            val userId = profileDbHandler.getUserModel()?.id
                            if (userId != null) {
                                val validLibraryItems = librariesForDialog.filterNotNull()
                                resourcesRepository.addAllResourcesToUserLibrary(validLibraryItems, userId)
                                Utilities.toast(fragmentActivity, fragment.getString(R.string.added_to_my_library))
                            }
                            val filtered = librariesForDialog.filterNotNull()
                            if (resourcesRepository.downloadResources(filtered)) {
                                onStartDownload(filtered.mapNotNull { lib -> lib.resourceRemoteAddress })
                            }
                        } else {
                            fragment.showNotConnectedToast()
                        }
                    }
                }.setNegativeButton(R.string.txt_cancel, null)

            downloadSuggestionDialog = alertDialogBuilder.create()

            // createListView logic
            lv = convertView?.findViewById(R.id.alertDialog_listView)
            val names = librariesForDialog.map { it?.title }
            val adapter = ArrayAdapter(fragmentActivity.baseContext, R.layout.rowlayout, R.id.checkBoxRowLayout, names)
            lv?.choiceMode = ListView.CHOICE_MODE_MULTIPLE
            lv?.setCheckChangeListener {
                downloadSuggestionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
            }
            lv?.adapter = adapter

            downloadSuggestionDialog.show()
            downloadSuggestionDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
        }
    }
}
```

Wait, `DownloadDialogHelper` doesn't need to be in `BaseResourceFragment`. It can be in `org.ole.planet.myplanet.base.DownloadDialogHelper.kt`!
And `removeFromShelf` can just be moved into `BaseRecyclerFragment.deleteSelected`. `BaseRecyclerFragment` already overrides `deleteSelected`, we can just implement `removeFromShelf` logic there directly!
Let's see `deleteSelected(deleteProgress: Boolean)` in `BaseRecyclerFragment.kt`:
```kotlin
    open fun deleteSelected(deleteProgress: Boolean) {
        selectedItems?.forEach { item ->
            try {
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                val `object` = item as RealmObject
                deleteCourseProgress(deleteProgress, `object`)
                // move removeFromShelf logic here:
                viewLifecycleOwner.lifecycleScope.launch {
                    val userId = profileDbHandler.getUserModel()?.id
                    if (!userId.isNullOrEmpty()) {
                        if (`object` is RealmMyLibrary) {
                            val resourceId = `object`.resourceId
                            if (resourceId != null) {
                                resourcesRepository.removeResourceFromShelf(resourceId, userId)
                                Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
                            }
                        } else if (`object` is RealmMyCourse) {
                            val courseId = `object`.courseId
                            if (courseId != null) {
                                coursesRepository.removeCourseFromShelf(courseId, userId)
                                Utilities.toast(activity, getString(R.string.removed_from_mycourse))
                            }
                        }
                    }
                }
                if (mRealm.isInTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
            }
        }
        selectedItems?.clear()
    }
```
Wait, `BaseRecyclerFragment` inherits from `BaseRecyclerParentFragment` which inherits from `BaseResourceFragment`. `BaseRecyclerFragment` has `deleteSelected`, but `ResourcesFragment` (which inherits from `BaseRecyclerFragment`) ALSO overrides `deleteSelected`.
Let's check `ResourcesFragment`:
```kotlin
    override fun deleteSelected(deleteProgress: Boolean) {
        val userId = userModel?.id
        val itemsToDelete = selectedItems?.mapNotNull { it?.resourceId } ?: emptyList()

        if (userId != null && itemsToDelete.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                itemsToDelete.forEach { resourceId ->
                    resourcesRepository.removeResourceFromShelf(resourceId, userId)
                }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
                    refreshResourcesData()
                    selectedItems?.clear()
                    changeButtonStatus()
                    hideButton()
                }
            }
        }
    }
```
Yes, `ResourcesFragment` overrides it anyway! So `removeFromShelf` isn't even used there. It's only used in `BaseRecyclerFragment` (and maybe others like `CoursesFragment`). So moving `removeFromShelf` logic into `BaseRecyclerFragment` is perfect!

And what about `ACTION_NETWORK_CHANGED` receiver?
`BaseResourceFragment` has:
```kotlin
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pendingResult = goAsync()
            this@BaseResourceFragment.lifecycleScope.launch {
                try {
                    val list = resourcesRepository.getDownloadSuggestionList()
                    showDownloadDialog(list)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
```
If we remove `receiver` from `BaseResourceFragment`, who will listen to network changes and suggest downloads?
Is this actually a requirement? "Move showDownloadDialog logic out of BaseResourceFragment... to the specific fragments that actually require it".
If `ACTION_NETWORK_CHANGED` suggesting downloads is needed in `Dashboard` and `Resources`, we could move this receiver logic to `BaseDashboardFragment` and `BaseRecyclerParentFragment`.

Actually, `ResourcesFragment` overrides `shouldAutoRefresh` and handles its own things.
If we create `DownloadDialogHelper`, we can provide a `showDownloadDialog(fragment: BaseResourceFragment, dbMyLibrary: List<RealmMyLibrary?>)` extension.
Then in `BaseDashboardFragment.kt`, `BaseRecyclerFragment.kt`, `BaseContainerFragment.kt`, replace `showDownloadDialog(list)` with `DownloadDialogHelper.showDownloadDialog(this, list, resourcesRepository, configurationsRepository, profileDbHandler) { urls -> trackDownloadUrls(urls); showProgressDialog() }`.
Wait, if we define `fun BaseResourceFragment.showDownloadDialog(dbMyLibrary: List<RealmMyLibrary?>)` in `DownloadDialogHelper.kt`, we can just call `showDownloadDialog(list)` anywhere! It works transparently and removes `RealmMyLibrary` imports from `BaseResourceFragment.kt`.

Let's do that!
