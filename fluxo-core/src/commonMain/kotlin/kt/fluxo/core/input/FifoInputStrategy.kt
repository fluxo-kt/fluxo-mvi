package kt.fluxo.core.input

import kotlinx.coroutines.Job

internal object FifoInputStrategy : InputStrategy.Factory {

    override fun <Input> invoke(handler: InputStrategyHandler<Input>): InputStrategy<Input> = Fifo(handler)

    private class Fifo<Input>(
        override val handler: InputStrategyHandler<Input>,
    ) : ChannelBasedInputStrategy<Input>() {

        override suspend fun handleInput(request: Input): Job {

        }
    }
}
