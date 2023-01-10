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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kt.fluxo.core.Bootstrapper
import kt.fluxo.core.FluxoClosedException
import kt.fluxo.core.FluxoSettings
import kt.fluxo.core.IntentHandler
import kt.fluxo.core.SideEffectsStrategy
import kt.fluxo.core.SideJob
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.data.GuaranteedEffect
import kt.fluxo.core.debug.DEBUG
import kt.fluxo.core.debug.debugIntentWrapper
import kt.fluxo.core.dsl.InputStrategyScope
import kt.fluxo.core.factory.StoreDecorator
import kt.fluxo.core.intercept.StoreRequest.HandleIntent
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

    @Deprecated("For migration")
    private val requestsChannel: Channel<HandleIntent<Intent, State>>

    private val sideJobScope: CoroutineScope
    private val sideJobsMap = ConcurrentHashMap<String, RunningSideJob>()
    private val sideJobsMutex = Mutex()

    private val sideEffectChannel: Channel<SideEffect>?
    private val sideEffectFlowField: Flow<SideEffect>?
    override val sideEffectFlow: Flow<SideEffect>
        get() = sideEffectFlowField ?: error("Side effects are disabled for the current store: $name")

    private val initialization = atomic<Any?>(null)

    private val debugChecks = conf.debugChecks
    private val closeOnExceptions = conf.closeOnExceptions
    private val inputStrategy = conf.inputStrategy
    private val bootstrapper = conf.bootstrapper
    private val intentFilter = conf.intentFilter
    private val coroutineStart = if (!conf.optimized) CoroutineStart.DEFAULT else CoroutineStart.UNDISPATCHED

    override val coroutineContext: CoroutineContext
    override val subscriptionCount: StateFlow<Int>

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

        val job = ctx[Job].let { if (closeOnExceptions) Job(it) else SupervisorJob(it) }
        job.invokeOnCompletion(::onShutdown)

        val parentExceptionHandler = conf.exceptionHandler ?: ctx[CoroutineExceptionHandler]
        val exceptionHandler = CoroutineExceptionHandler { context, e ->
            try {
                val ce = e.toCancellationException()
                if (!onUnhandledError(ce?.cause ?: e)) {
                    if (closeOnExceptions) {
                        context.cancel(ce)
                        @Suppress("UNINITIALIZED_VARIABLE")
                        coroutineContext.cancel(ce)
                    } else {
                        parentExceptionHandler?.handleException(context, e)
                    }
                }
            } catch (e2: Throwable) {
                e2.addSuppressed(e)
                throw e2
            }
        }
        coroutineContext = ctx + exceptionHandler + job
        val scope: CoroutineScope = this

        // Minor optimization if side jobs are not available when bootstrapper is not set and ReducerIntentHandler is used.
        sideJobScope = when {
            intentHandler is ReducerIntentHandler && bootstrapper == null -> scope
            else -> scope + conf.sideJobsContext + exceptionHandler + SupervisorJob(job) + when {
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
            // We don't want to fall into the recursion, so only one resending per moment.
            var resent = false
            if (isActive && intentResendLock?.tryLock() == true) {
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
            onUndeliveredIntent(it.intent, resent)
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
                onUndeliveredSideEffect(it, resent)
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
                context = if (!debugChecks) EmptyCoroutineContext else CoroutineName("$F[$name:lazyStart]"),
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


    override fun start(): Job {
        if (!isActive) {
            throw FluxoClosedException(
                "Store is closed, it cannot be restarted",
                cancellationCause?.let { it.cause ?: it },
            )
        }
        if (initialization.compareAndSet(expect = null, update = Unit)) {
            initialization.value = launch()
        }
        return initialization.value as Job
    }

    override suspend fun emit(value: Intent) {
        start()
        send(if (DEBUG && debugChecks) debugIntentWrapper(value) else value, null)
    }

    private suspend fun send(intent: Intent, deferred: CompletableDeferred<Unit>?): Job {
        val d = deferred ?: CompletableDeferred()
        requestsChannel.send(HandleIntent(intent, d))
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

    override var value: State
        get() = mutableState.value
        set(value) {
            updateStateAndGet { value }
        }

    override suspend fun updateState(function: (State) -> State): State {
        return updateStateAndGet(function)
    }

    private inline fun updateStateAndGet(function: (State) -> State): State {
        val stateFlow = mutableState
        while (true) {
            val prevValue = stateFlow.value
            val nextValue = function(prevValue)
            if (stateFlow.compareAndSet(prevValue, nextValue)) {
                if (prevValue != nextValue) {
                    prevValue.closeSafely()
                }
                onStateChanged(nextValue)
                return nextValue
            }
            if (!isActive) {
                nextValue.closeSafely()
                throw cancellationCause!!
            }
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
        if (!onUnhandledError(e)) {
            throw e
        }
    }

    private fun Any?.closeSafely(cause: Throwable? = null, ctx: CoroutineContext? = null) {
        if (this is Closeable) {
            try {
                close() // Close if Closeable resource
            } catch (e: Throwable) {
                if (cause != null) {
                    e.addSuppressed(cause)
                }
                handleException(e, ctx ?: coroutineContext)
            }
        }
    }

    /** Will be called only once for each [FluxoStore] */
    private fun launch() = launch(
        context = if (!debugChecks) EmptyCoroutineContext else CoroutineName("$F[$name:launch]"),
        start = coroutineStart,
    ) {
        // observe and process intents
        val requestsFlow = requestsChannel.receiveAsFlow()
        val inputStrategy = inputStrategy
        val inputStrategyScope: InputStrategyScope<HandleIntent<Intent, State>> = { request ->
            if (debugChecks) {
                withContext(CoroutineName("$F[$name <= Intent ${request.intent}]")) {
                    safelyHandleIntent(request.intent, request.deferred)
                }
            } else {
                safelyHandleIntent(request.intent, request.deferred)
            }
        }
        launch(start = coroutineStart) {
            with(inputStrategy) {
                inputStrategyScope.processRequests(requestsFlow)
            }
        }

        bootstrapper?.let { bootstrapper ->
            try {
                onBootstrap(bootstrapper)
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                handleException(e, currentCoroutineContext())
            }
        }

        onStart()
    }

    private suspend fun safelyHandleIntent(intent: Intent, deferred: CompletableDeferred<Unit>?) {
        val stateBeforeCancellation = value
        try {
            onIntent(intent)
        } catch (ce: CancellationException) {
            deferred?.completeExceptionally(ce)
            // reset state as intent did not complete successfully
            if (inputStrategy.rollbackOnCancellation) {
                value = stateBeforeCancellation
            }
        } catch (e: Throwable) {
            deferred?.completeExceptionally(e)
            handleException(e, currentCoroutineContext())
        } finally {
            deferred?.complete(Unit)
        }
    }

    override suspend fun onIntent(intent: Intent) {
        // Apply an intent filter before processing, if defined
        intentFilter?.also { accept ->
            if (!accept(value, intent)) {
                return
            }
        }

        val guardedScope = when {
            !debugChecks -> null
            else -> GuardedStoreDecorator(
                guardian = InputStrategyGuardian(
                    parallelProcessing = inputStrategy.parallelProcessing,
                    isBootstrap = false,
                    intent = intent,
                    handler = intentHandler,
                ),
                store = this,
            )
        }
        val scope = guardedScope ?: this
        with(intentHandler) {
            // Await all children completions with coroutineScope
            // TODO: Can we awoid lambda creation?
            coroutineScope {
                scope.handleIntent(intent)
            }
        }
        guardedScope?.close()
    }

    override suspend fun sideJob(
        key: String,
        context: CoroutineContext,
        start: CoroutineStart,
        block: SideJob<Intent, State, SideEffect>,
    ): Job {
        check(this !== sideJobScope) { "Side jobs are disabled for the current store: $name" }

        val map = sideJobsMap
        sideJobsMutex.withLock(if (debugChecks) key else null) {
            val prevSj = map[key]

            // Cancel if we have a sideJob already running
            prevSj?.run {
                job?.cancel()
                job = null
            }

            // Go through and remove any sideJobs that have completed (either by
            // cancellation or because they finished on their own)
            val iterator = map.values.iterator()
            for (sj in iterator) {
                if (sj.job?.isActive != true) {
                    iterator.remove()
                }
            }

            // Launch a new sideJob in its own isolated coroutine scope where:
            //   1) cancelled when the scope or sideJobScope cancelled
            //   2) errors caught for crash reporting
            //   3) has a supervisor job, so we can cancel the sideJob without cancelling whole store scope
            //
            // Consumers of this sideJob can launch many jobs, and all will be cancelled together when the
            // sideJob restarted, or the store scope cancelled.
            val wasRestarted = prevSj != null
            val coroutineContext = when {
                !debugChecks -> context
                else -> {
                    val parent = currentCoroutineContext()[CoroutineName]?.name
                    val name = if (parent != null) "$parent => SideJob $key" else "$F[$name SideJob $key]"
                    CoroutineName(name) + context
                }
            }
            // TODO: Test if job awaits for all potential children completions when join
            val job = sideJobScope.launch(context = coroutineContext, start = start) {
                try {
                    onSideJob(key, wasRestarted, block)
                } catch (e: CancellationException) {
                    // just rethrow, cancel the job and all children
                    throw e
                } catch (e: Throwable) {
                    handleException(e, currentCoroutineContext())
                }
            }
            map[key] = RunningSideJob(job = job)

            return job
        }
    }

    override suspend fun onSideJob(key: String, wasRestarted: Boolean, sideJob: SideJob<Intent, State, SideEffect>) {
        sideJob(wasRestarted)
    }

    override suspend fun onBootstrap(bootstrapper: Bootstrapper<Intent, State, SideEffect>) {
        val guardedScope = when {
            !debugChecks -> null
            else -> GuardedStoreDecorator(
                guardian = InputStrategyGuardian(
                    parallelProcessing = inputStrategy.parallelProcessing,
                    isBootstrap = true,
                    intent = null,
                    handler = bootstrapper,
                ),
                store = this,
            )
        }
        val scope = guardedScope ?: this
        // Await all children completions with coroutineScope
        // TODO: Can we awoid lambda creation?
        coroutineScope {
            scope.bootstrapper()
        }
        guardedScope?.close()
    }

    /**
     * Called on a shutdown for any reason.
     */
    private fun onShutdown(error: Throwable?) {
        val cancellation = error.toCancellationException() ?: cancellationCause
        val cause = cancellation?.cause

        // Cancel and clear sideJobs
        sideJobsMap.values.forEach { it.job?.cancel(cancellation) }
        sideJobsMap.clear()

        // Close and clear state & side effects machinery.
        value.closeSafely(cause)
        (sideEffectFlowField as? MutableSharedFlow)?.apply {
            val replayCache = replayCache
            @OptIn(ExperimentalCoroutinesApi::class)
            resetReplayCache()
            replayCache.forEach { it.closeSafely(cause) }
        }
        launch(Dispatchers.Unconfined + Job(), start = CoroutineStart.UNDISPATCHED) {
            sideEffectChannel?.apply {
                @OptIn(ExperimentalCoroutinesApi::class)
                while (!isEmpty) {
                    val result = receiveCatching()
                    if (result.isClosed) break
                    result.getOrNull()?.closeSafely(cause)
                }
                close(cause)
            }
            onClose(cause)
        }
    }

    // endregion


    // region StateFlow

    override val replayCache: List<State> get() = mutableState.replayCache

    override suspend fun collect(collector: FlowCollector<State>) = mutableState.collect(collector)

    // endregion
}
