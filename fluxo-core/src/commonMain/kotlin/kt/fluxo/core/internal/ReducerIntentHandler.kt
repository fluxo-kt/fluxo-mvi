package kt.fluxo.core.internal

import kt.fluxo.core.IntentHandler
import kt.fluxo.core.Reducer
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.dsl.StoreScopeLegacy

/**
 * Wraps a [Reducer], making an [IntentHandler] from it.
 */
@PublishedApi
@InternalFluxoApi
internal class ReducerIntentHandler<Intent, State, out SideEffect : Any>(
    private val reducer: Reducer<Intent, State>,
) : IntentHandler<Intent, State, SideEffect> {

    override suspend fun StoreScopeLegacy<Intent, State, SideEffect>.handleIntent(intent: Intent) {
        updateState { state ->
            reducer(state, intent)
        }
    }
}
