package kt.fluxo.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.annotation.ThreadSafe
import kt.fluxo.core.intercept.FluxoEvent
import kt.fluxo.core.internal.Closeable
import kotlin.js.JsName

/**
 * Convenience typealias for an MVVM+ Fluxo [Store] setup.
 */
public typealias Container<State, SideEffect> = Store<FluxoIntent<State, SideEffect>, State, SideEffect>

@ThreadSafe
public interface Store<Intent, State, SideEffect : Any> : Closeable {

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

    @ExperimentalFluxoApi
    public val eventsFlow: Flow<FluxoEvent<Intent, State, SideEffect>>

    public val isActive: Boolean


    /**
     *
     * @throws StoreClosedException
     */
    @JsName("send")
    public fun send(intent: Intent)

    /**
     *
     * @throws StoreClosedException
     */
    @JsName("sendAsync")
    public suspend fun sendAsync(intent: Intent): Job


    /**
     * Starts [Store] processing if not started already.
     *
     * @throws StoreClosedException
     */
    @JsName("start")
    public fun start(): Job?

    public override fun close()
}
