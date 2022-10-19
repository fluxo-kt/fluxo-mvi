package kt.fluxo.core

import kt.fluxo.core.dsl.SideJobScope

@Suppress("MemberVisibilityCanBePrivate")
public sealed class FluxoEvent<Intent, State, SideEffect : Any>(
    public val store: Store<Intent, State, SideEffect>,
) {
    // region Store

    public class StoreStarted<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Store started: $store"
        }
    }

    public class StoreCleared<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val cause: Throwable?,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Store cleared: $store"
        }
    }

    // endregion

    // region Intent

    public class IntentQueued<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val intent: Intent,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Intent Queued: $intent"
        }
    }

    public class IntentAccepted<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val intent: Intent,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Accepting intent: $intent"
        }
    }

    public class IntentRejected<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val stateWhenRejected: State,
        public val intent: Intent,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Rejecting intent: $intent"
        }
    }

    public class IntentDropped<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val intent: Intent,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Dropping intent: $intent"
        }
    }

    public class IntentHandledSuccessfully<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val intent: Intent,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Intent handled successfully: $intent"
        }
    }

    public class IntentCancelled<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val intent: Intent,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Intent cancelled: $intent"
        }
    }

    public class IntentHandlerError<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val intent: Intent,
        public val throwable: Throwable,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Error handling intent: $intent (${throwable.message})"
        }
    }

    // endregion

    // region SideEffect

    public class SideEffectQueued<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val sideEffect: SideEffect,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "SideEffect Queued: $sideEffect"
        }
    }

    public class SideEffectEmitted<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val sideEffect: SideEffect,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Emitting SideEffect: $sideEffect"
        }
    }

    // endregion

    // region States

    public class StateChanged<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val state: State,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "State changed: $state"
        }
    }

    // endregion

    // region Side Jobs

    public class SideJobQueued<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val key: String,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "sideJob queued: $key"
        }
    }

    public class SideJobStarted<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val key: String,
        public val restartState: SideJobScope.RestartState,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return when (restartState) {
                SideJobScope.RestartState.Initial -> "sideJob started: $key"
                SideJobScope.RestartState.Restarted -> "sideJob restarted: $key"
            }
        }
    }

    public class SideJobCompleted<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val key: String,
        public val restartState: SideJobScope.RestartState,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "sideJob finished: $key"
        }
    }

    public class SideJobCancelled<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val key: String,
        public val restartState: SideJobScope.RestartState,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "sideJob cancelled: $key"
        }
    }

    public class SideJobError<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val key: String,
        public val restartState: SideJobScope.RestartState,
        public val throwable: Throwable,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Error in sideJob: $key (${throwable.message})"
        }
    }

    // endregion

    // region Other

    public class UnhandledError<Intent, State, SideEffect : Any>(
        store: Store<Intent, State, SideEffect>,
        public val throwable: Throwable,
    ) : FluxoEvent<Intent, State, SideEffect>(store) {
        override fun toString(): String {
            return "Uncaught error (${throwable.message})"
        }
    }

    // endregion
}