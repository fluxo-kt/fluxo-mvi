package kt.fluxo.core.input

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Parallel processing of all intents, can provide better responsiveness comparing to [Fifo][FifoInputStrategyLegacy].
 *
 * **IMPORTANT:** No guarantee that inputs will be processed in any given order!
 *
 * @TODO: Allow to limit parallelism?
 */
internal object ParallelInputStrategyLegacy : InputStrategyLegacy() {

    override val parallelProcessing: Boolean get() = true

    override suspend fun <Request> (InputStrategyScope<Request>).processRequests(queue: Flow<Request>) {
        coroutineScope {
            queue.collect { request ->
                launch {
                    invoke(request)
                }
            }
        }
    }
}
