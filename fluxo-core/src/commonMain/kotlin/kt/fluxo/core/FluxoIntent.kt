package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScope

/**
 * MVVM+ Intent for the Fluxo [Container]
 */
public typealias FluxoIntent<S, SE> = suspend StoreScope<Nothing, S, SE>.() -> Unit

/**
 * Convenience typealias for a Fluxo [FluxoIntent] with side effects disabled.
 */
public typealias FluxoIntentS<S> = FluxoIntent<S, Nothing>
