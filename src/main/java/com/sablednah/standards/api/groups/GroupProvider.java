package com.sablednah.standards.api.groups;

import java.util.Collection;
import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;

/**
 * The mod that actually knows who is in what.
 *
 * <h2>Query membership, own rendering</h2>
 *
 * <p><b>The most important rule in this API.</b> Standards never owns membership. LegendQuest's
 * parties drive shared kill XP, friendly-fire suppression and party teleport — per-kill and
 * per-tick paths — and a seam that made those read another mod's truth would be a bad seam even
 * while it happened to be correct.</p>
 *
 * <p>So: the provider owns who is in what, the invites, the lifecycle and the persistence.
 * Standards owns the scoreboard team slot, the chat tag, and the group-aware behaviour of its own
 * commands.</p>
 *
 * <h2>Membership may be computed</h2>
 *
 * <p>Nothing here assumes a stored member list. LegendQuest's guilds derive from character class —
 * rogues in the thieves' guild, mages in the arcane one — with no invites and nothing persisted.
 * An interface that quietly assumed storage would work for parties and factions and break on the
 * first guild, which is exactly the sort of thing that breaks late.</p>
 *
 * <h2>Registering</h2>
 *
 * <pre>{@code
 * Groups.register(new GroupProvider() {
 *     public GroupKind kind() { return PARTY_KIND; }
 *     public Collection<Group> groupsOf(ServerPlayer p) {
 *         return partyOf(p).map(List::of).orElse(List.of());
 *     }
 *     public Optional<Group> byName(String name) { return partyNamed(name); }
 *     public Collection<Group> all() { return allParties(); }
 * });
 * }</pre>
 *
 * <p>Call during setup, guarded by a {@code standards} loaded check. One provider per kind; a
 * second registration for the same kind is refused and logged rather than silently replacing the
 * first, because two mods disagreeing about who is in a party is not something to resolve
 * quietly.</p>
 */
public interface GroupProvider {

    /** The kind this provider answers for. One provider per kind. */
    GroupKind kind();

    /**
     * The groups of this kind that the player belongs to.
     *
     * <p>Empty when they belong to none. For an {@linkplain GroupKind#exclusive() exclusive} kind
     * this returns at most one, and {@link Groups#primary} is the accessor consumers should use.</p>
     *
     * <p>Called from chat rendering and from grief checks, so keep it cheap.</p>
     */
    Collection<Group> groupsOf(ServerPlayer player);

    /** Look one up by its player-facing name, for commands. Empty if there is no such group. */
    Optional<Group> byName(String name);

    /**
     * Every group of this kind.
     *
     * <p>May be empty for a computed kind that cannot enumerate itself, which is not an error —
     * consumers use it for listings and must cope with getting nothing.</p>
     */
    default Collection<Group> all() {
        return java.util.List.of();
    }
}
