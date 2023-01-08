package kt.fluxo.test.interception

import kotlinx.coroutines.flow.MutableStateFlow
import kt.fluxo.test.compare.CommonBenchmark.addAndGet
import kt.fluxo.test.compare.CommonBenchmark.getAndAdd
import kt.fluxo.test.interception.PipelineInterceptionProceedLambdaChain.Interceptor

/**
 * Pipeline interception multiple intercepted actions support, based on lambdas proceed
 *
 * * Doesn't require multiple implementaions for each action as simple interception chain
 * * Usage code is harder to read and maintain
 * * Basically 1.5-2x slower than simple interception chain.
 */
object PipelineInterceptionProceedLambdaChain : InterceptionBase() {

    private fun interface Interceptor<R> {

        suspend fun intent(request: R, proceed: suspend (request: R) -> Unit)
    }


    private class InterceptorChain<R>(
        value: R,
        private val interceptors: Array<out Interceptor<R>>,
        private val call: suspend Interceptor<R>.(R, proceed: suspend (R) -> Unit) -> Unit,
        private val finish: suspend (finalValue: R) -> Unit,
    ) {
        val index = MutableStateFlow(0)
        val value = MutableStateFlow(value)

        suspend fun proceed() {
            val i = index.value
            if (i >= interceptors.size) {
                finish(value.value)
            } else {
                interceptors[i].run {
                    call(value.value) { v ->
                        value.value = v
                        index.getAndAdd(delta = 1)
                        proceed()
                    }
                }
            }
        }
    }


    override suspend fun test(interceptions: Int, interceptors: Int, state: MutableStateFlow<Int>) {
        val interceptor = Interceptor<Int> { request, proceed ->
            proceed(request + state.addAndGet(1))
        }

        // Build a full stack of interceptors
        val interceptorsArray = Array(interceptors) { interceptor }

        val originalRequest = 0
        val chain = InterceptorChain(
            originalRequest,
            interceptorsArray,
            { value, proceed -> intent(value, proceed) },
        ) { state.addAndGet(1) }

        chain.proceed()
        var expected = interceptors + 1
        check(state.value == expected) { "state.value=${state.value}, expected=$expected" }

        repeat(interceptions - 1) {
            chain.index.value = 0
            chain.value.value = originalRequest
            chain.proceed()
        }
        expected *= interceptions
        check(state.value == expected) { "state.value=${state.value}, expected=$expected" }
    }
}
