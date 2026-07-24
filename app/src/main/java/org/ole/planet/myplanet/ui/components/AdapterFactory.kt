package org.ole.planet.myplanet.ui.components

import android.content.Context
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.ui.courses.CoursesAdapter
import org.ole.planet.myplanet.ui.resources.ResourcesAdapter

interface AdapterFactory {
    fun createResourcesAdapter(
        context: Context,
        isGuest: Boolean,
        openedResourceIds: Set<String>,
        currentUserName: String? = null,
        onEditClick: ((ResourceListModel) -> Unit)? = null
    ): ResourcesAdapter

    fun createCoursesAdapter(
        context: Context,
        map: HashMap<String?, JsonObject>,
        isGuest: Boolean,
        isMyCourseLib: Boolean = false
    ): CoursesAdapter
}

class DefaultAdapterFactory : AdapterFactory {
    override fun createResourcesAdapter(
        context: Context,
        isGuest: Boolean,
        openedResourceIds: Set<String>,
        currentUserName: String?,
        onEditClick: ((ResourceListModel) -> Unit)?
    ): ResourcesAdapter {
        return ResourcesAdapter(
            context = context,
            isGuest = isGuest,
            openedResourceIds = openedResourceIds,
            currentUserName = currentUserName,
            onEditClick = onEditClick
        )
    }

    override fun createCoursesAdapter(
        context: Context,
        map: HashMap<String?, JsonObject>,
        isGuest: Boolean,
        isMyCourseLib: Boolean
    ): CoursesAdapter {
        return CoursesAdapter(
            context = context,
            map = map,
            isGuest = isGuest,
            isMyCourseLib = isMyCourseLib
        )
    }
}
