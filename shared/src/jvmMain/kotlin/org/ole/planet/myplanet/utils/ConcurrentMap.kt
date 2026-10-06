package org.ole.planet.myplanet.utils

import java.util.concurrent.ConcurrentHashMap

actual fun <K : Any, V : Any> concurrentMutableMap(): MutableMap<K, V> = ConcurrentHashMap()
