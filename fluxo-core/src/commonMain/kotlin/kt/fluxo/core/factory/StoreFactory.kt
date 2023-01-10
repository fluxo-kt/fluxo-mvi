package kt.fluxo.core.factory

import kt.fluxo.core.FluxoSettings
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.Store
import kt.fluxo.core.StoreSE
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.annotation.ThreadSafe
import kt.fluxo.core.fluxoSettings
import kotlin.js.JsName

/**
 * Creates instances of [Store]s using the provided components.
 * You can create different [Store] wrappers and combine them depending on circumstances.
 *
 * Expected to be thread safe.
 */
@ThreadSafe
@ExperimentalFluxoApi
public interface StoreFactory {

    /**
     * Creates an implementation of [Store] with side effects.
     */
    @JsName("create")
    public fun <Intent, State, SideEffect : Any> create(
        initialState: State,
        @BuilderInference handler: IntentHandler<Intent, State, SideEffect>,
        settings: FluxoSettings<Intent, State, SideEffect> = fluxoSettings(),
    ): StoreSE<Intent, State, SideEffect>

    /**
     * Creates an implementation of [Store] with no side effects.
     */
    @JsName("createWithNoSideEffects")
    public fun <Intent, State> create(
        initialState: State,
        @BuilderInference handler: IntentHandler<Intent, State, Nothing>,
        settings: FluxoSettings<Intent, State, Nothing> = fluxoSettings(),
    ): Store<Intent, State> {
        return create<Intent, State, Nothing>(initialState, handler, settings)
    }
}