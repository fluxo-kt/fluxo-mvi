package kt.fluxo.tests

import kotlinx.coroutines.flow.first
import kt.fluxo.core.closeAndWait
import kt.fluxo.core.store
import kt.fluxo.test.runUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReducerTest {
    @Test
    fun basic_reducer_usage() = runUnitTest {
        val store = store<Int, Int>(0, reducer = { it })
        store.send(123)
        store.first { it == 123 }
        store.send(321)
        store.first { it == 321 }
        assertEquals(321, store.value)
        store.closeAndWait()
    }
}
