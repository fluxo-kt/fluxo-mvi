@file:Suppress("MemberVisibilityCanBePrivate", "NO_EXPLICIT_VISIBILITY_IN_API_MODE")

package kt.fluxo.core.intercept

import kotlinx.coroutines.channels.Channel
import kt.fluxo.core.Store
import kt.fluxo.core.dsl.SideJobScopeLegacy.RestartState
import kt.fluxo.core.dsl.SideJobScopeLegacy.RestartState.Restarted
import kt.fluxo.core.Bootstrapper as B

@Suppress("ArgumentListWrapping")
@Deprecated("For migration")
public sealed class FluxoEvent<Intent, State, SideEffect : Any>(
    public val store: Store<Intent, State, SideEffect>,
) {
    // region Store

    class StoreStarted<I, S, SE : Any>(store: Store<I, S, SE>) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Store started: $store"
    }

    class StoreClosed<I, S, SE : Any>(store: Store<I, S, SE>, val cause: Throwable?) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Store closed: $store, cause=$cause"
    }

    class StateChanged<I, S, SE : Any>(store: Store<I, S, SE>, val state: S) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "State changed: $store, $state"
    }

    class UnhandledError<I, S, SE : Any>(store: Store<I, S, SE>, val e: Throwable) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Uncaught error: $store (${e.message ?: e})"
    }

    // endregion

    // region Bootstrap

    class BootstrapperStarted<I, S, SE : Any>(store: Store<I, S, SE>, val bootstrapper: B<I, S, SE>) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Bootstrapper started: $store, $bootstrapper"
    }

    class BootstrapperCompleted<I, S, SE : Any>(store: Store<I, S, SE>, val bootstrapper: B<I, S, SE>) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Bootstrapper completed: $store, $bootstrapper"
    }

    class BootstrapperCancelled<I, S, SE : Any>(store: Store<I, S, SE>, val bootstrapper: B<I, S, SE>) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Bootstrapper cancelled: $store, $bootstrapper"
    }

    class BootstrapperError<I, S, SE : Any>(store: Store<I, S, SE>, val bootstrapper: B<I, S, SE>, val e: Throwable) :
        FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Bootstrapper error: $store, $bootstrapper (${e.message ?: e})"
    }

    // endregion

    // region Intent

    class IntentQueued<I, S, SE : Any>(store: Store<I, S, SE>, val intent: I) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent queued: $store, $intent"
    }

    class IntentAccepted<I, S, SE : Any>(store: Store<I, S, SE>, val intent: I) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent accepted: $store, $intent"
    }

    class IntentRejected<I, S, SE : Any>(store: Store<I, S, SE>, val stateWhenRejected: S, val intent: I) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent rejected: $store, $intent"
    }

    class IntentHandled<I, S, SE : Any>(store: Store<I, S, SE>, val intent: I) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent handled: $store, $intent"
    }

    class IntentCancelled<I, S, SE : Any>(store: Store<I, S, SE>, val intent: I) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent cancelled: $store, $intent"
    }

    class IntentError<I, S, SE : Any>(store: Store<I, S, SE>, val intent: I, val e: Throwable) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent error: $store, $intent (${e.message ?: e})"
    }

    /**
     * When object transferred via [Channel] from one coroutine to another
     * it can be lost if either send or receive operation cancelled in transit.
     * This event signals about such case for an [intent].
     *
     * See "Undelivered elements" section in [Channel] documentation for details.
     * Also see [GitHub issue](https://github.com/Kotlin/kotlinx.coroutines/issues/1936).
     *
     * @param resent `true` if [intent] successfully resent to the [Channel] and can be delivered later
     */
    class IntentUndelivered<I, S, SE : Any>(store: Store<I, S, SE>, val intent: I, val resent: Boolean) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "Intent undelivered: $store, $intent"
    }

    // endregion

    // region SideEffect

    class SideEffectEmitted<I, S, SE : Any>(store: Store<I, S, SE>, val sideEffect: SE) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "SideEffect emitted: $store, $sideEffect"
    }

    /**
     * When object transferred via [Channel] from one coroutine to another
     * it can be lost if either send or receive operation cancelled in transit.
     * This event signals about such case for a [sideEffect].
     *
     * See "Undelivered elements" section in [Channel] documentation for details.
     * Also see [GitHub issue](https://github.com/Kotlin/kotlinx.coroutines/issues/1936).
     *
     * @param resent `true` if [sideEffect] successfully resent to the [Channel] and can be delivered later
     */
    class SideEffectUndelivered<I, S, SE : Any>(
        store: Store<I, S, SE>,
        val sideEffect: SE,
        val resent: Boolean,
    ) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "SideEffect undelivered: $store, $sideEffect"
    }

    // endregion

    // region Side Jobs

    class SideJobQueued<I, S, SE : Any>(store: Store<I, S, SE>, val key: String) : FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "sideJob queued: $store, $key"
    }

    class SideJobStarted<I, S, SE : Any>(store: Store<I, S, SE>, val key: String, val restartState: RestartState) :
        FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "sideJob ${if (restartState === Restarted) "restarted" else "started"}: $store, $key"
    }

    class SideJobCompleted<I, S, SE : Any>(store: Store<I, S, SE>, val key: String, val restartState: RestartState) :
        FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "sideJob${if (restartState === Restarted) " (restarted)" else ""} completed: $store, $key"
    }

    class SideJobCancelled<I, S, SE : Any>(store: Store<I, S, SE>, val key: String, val restartState: RestartState) :
        FluxoEvent<I, S, SE>(store) {
        override fun toString(): String = "sideJob${if (restartState === Restarted) " (restarted)" else ""} cancelled: $store, $key"
    }

    class SideJobError<I, S, SE : Any>(store: Store<I, S, SE>, val key: String, val restartState: RestartState, val e: Throwable) :
        FluxoEvent<I, S, SE>(store) {
        override fun toString(): String =
            " sideJob${if (restartState === Restarted) " (restarted)" else ""} error: $store, $key (${e.message ?: e})"
    }

    // endregion
}
