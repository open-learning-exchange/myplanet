package org.ole.planet.myplanet.ui.health

import android.text.TextUtils
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.GsonUtils.getString
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

internal object HealthExaminationItemMapper {
    fun map(
        list: List<HealthExamination>,
        userModel: UserEntity?,
        userMap: Map<String, UserEntity>
    ): List<HealthExaminationAdapter.HealthExaminationItem> {
        val displayNameCache = mutableMapOf<String, String>()
        return list.map { item ->
            val formattedDate = formatDate(item.date, "MMM dd, yyyy")
            val encrypted = userModel?.let { user -> item.getEncryptedDataAsJson(user) }
            val createdBy = getString("createdBy", encrypted)

            val (resolvedName, isSelfExamination) = if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)) {
                val name = displayNameCache.getOrPut(createdBy) {
                    val model = userMap[createdBy]
                    model?.getFullName() ?: createdBy.substringAfter(':', "").takeIf { it.isNotBlank() } ?: createdBy
                }
                name to false
            } else {
                "" to true
            }

            HealthExaminationAdapter.HealthExaminationItem(
                examination = item,
                formattedDate = formattedDate,
                isSelfExamination = isSelfExamination,
                resolvedName = resolvedName,
                encrypted = encrypted
            )
        }
    }
}
