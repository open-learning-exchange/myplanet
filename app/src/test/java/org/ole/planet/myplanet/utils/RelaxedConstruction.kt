package org.ole.planet.myplanet.utils

import io.mockk.mockkClass
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Builds [T] through its widest constructor, passing each of [overrides] to the first unfilled
 * parameter whose type accepts it (in order, so two `Gson` overrides fill `gson` then
 * `plainGson`) and a relaxed MockK mock for everything else. Golden tests only exercise pure
 * payload builders, so most collaborators never matter.
 */
inline fun <reified T : Any> relaxedInstance(vararg overrides: Any): T = relaxedInstance(T::class.java, *overrides)

fun <T : Any> relaxedInstance(type: Class<T>, vararg overrides: Any): T {
    val constructor = type.declaredConstructors.filterNot { it.isSynthetic }.maxBy { it.parameterCount }
    constructor.isAccessible = true
    val pending = overrides.toMutableList()
    val args = constructor.parameterTypes.map { parameterType ->
        val match = pending.firstOrNull { parameterType.isInstance(it) }
        if (match != null) {
            pending.remove(match)
            match
        } else {
            mockkClass(parameterType.kotlin, relaxed = true)
        }
    }
    require(pending.isEmpty()) { "${type.simpleName} has no free constructor parameter for $pending" }
    @Suppress("UNCHECKED_CAST")
    return constructor.newInstance(*args.toTypedArray()) as T
}

/** Calls a private member function by name, unwrapping reflection's exception wrapper. */
fun Any.callPrivate(name: String, vararg args: Any?): Any? {
    val method = this::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
    method.isAccessible = true
    return try {
        method.invoke(this, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

/** Suspend twin of [callPrivate]: passes the caller's continuation as the trailing argument. */
suspend fun Any.callPrivateSuspend(name: String, vararg args: Any?): Any? {
    val method = this::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size + 1 }
    method.isAccessible = true
    return suspendCoroutineUninterceptedOrReturn { continuation ->
        try {
            method.invoke(this, *args, continuation)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
