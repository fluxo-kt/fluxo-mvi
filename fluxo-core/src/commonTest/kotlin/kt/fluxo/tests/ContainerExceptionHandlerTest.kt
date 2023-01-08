package kt.fluxo.tests

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kt.fluxo.core.FluxoClosedException
import kt.fluxo.core.closeAndWait
import kt.fluxo.core.container
import kt.fluxo.test.getValue
import kt.fluxo.test.runUnitTest
import kt.fluxo.test.setValue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ContainerExceptionHandlerTest {

    @Test
    fun by_default_exception_breaks_the_store() = runUnitTest {
        val initState = 1
        var completionException by MutableStateFlow<Throwable?>(null)
        val job = Job()
        val container = container(initState) {
            // Override job to disable error propagation in TestScope
            scope = CoroutineScope(job)
            onError { completionException = it }
            closeOnExceptions = true
        }
        container.send {
            throw IllegalStateException()
        }
        yield()

        assertFailsWith<FluxoClosedException> {
            container.send {
                updateState { 2 }
            }
        }
        yield()

        assertEquals(false, container.isActive, "Container is active but shouldn't.")
        assertEquals(false, job.isActive, "Job is active but shouldn't.")
        assertEquals(initState, container.stateFlow.first())

        assertIs<IllegalStateException>(completionException, "completionException was not caught.")
    }

    @Test
    fun with_exception_handler_exceptions_are_caught() = runUnitTest {
        val initState = 10
        val exceptions = mutableListOf<Throwable>()
        val container = container(initState) {
            intentContext = Dispatchers.Unconfined
            onError { exceptions += it }
            closeOnExceptions = false
        }
        val newState = 20

        container.send {
            updateState {
                throw IllegalStateException()
            }
        }
        container.sendAsync {
            updateState {
                newState
            }
        }.join()

        assertEquals(newState, container.state)
        assertEquals(1, exceptions.size)
        assertIs<IllegalStateException>(exceptions.first(), "completionException was not caught.")

        container.closeAndWait()
    }

    @Test
    fun with_exception_handler_cancellation_exception_propagated_normally() =
        checkCancellationPropagation(withExceptionHandler = true)

    @Test
    fun without_exception_handler_cancellation_exception_propagated_normally() =
        checkCancellationPropagation(withExceptionHandler = false)

    private fun checkCancellationPropagation(withExceptionHandler: Boolean) = runUnitTest {
        val scopeJob = SupervisorJob()
        val containerScope = CoroutineScope(scopeJob)
        val handler = when {
            withExceptionHandler -> CoroutineExceptionHandler { _, _ -> }
            else -> null
        }
        val container = containerScope.container<Unit, Nothing>(initialState = Unit) {
            exceptionHandler = handler
            intentContext = Dispatchers.Unconfined
        }

        // required on JS to pass this test
        container.start()?.join()

        val exceptions = mutableListOf<Throwable>()
        container.send {
            try {
                flow {
                    while (true) {
                        emit(Unit)
                        withContext(Dispatchers.Default) {
                            delay(1000)
                        }
                    }
                }.collect()
            } catch (e: CancellationException) {
                exceptions.add(e)
                throw e
            }
        }
        containerScope.cancel()
        scopeJob.join()
        assertEquals(1, exceptions.size, "CancellationException was not caught")
    }

    @Test
    @Ignore
    fun with_exception_handler_test_does_not_break() {
        TODO()
    }

    @Test
    @Ignore
    fun without_exception_handler_test_does_break() {
        TODO()
    }
}
