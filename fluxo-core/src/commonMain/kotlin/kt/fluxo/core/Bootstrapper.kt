package kt.fluxo.core

import kt.fluxo.core.dsl.BootstrapperScopeLegacy

public typealias Bootstrapper<Intent, State, SideEffect> = suspend BootstrapperScopeLegacy<Intent, State, SideEffect>.() -> Unit
