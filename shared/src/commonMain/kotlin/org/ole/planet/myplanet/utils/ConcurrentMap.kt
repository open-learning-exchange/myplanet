package org.ole.planet.myplanet.utils

/** A thread-safe mutable map: `java.util.concurrent.ConcurrentHashMap` on the JVM targets. Null keys and values are not allowed. */
expect fun <K : Any, V : Any> concurrentMutableMap(): MutableMap<K, V>
