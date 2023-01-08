package kt.fluxo.jmh.arch

import kt.fluxo.test.interception.DecoratorInterception
import kt.fluxo.test.interception.PipelineInterceptionChain
import kt.fluxo.test.interception.PipelineInterceptionProceedLambdaChain
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

/**
 * Research on different interception types performance
 *
 * _Example results:_
 * ```
 * Benchmark                                  Mode  Cnt   Score  Error   Units
 * decorator_interception                    thrpt   60   1.569 ±0.006  ops/ms
 * pipeline_interception                     thrpt   60   1.045 ±0.023  ops/ms
 * pipeline_interception_lambdas             thrpt   60   0.381 ±0.004  ops/ms
 *
 * decorator_interception                     avgt   60   3.805 ±0.007   ms/op
 * pipeline_interception                      avgt   60   5.778 ±0.133   ms/op
 * pipeline_interception_lambdas              avgt   60  15.740 ±0.383   ms/op
 *
 * decorator_interception                       ss   60   6.278 ±0.954   ms/op
 * pipeline_interception                        ss   60   8.871 ±1.178   ms/op
 * pipeline_interception_lambdas                ss   60  19.941 ±2.281   ms/op
 *
 *
 * decorator_interception__creations         thrpt   60   1.785 ±0.007  ops/ms
 * pipeline_interception__creations          thrpt   60   1.176 ±0.024  ops/ms
 * pipeline_interception_lambdas__creations  thrpt   60   0.395 ±0.003  ops/ms
 *
 * decorator_interception__creations          avgt   60   3.350 ±0.035   ms/op
 * pipeline_interception__creations           avgt   60   5.085 ±0.089   ms/op
 * pipeline_interception_lambdas__creations   avgt   60  15.539 ±0.527   ms/op
 *
 * decorator_interception__creations            ss   60   7.327 ±1.825   ms/op
 * pipeline_interception__creations             ss   60   8.811 ±0.969   ms/op
 * pipeline_interception_lambdas__creations     ss   60  24.094 ±2.759   ms/op
 * ```
 */
@State(Scope.Benchmark)
@Suppress("FunctionNaming", "FunctionName")
open class InterceptionTypesBenchmark {
    /**
     * **Complete pipeline interception as used in OkHttp, etc.**
     *
     * * Also has some similarities with Netty ChannelPipeline/ChannelHandler.
     */
    @Benchmark
    fun pipeline_interception(bh: Blackhole) = bh.consume(PipelineInterceptionChain.test(creations = 10, interceptions = 3000))

    @Benchmark
    fun pipeline_interception__creations(bh: Blackhole) = bh.consume(PipelineInterceptionChain.test(creations = 3000, interceptions = 10))


    /**
     * **Complete pipeline interception, proceed with lambdas**
     *
     * * Doesn't require multiple implementaions for each action as simple interception chain
     * * Usage code is harder to read and maintain
     * * Basically 1.5-3x slower than simple interception chain.
     */
    @Benchmark
    fun pipeline_interception_lambdas(bh: Blackhole) =
        bh.consume(PipelineInterceptionProceedLambdaChain.test(creations = 10, interceptions = 3000))

    @Benchmark
    fun pipeline_interception_lambdas__creations(bh: Blackhole) =
        bh.consume(PipelineInterceptionProceedLambdaChain.test(creations = 3000, interceptions = 10))


    /**
     * **Decorator interception as in MVIKotlin (via factory) or in Orbit (via ContainerDecorator).**
     *
     * * Best performance
     * * Less convenient configuration
     */
    @Benchmark
    fun decorator_interception(bh: Blackhole) = bh.consume(DecoratorInterception.test(creations = 10, interceptions = 3000))

    @Benchmark
    fun decorator_interception__creations(bh: Blackhole) = bh.consume(DecoratorInterception.test(creations = 3000, interceptions = 10))


    /**
     * **Event stream as in Ballast.**
     *
     * * Allows to observe, but not intercept.
     * * Requires additional reactive machinery.
     */
}
