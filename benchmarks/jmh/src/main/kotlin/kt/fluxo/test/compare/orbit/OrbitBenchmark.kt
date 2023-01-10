package kt.fluxo.test.compare.orbit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kt.fluxo.test.compare.CommonBenchmark.consumeCommon
import kt.fluxo.test.compare.CommonBenchmark.launchCommon
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container
import org.orbitmvi.orbit.syntax.simple.SimpleSyntax
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.reduce

internal object OrbitBenchmark {
    fun mvvmIntent(): Int {
        val dispatcher = newSingleThreadContext(::mvvmIntent.name)
        val job = SupervisorJob()
        val host = object : ContainerHost<Int, Nothing> {
            override val container = CoroutineScope(dispatcher + job).container<Int, Nothing>(0)
        }

        runBlocking {
            val intent: suspend SimpleSyntax<Int, Nothing>.() -> Unit = { reduce { state + 1 } }
            val launchDef = launchCommon(intent) { host.intent(transformer = it) }
            host.container.stateFlow.consumeCommon(launchDef, job, dispatcher)
        }

        return host.container.stateFlow.value
    }
}
