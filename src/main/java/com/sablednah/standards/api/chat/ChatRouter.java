package com.sablednah.standards.api.chat;

import net.minecraft.server.level.ServerPlayer;

/**
 * Something that can take a chat message and deliver it somewhere other than the whole server.
 *
 * <p>A party channel, a staff channel, a faction channel, a local-radius chat. The mod owning the
 * channel claims the message and sends it to whoever it belongs to; Standards then does not
 * broadcast it.</p>
 *
 * <h2>Why this exists rather than "cancel the event yourself"</h2>
 *
 * <p>Because a channel that bypasses Standards bypasses <b>mutes</b>, and a mute that only silences
 * public chat is a lie. The only way to redirect chat without this seam is to cancel
 * {@code ServerChatEvent} at a higher priority than Standards — at which point our listener never
 * runs, and a muted player simply flips to the other channel and talks. Their AFK marker never
 * clears either, so they stay listed as away while chatting.</p>
 *
 * <p>Reported by the LegendQuest session while building a party-chat capture toggle, and correct on
 * our own semantics rather than theirs: {@code MessageCommands} gates {@code /msg} on mutes at two
 * separate points, so a mute is plainly meant to silence <em>every</em> channel.</p>
 *
 * <p><b>The value here is that the gate cannot be skipped.</b> Standards keeps the event and runs
 * its checks first, every time, for every channel that will ever exist. An arrangement where each
 * channel mod politely asks first works right up until one forgets, and nobody finds out until a
 * muted player is heard.</p>
 *
 * <h2>First claimant wins — unlike {@link NameDecorator}</h2>
 *
 * <p>Worth stating, because the two live in the same package and behave oppositely. Decorators are
 * <b>additive</b>: a name carries a faction tag and a party tag and a rank without contradiction,
 * so every decorator gets a turn.</p>
 *
 * <p>A message goes to <b>exactly one</b> audience. Two routers both claiming it is a conflict, not
 * a merge, so routers are offered the message in descending priority and the first to claim it
 * ends the matter. That is the same shape as the economy provider, for the same reason.</p>
 *
 * <h2>You render it, not us</h2>
 *
 * <p>{@link #route} delivers the message itself and reports whether it did. Standards deliberately
 * does not hand you an audience and render on your behalf — a party line <em>should</em> look
 * different from public chat, and the mod that owns the channel is the one that knows how.</p>
 *
 * <p>That also leaves {@code /ignore} where it belongs. Whether being ignored should silence
 * someone inside a small opt-in channel is a judgement about that channel, so the router decides,
 * not the seam.</p>
 *
 * <h2>Implementing one</h2>
 *
 * <pre>{@code
 * Chat.registerRouter(new ChatRouter() {
 *     public String id() { return "legendquest:party"; }
 *     public int priority() { return 100; }
 *     public boolean route(ServerPlayer sender, String message) {
 *         if (!captureEnabled(sender)) {
 *             return false;                       // not mine — let it go to the server
 *         }
 *         deliverToParty(sender, message);        // your channel, your formatting
 *         return true;                            // claimed; Standards will not broadcast it
 *     }
 * });
 * }</pre>
 *
 * <p>By the time {@code route} is called, the sender is <b>known not to be muted</b> and their AFK
 * marker has already been cleared. You do not need to check either.</p>
 */
public interface ChatRouter {

    /** A stable id, {@code modid:channel}, used in logs when something goes wrong. */
    String id();

    /**
     * Higher is offered the message first. Standards' own channels claim nothing, so any value
     * works; pick deliberately only if two of your channels could both want the same message.
     */
    default int priority() {
        return 0;
    }

    /**
     * Take this message, or decline it.
     *
     * <p>Called only for messages that have already passed the mute gate. Returning {@code true}
     * asserts that the message <em>has been delivered</em> — Standards will not broadcast it, so a
     * router that claims a message and then fails to send it has silently eaten it.</p>
     *
     * @param sender  the player talking, never muted at this point
     * @param message the raw text they typed, undecorated
     * @return true if this router delivered it; false to let it carry on to the server at large
     */
    boolean route(ServerPlayer sender, String message);
}
