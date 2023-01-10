package kt.fluxo.test.compare

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kt.fluxo.core.internal.Closeable

internal object CommonBenchmark {

    internal inline fun <I> CoroutineScope.launchCommon(intent: I, yield: Boolean = false, crossinline send: suspend (I) -> Unit) = launch {
        @Suppress("UnnecessaryVariable")
        val i = intent
        repeat(BENCHMARK_REPETITIONS) {
            send(i)
            // Yield makes everything super slow!
            if (yield) yield()
        }
    }

    internal suspend fun Flow<Int>.consumeCommon(launchDef: Job, parentJob: Job? = null, dispatcher: Closeable? = null): Int {
        val state = first { it >= BENCHMARK_REPETITIONS }

        launchDef.join()
        parentJob?.cancelAndJoin()
        @Suppress("BlockingMethodInNonBlockingContext")
        dispatcher?.close()
        return state
    }

    internal fun MutableStateFlow<Int>.getAndAdd(delta: Int): Int {
        while (true) {
            val value = value
            if (compareAndSet(value, value + delta)) {
                return value
            }
        }
    }

    internal fun MutableStateFlow<Int>.addAndGet(delta: Int): Int = getAndAdd(delta) + delta
}
