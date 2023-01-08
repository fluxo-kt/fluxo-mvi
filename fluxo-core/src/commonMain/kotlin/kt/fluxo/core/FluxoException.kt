package kt.fluxo.core

public sealed interface FluxoException

public class FluxoClosedException
internal constructor(message: String, cause: Throwable?) : IllegalStateException(message, cause), FluxoException

public open class FluxoRuntimeException
internal constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause), FluxoException
