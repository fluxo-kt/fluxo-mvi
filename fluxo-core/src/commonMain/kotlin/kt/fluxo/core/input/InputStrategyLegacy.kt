package kt.fluxo.core.input

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.native.ObjCName

@ExperimentalFluxoApi
public abstract class InputStrategyLegacy {

    /**
     *
     * @param onUndeliveredElement See "Undelivered elements" section in [Channel] documentation for details.
     *
     * @see kotlinx.coroutines.channels.Channel
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
        public val Fifo: InputStrategyLegacy get() = FifoInputStrategyLegacy

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
        public val Lifo: InputStrategyLegacy get() = LifoInputStrategyLegacy

        /**
         * Parallel processing of all intents, can provide better responsiveness comparing to [Fifo].
         *
         * **IMPORTANT:** No guarantee that inputs will be processed in any given order!
         */
        @JsName("Parallel")
        @ObjCName("Parallel")
        @get:JvmName("Parallel")
        public val Parallel: InputStrategyLegacy get() = ParallelInputStrategyLegacy
    }
}
