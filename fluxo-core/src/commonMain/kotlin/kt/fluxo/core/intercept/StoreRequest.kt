package kt.fluxo.core.intercept

import kotlinx.coroutines.CompletableDeferred
import kt.fluxo.core.annotation.ExperimentalFluxoApi

@ExperimentalFluxoApi
@Deprecated("For migration")
public sealed interface StoreRequest<out Intent, out State> {

    /**
     * A request to handle an [intent].
     */
    @Deprecated("For migration")
    public class HandleIntent<out Intent, State>(
        public val intent: Intent,
        public val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : StoreRequest<Intent, State>
}
