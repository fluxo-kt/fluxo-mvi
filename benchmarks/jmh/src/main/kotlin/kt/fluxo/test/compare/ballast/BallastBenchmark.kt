package kt.fluxo.test.compare.ballast

import com.copperleaf.ballast.BallastViewModelConfiguration
import com.copperleaf.ballast.EventHandler
import com.copperleaf.ballast.EventHandlerScope
import com.copperleaf.ballast.InputHandler
import com.copperleaf.ballast.InputHandlerScope
import com.copperleaf.ballast.build
import com.copperleaf.ballast.core.BasicViewModel
import com.copperleaf.ballast.core.FifoInputStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kt.fluxo.test.compare.CommonBenchmark.consumeCommon
import kt.fluxo.test.compare.CommonBenchmark.launchCommon
import kt.fluxo.test.compare.IntentIncrement

internal object BallastBenchmark {
    fun mviHandler(): Int {
        val dispatcher = newSingleThreadContext(BallastBenchmark::mviHandler.name)
        val job = SupervisorJob()
        val vm = object : BasicViewModel<IntentIncrement, Nothing, Int>(
            coroutineScope = CoroutineScope(dispatcher + job),
            eventHandler = object : EventHandler<IntentIncrement, Nothing, Int> {
                override suspend fun EventHandlerScope<IntentIncrement, Nothing, Int>.handleEvent(event: Nothing) {}
            },
            config = BallastViewModelConfiguration.Builder().apply {
                initialState = 0
                inputStrategy = FifoInputStrategy()
                inputsDispatcher = dispatcher
                eventsDispatcher = dispatcher
                sideJobsDispatcher = dispatcher
                interceptorDispatcher = dispatcher
                inputHandler = object : InputHandler<IntentIncrement, Nothing, Int> {
                    override suspend fun InputHandlerScope<IntentIncrement, Nothing, Int>.handleInput(input: IntentIncrement) {
                        when (input) {
                            IntentIncrement.Increment -> {
                                updateState { it + 1 }
                            }
                        }
                    }
                }
            }.build(),
        ) {}

        return runBlocking {
            val launchDef = launchCommon(IntentIncrement.Increment) { vm.send(it) }
            vm.observeStates().consumeCommon(launchDef, job, dispatcher)
        }
    }
}
