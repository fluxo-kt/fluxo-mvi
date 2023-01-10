@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "PropertyName", "VariableNaming")

package kt.fluxo.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kt.fluxo.core.annotation.FluxoDsl
import kt.fluxo.core.annotation.NotThreadSafe
import kt.fluxo.core.debug.DEBUG
import kt.fluxo.core.internal.InputStrategyGuardian
import kt.fluxo.core.internal.RunningSideJob.Companion.BOOTSTRAPPER_SIDE_JOB
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.internal.InlineOnly
import kotlin.js.JsName
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.native.ObjCName


public typealias FluxoSettingsS<Intent, State> = FluxoSettings<Intent, State, Nothing>


/**
 * Settings for the Fluxo store.
 *
 * * Change settings once and for all at one place with [FluxoSettings.DEFAULT].
 * * Provide prepared settings object for the group of your stores (use [FluxoSettings()][FluxoSettings.invoke] and then the standard DSL).
 * * Just configure each store individually with the standard DSL (see [container] and [store]).
 */
@FluxoDsl
@NotThreadSafe
public class FluxoSettings<Intent, State, SideEffect : Any> private constructor() {

    public var name: String? = null

    /**
     * If true the [Store] will be started only on first subscriber.
     */
    @get:JvmName("isLazy")
    public var lazy: Boolean = true

    /**
     * Disable if you want [Store] to keep working after some exception happened in processing.
     *
     * @see exceptionHandler
     */
    @get:JvmName("isCloseOnExceptions")
    public var closeOnExceptions: Boolean = true

    /**
     * Enables all the debug checks in the [Store].
     *
     * Disable explicitly if you need to bypass the [InputStrategyGuardian] checks, and sure in it.
     *
     * [closeOnExceptions] is also enabled if [debugChecks] enabled explicitly.
     */
    public var debugChecks: Boolean = DEBUG
        set(value) {
            field = value
            if (value) closeOnExceptions = true
        }

    /**
     * Either a positive [SideEffect]s channel capacity or one of the constants defined in [Channel.Factory].
     * Used as a `extraBufferCapacity` parameter for [MutableSharedFlow] when [SideEffectsStrategy.SHARE] used.
     *
     * @see sideEffectsStrategy
     */
    public var sideEffectBufferSize: Int = Channel.BUFFERED


    // region Processing control

    public var bootstrapper: Bootstrapper<in Intent, State, SideEffect>? = null

    /** [bootstrapper] convenience method */
    @InlineOnly
    @JsName("onStart")
    public inline fun onStart(noinline bootstrapper: Bootstrapper<in Intent, State, SideEffect>) {
        this.bootstrapper = bootstrapper
    }

    /** [bootstrapper] convenience method */
    @InlineOnly
    @JsName("onCreate")
    @Deprecated("Use onStart instead", ReplaceWith("onStart(bootstrapper)"))
    public inline fun onCreate(noinline bootstrapper: Bootstrapper<in Intent, State, SideEffect>) {
        onStart(bootstrapper)
    }

    /** [bootstrapper] convenience method when you need only a [sideJob][kt.fluxo.core.dsl.StoreScope.sideJob] */
    @JsName("bootstrapperJob")
    public fun bootstrapperJob(
        key: String = BOOTSTRAPPER_SIDE_JOB,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: SideJob<Intent, State, SideEffect>,
    ) {
        this.bootstrapper = { sideJob(key, context, start, block) }
    }


    /**
     * [Interceptors][FluxoInterceptor] for the [Store] events.
     * Attach loggers, time-travelling, analytics, everything you want.
     *
     * @see FluxoInterceptor
     */
    @Deprecated("For migration")
    public val interceptors: MutableList<FluxoInterceptor<Intent, State, SideEffect>> = mutableListOf()

    /**
     * If you need to filter out some [Intent]s.
     *
     * (`true` to accept intent, `false` otherwise)
     */
    public var intentFilter: IntentFilter<in Intent, State>? = null

    /**
     * Take full control of the intent processing pipeline.
     * [Fifo], [Lifo], and [Parallel] strategies available out of the box.
     * Or you can create your own if you need.
     */
    public var inputStrategy: InputStrategy = Fifo

    /**
     * A strategy to be applied when sharing side effects.
     * [sideEffectBufferSize] defaults to `0` when [SideEffectsStrategy.SHARE] used.
     *
     * @see SideEffectsStrategy
     * @see sideEffectBufferSize
     */
    public var sideEffectsStrategy: SideEffectsStrategy = SideEffectsStrategy.RECEIVE
        set(value) {
            field = value
            if (value is SideEffectsStrategy.SHARE && sideEffectBufferSize == Channel.BUFFERED) {
                sideEffectBufferSize = 0
            }
        }

    // endregion


    // region Coroutines control

    /**
     * Additional 'parent' [CoroutineScope] for running store. It will be added to [coroutineContext].
     */
    public var scope: CoroutineScope? = null

