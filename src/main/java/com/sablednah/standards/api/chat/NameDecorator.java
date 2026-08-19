package com.sablednah.standards.api.chat;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;

/**
 * Something that adds a prefix or a suffix to a player's name in chat.
 *
 * <p>The point is that <em>several</em> mods do this at once and none of them knows about the
 * others. A faction mod contributes a tag, a party mod contributes another, LegendQuest
 * contributes a rank from character level and an epithet from karma, and the result reads as one
 * line:</p>
 *
 * <pre>
 * [FACTION][PARTY] Lord Sablednah the noble: says hello
 * </pre>
 *
 * <h2>Ordering: priority is closeness to the name</h2>
 *
 * <p>One rule, applied to both sides. A <b>higher</b> {@link #priority()} sits <b>nearer the
 * name</b>; lower priorities are pushed outwards. So prefixes render lowest-priority-leftmost, and
 * suffixes mirror that — highest priority immediately after the name.</p>
 *
 * <p>That is what lets the example above come out right with nobody coordinating: the party tag
 * registers low and drifts out to the left, while LegendQuest's rank registers higher and stays
 * welded to the name, where a title belongs.</p>
 *
 * <h2>Implementing one</h2>
 *
 * <pre>{@code
 * Chat.register(new NameDecorator() {
 *     public String id() { return "legendquest:rank"; }
 *     public int priority() { return 100; }            // close to the name
 *     public Optional<String> prefix(ServerPlayer p) {
 *         return rankOf(p).map(rank -> "&6" + rank);    // "Lord"
 *     }
 *     public Optional<String> suffix(ServerPlayer p) {
 *         return epithetOf(p).map(e -> "&7" + e);       // "the noble"
 *     }
 * });
 * }</pre>
 *
 * <p>Register during setup, guarded by a {@code standards} loaded check so it stays a soft
 * dependency. Returning empty means "nothing for this player right now" and is the normal case —
 * a player in no faction simply has no faction tag.</p>
 *
 * <p>Text uses {@code &} colour codes and is resolved server-side, so decorated names appear
 * correctly on <b>unmodified clients</b>. Called on the server thread for every chat message: keep
 * it cheap, and do not go looking things up over a network.</p>
 */
public interface NameDecorator {

    /** A stable id, for diagnostics and so an owner can switch one off. */
    String id();

    /**
     * Where this sits relative to other decorators. Higher is nearer the name.
     *
     * <p>Rough convention, so independently-written mods land sensibly without talking to each
     * other: 0–99 for broad affiliations (faction, team, party), 100–199 for character-level
     * things (rank, class, title), 200+ for anything that must hug the name.</p>
     */
    int priority();

    /** What to put before the name, if anything. */
    default Optional<String> prefix(ServerPlayer player) {
        return Optional.empty();
    }

    /** What to put after the name, if anything. */
    default Optional<String> suffix(ServerPlayer player) {
        return Optional.empty();
    }
}
