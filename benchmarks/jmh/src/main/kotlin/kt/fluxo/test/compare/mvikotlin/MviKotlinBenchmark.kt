package kt.fluxo.test.compare.mvikotlin

import com.arkivanov.mvikotlin.core.store.create
import com.arkivanov.mvikotlin.extensions.coroutines.states
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.runBlocking
import kt.fluxo.test.compare.CommonBenchmark.consumeCommon
import kt.fluxo.test.compare.CommonBenchmark.launchCommon
import kt.fluxo.test.compare.IntentIncrement
import java.io.OutputStream
import java.io.PrintStream

internal object MviKotlinBenchmark {

    // https://github.com/arkivanov/MVIKotlin/blob/57ade7a/sample/coroutines/shared/src/commonMain/kotlin/com/arkivanov/mvikotlin/sample/coroutines/shared/details/store/DetailsStoreFactory.kt

    fun mviReducer(): Int {
        // Override stderr to get rid of `[MVIKotlin]: Main thread ID is undefined, main thread assert is disabled`.
        val stderr = System.err
        try {
            System.setErr(PrintStream(OutputStream.nullOutputStream()))
            return mviReducer0()
        } finally {
            System.setErr(stderr)
        }
    }

    private fun mviReducer0(): Int {
        val store = DefaultStoreFactory().create<IntentIncrement, Int>(name = "CounterStore", initialState = 0) {
            when (it) {
                IntentIncrement.Increment -> this + 1
            }
        }
        runBlocking {
            val launchDef = launchCommon(IntentIncrement.Increment) { store.accept(it) }
            consumeCommon(store.states, launchDef)
        }
        store.dispose()
        return store.state
    }
}
