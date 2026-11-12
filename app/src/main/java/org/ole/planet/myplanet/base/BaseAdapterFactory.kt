package org.ole.planet.myplanet.base

import android.content.Context
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.ui.courses.CoursesAdapter
import org.ole.planet.myplanet.ui.resources.ResourcesAdapter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.ListViewMode

interface BaseAdapterFactory {
    fun createResourcesAdapter(
        context: Context,
        isGuest: Boolean,
        openedResourceIds: Set<String>,
        currentUserName: String? = null,
        viewMode: ListViewMode = ListViewMode.GRID,
        dispatcherProvider: DispatcherProvider,
        onEditClick: ((ResourceListModel) -> Unit)? = null
    ): ResourcesAdapter

    fun createCoursesAdapter(
        context: Context,
        isGuest: Boolean,
        isMyCourseLib: Boolean = false,
        viewMode: ListViewMode = ListViewMode.GRID
    ): CoursesAdapter
}

class DefaultBaseAdapterFactory : BaseAdapterFactory {
    override fun createResourcesAdapter(
        context: Context,
        isGuest: Boolean,
        openedResourceIds: Set<String>,
        currentUserName: String?,
        viewMode: ListViewMode,
        dispatcherProvider: DispatcherProvider,
        onEditClick: ((ResourceListModel) -> Unit)?
    ): ResourcesAdapter {
        return ResourcesAdapter(
            context = context,
            isGuest = isGuest,
            openedResourceIds = openedResourceIds,
            currentUserName = currentUserName,
            viewMode = viewMode,
            dispatcherProvider = dispatcherProvider,
            onEditClick = onEditClick
        )
    }

    override fun createCoursesAdapter(
        context: Context,
        isGuest: Boolean,
        isMyCourseLib: Boolean,
        viewMode: ListViewMode
    ): CoursesAdapter {
        return CoursesAdapter(
            context = context,
            isGuest = isGuest,
            isMyCourseLib = isMyCourseLib,
            viewMode = viewMode
        )
    }
}
