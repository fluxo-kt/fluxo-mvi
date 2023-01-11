package kt.fluxo.core.input

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kt.fluxo.core.annotation.CallSuper
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kt.fluxo.core.internal.Closeable
import kotlin.js.JsName

/**
 * Second version of [InputStrategy] system.
 * Full control of how, when and where to execute [Input]s.
 */
@ExperimentalFluxoApi
public interface InputStrategy<in Input> : Closeable {

    public val parallelProcessing: Boolean get() = false

    public val rollbackOnCancellation: Boolean get() = !parallelProcessing

    public val resendUndelivered: Boolean get() = true

    public suspend fun init() {}

    public suspend fun handleInput(request: Input): Job

    override fun close() {}


    @ExperimentalFluxoApi
    public interface Factory {
        @JsName("create")
        public operator fun <Input> invoke(handler: InputStrategyHandler<Input>): InputStrategy<Input>
    }

    @ExperimentalFluxoApi
    public class IntentRequest<out Intent>(
        public val intent: Intent,
        // FIXME: Should be properly cancelled in Lifo and similar strategies!
        public val deferred: CompletableDeferred<Unit>,
    )
}


@ExperimentalFluxoApi
public interface InputStrategyHandler<in Intent> {
    @CallSuper
    @JsName("executeInput")
    public suspend fun executeInput(request: Intent)

    @CallSuper
    @JsName("onUndeliveredIntent")
    public fun onUndeliveredIntent(intent: Intent, wasResent: Boolean)
}
