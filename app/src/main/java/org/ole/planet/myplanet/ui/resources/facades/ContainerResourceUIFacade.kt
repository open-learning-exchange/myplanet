package org.ole.planet.myplanet.ui.resources.facades

import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import javax.inject.Inject

open class ContainerResourceUIFacade @Inject constructor(
    resourcesRepository: ResourcesRepository,
    coursesRepository: CoursesRepository,
    submissionsRepository: SubmissionsRepository,
    configurationsRepository: ConfigurationsRepository,
    val userRepository: UserRepository
) : ResourceUIFacade(
    resourcesRepository,
    coursesRepository,
    submissionsRepository,
    configurationsRepository
)