    /**
     * [CoroutineDispatcher] or any [CoroutineContext] to run the [Store] event loop.
     */
    public var coroutineContext: CoroutineContext = Dispatchers.Default
    @Deprecated("For removal", level = DeprecationLevel.ERROR)
    public var intentContext: CoroutineContext = EmptyCoroutineContext
    public var sideJobsContext: CoroutineContext = EmptyCoroutineContext
    @Deprecated("For removal", level = DeprecationLevel.ERROR)
    public var interceptorContext: CoroutineContext = EmptyCoroutineContext

    /**
     * If false the [Store] will offload everything possible to the provided [scope], not trying to optimize performance.
     */
    @get:JvmName("isOptimized")
    public var optimized: Boolean = true

    /**
     * [CoroutineExceptionHandler] to receive exception happened in processing.
     *
     * **NOTE:** Disables [closeOnExceptions] when set to non-null value!
     *
     * @see closeOnExceptions
     */
    public var exceptionHandler: CoroutineExceptionHandler? = null
        set(value) {
            field = value
            closeOnExceptions = value == null
        }

    /** [exceptionHandler] convenience method */
    @InlineOnly
    @JsName("setExceptionHandler")
    public inline fun exceptionHandler(crossinline handler: (CoroutineContext, Throwable) -> Unit) {
        exceptionHandler = CoroutineExceptionHandler(handler)
    }

    /** [exceptionHandler] convenience method */
    @InlineOnly
    @JsName("onError")
    public inline fun onError(crossinline handler: CoroutineContext.(Throwable) -> Unit) {
        exceptionHandler = CoroutineExceptionHandler(handler)
    }

    // endregion


    // region Accessors for in-box input strategies

    /**
     * `First-in, first-out` - ordered processing strategy. Predictable and intuitive, default choice.
     *
     * Consider [Parallel] or [Lifo] instead if you need more responsiveness.
     */
    @InlineOnly
    @JsName("Fifo")
    @ObjCName("Fifo")
    @get:JvmName("Fifo")
    public inline val Fifo: InputStrategy get() = InputStrategy.Fifo

    /**
     * `Last-in, first-out` - strategy optimized for lots of events (e.g. user actions).
     * Provides more responsiveness comparing to [Fifo], but can lose some intents!
     *
     * **IMPORTANT:** Cancels previous unfinished intents when receives new one!
     *
     * Consider [Parallel] if you steel need more responsiveness, but without dropping of any event.
     */
    @InlineOnly
    @JsName("Lifo")
    @ObjCName("Lifo")
    @get:JvmName("Lifo")
    public inline val Lifo: InputStrategy get() = InputStrategy.Lifo

    /**
     * Parallel processing of all intents, can provide better responsiveness comparing to [Fifo].
     *
     * **IMPORTANT:** No guarantee that inputs will be processed in any given order!
     */
    @InlineOnly
    @JsName("Parallel")
    @ObjCName("Parallel")
    @get:JvmName("Parallel")
    public inline val Parallel: InputStrategy get() = InputStrategy.Parallel

    // endregion


    // region Migration helpers

    /**
     * Not applicable from here! Use stopTimeout parameter for the [repeatOnSubscription] method instead.
     *
     * @see repeatOnSubscription
     */
    @InlineOnly
    @Deprecated("Not applicable from here! Use stopTimeout parameter for repeatOnSubscription method instead")
    public var repeatOnSubscribedStopTimeout: Long
        inline get() = 0
        inline set(@Suppress("UNUSED_PARAMETER") value) {}

    // endregion


    public fun copy(): FluxoSettings<Intent, State, SideEffect> {
        val s = FluxoSettings<Intent, State, SideEffect>()

        // copy values in reverse order to mitigate setters logic

        s.exceptionHandler = exceptionHandler

        s.optimized = optimized
        s.sideJobsContext = sideJobsContext
        s.coroutineContext = coroutineContext
        s.scope = scope

        s.sideEffectsStrategy = sideEffectsStrategy
        s.inputStrategy = inputStrategy
        s.intentFilter = intentFilter
        s.interceptors.addAll(interceptors)
        s.bootstrapper = bootstrapper

        s.sideEffectBufferSize = sideEffectBufferSize
        s.debugChecks = debugChecks
        s.closeOnExceptions = closeOnExceptions
        s.lazy = lazy
        s.name = name

        return s
    }

    @NotThreadSafe
    public companion object Factory {
        /**
         * Global default [FluxoSettings]. Not thread safe!
         */
        @JvmField
        @JsName("DEFAULT")
        @ObjCName("DEFAULT")
        public val DEFAULT: FluxoSettings<out Any?, out Any?, out Any> = FluxoSettings()

        /**
         * Creates a new typed copy of [global default][DEFAULT] [FluxoSettings].
         */
        @JsName("create")
        @JvmName("create")
        @ObjCName("create")
        public operator fun <Intent, State, SideEffect : Any> invoke(): FluxoSettings<Intent, State, SideEffect> {
            @Suppress("UNCHECKED_CAST")
            return DEFAULT.copy() as FluxoSettings<Intent, State, SideEffect>
        }
    }
}
