package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScope

public typealias Bootstrapper<Intent, State, SideEffect> = suspend StoreScope<Intent, State, SideEffect>.() -> Unit

public typealias BootstrapperS<Intent, State> = Bootstrapper<Intent, State, Nothing>
