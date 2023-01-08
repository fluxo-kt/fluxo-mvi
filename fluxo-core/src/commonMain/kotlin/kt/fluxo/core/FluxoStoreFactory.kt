package kt.fluxo.core

import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.internal.FluxoStore

@ExperimentalFluxoApi
public object FluxoStoreFactory : StoreFactory {
    override fun <Intent, State, SideEffect : Any> create(
        initialState: State,
        handler: IntentHandler<Intent, State, SideEffect>,
        settings: FluxoSettings<Intent, State, SideEffect>,
    ): Store<Intent, State, SideEffect> {
        return FluxoStore(
            initialState = initialState,
            intentHandler = handler,
            conf = settings,
        )
    }
}
