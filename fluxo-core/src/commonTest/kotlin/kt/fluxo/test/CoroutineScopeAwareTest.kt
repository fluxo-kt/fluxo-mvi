package kt.fluxo.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest

internal open class CoroutineScopeAwareTest(
    context: CoroutineContext = Job(),
) {
    internal companion object {
        internal const val INIT = "init"
    }

    protected val scope = CoroutineScope(context)

    @AfterTest
    open fun afterTest() {
        scope.cancel()
    }
}
