@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught")

package kt.fluxo.core.internal

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kt.fluxo.core.Bootstrapper
import kt.fluxo.core.FluxoClosedException
import kt.fluxo.core.FluxoSettings
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.SideEffectsStrategy
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.data.GuaranteedEffect
import kt.fluxo.core.debug.DEBUG
import kt.fluxo.core.debug.debugIntentWrapper
import kt.fluxo.core.dsl.InputStrategyScope
import kt.fluxo.core.dsl.SideJobScopeLegacy.RestartState
import kt.fluxo.core.factory.StoreDecorator
import kt.fluxo.core.intercept.StoreRequest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@InternalFluxoApi
internal class FluxoStore<Intent, State, SideEffect : Any>(
    initialState: State,
    private val intentHandler: IntentHandler<Intent, State, SideEffect>,
    conf: FluxoSettings<Intent, State, SideEffect>,
) : StoreDecorator<Intent, State, SideEffect> {

    private companion object {
        private const val F = "Fluxo"
        private val storeNumber = atomic(0)
    }

    private val mutableState = MutableStateFlow(initialState)

    // TODO: Generate name from Store host with reflection in debug mode?
    override val name: String = conf.name ?: "store#${storeNumber.getAndIncrement()}"

    override val coroutineContext: CoroutineContext

    private val sideJobScope: CoroutineScope

    private val requestsChannel: Channel<StoreRequest<Intent, State>>

    private val sideJobsMap = ConcurrentHashMap<String, RunningSideJob<Intent, State, SideEffect>>()

    private val sideEffectChannel: Channel<SideEffect>?
    private val sideEffectFlowField: Flow<SideEffect>?
    override val sideEffectFlow: Flow<SideEffect>
        get() = sideEffectFlowField ?: error("Side effects are disabled for the current store: $name")

    override val subscriptionCount: StateFlow<Int>

    private val initialised = atomic(false)

    private val debugChecks = conf.debugChecks
    private val closeOnExceptions = conf.closeOnExceptions
    private val inputStrategy = conf.inputStrategy
    private val bootstrapper = conf.bootstrapper
    private val intentFilter = conf.intentFilter
    private val coroutineStart = if (!conf.optimized) CoroutineStart.DEFAULT else CoroutineStart.UNDISPATCHED

    /**
     * @throws IllegalStateException when invoked while [coroutineContext] still active.
     */
    @OptIn(InternalCoroutinesApi::class)
    private val cancellationCause get() = coroutineContext[Job]?.getCancellationException()

    init {
        val ctx = conf.coroutineContext + (conf.scope?.coroutineContext ?: EmptyCoroutineContext) + when {
            !debugChecks -> EmptyCoroutineContext
            else -> CoroutineName(toString())
        }

        val parent = ctx[Job]
        val job = if (closeOnExceptions) Job(parent) else SupervisorJob(parent)
        job.invokeOnCompletion(::onShutdown)

        val parentExceptionHandler = conf.exceptionHandler ?: ctx[CoroutineExceptionHandler]
        val exceptionHandler = CoroutineExceptionHandler { context, e ->
            parentExceptionHandler?.handleException(context, e)
            if (closeOnExceptions) {
                try {
                    val ce = CancellationException("Uncaught exception: $e", e)
                    @Suppress("UNINITIALIZED_VARIABLE")
                    this@FluxoStore.coroutineContext.cancel(ce)
                    context.cancel(ce)
                } catch (e2: Throwable) {
                    e2.addSuppressed(e)
                    throw e2
                }
            }
        }
        coroutineContext = ctx + exceptionHandler + job
        val scope: CoroutineScope = this

        sideJobScope = if (intentHandler is ReducerIntentHandler && bootstrapper == null) {
            // Minor optimization as side jobs are not available when bootstrapper is not set and ReducerIntentHandler is used.
            scope
        } else {
            scope + conf.sideJobsContext + exceptionHandler + SupervisorJob(job) + when {
                !debugChecks -> EmptyCoroutineContext
                else -> CoroutineName("$F[$name:sideJobScope]")
            }
        }

        // Leak-free transfer via channel
        // https://github.com/Kotlin/kotlinx.coroutines/issues/1936
        // See "Undelivered elements" section in Channel documentation for details.
        //
        // Handled cases:
        // — sending operation cancelled before it had a chance to actually send the element.
        // — receiving operation retrieved the element from the channel cancelled when trying to return it the caller.
        // — channel cancelled, in which case onUndeliveredElement called on every remaining element in the channel's buffer.
        val intentResendLock = if (inputStrategy.resendUndelivered) Mutex() else null
        requestsChannel = inputStrategy.createQueue {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                when (it) {
                    is StoreRequest.HandleIntent -> {
                        // We don't want to fall into the recursion, so only one resending per moment.
                        var resent = false
                        if (isActive && intentResendLock != null && intentResendLock.tryLock()) {
                            try {
                                @Suppress("UNINITIALIZED_VARIABLE")
                                resent = requestsChannel.trySend(it).isSuccess
                            } catch (_: Throwable) {
                            } finally {
                                intentResendLock.unlock()
                            }
                        }
                        if (!resent) {
                            it.intent.closeSafely()
                            it.deferred.cancel()
                        }
                    }

                    is StoreRequest.RestoreState -> updateState(it)
                }
            }
        }

        // Prepare side effects handling, considering all available strategies
        var subscriptionCount = mutableState.subscriptionCount
        val sideEffectsSubscriptionCount: StateFlow<Int>?
        val sideEffectsStrategy = conf.sideEffectsStrategy
        if (sideEffectsStrategy === SideEffectsStrategy.DISABLE) {
            // Disable side effects
            sideEffectChannel = null
            sideEffectFlowField = null
            sideEffectsSubscriptionCount = null
        } else if (sideEffectsStrategy is SideEffectsStrategy.SHARE) {
            // MutableSharedFlow-based SideEffect
            sideEffectChannel = null
            val flow = MutableSharedFlow<SideEffect>(
                replay = sideEffectsStrategy.replay,
                extraBufferCapacity = conf.sideEffectBufferSize,
                onBufferOverflow = BufferOverflow.SUSPEND,
            )
            sideEffectsSubscriptionCount = flow.subscriptionCount
            sideEffectFlowField = flow
        } else {
            val seResendLock = Mutex()
            val channel = Channel<SideEffect>(conf.sideEffectBufferSize, BufferOverflow.SUSPEND) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // We don't want to fall into the recursion, so only one resending per moment.
                    var resent = false
                    if (isActive && seResendLock.tryLock()) {
                        try {
                            @Suppress("UNINITIALIZED_VARIABLE")
                            resent = sideEffectChannel!!.trySend(it).isSuccess
                        } catch (_: Throwable) {
                        } finally {
                            seResendLock.unlock()
                        }
                    }
                    if (!resent) {
                        it.closeSafely()
                    }
                }
            }
            sideEffectChannel = channel
            sideEffectsSubscriptionCount = MutableStateFlow(0)
            val flow = if (sideEffectsStrategy === SideEffectsStrategy.CONSUME) {
                channel.consumeAsFlow()
            } else {
                check(!debugChecks || sideEffectsStrategy === SideEffectsStrategy.RECEIVE)
                channel.receiveAsFlow()
            }
            sideEffectFlowField = SubscriptionCountFlow(sideEffectsSubscriptionCount, flow)
        }
        if (sideEffectsSubscriptionCount != null) {
            subscriptionCount = subscriptionCount
                .plusIn(this + Dispatchers.Unconfined, SharingStarted.Eagerly, sideEffectsSubscriptionCount)
        }
        this.subscriptionCount = subscriptionCount

        // Start the Store
        if (!conf.lazy) {
            // Eager start
            start()
        } else {
            // Lazy start on state subscription
            val subscriptions = subscriptionCount
            scope.launch(
                when {
                    !debugChecks -> EmptyCoroutineContext
                    else -> CoroutineName("$F[$name:lazyStart]")
                },
                start = coroutineStart,
            ) {
                // start on the first subscriber.
                subscriptions.first { it > 0 }
                if (isActive && this@FluxoStore.isActive) {
                    start()
                }
            }
        }
    }


    override fun start(): Job? {
        if (!isActive) {
            throw FluxoClosedException(
                "Store is closed, it cannot be restarted",
                cancellationCause?.let { it.cause ?: it },
            )
        }
        if (initialised.compareAndSet(expect = false, update = true)) {
            return launch()
        }
        return null
    }

    override suspend fun emit(value: Intent) {
        start()
        send(if (DEBUG && debugChecks) debugIntentWrapper(value) else value, null)
    }

    private suspend fun send(intent: Intent, deferred: CompletableDeferred<Unit>?): Job {
        val d = deferred ?: CompletableDeferred()
        requestsChannel.send(StoreRequest.HandleIntent(intent, d))
        return d
    }

    override fun send(intent: Intent): Job {
        start()
        val i = if (DEBUG && debugChecks) debugIntentWrapper(intent) else intent
        val deferred = CompletableDeferred<Unit>()
        launch(start = CoroutineStart.UNDISPATCHED) {
            send(i, deferred)
        }
        return deferred
    }


    override fun toString(): String = "$F[$name]"


    // region Internals

    private suspend fun updateState(request: StoreRequest.RestoreState<Intent, State>): State {
        try {
            val state = updateState(request.state)
            request.deferred.complete(Unit)
            return state
        } catch (e: Throwable) {
            request.deferred.cancel(e.toCancellationException())
            throw e
        }
    }

    private suspend fun updateState(nextValue: State): State = updateStateAndGet { nextValue }

    private suspend fun updateStateAndGet(function: (State) -> State): State {
        while (true) {
            val prevValue = mutableState.value
            val nextValue = function(prevValue)
            if (mutableState.compareAndSet(prevValue, nextValue)) {
                if (prevValue != nextValue) {
                    prevValue.closeSafely()
                }
                return nextValue
            }
            // TODO: we may need to close nextValue here
            currentCoroutineContext().ensureActive()
        }
    }

    override suspend fun postSideEffect(sideEffect: SideEffect) {
        try {
            if (!isActive) {
                throw cancellationCause ?: CancellationException(F)
            }
            if (sideEffect is GuaranteedEffect<*>) {
                sideEffect.setResendFunction(::postSideEffectSync)
            }
            sideEffectChannel?.send(sideEffect)
                ?: (sideEffectFlowField as MutableSharedFlow).emit(sideEffect)
        } catch (e: CancellationException) {
            sideEffect.closeSafely(e.cause)
        } catch (e: Throwable) {
            sideEffect.closeSafely(e)
            handleException(e, currentCoroutineContext())
        }
    }

    private fun postSideEffectSync(sideEffect: SideEffect) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            postSideEffect(sideEffect)
        }
    }

    private fun handleException(e: Throwable, context: CoroutineContext) {
        if (!closeOnExceptions) {
            val handler = coroutineContext[CoroutineExceptionHandler]
            if (handler != null) {
                handler.handleException(context, e)
                return
            }
        }
        throw e
    }

    private suspend fun Any?.closeSafely(cause: Throwable? = null) {
        if (this is Closeable) {
            try {
                close() // Close if Closeable resource
            } catch (e: Throwable) {
                if (cause != null) {
                    e.addSuppressed(cause)
                }
                handleException(e, currentCoroutineContext())
            }
        }
    }

    /** Will be called only once for each [FluxoStore] */
    private fun launch() = launch(
        when {
            !debugChecks -> EmptyCoroutineContext
            else -> CoroutineName("$F[$name:launch]")
        },
        start = coroutineStart,
    ) {
        // observe and process intents
        val requestsFlow = requestsChannel.receiveAsFlow()
        val inputStrategy = inputStrategy
        val inputStrategyScope: InputStrategyScope<StoreRequest<Intent, State>> = { request ->
            when (request) {
                is StoreRequest.HandleIntent -> {
                    if (debugChecks) {
                        withContext(CoroutineName("$F[$name <= Intent ${request.intent}]")) {
                            safelyHandleIntent(request.intent, request.deferred)
                        }
                    } else {
                        safelyHandleIntent(request.intent, request.deferred)
                    }
                }

                is StoreRequest.RestoreState -> updateState(request)
            }
        }
        launch(start = coroutineStart) {
            with(inputStrategy) {
                inputStrategyScope.processRequests(requestsFlow)
            }
        }

        // bootstrap
        bootstrapper?.let { bootstrapper ->
            safelyRunBootstrapper(bootstrapper)
        }
    }

    private suspend fun safelyHandleIntent(intent: Intent, deferred: CompletableDeferred<Unit>?) {
        val stateBeforeCancellation = mutableState.value
        try {
            // Apply an intent filter before processing, if defined
            intentFilter?.also { accept ->
                if (!accept(stateBeforeCancellation, intent)) {
                    return
                }
            }

            with(intentHandler) {
                // Await all children completions with coroutineScope
                coroutineScope {
                    val storeScope = StoreScopeImpl(
                        job = intent,
                        guardian = when {
                            !debugChecks -> null
                            else -> InputStrategyGuardian(
                                parallelProcessing = inputStrategy.parallelProcessing,
                                isBootstrap = false,
                                intent = intent,
                                handler = intentHandler,
                            )
                        },
                        getState = mutableState::value,
                        updateStateAndGet = ::updateStateAndGet,
                        sendSideEffect = ::postSideEffect,
                        sendSideJob = ::postSideJob,
                        subscriptionCount = subscriptionCount,
                        coroutineContext = coroutineContext,
                    )
                    storeScope.handleIntent(intent)
                    storeScope.close()
                }
            }
            deferred?.complete(Unit)
        } catch (ce: CancellationException) {
            deferred?.completeExceptionally(ce)
            // reset state as intent did not complete successfully
            if (inputStrategy.rollbackOnCancellation) {
                updateState(stateBeforeCancellation)
            }
        } catch (e: Throwable) {
            deferred?.completeExceptionally(e)
            handleException(e, currentCoroutineContext())
        }
    }

    private suspend fun postSideJob(request: SideJobRequest<Intent, State, SideEffect>) {
        check(this !== sideJobScope) { "Side jobs are disabled for the current store: $name" }

        val key = request.key

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
                    !debugChecks -> request.context
                    else -> CoroutineName("$F[$name SideJob $key <= Intent ${request.parent}]") + request.context
                },
                start = request.start,
            ) {
                try {
                    val sideJobScope = SideJobScopeImpl(
                        updateStateAndGet = ::updateStateAndGet,
                        sendIntent = ::send,
                        sendSideEffect = ::postSideEffect,
                        currentStateWhenStarted = mutableState.value,
                        restartState = restartState,
                        coroutineScope = this,
                    )

                    // Await all children completions with coroutineScope
                    coroutineScope {
                        val block = request.block
                        sideJobScope.block()
                    }
                } catch (_: CancellationException) {
                } catch (e: Throwable) {
                    handleException(e, currentCoroutineContext())
                }
            },
        )
    }

    private suspend fun safelyRunBootstrapper(bootstrapper: Bootstrapper<Intent, State, SideEffect>) {
        try {
            // Await all children completions with coroutineScope
            coroutineScope {
                val bootstrapperScope = BootstrapperScopeImpl(
                    bootstrapper = bootstrapper,
                    guardian = if (!debugChecks) {
                        null
                    } else {
                        InputStrategyGuardian(
                            parallelProcessing = inputStrategy.parallelProcessing,
                            isBootstrap = true,
                            intent = null,
                            handler = bootstrapper,
                        )
                    },
                    getState = mutableState::value,
                    updateStateAndGet = ::updateStateAndGet,
                    sendIntent = ::send,
                    sendSideEffect = ::postSideEffect,
                    sendSideJob = ::postSideJob,
                    subscriptionCount = subscriptionCount,
                    coroutineContext = coroutineContext,
                )
                bootstrapperScope.bootstrapper()
                bootstrapperScope.close()
            }
        } catch (_: CancellationException) {
        } catch (e: Throwable) {
            handleException(e, currentCoroutineContext())
        }
    }

    /**
     * Called on a shutdown for any reason.
     */
    private fun onShutdown(cause: Throwable?) {
        val cancellationCause = cause.toCancellationException() ?: cancellationCause
        val ceCause = cancellationCause?.cause

        // Cancel and clear sideJobs
        for (value in sideJobsMap.values) {
            value.job?.cancel(cancellationCause)
            value.job = null
        }
        sideJobsMap.clear()

        // Close and clear state & side effects machinery.
        launch(Dispatchers.Unconfined + Job(), start = CoroutineStart.UNDISPATCHED) {
            (sideEffectFlowField as? MutableSharedFlow)?.apply {
                val replayCache = replayCache
                @OptIn(ExperimentalCoroutinesApi::class)
                resetReplayCache()
                replayCache.forEach { it.closeSafely(ceCause) }
            }
            mutableState.value.closeSafely(ceCause)
            sideEffectChannel?.apply {
                while (@OptIn(ExperimentalCoroutinesApi::class) !isEmpty) {
                    val result = receiveCatching()
                    if (result.isClosed) break
                    result.getOrNull()?.closeSafely(ceCause)
                }
                close(ceCause)
            }

            onClose(ceCause ?: cancellationCause)
        }
    }

    // endregion


    // region StateFlow

    override val value: State get() = mutableState.value

    override val replayCache: List<State> get() = mutableState.replayCache

    override suspend fun collect(collector: FlowCollector<State>) = mutableState.collect(collector)

    // endregion
}
