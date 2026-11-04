package com.shopmanager.app.data.learning

import android.content.Context
import com.shopmanager.app.data.materials.MissingFinderStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

object LearningHub {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "learning-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    val io: CoroutineDispatcher = executor.asCoroutineDispatcher()

    val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + io + CoroutineExceptionHandler { _, _ -> })

    @Volatile private var store: MissingFinderStore? = null
    @Volatile private var usage: UsageLearner? = null

    fun store(context: Context): MissingFinderStore =
        store ?: synchronized(this) {
            store ?: MissingFinderStore(context.applicationContext).also { store = it }
        }

    fun usage(context: Context): UsageLearner =
        usage ?: synchronized(this) {
            usage ?: UsageLearner(context.applicationContext).also { usage = it }
        }

    fun flush() {
        scope.launch {
            runCatching { store?.flushNow() }
            runCatching { usage?.flushNow() }
        }
    }
}

class DebouncedTask(private val delayMs: Long, private val action: () -> Unit) {
    private val lock = Any()
    private var job: Job? = null

    fun trigger() {
        synchronized(lock) {
            job?.cancel()
            job = LearningHub.scope.launch {
                delay(delayMs)
                runCatching(action)
            }
        }
    }

    fun flushNow() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
        runCatching(action)
    }
}
