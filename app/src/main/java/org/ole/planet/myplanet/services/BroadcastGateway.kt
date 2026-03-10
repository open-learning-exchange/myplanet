package org.ole.planet.myplanet.services

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastGateway @Inject constructor(
    val broadcastService: BroadcastService
)
