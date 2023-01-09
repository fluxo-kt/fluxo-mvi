package kt.fluxo.tests.dsl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kt.fluxo.core.dsl.ContainerHost
import kt.fluxo.core.container
import kt.fluxo.core.intent
import kt.fluxo.test.CoroutineScopeAwareTest
import kt.fluxo.test.IgnoreNativeAndJs
import kt.fluxo.test.ScopedBlockingWorkSimulator
import kt.fluxo.test.runBlocking
import kt.fluxo.test.test
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

internal class DslThreadingTest : CoroutineScopeAwareTest() {

    private val middleware = BaseDslMiddleware()

    @Test
    @IgnoreNativeAndJs
    fun blocking_intent_with_context_switch_does_not_block_the_reducer() = runBlocking {
        val action = Random.nextInt()
        val testFlowObserver = middleware.container.stateFlow.test()

        middleware.backgroundIntent()

        withTimeout(TIMEOUT) {
            middleware.intentMutex.withLock {}
        }

        middleware.reducer(action)

        testFlowObserver.awaitCount(2)
        assertContentEquals(listOf(TestState(42), TestState(action)), testFlowObserver.values)
    }

    @Test
    @IgnoreNativeAndJs
    fun suspending_intent_does_not_block_the_reducer() = runBlocking {
        val action = Random.nextInt()
        val testFlowObserver = middleware.container.stateFlow.test()

        middleware.suspendingIntent()
        withTimeout(TIMEOUT) {
            middleware.intentMutex.withLock {}
        }

        middleware.reducer(action)

        testFlowObserver.awaitCount(2)
        assertContentEquals(listOf(TestState(42), TestState(action)), testFlowObserver.values)
    }

    @Test
    @IgnoreNativeAndJs
    fun blocking_intent_without_context_switch_does_not_block_the_reducer() = runBlocking {
        val action = Random.nextInt()
        val testFlowObserver = middleware.container.stateFlow.test()

        middleware.blockingIntent()

        withTimeout(TIMEOUT) {
            middleware.intentMutex.withLock {}
        }

        middleware.reducer(action)

        testFlowObserver.awaitCount(2, 100L)
        assertContentEquals(listOf(TestState(42), TestState(action)), testFlowObserver.values)
    }

    @Test
    @IgnoreNativeAndJs
    fun blocking_reducer_does_not_block_an_intent() = runBlocking {
        val testFlowObserver = middleware.container.stateFlow.test()

        middleware.blockingReducer()
        withTimeout(TIMEOUT) {
            middleware.reducerMutex.withLock {}
        }

        middleware.simpleIntent()

        middleware.reducer(5678)

        testFlowObserver.awaitCount(2, 100L)
        assertContentEquals(listOf(TestState(42), TestState(5678)), testFlowObserver.values)
    }

    private data class TestState(val id: Int)

    @Suppress("ControlFlowWithEmptyBody", "EmptyWhileBlock", "DEPRECATION")
    private inner class BaseDslMiddleware : ContainerHost<TestState, String> {

        override val container = scope.container<TestState, String>(TestState(42)) {
            inputStrategy = Parallel
            debugChecks = false
        }

        val intentMutex = Mutex(locked = true)
        val reducerMutex = Mutex(locked = true)
        val workSimulator = ScopedBlockingWorkSimulator(scope)

        fun reducer(action: Int) = intent {
            reduce { state ->
                state.copy(id = action)
            }
        }

        fun blockingReducer() = intent {
            reduce { state ->
                reducerMutex.unlock()
                workSimulator.simulateWork()
                state.copy(id = 123)
            }
        }

        fun backgroundIntent() = intent {
            intentMutex.unlock()
            withContext(Dispatchers.Default) {
                while (currentCoroutineContext().isActive) {
                }
            }
        }

        fun blockingIntent() = intent {
            intentMutex.unlock()
            while (currentCoroutineContext().isActive) {
            }
        }

        fun suspendingIntent() = intent {
            intentMutex.unlock()
            delay(Int.MAX_VALUE.toLong())
        }

        fun simpleIntent() = intent {
            intentMutex.unlock()
        }
    }

    private companion object {
        private const val TIMEOUT = 1000L
    }
}
