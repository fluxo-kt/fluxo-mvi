package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScope

public typealias Bootstrapper<Intent, State, SideEffect> = suspend StoreScope<Intent, State, SideEffect>.() -> Unit
