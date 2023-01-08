@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "TooManyFunctions", "TrailingCommaOnCallSite")

package kt.fluxo.core

import kotlinx.coroutines.CoroutineScope
import kt.fluxo.core.annotation.FluxoDsl
import kt.fluxo.core.internal.FluxoIntentHandler
import kt.fluxo.core.internal.FluxoStore
import kt.fluxo.core.internal.ReducerIntentHandler
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.internal.InlineOnly
import kotlin.internal.LowPriorityInOverloadResolution
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.native.ObjCName


// Convenience DSL for Fluxo usage (inline)


// region CoroutineScope extensions for Store

/**
 * Returns MVVM+ [Store], connected to [CoroutineScope] lifecycle.
 */
@InlineOnly
public inline fun <State> CoroutineScope.container(
    initialState: State,
    settings: FluxoSettings<FluxoIntentS<State>, State, Nothing> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<FluxoIntentS<State>, State, Nothing>.() -> Unit = {},
): ContainerS<State> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return kt.fluxo.core.container(initialState, settings, setup = {
        eventLoopContext = coroutineContext
        setup()
    })
}

/**
 * Returns MVVM+ [Store] with [SideEffect] support, connected to [CoroutineScope] lifecycle.
 */
@InlineOnly
@LowPriorityInOverloadResolution
@JsName("scopedContainerWithSideEffects")
@JvmName("containerWithSideEffects")
@ObjCName("containerWithSideEffects")
public inline fun <State, SideEffect : Any> CoroutineScope.container(
    initialState: State,
    settings: FluxoSettings<FluxoIntent<State, SideEffect>, State, SideEffect> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<FluxoIntent<State, SideEffect>, State, SideEffect>.() -> Unit = {},
): Container<State, SideEffect> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return kt.fluxo.core.container(initialState, settings, setup = {
        eventLoopContext = coroutineContext
        setup()
    })
}

/**
 * Returns basic [Reducer]-based [Store], connected to [CoroutineScope] lifecycle.
 *
 * Use [container] for MVVM+ [Store].
 */
@InlineOnly
public inline fun <Intent, State> CoroutineScope.store(
    initialState: State,
    @BuilderInference noinline reducer: Reducer<Intent, State>,
    settings: FluxoSettings<Intent, State, Nothing> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<Intent, State, Nothing>.() -> Unit = {},
): StoreS<Intent, State> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return store(initialState, ReducerIntentHandler(reducer), settings, setup)
}

/**
 * Returns MVI [Store] with MVVM+ [IntentHandler] DSL, connected to [CoroutineScope] lifecycle.
 *
 * Use [container] for MVVM+ [Store].
 */
@InlineOnly
public inline fun <Intent, State> CoroutineScope.store(
    initialState: State,
    @BuilderInference handler: IntentHandler<Intent, State, Nothing>,
    settings: FluxoSettings<Intent, State, Nothing> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<Intent, State, Nothing>.() -> Unit = {},
): StoreS<Intent, State> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return kt.fluxo.core.store(initialState, handler, settings, setup = {
        eventLoopContext = coroutineContext
        setup()
    })
}

/**
 * Returns MVI [Store] with MVVM+ [IntentHandler] DSL and [SideEffect] support, connected to [CoroutineScope] lifecycle.
 *
 * Use [container] for MVVM+ [Store].
 */
@InlineOnly
@JsName("scopedStoreWithSideEffects")
@JvmName("storeWithSideEffects")
@ObjCName("storeWithSideEffects")
public inline fun <Intent, State, SideEffect : Any> CoroutineScope.store(
    initialState: State,
    @BuilderInference handler: IntentHandler<Intent, State, SideEffect>,
    settings: FluxoSettings<Intent, State, SideEffect> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<Intent, State, SideEffect>.() -> Unit = {},
): Store<Intent, State, SideEffect> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return kt.fluxo.core.store(initialState, handler, settings, setup = {
        eventLoopContext = coroutineContext
        setup()
    })
}

// endregion


// region Basic Store variants

/**
 * Returns MVVM+ [Store].
 */
