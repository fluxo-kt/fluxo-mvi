package kt.fluxo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kt.fluxo.core.Container
import kt.fluxo.core.container
import kt.fluxo.test.test
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
internal class ContainerThreadingTest {

    private val scope = CoroutineScope(Job())

    @AfterTest
    fun afterTest() {
        scope.cancel()
    }

    @Test
    fun container_can_process_second_action_while_the_first_suspended() = runTest {
        val container = scope.container<Int, Nothing>(Random.nextInt()) {
            inputStrategy = Parallel
        }
        val observer = container.stateFlow.test()
        val newState = Random.nextInt()

        container.send {
            delay(Long.MAX_VALUE)
        }
        container.send {
            updateState { newState }
        }

        observer.awaitCount(2)
        assertEquals(newState, container.stateFlow.value)
    }

    @Test
    fun reductions_applied_in_order_if_called_from_single_thread() = runTest {
        // This scenario meant to simulate calling only reducers from the UI thread.
        val container = scope.container<TestState, Nothing>(TestState())
        val testStateObserver = container.stateFlow.test()
        val expectedStates = mutableListOf(
            TestState(
                emptyList()
            )
        )
        for (i in 0 until ITEM_COUNT) {
            val value = (i % 3)
            expectedStates.add(
                expectedStates.last().copy(ids = expectedStates.last().ids + (value + 1))
            )

            when (value) {
                0 -> container.one()
                1 -> container.two()
                2 -> container.three()
                else -> error("misconfigured test")
            }
        }

        testStateObserver.awaitFor { values.isNotEmpty() && values.last().ids.size == ITEM_COUNT }

        assertEquals(expectedStates.last(), testStateObserver.values.last())
    }

    @Test
    fun reductions_run_in_sequence_but_in_undefined_order_when_executed_from_multiple_threads() = runTest {
        // This scenario meant to simulate calling only reducers from the UI thread.
        val container = scope.container<TestState, Nothing>(TestState()) {
            inputStrategy = Parallel
            debugChecks = false
        }
        val testStateObserver = container.stateFlow.test()
        val expectedStates = mutableListOf(
            TestState(
                emptyList()
            )
        )
        coroutineScope {
            for (i in 0 until ITEM_COUNT) {
                val value = (i % 3)
                expectedStates.add(
                    expectedStates.last().copy(ids = expectedStates.last().ids + (value + 1))
                )

                launch {
                    when (value) {
                        0 -> container.one(true)
                        1 -> container.two(true)
                        2 -> container.three(true)
                        else -> error("misconfigured test")
                    }
                }
            }
        }

        testStateObserver.awaitFor { values.isNotEmpty() && values.last().ids.size == ITEM_COUNT }

        assertEquals(ITEM_COUNT / 3, testStateObserver.values.last().ids.count { it == 1 })
        assertEquals(ITEM_COUNT / 3, testStateObserver.values.last().ids.count { it == 2 })
    }

    private data class TestState(val ids: List<Int> = emptyList())

    private fun Container<TestState, Nothing>.one(delay: Boolean = false) = send {
        if (delay) {
            delay(Random.nextLong(20))
        }
        updateState {
            it.copy(ids = state.ids + 1)
        }
    }

    private fun Container<TestState, Nothing>.two(delay: Boolean = false) = send {
        if (delay) {
            delay(Random.nextLong(20))
        }
        updateState {
            it.copy(ids = state.ids + 2)
        }
    }

    private fun Container<TestState, Nothing>.three(delay: Boolean = false) = send {
        if (delay) {
            delay(Random.nextLong(20))
        }
        updateState {
            it.copy(ids = state.ids + 3)
        }
    }

    private companion object {
        const val ITEM_COUNT = 1119
    }
}
