package kt.fluxo.core

import kotlinx.coroutines.flow.Flow


/**
 * Convenience typealias for an MVVM+ Fluxo [Store] setup.
 *
 * @param State State type for this [Store].
 * @param SideEffect Side effects type posted by this container. Can be [Nothing] if this
 * container never posts side effects.
 */
public typealias Container<State, SideEffect> = StoreSE<FluxoIntent<State, SideEffect>, State, SideEffect>

/**
 * Convenience typealias for an MVVM+ Fluxo [Store] setup with no side effects.
 *
 * @param State State type for this [Store].
 */
public typealias ContainerS<State> = Store<FluxoIntent<State, Nothing>, State>


/**
 * Interface for a Fluxo MVI state store with side effects.
 *
 * @param Intent Intent type for this [Store]. See [Container] for MVVM+ [Store].
 * @param State State type for this [Store].
 * @param SideEffect Side effects type posted by this container. Can be [Nothing] if this
 * container never posts side effects.
 */
public interface StoreSE<in Intent, out State, out SideEffect : Any> : Store<Intent, State> {

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
}
