package kt.fluxo.core.intercept

import kotlinx.coroutines.CompletableDeferred
import kt.fluxo.core.annotation.ExperimentalFluxoApi

@ExperimentalFluxoApi
@Deprecated("For migration")
public sealed interface StoreRequest<out Intent, out State> {

    /**
     * A request to forcibly set the [State] to a specific [value][state].
     */
    @Deprecated("For removal", level = DeprecationLevel.ERROR)
    public class RestoreState<Intent, out State>(
        public val state: State,
        public val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : StoreRequest<Intent, State>

    /**
     * A request to handle an [intent].
     */
    @Deprecated("For migration")
    public class HandleIntent<out Intent, State>(
        public val intent: Intent,
        public val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : StoreRequest<Intent, State>
}
