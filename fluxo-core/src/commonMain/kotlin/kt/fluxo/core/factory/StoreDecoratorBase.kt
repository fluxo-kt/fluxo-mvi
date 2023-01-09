package kt.fluxo.core.factory

import kt.fluxo.core.annotation.ExperimentalFluxoApi

@ExperimentalFluxoApi
@Suppress("UnnecessaryAbstractClass")
public abstract class StoreDecoratorBase<in Intent, State, SideEffect : Any>(
    store: StoreDecorator<Intent, State, SideEffect>,
) : StoreDecorator<Intent, State, SideEffect> by store