@InlineOnly
public inline fun <State> container(
    initialState: State,
    settings: FluxoSettings<FluxoIntentS<State>, State, Nothing> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<FluxoIntentS<State>, State, Nothing>.() -> Unit = {},
): ContainerS<State> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return container<State, Nothing>(initialState, settings) {
        sideEffectsStrategy = SideEffectsStrategy.DISABLE
        setup()
    }
}

/**
 * Returns MVVM+ [Store] with [SideEffect] support.
 */
@InlineOnly
@LowPriorityInOverloadResolution
@JsName("containerWithSideEffects")
@JvmName("containerWithSideEffects")
@ObjCName("containerWithSideEffects")
public inline fun <State, SideEffect : Any> container(
    initialState: State,
    settings: FluxoSettings<FluxoIntent<State, SideEffect>, State, SideEffect> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<FluxoIntent<State, SideEffect>, State, SideEffect>.() -> Unit = {},
): Container<State, SideEffect> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return store(
        initialState,
        @Suppress("UNCHECKED_CAST") (FluxoIntentHandler as IntentHandler<FluxoIntent<State, SideEffect>, State, SideEffect>),
        settings,
        setup,
    )
}

/**
 * Returns basic [Reducer]-based [Store].
 *
 * Use [container] for MVVM+ [Store].
 */
@InlineOnly
public inline fun <Intent, State> store(
    initialState: State,
    @BuilderInference noinline reducer: Reducer<Intent, State>,
    settings: FluxoSettings<Intent, State, Nothing> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<Intent, State, Nothing>.() -> Unit = {},
): StoreS<Intent, State> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return store(initialState, ReducerIntentHandler(reducer), settings, setup)
}

/**
 * Returns MVI [Store] with MVVM+ [IntentHandler] DSL.
 *
 * Use [container] for MVVM+ [Store].
 */
@InlineOnly
public inline fun <Intent, State> store(
    initialState: State,
    @BuilderInference handler: IntentHandler<Intent, State, Nothing>,
    settings: FluxoSettings<Intent, State, Nothing> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<Intent, State, Nothing>.() -> Unit = {},
): StoreS<Intent, State> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return store<Intent, State, Nothing>(initialState, handler, settings) {
        sideEffectsStrategy = SideEffectsStrategy.DISABLE
        setup()
    }
}

/**
 * Returns MVI [Store] with MVVM+ [IntentHandler] DSL and [SideEffect] support.
 *
 * Use [container] for MVVM+ [Store].
 */
@InlineOnly
@JsName("storeWithSideEffects")
@JvmName("storeWithSideEffects")
@ObjCName("storeWithSideEffects")
public inline fun <Intent, State, SideEffect : Any> store(
    initialState: State,
    @BuilderInference handler: IntentHandler<Intent, State, SideEffect>,
    settings: FluxoSettings<Intent, State, SideEffect> = fluxoSettings(),
    @BuilderInference setup: FluxoSettings<Intent, State, SideEffect>.() -> Unit = {},
): Store<Intent, State, SideEffect> {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return FluxoStore(
        initialState = initialState,
        intentHandler = handler,
        // Always do a copy as we don't want to modify original settings.
        conf = settings.copy().apply(setup),
    )
}


/**
 * This function helps to avoid unnecessary additional copy of the [FluxoSettings].
 */
@InlineOnly
@PublishedApi
internal inline fun <Intent, State, SideEffect : Any> fluxoSettings(): FluxoSettings<Intent, State, SideEffect> {
    @Suppress("UNCHECKED_CAST")
    return FluxoSettings.DEFAULT as FluxoSettings<Intent, State, SideEffect>
}

// endregion


// region Store DSL

/**
 * Build and execute a functional [intent][FluxoIntent] on [Store].
 */
@FluxoDsl
@InlineOnly
public inline fun <S, SE : Any> ContainerHost<S, SE>.intent(noinline intent: FluxoIntent<S, SE>): Unit = container.send(intent)

/**
 * Build and execute a functional [intent][FluxoIntent] on [Store].
 */
@FluxoDsl
@InlineOnly
public inline fun <S, SE : Any> Container<S, SE>.intent(noinline intent: FluxoIntent<S, SE>): Unit = send(intent)

// endregion
