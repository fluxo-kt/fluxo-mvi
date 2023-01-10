@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE")

package kt.fluxo.core.dsl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kt.fluxo.core.Container
import kt.fluxo.core.FluxoIntent
import kt.fluxo.core.FluxoSettings
import kt.fluxo.core.Store
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kotlin.internal.InlineOnly

@InlineOnly
@Deprecated(message = "Please use the container instead", replaceWith = ReplaceWith("container"))
public inline val <S, SE : Any> ContainerHost<S, SE>.store get() = container


@InlineOnly
@ExperimentalFluxoApi
@Deprecated(
    message = "Please use the send instead",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("send(intent)"),
)
public suspend inline fun <S, SE : Any> Container<S, SE>.orbit(noinline intent: FluxoIntent<S, SE>) = emit(intent)

@InlineOnly
@Deprecated(
    message = "Please use the send instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("send(intent)"),
)
public inline fun <I> Store<I, *>.accept(intent: I): Job = send(intent)


@InlineOnly
@Deprecated(
    message = "Please use the intentContext instead",
    replaceWith = ReplaceWith("intentContext"),
)
@OptIn(ExperimentalStdlibApi::class)
public inline var FluxoSettings<*, *, *>.intentDispatcher: CoroutineDispatcher
    get() = coroutineContext[CoroutineDispatcher] ?: scope?.coroutineContext?.get(CoroutineDispatcher) ?: Dispatchers.Default
    set(value) {
        coroutineContext = value
    }
