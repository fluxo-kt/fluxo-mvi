package kt.fluxo.core

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kt.fluxo.core.dsl.StoreScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.JsName
import kotlin.jvm.JvmName


@Deprecated("For migration")
public interface FluxoInterceptor<Intent, State, SideEffect : Any> {

    @JsName("start")
    public fun StoreScope<Intent, State, SideEffect>.start(events: Flow<Any>) {
        val context = when (coroutineContext[CoroutineName]) {
            null -> EmptyCoroutineContext
            else -> CoroutineName("$name: interceptor ${this::class.simpleName}")
        }
        launch(context = context, start = CoroutineStart.UNDISPATCHED) {
            events.collect(::onNotify)
        }
    }

    @JsName("onNotify")
    public suspend fun onNotify(event: Any) {
    }
}

/**
 * Convenience factory for creating [FluxoInterceptor] from a [function][onEvent].
 */
@JvmName("create")
@Suppress("FunctionName", "RedundantSuppression")
@Deprecated("For migration")
public inline fun <I, S, SE : Any> FluxoInterceptor(crossinline onEvent: (event: Any) -> Unit): FluxoInterceptor<I, S, SE> {
    return object : FluxoInterceptor<I, S, SE> {
        override suspend fun onNotify(event: Any) = onEvent(event)
    }
}
