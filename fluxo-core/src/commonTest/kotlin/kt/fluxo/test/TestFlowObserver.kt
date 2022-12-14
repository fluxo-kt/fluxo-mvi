package kt.fluxo.test

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout


/**
 * Allows you to record all observed values of a flow for easy testing.
 *
 * @param flow The flow to observe.
 */
@Deprecated("Use Turbine tests instead")
@Suppress("DEPRECATION")
class TestFlowObserver<T>(flow: Flow<T>) {
    private val _values = atomic(emptyList<T>())
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    val values: List<T>
        get() = _values.value

    init {
        val coroutineName = scope.coroutineContext[CoroutineName]?.name
            .let { if (it != null) "$it: TestFlowObserver" else "TestFlowObserver" }
        scope.launch(CoroutineName(coroutineName)) {
            flow.collect { emission ->
                _values.getAndUpdate { it + emission }
            }
        }
    }

    /**
     * Waits until the specified condition fulfilled, or the timeout hit.
     *
     * @param timeout How long to wait for in milliseconds
     * @param condition The awaited condition
     * @param throwTimeout Throw if timed out (by default)
     */
    suspend fun awaitFor(timeout: Long = 5000L, throwTimeout: Boolean = true, condition: TestFlowObserver<T>.() -> Boolean) {
        // https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md#using-withtimeout-inside-runtest
        withContext(Dispatchers.Default) {
            try {
                withTimeout(timeout) {
                    @Suppress("UNUSED_EXPRESSION")
                    while (!condition()) {
                        delay(AWAIT_TIMEOUT_MS)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (throwTimeout) throw e
            }
        }
    }

    /**
     * Waits until the specified count of elements received, or the timeout hit.
     *
     * @param count The awaited element count.
     * @param timeout How long to wait for in milliseconds
     * @param throwTimeout Throw if timed out (by default)
     */
    suspend fun awaitCount(count: Int, timeout: Long = 5000L, throwTimeout: Boolean = true) {
        awaitFor(timeout, throwTimeout) { values.size >= count }
    }

    /**
     * Waits until the specified count of elements received, or the timeout hit.
     *
     * @param count The awaited element count.
     * @param timeout How long to wait for in milliseconds
     */
    suspend fun awaitCountSuspending(count: Int, timeout: Long = 5000L): Unit = awaitFor(timeout) { values.size == count }

    /**
     * Closes the subscription on the underlying stream.
     * No further values will be received after this call.
     */
    fun close(): Unit = scope.cancel()

    private companion object {
        private const val AWAIT_TIMEOUT_MS = 10L
    }
}

/**
 * Allows you to put a [Flow] into test mode.
 */
@Deprecated("Use Turbine tests instead")
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
fun <T> Flow<T>.test(): TestFlowObserver<T> = TestFlowObserver(this)
