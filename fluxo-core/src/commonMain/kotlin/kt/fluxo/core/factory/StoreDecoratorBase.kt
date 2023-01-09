package kt.fluxo.core.factory

import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kotlin.jvm.JvmField

@ExperimentalFluxoApi
public abstract class StoreDecoratorBase<Intent, State, SideEffect : Any>(
    @JvmField
    protected val store: StoreDecorator<Intent, State, SideEffect>,
) : StoreDecorator<Intent, State, SideEffect> by store
