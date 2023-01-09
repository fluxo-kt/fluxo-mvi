package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScope

public typealias SideJob<Intent, State, SideEffect> = suspend StoreScope<Intent, State, SideEffect>.() -> Unit
