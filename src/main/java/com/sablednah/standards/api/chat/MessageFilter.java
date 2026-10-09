package com.sablednah.standards.api.chat;

import net.minecraft.server.level.ServerPlayer;

/**
 * Something that screens what a player says before anyone hears it — a profanity filter, an
 * advert blocker, a spam guard.
 *
 * <h2>Why this exists rather than "listen to ServerChatEvent yourself"</h2>
 *
 * <p>Because Standards delivers a lot of player text that never passes through that event's
 * message body. A decorated chat line is cancelled and re-sent as a system message built from
 * {@code getRawText()}, so a filter that changed the event's message is quietly undone; a routed
 * party or faction line goes to its channel from the raw text too; and {@code /msg}, {@code /r},
 * {@code /me} and {@code /mail} are commands with their own delivery. A filter that only knows
 * about the event works on a vanilla server and silently stops working the day Standards formats
 * its first line. Asked for by ChatFilter ReForged, which found exactly that.</p>
 *
 * <p>So Standards asks, at every point where it is about to publish a player's words, and it asks
 * <b>before</b> routing — a party channel is still somebody talking.</p>
 *
 * <h2>Filters chain — unlike routers</h2>
 *
 * <p>Every filter gets a turn, highest {@link #priority()} first, each seeing the text the previous
 * one left. The first to <b>block</b> ends it. That is the decorator shape (additive) with the
 * router's early exit for the one verdict that cannot be merged.</p>
 *
 * <h2>Implementing one</h2>
 *
 * <pre>{@code
 * Chat.registerFilter(new MessageFilter() {
 *     public String id() { return "chatfilter:words"; }
 *     public Screening screen(ServerPlayer sender, String text, String channel) {
 *         if (!rude(text)) return Screening.pass();
 *         return Screening.censor(starred(text), viewer -> viewer == sender);
 *     }
 * });
 * }</pre>
 *
 * <p>By the time {@code screen} is called the sender is <b>known not to be muted</b>. Keep it cheap:
 * it runs on the server thread for every line.</p>
 */
public interface MessageFilter {

    /** Ordinary public chat, including lines a {@link ChatRouter} is about to claim. */
    String CHAT = "chat";
    /** {@code /msg} and its aliases, and {@code /r}. */
    String PRIVATE = "private";
    /** {@code /me}. */
    String EMOTE = "emote";
    /** {@code /mail send}. The recipient reads it later, so there is no per-viewer copy. */
    String MAIL = "mail";

    /** A stable id, {@code modid:name}, used in logs when something goes wrong. */
    String id();

    /** Higher is asked first. */
    default int priority() {
        return 0;
    }

    /**
     * Judge one message.
     *
     * @param sender  the player talking, never muted at this point
     * @param text    what they typed, after any earlier filter's censoring
     * @param channel one of the constants above, or another mod's own channel name
     */
    Screening screen(ServerPlayer sender, String text, String channel);
}
