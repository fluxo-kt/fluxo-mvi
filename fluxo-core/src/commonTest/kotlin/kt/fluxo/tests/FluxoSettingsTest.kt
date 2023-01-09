package kt.fluxo.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kt.fluxo.core.FluxoSettings
import kt.fluxo.core.InputStrategy.InBox.Fifo
import kt.fluxo.core.InputStrategy.InBox.Lifo
import kt.fluxo.core.SideEffectsStrategy
import kt.fluxo.core.container
import kt.fluxo.core.debug.DEBUG
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FluxoSettingsTest {
    @Test
    fun repeatOnSubscribedStopTimeout() {
        val container = container(Unit) @Suppress("DEPRECATION") {
            repeatOnSubscribedStopTimeout = 0L
            assertEquals(0L, repeatOnSubscribedStopTimeout)
        }
        container.close()
    }

    @Test
    fun sideEffectsStrategy() {
        val s = FluxoSettings.DEFAULT.copy()
        assertEquals(Channel.BUFFERED, s.sideEffectBufferSize)
        val share = SideEffectsStrategy.SHARE()
        s.sideEffectsStrategy = share
        assertEquals(share, s.sideEffectsStrategy)
        assertEquals(0, s.sideEffectBufferSize)
    }

    @Test
    fun exceptionHandler() {
        val container = container(Unit) {
            closeOnExceptions = false
            exceptionHandler = null
            assertTrue(closeOnExceptions)

            onError {}
            assertFalse(closeOnExceptions)
        }
        container.close()
    }

    @Test
    fun debugChecks() {
        val container = container(Unit) {
            closeOnExceptions = false
            debugChecks = true
            assertTrue(closeOnExceptions)
        }
        container.close()
    }

    @Test
    fun settings_copy() {
        val s = FluxoSettings.DEFAULT.copy()

        assertNull(s.exceptionHandler)
        assertTrue(s.optimized)

        assertEquals(EmptyCoroutineContext, s.sideJobsContext)
        assertEquals(Dispatchers.Default, s.coroutineContext)
        assertNull(s.scope)

        assertEquals(SideEffectsStrategy.RECEIVE, s.sideEffectsStrategy)
        assertEquals(Fifo, s.inputStrategy)
        assertNull(s.intentFilter)
        assertContentEquals(listOf(), s.interceptors)
        assertNull(s.bootstrapper)

        assertEquals(Channel.BUFFERED, s.sideEffectBufferSize)
        assertEquals(DEBUG, s.debugChecks)
        assertTrue(s.closeOnExceptions)
        assertTrue(s.lazy)
        assertNull(s.name)

        val share = SideEffectsStrategy.SHARE()
        s.sideEffectsStrategy = share
        s.inputStrategy = Lifo
        s.name = "test"

        assertEquals(share, s.sideEffectsStrategy)
        assertEquals(0, s.sideEffectBufferSize)

        val c = s.copy()
        assertEquals(share, c.sideEffectsStrategy)
        assertEquals(0, c.sideEffectBufferSize)
        assertEquals(Lifo, c.inputStrategy)
        assertEquals("test", c.name)
    }
}
