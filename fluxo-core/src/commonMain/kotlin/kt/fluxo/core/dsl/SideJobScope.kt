package kt.fluxo.core.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kt.fluxo.core.Store
import kt.fluxo.core.annotation.FluxoDsl

@FluxoDsl
public interface SideJobScope<in Intent, State, in SideEffect : Any> : CoroutineScope {

    public val currentStateWhenStarted: State

    public val restartState: RestartState

    /**
     * Updates the [Store.state] atomically using the specified [function] of its value.
     *
     * [function] may be evaluated multiple times, if [Store.state] is being concurrently updated.
     *
     * @see MutableStateFlow.update
     */
    public suspend fun updateState(function: (State) -> State): State

    public suspend fun postIntent(intent: Intent): Job

    public suspend fun postSideEffect(sideEffect: SideEffect)

    public enum class RestartState {
        Initial, Restarted
    }
}
