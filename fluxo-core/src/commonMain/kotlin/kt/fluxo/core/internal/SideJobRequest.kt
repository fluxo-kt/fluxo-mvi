package kt.fluxo.core.internal

import kotlinx.coroutines.CoroutineStart
import kt.fluxo.core.SideJob
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmField

internal class SideJobRequest<out Intent, State, out SideEffect : Any>(
    @JvmField val key: String,
    @JvmField val parent: Any?,
    @JvmField val context: CoroutineContext = EmptyCoroutineContext,
    @JvmField val start: CoroutineStart = CoroutineStart.DEFAULT,
    @JvmField val block: SideJob<Intent, State, SideEffect>,
) {
    internal companion object {
        internal const val DEFAULT_SIDE_JOB = "default"
        internal const val BOOTSTRAPPER_SIDE_JOB = "bootstrapper"
        internal const val DEFAULT_REPEAT_ON_SUBSCRIPTION_JOB = "repeatOnSubscription"
    }
}
