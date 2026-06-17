package org.ole.planet.myplanet.di

import android.os.Handler
import android.os.HandlerThread
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher

import kotlin.coroutines.CoroutineContext

@Singleton
class RealmDispatcherProvider @Inject constructor() : CoroutineDispatcher() {
    private var handlerThread: HandlerThread? = null
    private var _dispatcher: CoroutineDispatcher? = null

    private val dispatcher: CoroutineDispatcher
        get() = _dispatcher ?: throw IllegalStateException("RealmDispatcherProvider not started")

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatch(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return _dispatcher?.isDispatchNeeded(context) ?: true
    }

    fun start() {
        if (handlerThread == null) {
            handlerThread = HandlerThread("RealmQueryThread").also { it.start() }
            _dispatcher = Handler(handlerThread!!.looper).asCoroutineDispatcher()
        }
    }

    fun shutdown() {
        handlerThread?.quitSafely()
        handlerThread = null
        _dispatcher = null
    }
}
