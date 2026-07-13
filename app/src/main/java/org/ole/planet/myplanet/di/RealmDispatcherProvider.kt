package org.ole.planet.myplanet.di

import android.os.Handler
import android.os.HandlerThread
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher

@Singleton
class RealmDispatcherProvider @Inject constructor() : CoroutineDispatcher() {
    private var handlerThread: HandlerThread? = null
    private var _dispatcher: CoroutineDispatcher? = null

    private val dispatcher: CoroutineDispatcher
        @Synchronized get() {
            _dispatcher?.let { return it }
            val thread = HandlerThread("RealmQueryThread").also {
                it.start()
                handlerThread = it
            }
            val newDispatcher = Handler(thread.looper).asCoroutineDispatcher()
            _dispatcher = newDispatcher
            return newDispatcher
        }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatch(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return dispatcher.isDispatchNeeded(context)
    }

    @Synchronized
    fun shutdown() {
        handlerThread?.quitSafely()
        handlerThread = null
        _dispatcher = null
    }
}
