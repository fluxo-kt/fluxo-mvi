package kt.fluxo.test.interception

import kotlinx.coroutines.flow.MutableStateFlow
import kt.fluxo.test.compare.CommonBenchmark.addAndGet
import kt.fluxo.test.interception.DecoratorInterception.IntentHandler

/**
 * Decorator interception as in MVIKotlin (via factory) or in Orbit (via ContainerDecorator).
 */
object DecoratorInterception : InterceptionBase() {

    // https://square.github.io/okhttp/features/interceptors/
    // https://github.com/square/okhttp/blob/3ad1912/okhttp/src/jvmMain/kotlin/okhttp3/Interceptor.kt


    private fun interface IntentHandler<I> {
        suspend fun intent(value: I)
    }

    @Suppress("UnnecessaryAbstractClass")
    private abstract class IntentHandlerDecorator<I>(protected val actual: IntentHandler<I>) : IntentHandler<I> {
        override suspend fun intent(value: I) = actual.intent(value)
    }


    override suspend fun test(interceptions: Int, interceptors: Int, state: MutableStateFlow<Int>) {
        // Build a full stack of decorators
        var handler = IntentHandler<Int> { it + state.addAndGet(1) }
        repeat(INTERCEPTORS_COUNT) {
            handler = object : IntentHandlerDecorator<Int>(handler) {
                override suspend fun intent(value: Int) = actual.intent(it + state.addAndGet(1))
            }
        }
        val originalRequest = 0
        handler.intent(originalRequest)

        var expected = interceptors + 1
        check(state.value == expected) { "state.value=${state.value}, expected=$expected" }

        repeat(interceptions - 1) {
            handler.intent(originalRequest)
        }
        expected *= interceptions
        check(state.value == expected) { "state.value=${state.value}, expected=$expected" }
    }
}
