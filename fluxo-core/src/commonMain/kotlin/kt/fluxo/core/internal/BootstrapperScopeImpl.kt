package kt.fluxo.core.internal

import kotlinx.coroutines.Deferred
import kt.fluxo.core.dsl.BootstrapperScope

internal class BootstrapperScopeImpl<in Intent, State, SideEffect : Any>(
    guardian: InputStrategyGuardian?,
    getState: () -> State,
    updateStateAndGet: ((State) -> State) -> State,
    private val sendIntent: suspend (Intent) -> Deferred<Unit>,
    sendSideEffect: suspend (SideEffect) -> Unit,
    sendSideJob: suspend (SideJobRequest<Intent, State, SideEffect>) -> Unit,
) : StoreScopeImpl<Intent, State, SideEffect>(
    guardian = guardian,
    getState = getState,
    updateStateAndGet = updateStateAndGet,
    sendSideEffect = sendSideEffect,
    sendSideJob = sendSideJob,
), BootstrapperScope<Intent, State, SideEffect> {

    override suspend fun postIntent(intent: Intent) = sendIntent(intent)
}
