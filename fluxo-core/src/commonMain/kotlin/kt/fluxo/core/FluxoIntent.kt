package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScope

public typealias FluxoIntent<S, SE> = suspend StoreScope<Nothing, S, SE>.() -> Unit
public typealias FluxoIntentS<S> = FluxoIntent<S, Nothing>
