package com.sablednah.standards.api.groups;

import java.util.Collection;
import java.util.UUID;

/**
 * One group: this party, that faction, the moderators.
 *
 * <p>Implemented by whoever owns the membership. Standards never constructs one.</p>
 */
public interface Group {

    /** Which sort of group this is. */
    GroupKind kind();

    /**
     * A stable identifier, unique within the kind, that survives a rename.
     *
     * <p>Config and stored references must key on this rather than on {@link #name()} — players
     * rename their groups, and a rename that silently drops a group's styling or its claims is a
     * bug that only shows up on somebody else's server.</p>
     */
    String id();

    /** What the players call it. Chosen by them, and changeable at any time. */
    String name();

    /**
     * Whether this player belongs.
     *
     * <p><b>The cheap question, and the one to prefer.</b> A grief check runs on every block break
     * and place, so it must not walk a member list — and for a computed kind there may not be one.
     * Implementations must keep this fast and allocation-light.</p>
     */
    boolean contains(UUID player);

    /**
     * Everyone in it.
     *
     * <p><b>May be incomplete, and may be expensive.</b> Membership is not necessarily stored:
     * LegendQuest's guilds derive from character class, so "every rogue on the server" is a
     * question with no cheap answer for players who are offline. A provider is permitted to
     * return only the members it can enumerate — typically the online ones — and consumers must
     * treat the result as a best effort rather than a roll.</p>
     *
     * <p>If you are asking in order to test one player, use {@link #contains} instead.</p>
     */
    Collection<UUID> members();
}
