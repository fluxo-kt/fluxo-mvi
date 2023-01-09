@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kt.fluxo.core.factory

import kt.fluxo.core.Bootstrapper
import kt.fluxo.core.SideJob
import kt.fluxo.core.annotation.CallSuper
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.dsl.SideJobScopeLegacy.RestartState
import kt.fluxo.core.dsl.StoreScope
import kotlin.internal.InlineOnly
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kt.fluxo.core.dsl.StoreScope as Sc

@ExperimentalFluxoApi
public interface StoreDecorator<in Intent, State, SideEffect : Any> : StoreScope<Intent, State, SideEffect> {

    @CallSuper
    @JsName("onBootstrap")
    public suspend fun onBootstrap(bootstrapper: Bootstrapper<Intent, State, SideEffect>)

    @CallSuper
    public suspend fun onStart()


    @CallSuper
    @JsName("onStateChanged")
    public suspend fun onStateChanged(state: State)


    @CallSuper
    @JsName("onIntent")
    public suspend fun onIntent(intent: Intent)

    @CallSuper
    @JsName("onSideJob")
    public suspend fun onSideJob(key: String, restartState: RestartState, sideJob: SideJob<Intent, State, SideEffect>)


    @CallSuper
    @JsName("onUndeliveredIntent")
    public suspend fun onUndeliveredIntent(intent: Intent)

    @CallSuper
    @JsName("onUndeliveredSideEffect")
    public suspend fun onUndeliveredSideEffect(sideEffect: SideEffect)


    @CallSuper
    @JsName("onUnhandledError")
    public fun onUnhandledError(error: Throwable)

    @CallSuper
    @JsName("onClose")
    public suspend fun onClose(cause: Throwable?)
}

@InlineOnly
@ExperimentalFluxoApi
@JvmName("create")
@Suppress("FunctionNaming", "LongParameterList")
public inline fun <I, S, SE : Any> StoreDecorator(
    store: StoreDecorator<I, S, SE>,
    crossinline allowBootstrap: suspend Sc<I, S, SE>.(Bootstrapper<I, S, SE>) -> Boolean = { true },
    crossinline onStarted: suspend Sc<I, S, SE>.() -> Unit = {},

    crossinline onStateChange: suspend Sc<I, S, SE>.(state: S) -> Unit = {},

    crossinline allowIntent: suspend Sc<I, S, SE>.(intent: I) -> Boolean = { true },
    crossinline allowSideJob: suspend Sc<I, S, SE>.(key: String, RestartState, SideJob<I, S, SE>) -> Boolean = { _, _, _ -> true },

    crossinline onIntentUndelivered: Sc<I, S, SE>.(intent: I) -> Unit = {},
    crossinline onSideEffectUndelivered: Sc<I, S, SE>.(sideEffect: SE) -> Unit = {},

    crossinline onError: Sc<I, S, SE>.(error: Throwable) -> Unit = {},
    crossinline onClosed: suspend Sc<I, S, SE>.(cause: Throwable?) -> Unit = {},
): StoreDecorator<I, S, SE> {
    return object : StoreDecoratorBase<I, S, SE>(store) {

        override suspend fun onBootstrap(bootstrapper: Bootstrapper<I, S, SE>) {
            if (allowBootstrap(bootstrapper)) {
                super.onBootstrap(bootstrapper)
            }
        }

        override suspend fun onStart() {
            super.onStart()
            onStarted()
        }


        override suspend fun onStateChanged(state: S) {
            super.onStateChanged(state)
            onStateChange(state)
        }


        override suspend fun onIntent(intent: I) {
            if (allowIntent(intent)) {
                super.onIntent(intent)
            }
        }

        override suspend fun onSideJob(key: String, restartState: RestartState, sideJob: SideJob<I, S, SE>) {
            if (allowSideJob(key, restartState, sideJob)) {
                super.onSideJob(key, restartState, sideJob)
            }
        }


        override suspend fun onUndeliveredIntent(intent: I) {
            super.onUndeliveredIntent(intent)
            onIntentUndelivered(intent)
        }

        override suspend fun onUndeliveredSideEffect(sideEffect: SE) {
            super.onUndeliveredSideEffect(sideEffect)
            onSideEffectUndelivered(sideEffect)
        }


        override fun onUnhandledError(error: Throwable) {
            super.onUnhandledError(error)
            onError(error)
        }

        override suspend fun onClose(cause: Throwable?) {
            super.onClose(cause)
            onClosed(cause)
        }
    }
}
