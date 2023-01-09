package kt.fluxo.core.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.dsl.SideJobScopeLegacy

@InternalFluxoApi
@Deprecated("For removal")
internal class SideJobScopeImplLegacy<in Intent, State, in SideEffect : Any>(
    private val updateStateAndGet: suspend ((State) -> State) -> State,
    private val sendIntent: suspend (Intent) -> Job,
    private val sendSideEffect: suspend (SideEffect) -> Unit,
    override val currentStateWhenStarted: State,
    override val restartState: SideJobScopeLegacy.RestartState,
    coroutineScope: CoroutineScope,
) : SideJobScopeLegacy<Intent, State, SideEffect>, CoroutineScope by coroutineScope {

    override suspend fun updateState(function: (State) -> State) = updateStateAndGet(function)

    override suspend fun postIntent(intent: Intent) = sendIntent(intent)

    override suspend fun postSideEffect(sideEffect: SideEffect) = sendSideEffect(sideEffect)
}
