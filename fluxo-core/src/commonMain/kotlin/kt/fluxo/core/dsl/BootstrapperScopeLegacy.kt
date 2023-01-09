package kt.fluxo.core.dsl

import kotlinx.coroutines.Job
import kt.fluxo.core.annotation.FluxoDsl
import kotlin.js.JsName

@FluxoDsl
@Deprecated("For removal", level = DeprecationLevel.ERROR)
public interface BootstrapperScopeLegacy<in Intent, State, in SideEffect : Any> : StoreScopeLegacy<Intent, State, SideEffect> {

    @JsName("postIntent")
    public suspend fun postIntent(intent: Intent): Job
}
