package kt.fluxo.core.input

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kt.fluxo.core.annotation.ExperimentalFluxoApi
import kotlin.js.JsName


/**
 *
 * Note: please fill an issue if you need this class to be open for your specific cases!
 */
@ExperimentalFluxoApi
@Suppress("LeakingThis")
internal abstract class ChannelBasedInputStrategy<Input> : InputStrategy<Input>, FlowCollector<Input> {

    protected abstract val handler: InputStrategyHandler<Input>

    private val requestsChannel: Channel<Input>

    init {
        // Leak-free transfer via channel
        // https://github.com/Kotlin/kotlinx.coroutines/issues/1936
        // See "Undelivered elements" section in Channel documentation for details.
        //
        // Handled cases:
        // — sending operation cancelled before it had a chance to actually send the element.
        // — receiving operation retrieved the element from the channel cancelled when trying to return it the caller.
        // — channel cancelled, in which case onUndeliveredElement called on every remaining element in the channel's buffer.
        val intentResendLock = if (resendUndelivered) Mutex() else null
        requestsChannel = createQueue {
            var resent = false
            // We don't want to fall into the recursion, so only one resending per moment.
            if (intentResendLock?.tryLock() == true) {
                try {
                    @Suppress("UNINITIALIZED_VARIABLE")
                    val channel = requestsChannel
                    @OptIn(ExperimentalCoroutinesApi::class)
                    if (!channel.isClosedForSend) {
                        resent = channel.trySend(it).isSuccess
                    }
                } catch (_: Throwable) {
                } finally {
                    intentResendLock.unlock()
                }
            }
            handler.onUndeliveredIntent(it, resent)
        }
    }

    /**
     * Creates a [Channel] with the specified buffer capacity (or without a buffer by default).
     * See [Channel] interface documentation for details.
     *
     * @param onUndeliveredElement See "Undelivered elements" section in [Channel] documentation for details.
     *
     * @see kotlinx.coroutines.channels.Channel
     */
    @JsName("createQueue")
    protected open fun createQueue(onUndeliveredElement: ((Input) -> Unit)?): Channel<Input> {
        return Channel(
            capacity = Channel.UNLIMITED,
            onBufferOverflow = BufferOverflow.SUSPEND,
            onUndeliveredElement = onUndeliveredElement,
        )
    }

    override suspend fun init() {
        // TODO: Convert to flow?
        requestsChannel.collect(this)
    }

    override suspend fun emit(value: Input) {
    }

    override fun close() {
        requestsChannel.close()
    }
}
