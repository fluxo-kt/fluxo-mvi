package kt.fluxo.test.interception

import kotlinx.coroutines.flow.MutableStateFlow
import kt.fluxo.test.compare.CommonBenchmark.addAndGet
import kt.fluxo.test.interception.PipelineInterceptionChain.IntentInterceptor

/**
 * Pipeline interception as used in OkHttp, but adapted for multiple intercepted actions.
 *
 * Also has some similarities with Netty ChannelPipeline/ChannelHandler.
 */
object PipelineInterceptionChain : InterceptionBase() {

    // https://square.github.io/okhttp/features/interceptors/
    // https://github.com/square/okhttp/blob/3ad1912/okhttp/src/jvmMain/kotlin/okhttp3/Interceptor.kt

    // https://netty.io/4.1/api/io/netty/channel/ChannelHandler.html
    // https://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html
    // https://netty.io/4.1/api/io/netty/channel/ChannelHandlerContext.html


    private sealed interface Interceptor

    private fun interface IntentInterceptor<I> : Interceptor {
        suspend fun interceptIntent(chain: InterceptorChain<I>)
    }

    private interface InterceptorChain<R> {
        val value: R

        suspend fun proceed(value: R)
    }

    private abstract class RealInterceptorChain<R, I : Interceptor>(
        @JvmField
        protected val interceptors: Array<out I>,
        private val index: Int,
        override val value: R,
    ) : InterceptorChain<R> {

        protected abstract fun copy(index: Int = this.index, value: R = this.value): InterceptorChain<R>

        override suspend fun proceed(value: R) {
            check(index < interceptors.size) { "index ($index) >= interceptors.size (${interceptors.size})" }

            // Call the next interceptor in the chain.
            val next = copy(index = index + 1, value = value)
            val interceptor = interceptors[index]

            return interceptor.intercept(next)
        }

        protected abstract suspend fun I.intercept(next: InterceptorChain<R>)
    }

    private class IntentInterceptorChain<I>(interceptors: Array<out IntentInterceptor<I>>, index: Int, value: I) :
        RealInterceptorChain<I, IntentInterceptor<I>>(interceptors, index, value) {

        override fun copy(index: Int, value: I) = IntentInterceptorChain(interceptors, index, value)

        override suspend fun IntentInterceptor<I>.intercept(next: InterceptorChain<I>) = interceptIntent(next)
    }


    override suspend fun test(interceptions: Int, interceptors: Int, state: MutableStateFlow<Int>) {
        val interceptor = IntentInterceptor<Int> { it.proceed(it.value + state.addAndGet(1)) }
        val finalInterceptor = IntentInterceptor<Int> { it.value + state.addAndGet(1) }

        // Build a full stack of interceptors
        val interceptorsArray = Array(interceptors + 1) {
            if (it == interceptors) finalInterceptor else interceptor
        }

        val originalRequest = 0
        val chain = IntentInterceptorChain(interceptorsArray, index = 0, originalRequest)

        chain.proceed(originalRequest)
        var expected = interceptors + 1
        check(state.value == expected) { "state.value=${state.value}, expected=$expected" }

        repeat(interceptions - 1) {
            chain.proceed(originalRequest)
        }
        expected *= interceptions
        check(state.value == expected) { "state.value=${state.value}, expected=$expected" }
    }
}
