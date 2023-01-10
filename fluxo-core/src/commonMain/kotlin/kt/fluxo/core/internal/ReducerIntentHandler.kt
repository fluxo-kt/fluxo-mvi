package kt.fluxo.core.internal

import kt.fluxo.core.IntentHandler
import kt.fluxo.core.Reducer
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.dsl.StoreScope

/**
 * Wraps a [Reducer], making an [IntentHandler] from it.
 */
@PublishedApi
@InternalFluxoApi
internal class ReducerIntentHandler<Intent, State, SideEffect : Any>(
    private val reducer: Reducer<Intent, State>,
) : IntentHandler<Intent, State, SideEffect> {

    override suspend fun StoreScope<Intent, State, SideEffect>.handleIntent(intent: Intent) {
        updateState { state ->
            reducer(state, intent)
        }
    }
}
