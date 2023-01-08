package kt.fluxo.core.internal

import kotlinx.atomicfu.atomic
import kt.fluxo.core.FluxoRuntimeException
import kt.fluxo.core.Store
import kt.fluxo.core.annotation.InternalFluxoApi
import kt.fluxo.core.debug.debugClassName
import kotlin.contracts.contract

/**
 * A Guardian protects the integrity of the [Store] state against potential problems,
 * especially with race conditions due to parallel processing.
 */
@InternalFluxoApi
internal open class InputStrategyGuardian(
    private val parallelProcessing: Boolean,
    private val isBootstrap: Boolean,
    private val intent: Any?,
    private val handler: Any,
) {
    private val stateAccessed = atomic(false)
    private val sideJobPosted = atomic(false)
    private val usedProperly = atomic(false)
    private val closed = atomic(false)

    open fun checkStateAccess() {
        if (parallelProcessing) {
            performStateAccessCheck()
        }
        checkNotClosed()
        checkNoSideJobs()
        stateAccessed.value = true
        usedProperly.value = true
    }

    open fun checkStateUpdate() {
        if (parallelProcessing) {
            performStateAccessCheck()
        }
        checkNotClosed()
        checkNoSideJobs()
        stateAccessed.value = true
        usedProperly.value = true
    }

    open fun checkPostSideEffect() {
        checkNotClosed()
        checkNoSideJobs()
        usedProperly.value = true
    }

    open fun checkPostIntent() = checkPostSideEffect()

    open fun checkNoOp() {
        checkNotClosed()
        checkNoSideJobs()
        usedProperly.value = true
    }

    open fun checkSideJob() {
        checkNotClosed()
        sideJobPosted.value = true
        usedProperly.value = true
    }

    open fun close() {
        checkNotClosed()
        checkUsedProperly()
        closed.value = true
    }


    private fun performStateAccessCheck() {
        check(!stateAccessed.value) {
            "Parallel input strategy requires that inputs only access or update the state at most once as a " +
                "safeguard against race conditions.$info"
        }
    }

    private fun checkNotClosed() {
        check(!closed.value) {
            val scope = if (isBootstrap) "BootstrapperScope" else "StoreScope"
            "This $scope has already been closed. Are yoy trying to use it from the sideJob?$info"
        }
    }

    private fun checkNoSideJobs() {
        check(!sideJobPosted.value) {
            val handler = if (isBootstrap) "Bootstrapper" else "IntentHandler"
            "`sideJob { }` blocks must be the last statements of the $handler$info"
        }
    }

    private fun checkUsedProperly() {
        check(usedProperly.value) {
            val handler = if (isBootstrap) "Bootstrapper" else "IntentHandler"
            "$handler behavior was not safe. To ensure you're following the proper model, check that any " +
                "side job executed in a `sideJob { }` block. Call `noOp()` method method to explicitly mark " +
                "$handler that doesn't do anything with Store.$info"
        }
    }

    private val info: String
        get() {
            val name = try {
                handler.debugClassName()
            } catch (_: Throwable) {
                ""
            }
            return when {
                isBootstrap -> if (name.isNullOrEmpty()) "" else " ($name)"
                else -> if (name.isNullOrEmpty()) " (intent=$intent)" else " (intent=$intent; $name)"
            }
        }

    private inline fun check(value: Boolean, lazyMessage: () -> String) {
        contract {
            returns() implies value
        }
        if (!value) {
            throw FluxoRuntimeException(lazyMessage(), cause = null)
        }
    }
}
