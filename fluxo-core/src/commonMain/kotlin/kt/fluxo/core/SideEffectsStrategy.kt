@file:Suppress("CanSealedSubClassBeObject")

package kt.fluxo.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kt.fluxo.core.SideEffectsStrategy.RECEIVE

/**
 * Available strategies for how side effects sharing can be handled in the [Store].
 * When in doubt, use the [default][RECEIVE] one, and change if you have issues.
 */
public sealed interface SideEffectsStrategy {

    /**
     * [Channel]-based strategy with sharing through the [receiveAsFlow].
     * Multiple subscribers allowed. One side effect will be emitted to one subscriber only and **the order of subscribers unspecified**.
     *
     * @see kotlinx.coroutines.flow.receiveAsFlow
     * @see SHARE
     * @see CONSUME
     */
    public object RECEIVE : SideEffectsStrategy

    /**
     * [Channel]-based strategy with sharing through the [consumeAsFlow].
     *
     * **Restricts the count of subscribers to 1**. Consumes the underlying [Channel] completely on the first subscription from the flow!
     * The resulting flow can be collected just once and throws [IllegalStateException] when trying to collect it more than once.
     *
     * Attempting to subscribe or resubscribe to side effects from a [Store] already subscribed will result in an exception.
     * In other words, you will be required to create a new [Store] for each subscriber!
     *
     * **In the most cases you need [RECEIVE] or [SHARE] strategies instead of this one!**
     *
     * @see kotlinx.coroutines.flow.consumeAsFlow
     * @see RECEIVE
     * @see SHARE
     */
    public object CONSUME : SideEffectsStrategy

    /**
     * [MutableSharedFlow]-based strategy. Shares emitted side effects among all subscribers in a broadcast fashion,
     * so that all collectors get all emitted side effects.
     *
     * Keeps a [specified number][replay] of the most recent values in its replay cache. Every new subscriber first gets
     * the values from the replay cache and then gets new emitted values.
     *
     * @param replay number of side effects replayed to new subscribers (cannot be negative, defaults to zero).
     *
     * @see kotlinx.coroutines.flow.MutableSharedFlow
     */
    public class SHARE(public val replay: Int = 0) : SideEffectsStrategy

    /**
     * Side effects are completely disabled.
     * Saves a bit of app memory and sometimes your brain cells (as it can be a more pure way to use only state+intent).
     */
    public object DISABLE : SideEffectsStrategy
}
