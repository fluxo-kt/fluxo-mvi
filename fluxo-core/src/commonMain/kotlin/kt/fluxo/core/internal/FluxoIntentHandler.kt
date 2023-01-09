package kt.fluxo.core.internal

import kt.fluxo.core.FluxoIntent
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.dsl.StoreScope

/**
 * [IntentHandler] that allows functional MVVM+ intents to work as a handlers themselves.
 */
@PublishedApi
@InternalFluxoApi
internal object FluxoIntentHandler : IntentHandler<FluxoIntent<Any?, Any>, Any?, Any> {

    override suspend fun StoreScope<FluxoIntent<Any?, Any>, Any?, Any>.handleIntent(intent: FluxoIntent<Any?, Any>) {
        intent()
    }
}
