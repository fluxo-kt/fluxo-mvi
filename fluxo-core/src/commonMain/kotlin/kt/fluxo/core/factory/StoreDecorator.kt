@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kt.fluxo.core.factory

import kt.fluxo.core.Bootstrapper
import kt.fluxo.core.SideJob
import kt.fluxo.core.annotation.CallSuper
import kt.fluxo.core.annotation.ExperimentalFluxoApi
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
    public suspend fun onStart() {
    }


    @CallSuper
    @JsName("onStateChanged")
    public fun onStateChanged(state: State) {
    }


    @CallSuper
    @JsName("onIntent")
    public suspend fun onIntent(intent: Intent)

    @CallSuper
    @JsName("onSideJob")
    public suspend fun onSideJob(key: String, wasRestarted: Boolean, sideJob: SideJob<Intent, State, SideEffect>)


    @CallSuper
    @JsName("onUndeliveredIntent")
    public fun onUndeliveredIntent(intent: Intent, wasResent: Boolean) {
    }

    @CallSuper
    @JsName("onUndeliveredSideEffect")
    public fun onUndeliveredSideEffect(sideEffect: SideEffect, wasResent: Boolean) {
    }


    /**
     *
     * @return `true` if error was handled
     */
    @CallSuper
    @JsName("onUnhandledError")
    public fun onUnhandledError(error: Throwable): Boolean = false

    @CallSuper
    @JsName("onClose")
    public suspend fun onClose(cause: Throwable?) {
    }
}

@InlineOnly
@ExperimentalFluxoApi
@JvmName("create")
@Suppress("FunctionNaming", "LongParameterList")
public inline fun <I, S, SE : Any> StoreDecorator(
    store: StoreDecorator<I, S, SE>,
    crossinline allowBootstrap: suspend Sc<I, S, SE>.(Bootstrapper<I, S, SE>) -> Boolean = { true },
    crossinline onStarted: suspend Sc<I, S, SE>.() -> Unit = {},

    crossinline onStateChange: Sc<I, S, SE>.(state: S) -> Unit = {},

    crossinline allowIntent: suspend Sc<I, S, SE>.(intent: I) -> Boolean = { true },
    crossinline allowSideJob: suspend Sc<I, S, SE>.(key: String, wasRestarted: Boolean, SideJob<I, S, SE>) -> Boolean = { _, _, _ -> true },

    crossinline onIntentUndelivered: Sc<I, S, SE>.(intent: I, wasResent: Boolean) -> Unit = { _, _ -> },
    crossinline onSideEffectUndelivered: Sc<I, S, SE>.(sideEffect: SE, wasResent: Boolean) -> Unit = { _, _ -> },

    crossinline onError: Sc<I, S, SE>.(error: Throwable) -> Boolean = { false },
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


        override fun onStateChanged(state: S) {
            super.onStateChanged(state)
            onStateChange(state)
        }


        override suspend fun onIntent(intent: I) {
            if (allowIntent(intent)) {
                super.onIntent(intent)
            }
        }

        override suspend fun onSideJob(key: String, wasRestarted: Boolean, sideJob: SideJob<I, S, SE>) {
            if (allowSideJob(key, wasRestarted, sideJob)) {
                super.onSideJob(key, wasRestarted, sideJob)
            }
        }


        override fun onUndeliveredIntent(intent: I, wasResent: Boolean) {
            super.onUndeliveredIntent(intent, wasResent)
            onIntentUndelivered(intent, wasResent)
        }

        override fun onUndeliveredSideEffect(sideEffect: SE, wasResent: Boolean) {
            super.onUndeliveredSideEffect(sideEffect, wasResent)
            onSideEffectUndelivered(sideEffect, wasResent)
        }


        override fun onUnhandledError(error: Throwable): Boolean {
            return super.onUnhandledError(error) || onError(error)
        }

        override suspend fun onClose(cause: Throwable?) {
            super.onClose(cause)
            onClosed(cause)
        }
    }
}
