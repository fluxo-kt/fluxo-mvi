package kt.fluxo.core.internal

import kt.fluxo.core.dsl.InterceptorScopeLegacy
import kt.fluxo.core.intercept.StoreRequest
import kotlin.coroutines.CoroutineContext

internal class InterceptorScopeImpl<in Intent, State>(
    override val storeName: String,
    private val sendRequest: suspend (StoreRequest<Intent, State>) -> Unit,
    override val coroutineContext: CoroutineContext,
) : InterceptorScopeLegacy<Intent, State> {

    override suspend fun postRequest(request: StoreRequest<Intent, State>) = sendRequest(request)
}
