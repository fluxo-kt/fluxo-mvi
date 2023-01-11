package kt.fluxo.core.input

import kotlinx.coroutines.flow.Flow

/**
 * `First-in, first-out` - ordered processing strategy. Predictable and intuitive, default choice.
 *
 * Consider [Parallel][ParallelInputStrategyLegacy] or [Lifo][LifoInputStrategyLegacy] instead if you need more responsiveness.
 */
internal object FifoInputStrategyLegacy : InputStrategyLegacy() {

    override suspend fun <Request> (InputStrategyScope<Request>).processRequests(queue: Flow<Request>) {
        queue.collect(this)
    }
}
