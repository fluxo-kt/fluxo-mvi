@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kt.fluxo.core.dsl

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.SideJob
import kt.fluxo.core.Store
import kt.fluxo.core.StoreSE
import kt.fluxo.core.annotation.CallSuper
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.internal.SideJobRequest.Companion.DEFAULT_SIDE_JOB
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.internal.InlineOnly
import kotlin.js.JsName

/**
 * Mutable [Store] scope for handlers, decorators and
 */
@ExperimentalFluxoApi
public interface StoreScope<in Intent, State, SideEffect : Any> : StoreSE<Intent, State, SideEffect> {

    override var value: State

    /**
     * Updates the [Store.state] atomically using the specified [function] of its value.
     *
     * [function] may be evaluated multiple times, if [Store.state] is being concurrently updated.
     *
     * @see MutableStateFlow.update
     */
    @CallSuper
    @JsName("updateState")
    public suspend fun updateState(function: (prevState: State) -> State): State

    @CallSuper
    @JsName("postSideEffect")
    public suspend fun postSideEffect(sideEffect: SideEffect)

    /**
     *
     * @see kotlinx.coroutines.launch
     */
    @CallSuper
    @JsName("sideJob")
    public suspend fun sideJob(
        key: String = DEFAULT_SIDE_JOB,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: SideJob<Intent, State, SideEffect>,
    ): Job


    // region Migration and convenience helpers

    /**
     * Explicitly mark a branch in the [IntentHandler] as doing nothing.
     * It will be considered as been handled properly even though nothing happened.
     */
    public fun noOp() {}

    /** @see updateState */
    @InlineOnly
    @JsName("reduce")
    @Deprecated(
        message = "Please use updateState instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("updateState { state ->\n    reducer()    \n}"),
    )
    public suspend fun reduce(reducer: (State) -> State): State = updateState(reducer)


    /** @see sideJob */
    @InlineOnly
    @JsName("launch")
    @Deprecated(
        message = "Please use the sideJob function to launch long running jobs",
        replaceWith = ReplaceWith("sideJob(key, block)"),
        level = DeprecationLevel.ERROR,
    )
    public fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        key: String = DEFAULT_SIDE_JOB,
        block: SideJob<Intent, *, *>,
    ): Unit = throw NotImplementedError()

    /** @see sideJob */
    @InlineOnly
    @JsName("async")
    @Deprecated(
        message = "Please use the sideJob function to launch long running jobs",
        replaceWith = ReplaceWith("sideJob(key, block)"),
        level = DeprecationLevel.ERROR,
    )
    public fun async(
        context: CoroutineContext = EmptyCoroutineContext,
        key: String = DEFAULT_SIDE_JOB,
        block: SideJob<Intent, *, *>,
    ): Unit = throw NotImplementedError()

    // endregion
}
