package kt.fluxo.core.internal

import kotlinx.coroutines.Job
import kotlin.jvm.Volatile

internal class RunningSideJob(
    @Volatile
    internal var job: Job?,
) {
    internal companion object {
        internal const val DEFAULT_SIDE_JOB = "default"
        internal const val BOOTSTRAPPER_SIDE_JOB = "bootstrapper"
        internal const val DEFAULT_REPEAT_ON_SUBSCRIPTION_JOB = "repeatOnSubscription"
    }
}
