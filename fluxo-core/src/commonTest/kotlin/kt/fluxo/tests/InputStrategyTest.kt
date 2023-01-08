package kt.fluxo.tests

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kt.fluxo.core.FluxoRuntimeException
import kt.fluxo.core.InputStrategy
import kt.fluxo.core.InputStrategy.InBox.Fifo
import kt.fluxo.core.InputStrategy.InBox.Lifo
import kt.fluxo.core.InputStrategy.InBox.Parallel
import kt.fluxo.core.closeAndWait
import kt.fluxo.core.container
import kt.fluxo.core.dsl.InputStrategyScope
import kt.fluxo.core.dsl.StoreScope
import kt.fluxo.core.intent
import kt.fluxo.core.store
import kt.fluxo.test.CoroutineScopeAwareTest
import kt.fluxo.test.IgnoreNativeAndJs
import kt.fluxo.test.runUnitTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("SuspendFunctionOnCoroutineScope")
internal class InputStrategyTest : CoroutineScopeAwareTest() {

    private companion object {
        private const val NUMBER_OF_ITEMS = 1000
    }

    private suspend fun CoroutineScope.input_strategy_test(
        strategy: InputStrategy,
        equal: Boolean,
        generic: Boolean = false,
    ): List<Int> {
        val ints = Array(NUMBER_OF_ITEMS) { it }
        val resLock = Mutex()
        val finishLock = Mutex(locked = true)

        val results = ArrayList<Int>()
        val store = this.store(ints.first(), handler = { intent: Int ->
            if (!generic) delay(timeMillis = 1)
            resLock.withLock { results.add(intent) }
            updateState { intent }
            if (intent == ints.last()) {
                if (generic) delay(timeMillis = 10)
                finishLock.unlock()
            }
        }) { inputStrategy = strategy }
        ints.forEach(store::send)

        // Wait till the processing finish
        finishLock.lock()
        if (generic) {
            // TODO: Store.awaitIdle()
            while (results.size < NUMBER_OF_ITEMS) {
                delay(timeMillis = 10)
            }
        }
        store.closeAndWait()

        if (equal) {
            assertContentEquals(ints.asList(), results)
        }

        return results
    }


    @Test
    fun fifo_test_scope() = runUnitTest(dispatchTimeoutMs = 5_000) { fifo_test() }

    @Test
    fun fifo_background_scope() = runUnitTest(dispatchTimeoutMs = 3_000) { backgroundScope.fifo_test() }

    @Test
    @IgnoreNativeAndJs
    fun fifo_generic_scope() = runUnitTest(dispatchTimeoutMs = 20_000) { scope.fifo_test() }

    private suspend fun CoroutineScope.fifo_test() = input_strategy_test(strategy = Fifo, equal = true)


    @Test
    fun lifo_test_scope() = runUnitTest { lifo_test() }

    @Test
    fun lifo_background_scope() = runUnitTest { backgroundScope.lifo_test() }

    @Test
    fun lifo_generic_scope() = runUnitTest { scope.lifo_test() }

    private suspend fun CoroutineScope.lifo_test() {
        val results = input_strategy_test(strategy = Lifo, equal = false)
        assertEquals(NUMBER_OF_ITEMS - 1, results.last())
        assertTrue(results.size < NUMBER_OF_ITEMS, "Expected to have cancelled intents with Lifo strategy")
    }


    @Test
    fun parallel_test_scope() = runUnitTest { parallel_test() }

    @Test
    fun parallel_background_scope() = runUnitTest { backgroundScope.parallel_test() }

    @Test
    fun parallel_generic_scope() = runUnitTest {
        scope.parallel_test(generic = true)
    }

    private suspend fun CoroutineScope.parallel_test(generic: Boolean = false) {
        val results = input_strategy_test(strategy = Parallel, equal = false, generic = generic)
        assertEquals(NUMBER_OF_ITEMS, results.size)
        assertEquals(NUMBER_OF_ITEMS, results.toHashSet().size)
    }


    @Test
    fun custom_test_scope() = runUnitTest { custom_test() }

    @Test
    fun custom_background_scope() = runUnitTest { backgroundScope.custom_test() }

    @Test
    fun custom_generic_scope() = runUnitTest { scope.custom_test() }

    private suspend fun CoroutineScope.custom_test() {
        val results = input_strategy_test(strategy = CustomInputStrategy(), equal = false)
        assertEquals(NUMBER_OF_ITEMS - 1, results.last())
        assertTrue(results.size < NUMBER_OF_ITEMS, "Expected to have cancelled intents with Custom strategy")
    }

    /** Useless strategy, just for testing */
    private class CustomInputStrategy : InputStrategy() {
        override fun <Request> createQueue(onUndeliveredElement: ((Request) -> Unit)?): Channel<Request> =
            Channel(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST, onUndeliveredElement = onUndeliveredElement)

        override suspend fun <Request> (InputStrategyScope<Request>).processRequests(queue: Flow<Request>) =
            queue.collectLatest(this)
    }


    @Test
    fun input_guardian_correctness() = runUnitTest {
        val intent: suspend StoreScope<*, Int, *>.() -> Unit = intent@{
            assertEquals(0, state)
            // Parallel input strategy requires that inputs only access or update the state at most once.
            assertFailsWith<FluxoRuntimeException> { state }
            sideJob(key = "${Random.nextLong()}") {
                // intent scope has already been closed.
                assertFailsWith<FluxoRuntimeException> { this@intent.noOp() }
                updateState { it + 1 }
            }
            // sideJob already declared
            assertFailsWith<FluxoRuntimeException> { noOp() }
        }
        val container = backgroundScope.container(0) {
            inputStrategy = Parallel
            debugChecks = true
            bootstrapper = intent
        }
        container.intent(intent)
        container.stateFlow.first { it == 2 }
        container.close()
    }
}
