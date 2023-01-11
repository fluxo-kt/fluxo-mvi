package kt.fluxo.core.input

@Deprecated("For migration")
public typealias InputStrategyScope<Request> = suspend (request: Request) -> Unit
