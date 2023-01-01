package kt.fluxo.test.fluxo

import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kt.fluxo.core.Store
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.closeAndWait
import kt.fluxo.core.container
import kt.fluxo.core.dsl.StoreScope
import kt.fluxo.core.internal.Closeable
import kt.fluxo.core.store
import kt.fluxo.test.CommonBenchmark.consumeCommon
import kt.fluxo.test.CommonBenchmark.launchCommon
import kt.fluxo.test.IntentIncrement

internal object FluxoBenchmark {

    fun mvvmIntent(): Int {
        val dispatcher = newSingleThreadContext(::mvvmIntent.name)
        val container = container(0) {
            eventLoopContext = dispatcher
            debugChecks = false
            lazy = false
        }
        val intent: suspend StoreScope<Nothing, Int, Nothing>.() -> Unit = { updateState { it + 1 } }
        return container.consumeFluxo(intent, dispatcher)
    }

    fun mviReducer(): Int {
        val dispatcher = newSingleThreadContext(::mviReducer.name)
        val store = store<IntentIncrement, Int>(0, reducer = {
            when (it) {
                IntentIncrement.Increment -> this + 1
            }
        }) {
            eventLoopContext = dispatcher
            debugChecks = false
            lazy = false
        }
        return store.consumeFluxo(IntentIncrement.Increment, dispatcher)
    }

    fun mviHandler(): Int {
        val dispatcher = newSingleThreadContext(::mviHandler.name)
        val store = store<IntentIncrement, Int>(0, handler = { intent ->
            when (intent) {
                IntentIncrement.Increment -> updateState { it + 1 }
            }
        }) {
            eventLoopContext = dispatcher
            debugChecks = false
            lazy = false
        }
        return store.consumeFluxo(IntentIncrement.Increment, dispatcher)
    }


    private fun <I> Store<I, Int, *>.consumeFluxo(intent: I, dispatcher: Closeable): Int {
        runBlocking {
            val launchDef = launchCommon(intent) { send(it) }

            consumeCommon(stateFlow, launchDef)

            @OptIn(ExperimentalFluxoApi::class)
            closeAndWait()
        }
        dispatcher.close()
        return state
    }
}
