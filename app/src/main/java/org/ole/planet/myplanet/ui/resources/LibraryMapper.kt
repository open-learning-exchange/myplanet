package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.google.gson.JsonObject
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.LibraryItem
import org.ole.planet.myplanet.utilities.TimeUtils

object LibraryMapper {
    fun map(
        libraryList: List<RealmMyLibrary>,
        ratingMap: HashMap<String?, JsonObject>?,
        tagMap: Map<String, List<RealmTag>>,
        selectedItems: List<RealmMyLibrary>,
        context: Context,
        userModel: RealmUserModel?
    ): List<LibraryItem> {
        return libraryList.map { library ->
            val resourceId = library.resourceId
            val ratingObj = ratingMap?.get(resourceId)

            // Rating logic
            val averageRatingVal: Float
            val totalRatingsVal: Int
            val displayRating: Float

            if (ratingObj != null) {
                averageRatingVal = ratingObj.get("averageRating")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: 0f
                totalRatingsVal = ratingObj.get("total")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
                val userRating = if (ratingObj.has("ratingByUser")) ratingObj["ratingByUser"].asFloat
                                 else if (ratingObj.has("userRating")) ratingObj["userRating"].asFloat
                                 else null
                displayRating = userRating ?: averageRatingVal
            } else {
                averageRatingVal = library.averageRating?.toFloatOrNull() ?: 0f
                totalRatingsVal = library.timesRated
                displayRating = averageRatingVal
            }

            val averageRatingStr = String.format(Locale.getDefault(), "%.2f", averageRatingVal)
            val timesRatedStr = context.getString(R.string.rating_count_format, totalRatingsVal)

            // Date logic
            val createdDateString = library.createdDate.let { TimeUtils.formatDate(it, "MMM dd, yyyy") }

            // Downloaded Icon
            val isOffline = library.isResourceOffline()
            val downloadedIconVisibility = if (!isOffline) View.VISIBLE else View.INVISIBLE
            val downloadedIconContentDescription = if (isOffline) context.getString(R.string.view) else context.getString(R.string.download)

            // Checkbox
            val isGuest = userModel?.isGuest() == true
            val checkboxVisible = if (!isGuest) View.VISIBLE else View.GONE
            val isSelected = selectedItems.any { it.id == library.id }
            val selectedText = context.getString(R.string.selected)
            val libraryTitle = library.title.orEmpty()
            val checkboxContentDescription = if (libraryTitle.isNotEmpty()) "$selectedText $libraryTitle" else selectedText

            // Tags
            // Use unmanaged tags if possible or just pass what we have
            val tags = tagMap[library.id] ?: emptyList()

            LibraryItem(
                id = library.id ?: "",
                resourceId = resourceId,
                originalObject = library,
                title = library.title,
                description = library.description,
                createdDate = library.createdDate,
                createdDateString = createdDateString,
                timesRated = timesRatedStr,
                averageRating = averageRatingStr,
                rating = displayRating,
                isResourceOffline = isOffline,
                tags = tags,
                downloadedIconVisibility = downloadedIconVisibility,
                downloadedIconContentDescription = downloadedIconContentDescription,
                checkboxVisible = checkboxVisible,
                checkboxChecked = isSelected,
                checkboxContentDescription = checkboxContentDescription
            )
        }
    }
}
