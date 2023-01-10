package kt.fluxo.test.compare.naive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kt.fluxo.test.compare.CommonBenchmark.consumeCommon
import kt.fluxo.test.compare.CommonBenchmark.getAndAdd
import kt.fluxo.test.compare.CommonBenchmark.launchCommon

internal object NaiveBenchmark {
    fun stateFlow(): Int {
        val state = MutableStateFlow(0)
        runBlocking {
            val launchDef = launchCommon(Unit) { state.getAndAdd(1) }
            state.consumeCommon(launchDef)
        }
        return state.value
    }
}
