@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kt.fluxo.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.annotation.ThreadSafe
import kt.fluxo.core.internal.Closeable
import kt.fluxo.core.internal.SideJobRequest.Companion.DEFAULT_SIDE_JOB
import kotlin.coroutines.cancellation.CancellationException
import kotlin.internal.InlineOnly
import kotlin.js.JsName


/**
 * Convenience typealias for an MVVM+ Fluxo [Store] setup.
 */
public typealias Container<State, SideEffect> = Store<FluxoIntent<State, SideEffect>, State, SideEffect>

/**
 * Convenience typealias for an MVVM+ Fluxo [Store] setup with side effects disabled.
 */
public typealias ContainerS<State> = Container<State, Nothing>

/**
 * Convenience typealias for a Fluxo [Store] setup with side effects disabled.
 */
public typealias StoreS<Intent, State> = Store<Intent, State, Nothing>


/**
 * Fluxo state store.
 *
 * Tip: Use [isActive][kotlinx.coroutines.isActive] to check if store is active.
 */
@ThreadSafe
public interface Store<in Intent, out State, out SideEffect : Any> : CoroutineScope, Closeable {

    /**
     * [Store] name. Auto-generated or specified via [FluxoSettings.name].
     *
     * @see state
     */
    public val name: String


    /**
     * Reactive access to the [State].
     *
     * @see state
     */
    public val stateFlow: StateFlow<State>

    /**
     * Current [State]. Shortcut for a `stateFlow.value`.
     *
     * @see stateFlow
     */
    public val state: State get() = stateFlow.value


    /**
     * A _hot_ [Flow] that shares emitted [SideEffect]s among its collectors.
     *
     * Behavior of this flow can be configured with [FluxoSettings.sideEffectsStrategy].
     *
     * @see FluxoSettings.sideEffectsStrategy
     * @see SideEffectsStrategy
     *
     * @throws IllegalStateException if [SideEffect]s where disabled for this [Store].
     */
    public val sideEffectFlow: Flow<SideEffect>


    /**
     * [StateFlow] with the number of subscribers (active collectors) for the current [Store].
     * Never negative and starts with zero. Can be used to react to changes in the number of subscriptions to this [Store].
     *
     * @see kt.fluxo.core.repeatOnSubscription
     * @see kotlinx.coroutines.flow.MutableSharedFlow.subscriptionCount
     */
    @ExperimentalFluxoApi
    public val subscriptionCount: StateFlow<Int>


    /**
     * Queues an [intent] for processing.
     *
     * @return a [Job] that can be [joined][Job.join] to await for processing completion
     */
    @JsName("send")
    public fun send(intent: Intent)

    /**
     * Queues an [intent] for processing.
     *
     * @return a [Job] that can be [joined][Job.join] to await for processing completion
     */
    @JsName("sendAsync")
    @Throws(FluxoClosedException::class, CancellationException::class)
    public suspend fun sendAsync(intent: Intent): Job


    /**
     * Starts [Store] processing if not started already.
     *
     * @throws FluxoClosedException
     */
    @JsName("start")
    public fun start(): Job?

    /**
     * Cancels this store completely
     *
     * @see kotlinx.coroutines.cancel
     */
    public override fun close() {
        cancel()
    }


    // region Convenience helpers

    @InlineOnly
    @JsName("launch")
    @Deprecated(
        message = "Please use the sideJob function to launch long running jobs",
        replaceWith = ReplaceWith("sideJob(key, block)"),
        level = DeprecationLevel.ERROR,
    )
    public fun launch(key: String = DEFAULT_SIDE_JOB, block: SideJob<Intent, *, *>): Unit = throw NotImplementedError()

    @InlineOnly
    @JsName("async")
    @Deprecated(
        message = "Please use the sideJob function to launch long running jobs",
        replaceWith = ReplaceWith("sideJob(key, block)"),
        level = DeprecationLevel.ERROR,
    )
    public fun async(key: String = DEFAULT_SIDE_JOB, block: SideJob<Intent, *, *>): Unit = throw NotImplementedError()

    // endregion
}
