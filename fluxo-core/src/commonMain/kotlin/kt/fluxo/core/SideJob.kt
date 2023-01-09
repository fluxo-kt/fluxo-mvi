package kt.fluxo.core

import kt.fluxo.core.dsl.SideJobScopeLegacy

public typealias SideJob<Intent, State, SideEffect> = suspend SideJobScopeLegacy<Intent, State, SideEffect>.() -> Unit
