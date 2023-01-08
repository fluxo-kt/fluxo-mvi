package kt.fluxo.core

import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kotlin.jvm.JvmField

@ExperimentalFluxoApi
public open class StoreDecorator<Intent, State, SideEffect : Any>(
    @JvmField
    protected val store: StoreScope<Intent, State, SideEffect>,
) : StoreScope<Intent, State, SideEffect> by store
