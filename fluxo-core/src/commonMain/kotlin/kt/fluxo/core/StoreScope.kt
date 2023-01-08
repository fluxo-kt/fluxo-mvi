@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kt.fluxo.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.internal.SideJobRequest.Companion.DEFAULT_SIDE_JOB
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.internal.InlineOnly
import kotlin.js.JsName

/**
 * Mutable [Store] scope for decorators, handlers and
 */
@ExperimentalFluxoApi
public interface StoreScope<in Intent, State, SideEffect : Any> : Store<Intent, State, SideEffect> {

    /**
     * Updates the [Store.state] atomically using the specified [function] of its value.
     *
     * [function] may be evaluated multiple times, if [Store.state] is being concurrently updated.
     *
     * @see MutableStateFlow.update
     */
    @JsName("updateState")
    public suspend fun updateState(function: (State) -> State): State

    @JsName("postSideEffect")
    public suspend fun postSideEffect(sideEffect: SideEffect)

    /**
     *
     * @see kotlinx.coroutines.launch
     */
    @JsName("sideJob")
    public suspend fun sideJob(
        key: String = DEFAULT_SIDE_JOB,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: SideJob<Intent, State, SideEffect>,
    ): Job


    // region Migration helpers

    /**
     * Helper for migration from Orbit.
     * Please use [updateState] instead.
     */
    @InlineOnly
    @JsName("reduce")
    @Deprecated(
        message = "Please use updateState instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("updateState { state ->\n    reducer()    \n}"),
    )
    public suspend fun reduce(reducer: (State) -> State) {
        updateState(reducer)
    }

    // endregion
}
