package kt.fluxo.test.compare.fluxo

import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kt.fluxo.core.FluxoIntentS
import kt.fluxo.core.Store
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.closeAndWait
import kt.fluxo.core.container
import kt.fluxo.core.internal.Closeable
import kt.fluxo.core.store
import kt.fluxo.test.compare.CommonBenchmark.consumeCommon
import kt.fluxo.test.compare.CommonBenchmark.launchCommon
import kt.fluxo.test.compare.IntentIncrement

internal object FluxoBenchmark {

    fun mvvmIntent(): Int {
        val dispatcher = newSingleThreadContext(FluxoBenchmark::mvvmIntent.name)
        val container = container(0) {
            coroutineContext = dispatcher
            debugChecks = false
            lazy = false
        }
        val intent: FluxoIntentS<Int> = { updateState { it + 1 } }
        return container.consumeFluxo(intent, dispatcher)
    }

    fun mviReducer(): Int {
        val dispatcher = newSingleThreadContext(FluxoBenchmark::mviReducer.name)
        val store = store<IntentIncrement, Int>(
            initialState = 0,
            reducer = {
                when (it) {
                    IntentIncrement.Increment -> this + 1
                }
            },
        ) {
            coroutineContext = dispatcher
            debugChecks = false
            lazy = false
        }
        return store.consumeFluxo(IntentIncrement.Increment, dispatcher)
    }

    fun mviHandler(): Int {
        val dispatcher = newSingleThreadContext(FluxoBenchmark::mviHandler.name)
        val store = store<IntentIncrement, Int>(
            initialState = 0,
            handler = { intent ->
                when (intent) {
                    IntentIncrement.Increment -> updateState { it + 1 }
                }
            },
        ) {
            coroutineContext = dispatcher
            debugChecks = false
            lazy = false
        }
        return store.consumeFluxo(IntentIncrement.Increment, dispatcher)
    }


    private fun <I> Store<I, Int>.consumeFluxo(intent: I, dispatcher: Closeable): Int {
        runBlocking {
            val launchDef = launchCommon(intent) { send(it) }

            consumeCommon(launchDef)

            @OptIn(ExperimentalFluxoApi::class)
            closeAndWait()
        }
        dispatcher.close()
        return value
    }
}
