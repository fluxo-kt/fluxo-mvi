package kt.fluxo.test.interception

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

abstract class InterceptionBase {

    fun test(creations: Int = INTERCEPTORS_INIT_COUNT, interceptions: Int = INTERCEPTIONS_COUNT, interceptors: Int = INTERCEPTORS_COUNT) =
        runBlocking {
            val state = MutableStateFlow(0)
            repeat(creations) {
                state.value = 0
                test(interceptions, interceptors, state)
            }
            state.value
        }

    protected abstract suspend fun test(interceptions: Int, interceptors: Int, state: MutableStateFlow<Int>)
}
