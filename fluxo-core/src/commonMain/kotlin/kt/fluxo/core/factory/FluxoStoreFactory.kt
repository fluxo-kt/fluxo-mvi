package kt.fluxo.core.factory

import kt.fluxo.core.FluxoSettings
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.Store
import kt.fluxo.core.StoreSE
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.internal.FluxoStore

@ExperimentalFluxoApi
public object FluxoStoreFactory : StoreFactory {
    override fun <Intent, State, SideEffect : Any> create(
        initialState: State,
        handler: IntentHandler<Intent, State, SideEffect>,
        settings: FluxoSettings<Intent, State, SideEffect>,
    ): StoreSE<Intent, State, SideEffect> {
        return FluxoStore(
            initialState = initialState,
            intentHandler = handler,
            conf = settings,
        )
    }
}
