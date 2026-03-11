package org.ole.planet.myplanet.ui.resources.facades

import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import javax.inject.Inject

open class ResourceUIFacade @Inject constructor(
    val resourcesRepository: ResourcesRepository,
    val coursesRepository: CoursesRepository,
    val submissionsRepository: SubmissionsRepository,
    val configurationsRepository: ConfigurationsRepository
)
