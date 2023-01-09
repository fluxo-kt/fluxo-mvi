@file:Suppress("TooManyFunctions")

package kt.fluxo.core

import kt.fluxo.core.annotation.CallSuper
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.dsl.SideJobScopeLegacy.RestartState
import kotlin.js.JsName

@ExperimentalFluxoApi
@Deprecated("For migration")
public interface FluxoInterceptor2<I, S, SE : Any> {

    @CallSuper
    public suspend fun onStart()

    @CallSuper
    @JsName("onClosed")
    public suspend fun onClose(cause: Throwable?)


    @CallSuper
    @JsName("onUnhandledError")
    public fun onUnhandledError(error: Throwable)

    @CallSuper
    @JsName("onStateChanged")
    public suspend fun onStateChanged(state: S)


    @CallSuper
    @JsName("bootstrap")
    public suspend fun bootstrap(bootstrapper: Bootstrapper<I, S, SE>)


    @CallSuper
    @JsName("queueIntent")
    public suspend fun queueIntent(intent: I)

    @CallSuper
    @JsName("handleIntent")
    public suspend fun processIntent(intent: I)

    @CallSuper
    @JsName("onUndeliveredIntent")
    public suspend fun onUndeliveredIntent(intent: I)


    @CallSuper
    @JsName("postSideEffect")
    public suspend fun postSideEffect(sideEffect: SE)

    @CallSuper
    @JsName("onUndeliveredSideEffect")
    public suspend fun onUndeliveredSideEffect(sideEffect: SE)


    @CallSuper
    @JsName("queueSideJob")
    public suspend fun <SJ : SideJob<I, S, SE>> queueSideJob(key: String, sideJob: SJ)

    @CallSuper
    @JsName("sideJob")
    public suspend fun <SJ : SideJob<I, S, SE>> sideJob(
        key: String,
        sideJob: SJ,
        restartState: RestartState,
        proceed: suspend (SJ) -> Unit,
    )
}
