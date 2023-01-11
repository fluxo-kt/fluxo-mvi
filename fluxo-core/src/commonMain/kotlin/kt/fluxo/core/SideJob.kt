package kt.fluxo.core

import kt.fluxo.core.dsl.StoreScope

/**
 * A function for doing something other than update the VM state or dispatch an effect.
 * This is moving outside the normal MVI workflow, so make sure you know what you're doing with this, and try to make sure it can be undone.
 *
 * For example, when deleting a record from the DB, do a soft delete so that it can be restored later, if needed.
 *
 * Side-jobs may safely be restarted; already-running side-jobs at the same `key` will be cancelled when starting the new side-job.
 * If a single VM is starting multiple side-jobs (likely from different inputs), they should each be given a unique
 * `key` within the VM to ensure they do not accidentally cancel each other.
 */
public typealias SideJob<Intent, State, SideEffect> = suspend StoreScope<Intent, State, SideEffect>.(wasRestarted: Boolean) -> Unit
