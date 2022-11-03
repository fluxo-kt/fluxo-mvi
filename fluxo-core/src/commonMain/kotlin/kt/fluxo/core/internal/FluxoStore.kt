package kt.fluxo.core.internal

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kt.fluxo.core.Bootstrapper
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.SideEffectsStrategy
import kt.fluxo.core.Store
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.data.GuaranteedEffect
import kt.fluxo.core.debug.DEBUG
import kt.fluxo.core.debug.debugIntentWrapper
import kt.fluxo.core.dsl.InputStrategyScope
import kt.fluxo.core.dsl.SideJobScope.RestartState
import kt.fluxo.core.intercept.FluxoEvent
import kt.fluxo.core.intercept.StoreRequest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@PublishedApi
@InternalFluxoApi
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
internal class FluxoStore<Intent, State, SideEffect : Any>(
    initialState: State,
    private val intentHandler: IntentHandler<Intent, State, SideEffect>,
    private val conf: FluxoConf<Intent, State, SideEffect>,
) : Store<Intent, State, SideEffect> {

    private companion object {
        private const val F = "Fluxo"

        private const val DELAY_TO_CLOSE_INTERCEPTOR_SCOPE_MILLIS = 5_000L
    }

    override val name: String get() = conf.name

    private val scope: CoroutineScope
    private val intentContext: CoroutineContext
    private val sideJobScope: CoroutineScope
    private val interceptorScope: CoroutineScope
    private val requestsChannel: Channel<StoreRequest<Intent, State>>

    private val sideJobsMap = ConcurrentHashMap<String, RunningSideJob<Intent, State, SideEffect>>()

    private val mutableState = MutableStateFlow(initialState)
    override val stateFlow: StateFlow<State> get() = mutableState

    private val sideEffectChannel: Channel<SideEffect>?
    private val sideEffectFlowField: Flow<SideEffect>?
    override val sideEffectFlow: Flow<SideEffect>
        get() = sideEffectFlowField ?: error("Side effects are disabled for the current store: $name")

    private val events = MutableSharedFlow<FluxoEvent<Intent, State, SideEffect>>(extraBufferCapacity = 256)

    override val isActive: Boolean
        get() = scope.isActive

    private val initialised = atomic(false)

    @OptIn(InternalCoroutinesApi::class)
    private val cancellationCause get() = scope.coroutineContext[Job]?.getCancellationException()

    init {
        val ctx = conf.eventLoopContext + when {
            !conf.debugChecks -> EmptyCoroutineContext
            else -> CoroutineName(toString())
        }

        val parent = ctx[Job]
        val job = if (conf.closeOnExceptions) Job(parent) else SupervisorJob(parent)
        job.invokeOnCompletion(::onShutdown)

        val parentExceptionHandler = conf.exceptionHandler ?: ctx[CoroutineExceptionHandler]
        val exceptionHandler = CoroutineExceptionHandler { context, e ->
            events.tryEmit(FluxoEvent.UnhandledError(this, e))
            parentExceptionHandler?.handleException(context, e) ?: throw e
        }
        scope = CoroutineScope(ctx + exceptionHandler + job)
        intentContext = scope.coroutineContext + conf.intentContext + when {
            !conf.debugChecks -> EmptyCoroutineContext
            else -> CoroutineName("$F[$name:intentScope]")
        }
        sideJobScope = scope + conf.sideJobsContext + exceptionHandler + SupervisorJob(job) + when {
            !conf.debugChecks -> EmptyCoroutineContext
            else -> CoroutineName("$F[$name:sideJobScope]")
        }
        // Shouldn't close immediately with scope, when interceptors set
        interceptorScope = if (conf.interceptors.isEmpty()) scope else {
            scope + conf.interceptorContext + SupervisorJob() + when {
                !conf.debugChecks -> EmptyCoroutineContext
                else -> CoroutineName("$F[$name:interceptorScope]")
            }
        }

        // Leak-free transfer via channel
        // https://github.com/Kotlin/kotlinx.coroutines/issues/1936
        // — send operation cancelled before it had a chance to actually send the element.
        // — receive operation retrieved the element from the channel but cancelled when trying to return it the caller.
        // — channel cancelled, in which case onUndeliveredElement called on every remaining element in the channel's buffer.
        // See "Undelivered elements" section in Channel documentation for details.
        requestsChannel = conf.inputStrategy.createQueue {
            scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
                when (it) {
                    is StoreRequest.HandleIntent -> {
                        @Suppress("UNINITIALIZED_VARIABLE")
                        val resent = if (requestsChannel.trySend(it).isSuccess) true else {
                            it.intent.closeSafely()
                            false
                        }
                        if (isActive) {
                            events.emit(FluxoEvent.IntentUndelivered(this@FluxoStore, it.intent, resent = resent))
                        }
                    }

                    is StoreRequest.RestoreState -> {
                        if (isActive) {
                            updateState(it.state)
                        }
                    }
                }
            }
        }

        // Prepare side effects handling, considering all available strategies
        var subscriptionCount = mutableState.subscriptionCount
        val sideEffectsSubscriptionCount: StateFlow<Int>?
        val sideEffectStrategy = conf.sideEffectsStrategy
        if (sideEffectStrategy === SideEffectsStrategy.DISABLE) {
            // Disable side effects
            sideEffectChannel = null
            sideEffectFlowField = null
            sideEffectsSubscriptionCount = null
        } else if (sideEffectStrategy is SideEffectsStrategy.SHARE) {
            // MutableSharedFlow-based SideEffect
            sideEffectChannel = null
            val flow = MutableSharedFlow<SideEffect>(
                replay = sideEffectStrategy.replay,
                extraBufferCapacity = conf.sideEffectBufferSize,
                onBufferOverflow = BufferOverflow.SUSPEND,
            )
            sideEffectsSubscriptionCount = flow.subscriptionCount
            sideEffectFlowField = flow
        } else {
            val channel = Channel<SideEffect>(conf.sideEffectBufferSize, BufferOverflow.SUSPEND) {
                scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
                    @Suppress("UNINITIALIZED_VARIABLE")
                    val resent = if (sideEffectChannel!!.trySend(it).isSuccess) true else {
                        it.closeSafely()
                        false
                    }
                    if (isActive) {
                        events.emit(FluxoEvent.SideEffectUndelivered(this@FluxoStore, it, resent = resent))
                    }
                }
            }
            sideEffectChannel = channel
            sideEffectsSubscriptionCount = MutableStateFlow(0)
            val flow = if (sideEffectStrategy === SideEffectsStrategy.CONSUME) channel.consumeAsFlow() else {
                check(!conf.debugChecks || sideEffectStrategy === SideEffectsStrategy.RECEIVE)
                channel.receiveAsFlow()
            }
            sideEffectFlowField = SubscriptionCountFlow(sideEffectsSubscriptionCount, flow)
        }
        if (sideEffectsSubscriptionCount != null) {
            subscriptionCount = subscriptionCount.plusIn(interceptorScope, sideEffectsSubscriptionCount)
        }

        // Start the Store
        if (!conf.lazy) {
            // Eager start
            start()
        } else {
            // Lazy start on state subscription
            val subscriptions = subscriptionCount
            scope.launch {
                // start on the first subscriber.
                subscriptions.first { it > 0 }
                start()
            }
        }
    }


    override fun start() {
        if (!isActive) {
            throw StoreClosedException("Store is closed, it cannot be restarted", cancellationCause?.let { it.cause ?: it })
        }
        if (initialised.compareAndSet(expect = false, update = true)) {
            launch()
        }
    }

    override fun close() {
        scope.cancel()
    }

    override suspend fun sendAsync(intent: Intent): Deferred<Unit> {
        start()
        val i = if (DEBUG || conf.debugChecks) debugIntentWrapper(intent) else intent
        val deferred = CompletableDeferred<Unit>()
        requestsChannel.send(StoreRequest.HandleIntent(deferred, i))
        events.emit(FluxoEvent.IntentQueued(this, i))
        return deferred
    }

    override fun send(intent: Intent) {
        start()
        val i = if (DEBUG || conf.debugChecks) debugIntentWrapper(intent) else intent
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            @Suppress("DeferredResultUnused") sendAsync(i)
        }
    }


    override fun toString(): String = "$F[$name]"


    // region Internals

    private suspend fun updateState(nextValue: State): State = updateStateAndGet { nextValue }

    private suspend fun updateStateAndGet(function: (State) -> State): State {
        while (true) {
            val prevValue = mutableState.value
            val nextValue = function(prevValue)
            if (mutableState.compareAndSet(prevValue, nextValue)) {
                if (prevValue != nextValue) {
                    // event fired only if state changed
                    events.emit(FluxoEvent.StateChanged(this, nextValue))
                    prevValue.closeSafely()
                }
                return nextValue
            }
        }
    }

    private suspend fun postSideEffect(sideEffect: SideEffect) {
        if (!scope.isActive) {
            sideEffect.closeSafely()
            events.emit(FluxoEvent.SideEffectUndelivered(this@FluxoStore, sideEffect, resent = false))
        } else try {
            if (sideEffect is GuaranteedEffect<*>) {
                sideEffect.setResendFunction(::postSideEffectSync)
            }
            sideEffectChannel?.send(sideEffect)
                ?: (sideEffectFlowField as MutableSharedFlow).emit(sideEffect)
            events.emit(FluxoEvent.SideEffectEmitted(this@FluxoStore, sideEffect))
        } catch (e: Throwable) {
            sideEffect.closeSafely()
            events.emit(FluxoEvent.SideEffectUndelivered(this@FluxoStore, sideEffect, resent = false))
            handleException(e, currentCoroutineContext())
            events.emit(FluxoEvent.UnhandledError(this@FluxoStore, e))
        }
    }

    private fun postSideEffectSync(sideEffect: SideEffect) {
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            postSideEffect(sideEffect)
        }
    }

    private fun handleException(e: Throwable, context: CoroutineContext) {
        if (!conf.closeOnExceptions) {
            val handler = scope.coroutineContext[CoroutineExceptionHandler]
            if (handler != null) {
                handler.handleException(context, e)
                return
            }
        }
        throw e
    }

    private suspend fun Any?.closeSafely() {
        if (this is Closeable) {
            try {
                close() // Close if Closeable resource
            } catch (e: Throwable) {
                handleException(e, currentCoroutineContext())
                events.emit(FluxoEvent.UnhandledError(this@FluxoStore, e))
            }
        }
    }

    /** Will be called only once for each [FluxoStore] */
    private fun launch() = scope.launch {
        // observe and process intents
        val requestsFlow = requestsChannel.receiveAsFlow()
        val inputStrategy = conf.inputStrategy
        val inputStrategyScope: InputStrategyScope<StoreRequest<Intent, State>> = { request ->
            when (request) {
                is StoreRequest.HandleIntent -> {
                    if (conf.debugChecks) {
                        withContext(CoroutineName("$F[$name <= Intent ${request.intent}]")) {
                            safelyHandleIntent(request.intent, request.deferred)
                        }
                    } else {
                        safelyHandleIntent(request.intent, request.deferred)
                    }
                }

                is StoreRequest.RestoreState -> {
                    updateState(request.state)
                    request.deferred?.complete(Unit)
                }
            }
        }
        scope.launch(intentContext) {
            with(inputStrategy) {
                inputStrategyScope.processRequests(requestsFlow)
            }
        }

        // launch interceptors handling (stop on StoreClosed)
        if (conf.interceptors.isNotEmpty()) {
            val interceptorScope = InterceptorScopeImpl(
                storeName = name,
                sendRequest = requestsChannel::send,
                coroutineScope = interceptorScope,
            )
            val eventsFlow: Flow<FluxoEvent<Intent, State, SideEffect>> = events.transformWhile {
                emit(it)
                it !is FluxoEvent.StoreClosed
            }
            for (interceptor in conf.interceptors) {
                with(interceptor) {
                    try {
                        interceptorScope.start(eventsFlow)
                    } catch (e: Throwable) {
                        handleException(e, currentCoroutineContext())
                        events.emit(FluxoEvent.UnhandledError(this@FluxoStore, e))
                    }
                }
            }
        }
        events.emit(FluxoEvent.StoreStarted(this@FluxoStore))

        // bootstrap
        conf.bootstrapper?.let { bootstrapper ->
            safelyRunBootstrapper(bootstrapper)
        }
    }

    private suspend fun safelyHandleIntent(intent: Intent, deferred: CompletableDeferred<Unit>?) {
        val stateBeforeCancellation = mutableState.value
        try {
            // Apply an intent filter before processing, if defined
            conf.intentFilter?.also { accept ->
                if (!accept(stateBeforeCancellation, intent)) {
                    events.emit(FluxoEvent.IntentRejected(this, stateBeforeCancellation, intent))
                    return
                }
            }

            events.emit(FluxoEvent.IntentAccepted(this, intent))
            val handlerScope = StoreScopeImpl(
                job = intent,
                guardian = when {
                    !conf.debugChecks -> null
                    else -> InputStrategyGuardian(parallelProcessing = conf.inputStrategy.parallelProcessing)
                },
                getState = mutableState::value,
                updateStateAndGet = ::updateStateAndGet,
                sendSideEffect = ::postSideEffect,
                sendSideJob = ::postSideJob,
            )
            with(handlerScope) {
                with(intentHandler) {
                    handleIntent(intent)
                }
            }
            handlerScope.close()
            events.emit(FluxoEvent.IntentHandled(this@FluxoStore, intent))
        } catch (_: CancellationException) {
            // reset state as intent did not complete successfully
            if (conf.inputStrategy.rollbackOnCancellation) {
                updateState(stateBeforeCancellation)
            }
            events.emit(FluxoEvent.IntentCancelled(this, intent))
        } catch (e: Throwable) {
            handleException(e, currentCoroutineContext())
            events.emit(FluxoEvent.IntentError(this, intent, e))
        } finally {
            deferred?.complete(Unit)
        }
    }

    private suspend fun postSideJob(request: SideJobRequest<Intent, State, SideEffect>) {
        events.emit(FluxoEvent.SideJobQueued(this@FluxoStore, request.key))

        val key = request.key
        val block = request.block

        val map = sideJobsMap
        val prevJob = map[key]
        val restartState = if (prevJob != null) RestartState.Restarted else RestartState.Initial

        // Cancel if we have a sideJob already running
        prevJob?.run {
            job?.cancel()
            job = null
        }

        // Go through and remove any sideJobs that have completed (either by
        // cancellation or because they finished on their own)
        map.entries.filterNot { it.value.job?.isActive == true }.map { it.key }.forEach { map.remove(it) }

        // Launch a new sideJob in its own isolated coroutine scope where:
        //   1) cancelled when the scope or sideJobScope cancelled
        //   2) errors caught for crash reporting
        //   3) has a supervisor job, so we can cancel the sideJob without cancelling whole store scope
        //
        // Consumers of this sideJob can launch many jobs, and all will be cancelled together when the
        // sideJob restarted, or the store scope cancelled.
        map[key] = RunningSideJob(
            request = request,
            job = sideJobScope.launch(
                context = when {
                    !conf.debugChecks -> EmptyCoroutineContext
                    else -> CoroutineName("$F[$name SideJob $key <= Intent ${request.parent}]")
                }
            ) {
                try {
                    val sideJobScope = SideJobScopeImpl(
                        sendIntent = ::sendAsync,
                        sendSideEffect = ::postSideEffect,
                        currentStateWhenStarted = mutableState.value,
                        restartState = restartState,
                        coroutineScope = this,
                    )
                    events.emit(FluxoEvent.SideJobStarted(this@FluxoStore, key, restartState))

                    // Await all children completion with coroutineScope
                    coroutineScope {
                        sideJobScope.block()
                    }
                    events.emit(FluxoEvent.SideJobCompleted(this@FluxoStore, key, restartState))
                } catch (_: CancellationException) {
                    events.emit(FluxoEvent.SideJobCancelled(this@FluxoStore, key, restartState))
                } catch (e: Throwable) {
                    handleException(e, currentCoroutineContext())
                    events.emit(FluxoEvent.SideJobError(this@FluxoStore, key, restartState, e))
                }
            }
        )
    }

    private suspend fun safelyRunBootstrapper(bootstrapper: Bootstrapper<Intent, State, SideEffect>) = withContext(intentContext) {
        events.emit(FluxoEvent.BootstrapperStarted(this@FluxoStore, bootstrapper))
        try {
            val bootstrapperScope = BootstrapperScopeImpl(
                bootstrapper = bootstrapper,
                guardian = if (conf.debugChecks) InputStrategyGuardian(parallelProcessing = false) else null,
                getState = mutableState::value,
                updateStateAndGet = ::updateStateAndGet,
                sendIntent = ::sendAsync,
                sendSideEffect = ::postSideEffect,
                sendSideJob = ::postSideJob,
            )

            // Await all children completion with coroutineScope
            coroutineScope {
                bootstrapperScope.bootstrapper()
            }
            bootstrapperScope.close()
            events.emit(FluxoEvent.BootstrapperCompleted(this@FluxoStore, bootstrapper))
        } catch (_: CancellationException) {
            events.emit(FluxoEvent.BootstrapperCancelled(this@FluxoStore, bootstrapper))
        } catch (e: Throwable) {
            handleException(e, currentCoroutineContext())
            events.emit(FluxoEvent.BootstrapperError(this@FluxoStore, bootstrapper, e))
        }
    }

    /**
     * Called on [scope] completion for any reason.
     */
    private fun onShutdown(cause: Throwable?) {
        events.tryEmit(FluxoEvent.StoreClosed(this, cause))

        val ce = cause.toCancellationException()
        for (value in sideJobsMap.values) {
            value.job?.cancel(ce)
            value.job = null
        }
        sideJobsMap.clear()

        // Close and clear state & side effects machinery.
        @OptIn(ExperimentalCoroutinesApi::class)
        interceptorScope.launch(Dispatchers.Unconfined + Job(), CoroutineStart.UNDISPATCHED) {
            mutableState.value.closeSafely()
            (sideEffectFlowField as? MutableSharedFlow)?.apply {
                val replayCache = replayCache
                resetReplayCache()
                replayCache.forEach { it.closeSafely() }
            }
            sideEffectChannel?.apply {
                while (!isEmpty) {
                    receiveCatching().getOrNull()?.closeSafely()
                }
                close(ce)
            }
        }

        // Interceptor scope shouldn't be closed immediately with scope, when interceptors set.
        // It allows to process final events in interceptors.
        // Cancel it with a delay.
        val interceptorScope = interceptorScope
        if (interceptorScope !== scope && interceptorScope.isActive) {
            val cancellationCause = cancellationCause
            interceptorScope.launch(start = CoroutineStart.UNDISPATCHED) {
                delay(DELAY_TO_CLOSE_INTERCEPTOR_SCOPE_MILLIS)
                interceptorScope.cancel(cancellationCause)
            }
        }
    }

    // endregion
}
