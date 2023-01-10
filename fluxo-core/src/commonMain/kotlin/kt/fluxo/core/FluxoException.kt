package kt.fluxo.core

import kotlin.coroutines.cancellation.CancellationException

public sealed interface FluxoException

/**
 * Thrown if operation is called on a closed [Store].
 */
public class FluxoClosedException
internal constructor(message: String, override val cause: Throwable?) : CancellationException(message), FluxoException

public open class FluxoRuntimeException
internal constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause), FluxoException
