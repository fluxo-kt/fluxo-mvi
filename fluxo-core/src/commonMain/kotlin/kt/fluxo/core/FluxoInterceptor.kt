package kt.fluxo.core

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kt.fluxo.core.dsl.InterceptorScope
import kt.fluxo.core.intercept.FluxoEvent
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.JsName
import kotlin.jvm.JvmName

public interface FluxoInterceptor<Intent, State, SideEffect : Any> {

    @JsName("start")
    public fun InterceptorScope<Intent, State>.start(events: Flow<FluxoEvent<Intent, State, SideEffect>>) {
        val context = when (coroutineContext[CoroutineName]) {
            null -> EmptyCoroutineContext
            else -> CoroutineName("$storeName: interceptor ${this::class.simpleName}")
        }
        launch(context = context, start = CoroutineStart.UNDISPATCHED) {
            events.collect(::onNotify)
        }
    }

    @JsName("onNotify")
    public suspend fun onNotify(event: FluxoEvent<Intent, State, SideEffect>) {}
}

/**
 * Convenience factory for creating [FluxoInterceptor] from a [function][onEvent].
 */
@JvmName("create")
@Suppress("FunctionName", "RedundantSuppression")
public inline fun <I, S, SE : Any> FluxoInterceptor(
    crossinline onEvent: (event: FluxoEvent<I, S, SE>) -> Unit,
): FluxoInterceptor<I, S, SE> {
    return object : FluxoInterceptor<I, S, SE> {
        override suspend fun onNotify(event: FluxoEvent<I, S, SE>) = onEvent(event)
    }
}
