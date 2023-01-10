package kt.fluxo.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kt.fluxo.core.closeAndWait
import kt.fluxo.core.container
import kt.fluxo.core.dsl.ContainerHost
import kt.fluxo.core.intent
import kt.fluxo.core.repeatOnSubscription
import kt.fluxo.test.CoroutineScopeAwareTest
import kt.fluxo.test.IgnoreNative
import kt.fluxo.test.runUnitTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class RepeatOnSubscriptionTest : CoroutineScopeAwareTest() {

    private val initialState = State()

    @Test
    fun repeatOnSubscription_mechanics() = runUnitTest {
        // TestScope will disable actual delay waiting
        for (stopTimeout in longArrayOf(0, 1000)) {
            for (scope in arrayOf(this, backgroundScope)) {
                val container = scope.container(initialState) {
                    onStart {
                        repeatOnSubscription(stopTimeout = stopTimeout) {
                            updateState { it.copy(count = it.count + 1) }
                        }
                    }
                }

                assertEquals(initialState, container.value)

                val states = container.take(2).toList()
                assertContentEquals(listOf(initialState, State(1)), states)

                delay(stopTimeout)
                val states2 = container.take(2).toList()
                assertContentEquals(listOf(State(1), State(2)), states2)

                container.closeAndWait()
            }
        }
    }


    @Test
    @IgnoreNative
    fun test_does_not_hang_when_using_repeatOnSubscription() = runUnitTest {
        val host = TestMiddleware()

        assertEquals(initialState, host.container.value)

        host.callOnSubscription { 42 }

        assertEquals(initialState, host.container.value)

        val states = host.container.take(2).toList()
        assertContentEquals(listOf(initialState, State(42)), states)

        host.container.closeAndWait()
    }

    private inner class TestMiddleware : ContainerHost<State, Nothing> {
        override val container = scope.container<State, Nothing>(initialState)

        fun callOnSubscription(externalCall: suspend () -> Int) = intent {
            repeatOnSubscription { wasRestarted ->
                assertFalse(wasRestarted)
                val result = externalCall()
                updateState { State(result) }
            }
        }
    }

    private data class State(val count: Int = 0)
}
