@file:Suppress("KDocUnresolvedReference")

package kt.fluxo.core.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal fun Throwable?.toCancellationException() = when (this) {
    null -> null
    is CancellationException -> this
    else -> CancellationException("", cause = this)
}


internal fun StateFlow<Int>.plusIn(scope: CoroutineScope, started: SharingStarted, other: StateFlow<Int>): StateFlow<Int> {
    return combine(other) { a, b -> a + b }
        .stateIn(scope, started, initialValue = value + other.value)
}


internal class SubscriptionCountFlow<T>(
    private val subscriptionCount: MutableStateFlow<Int>,
    private val delegate: Flow<T>,
) : Flow<T> {

    override suspend fun collect(collector: FlowCollector<T>) {
        try {
            subscriptionCount.getAndAdd(delta = 1)
            delegate.collect(collector)
        } finally {
            subscriptionCount.getAndAdd(delta = -1)
        }
    }
}

/**
 * Atomically adds the given [delta] to the current [value][StateFlow.value].
 *
 * @param delta the value to add
 * @return the previous value
 *
 * @see MutableStateFlow.compareAndSet
 * @see java.util.concurrent.atomic.AtomicInteger.getAndAdd
 */
internal fun MutableStateFlow<Int>.getAndAdd(delta: Int): Int {
    while (true) {
        val value = value
        if (compareAndSet(value, value + delta)) {
            return value
        }
    }
}
