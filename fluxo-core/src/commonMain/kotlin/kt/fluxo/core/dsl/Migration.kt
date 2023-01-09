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
import kotlin.jvm.JvmSynthetic

@InlineOnly
@Deprecated(message = "Please use the container instead", replaceWith = ReplaceWith("container"))
public inline val <S, SE : Any> ContainerHost<S, SE>.store get() = container


@InlineOnly
@JvmSynthetic
@ExperimentalFluxoApi
@Deprecated(
    message = "Please use the send instead",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("send(intent)"),
)
// TODO: inline modifier causes NPE in InlineParameterChecker
public suspend fun <S, SE : Any> Container<S, SE>.orbit(intent: FluxoIntent<S, SE>) = emit(intent)

@InlineOnly
@Deprecated(
    message = "Please use the send instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("send(intent)"),
)
public inline fun <I, S, SE : Any> Store<I, S, SE>.accept(intent: I): Job = send(intent)


@InlineOnly
@Deprecated(
    message = "Please use the intentContext instead",
    replaceWith = ReplaceWith("intentContext"),
)
@OptIn(ExperimentalStdlibApi::class)
public inline var <I, S, SE : Any> FluxoSettings<I, S, SE>.intentDispatcher: CoroutineDispatcher
    get() = coroutineContext[CoroutineDispatcher] ?: scope?.coroutineContext?.get(CoroutineDispatcher) ?: Dispatchers.Default
    set(value) {
        coroutineContext = value
    }
