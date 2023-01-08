@file:JvmName("FluxoDsl")

package kt.fluxo.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.annotation.FluxoDsl
import kt.fluxo.core.dsl.StoreScope
import kt.fluxo.core.internal.FluxoStore
import kotlin.js.JsName
import kotlin.jvm.JvmName


// Convenience DSL for Fluxo usage (non-inline)


/**
 *
 *
 * NOTE: Works only for the standart implementation of Fluxo [Store] ([FluxoStore]).
 */
@ExperimentalFluxoApi
@JsName("closeStoreAndWait")
@JvmName("closeStoreAndWait")
public suspend fun Store<*, *, *>.closeAndWait() {
    close()
    val store = this as FluxoStore
    store.interceptorScope.coroutineContext[Job]!!.join()
    store.intentContext[Job]!!.join()
}

/**
 *
 * @see StoreScope.sideJob
 */
@FluxoDsl
@JsName("repeatOnSubscriptionIn")
@JvmName("repeatOnSubscriptionIn")
@OptIn(ExperimentalCoroutinesApi::class)
public suspend fun <I, S, SE : Any> StoreScope<I, S, SE>.repeatOnSubscription(
    key: String = StoreScope.DEFAULT_REPEAT_ON_SUBSCRIPTION_JOB,
    stopTimeout: Long = 100L,
    block: SideJob<I, S, SE>,
) {
    sideJob(key) {
        val upstream = this@repeatOnSubscription.subscriptionCount
        if (stopTimeout > 0L) {
            upstream.mapLatest {
                if (it > 0) {
                    true
                } else {
                    delay(stopTimeout)
                    false
                }
            }
        } else {
            upstream.map { it > 0 }
        }.distinctUntilChanged().collectLatest { subscribed ->
            if (subscribed) {
                block()
            }
        }
    }
}
