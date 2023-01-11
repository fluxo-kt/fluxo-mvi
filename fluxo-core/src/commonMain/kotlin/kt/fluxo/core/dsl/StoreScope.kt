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
import kt.fluxo.core.internal.RunningSideJob.Companion.DEFAULT_SIDE_JOB
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.internal.InlineOnly
import kotlin.js.JsName
import kotlin.jvm.JvmSynthetic

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

    /**
     * Dispatch a [sideEffect] through the [sideEffectFlow].
     *
     * @throws IllegalStateException if [sideEffect]s are disabled for this [Store]
     */
    @CallSuper
    @JsName("postSideEffect")
    public suspend fun postSideEffect(sideEffect: SideEffect)

    /**
     * Do something other than [update the state][updateState] or [dispatch an effect][postSideEffect]. This is moving
     * outside the normal MVI workflow, so make sure you know what you're doing with this, and try to make sure it can be undone.
     *
     * For example, when deleting a record from the DB, do a soft delete so that it can be restored later, if needed.
     *
     * [Side-jobs][block] may safely be restarted; already-running side-jobs at the same [key] will be cancelled when starting
     * the new side-job. If a single [Store] is starting multiple side-jobs (likely from different inputs), they should each be
     * given a unique [key] within the VM to ensure they do not accidentally cancel each other.
     *
     * @see kotlinx.coroutines.launch
     */
    @CallSuper
    @JsName("sideJob")
    // FIXME: More options for a side job restarting or start-avoidance. SideJobs registry/management API
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
    @JvmSynthetic
    @JsName("reduce")
    @Deprecated(
        message = "Please use updateState instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("updateState { state ->\n    reducer()    \n}"),
    )
    public suspend fun reduce(reducer: (State) -> State): State = updateState(reducer)


    /** @see sideJob */
    @InlineOnly
    @JvmSynthetic
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
    @JvmSynthetic
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
