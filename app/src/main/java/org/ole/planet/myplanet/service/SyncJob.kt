package org.ole.planet.myplanet.service

/**
 * Represents a single sync operation which can be executed
 * as part of the overall synchronization process.
 */
data class SyncJob(val name: String, val task: suspend () -> Unit)

