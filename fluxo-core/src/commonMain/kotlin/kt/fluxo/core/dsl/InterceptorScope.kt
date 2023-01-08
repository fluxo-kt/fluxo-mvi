package kt.fluxo.core.dsl

import kotlinx.coroutines.CoroutineScope
import kt.fluxo.core.intercept.StoreRequest
import kotlin.js.JsName

public interface InterceptorScope<in Intent, State> : CoroutineScope {

    public val storeName: String

    @JsName("postRequest")
    public suspend fun postRequest(request: StoreRequest<Intent, State>)
}
