package kt.fluxo.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kt.fluxo.core.dsl.InputStrategyScope
import kt.fluxo.core.strategy.FifoInputStrategy
import kt.fluxo.core.strategy.LifoInputStrategy
import kt.fluxo.core.strategy.ParallelInputStrategy
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.native.ObjCName

public abstract class InputStrategy {

    /**
     *
     * @param onUndeliveredElement See "Undelivered elements" section in [Channel] documentation for details.
     */
    @JsName("createQueue")
    public open fun <Request> createQueue(onUndeliveredElement: ((Request) -> Unit)?): Channel<Request> {
        return Channel(
            capacity = Channel.UNLIMITED,
            onBufferOverflow = BufferOverflow.SUSPEND,
            onUndeliveredElement = onUndeliveredElement,
        )
    }

    public open val parallelProcessing: Boolean get() = false

    public open val rollbackOnCancellation: Boolean get() = !parallelProcessing

    public open val resendUndelivered: Boolean get() = true

    @JsName("processRequests")
    public abstract suspend fun <Request> InputStrategyScope<Request>.processRequests(queue: Flow<Request>)


    /**
     * Accessors for in-box input strategies.
     */
    public companion object InBox {
        /**
         * `First-in, first-out` - ordered processing strategy. Predictable and intuitive, default choice.
         *
         * Consider [Parallel] or [Lifo] instead if you need more responsiveness.
         */
        @JsName("Fifo")
        @ObjCName("Fifo")
        @get:JvmName("Fifo")
        public val Fifo: InputStrategy get() = FifoInputStrategy

        /**
         * `Last-in, first-out` - strategy optimized for lots of events (e.g. user actions).
         * Provides more responsiveness comparing to [Fifo], but can lose some intents!
         *
         * **IMPORTANT:** Cancels previous unfinished intents when receives new one!
         *
         * Consider [Parallel] if you steel need more responsiveness, but without dropping of any event.
         */
        @JsName("Lifo")
        @ObjCName("Lifo")
        @get:JvmName("Lifo")
        public val Lifo: InputStrategy get() = LifoInputStrategy

        /**
         * Parallel processing of all intents, can provide better responsiveness comparing to [Fifo].
         *
         * **IMPORTANT:** No guarantee that inputs will be processed in any given order!
         */
        @JsName("Parallel")
        @ObjCName("Parallel")
        @get:JvmName("Parallel")
        public val Parallel: InputStrategy get() = ParallelInputStrategy
    }
}
