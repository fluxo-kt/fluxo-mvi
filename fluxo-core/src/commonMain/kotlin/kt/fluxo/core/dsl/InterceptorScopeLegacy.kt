package kt.fluxo.core.dsl

import kotlinx.coroutines.CoroutineScope
import kt.fluxo.core.intercept.StoreRequest
import kotlin.js.JsName

@Deprecated("For removal", level = DeprecationLevel.ERROR)
public interface InterceptorScopeLegacy<in Intent, State> : CoroutineScope {

    public val storeName: String

    @JsName("postRequest")
    public suspend fun postRequest(request: StoreRequest<Intent, State>)
}
