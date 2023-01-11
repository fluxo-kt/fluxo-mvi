package kt.fluxo.core.input

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * `Last-in, first-out` - strategy optimized for lots of events (e.g. user actions).
 * Provides more responsiveness comparing to [Fifo][FifoInputStrategyLegacy], but can lose some intents!
 *
 * **IMPORTANT:** Cancels previous unfinished intents when receives new one!
 *
 * Consider [Parallel][ParallelInputStrategyLegacy] if you steel need more responsiveness, but without dropping of any event.
 */
internal object LifoInputStrategyLegacy : InputStrategyLegacy() {

    override fun <Request> createQueue(onUndeliveredElement: ((Request) -> Unit)?): Channel<Request> {
        return Channel(
            capacity = Channel.CONFLATED,
            onBufferOverflow = BufferOverflow.SUSPEND,
            onUndeliveredElement = onUndeliveredElement,
        )
    }

    override val resendUndelivered: Boolean get() = false

    override suspend fun <Request> (InputStrategyScope<Request>).processRequests(queue: Flow<Request>) {
        queue.collectLatest(this)
    }
}
