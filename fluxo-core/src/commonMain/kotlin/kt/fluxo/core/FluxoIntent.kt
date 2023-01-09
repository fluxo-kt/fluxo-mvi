package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScopeLegacy

/**
 * MVVM+ Intent for the Fluxo [Container]
 */
public typealias FluxoIntent<S, SE> = suspend StoreScopeLegacy<Nothing, S, SE>.() -> Unit

/**
 * Convenience typealias for a Fluxo [FluxoIntent] with side effects disabled.
 */
public typealias FluxoIntentS<S> = FluxoIntent<S, Nothing>
