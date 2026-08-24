package com.sablednah.standards.api.groups;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Where Standards asks who is in a group with whom.
 *
 * <p>The fourth seam, and deliberately not shaped like the other three. Worth stating why, because
 * the four look alike and behave differently:</p>
 *
 * <table>
 *   <caption>The seams</caption>
 *   <tr><td>Economy</td><td><b>one provider wins</b></td>
 *       <td>a balance is a single fact; two ledgers disagreeing is worse than either</td></tr>
 *   <tr><td>Chat decorators</td><td><b>everyone contributes</b></td>
 *       <td>a name carries a faction tag and a party tag and a rank without contradiction</td></tr>
 *   <tr><td>Chat routers</td><td><b>first claimant wins</b></td>
 *       <td>a message goes to exactly one audience</td></tr>
 *   <tr><td>Groups</td><td><b>one provider per kind</b></td>
 *       <td>a player is in a party and a faction and a guild, and those do not compete</td></tr>
 * </table>
 *
 * <p><b>Standards owns none of the memberships.</b> See {@link GroupProvider}.</p>
 *
 * <h2>Online players only, deliberately</h2>
 *
 * <p>Every accessor takes a {@link ServerPlayer} rather than a UUID, so nothing here answers for
 * somebody who is offline. That is a real boundary rather than an oversight: every consumer named
 * so far — friendly fire, chat rendering, teleport rules, grief checks — involves a player who is
 * present, and a computed kind often <em>cannot</em> answer for an absent player at all. If an
 * offline question turns up, it wants adding here with its own contract rather than being faked.</p>
 */
public final class Groups {

    private static final Logger LOG = LogUtils.getLogger();

    /** Keyed by kind id, so a second provider for the same kind is a conflict rather than a race. */
    private static final Map<String, GroupProvider> PROVIDERS = new LinkedHashMap<>();

    /**
     * Register the provider for a kind. Call during setup, guarded by a {@code standards} loaded
     * check.
     *
     * @return true if it was taken; false if that kind already had a provider, in which case the
     *         first one keeps it and this is logged as an error rather than swallowed
     */
    public static synchronized boolean register(GroupProvider provider) {
        GroupKind kind = provider.kind();
        GroupProvider existing = PROVIDERS.get(kind.id());
        if (existing != null) {
            // Two mods disagreeing about who is in a party is not something to settle quietly.
            LOG.error("Standards: group kind '{}' already has a provider; refusing the second",
                    kind.id());
            return false;
        }
        PROVIDERS.put(kind.id(), provider);
        LOG.info("Standards: group kind '{}' registered ({}, {} total)",
                kind.id(), kind.exclusive() ? "exclusive" : "multiple", PROVIDERS.size());
        return true;
    }

    /** For the self-test, which must not leave its fixtures behind. */
    public static synchronized void unregister(GroupKind kind) {
        PROVIDERS.remove(kind.id());
    }

    /** Every kind that has a provider, in registration order. */
    public static synchronized List<GroupKind> kinds() {
        return PROVIDERS.values().stream().map(GroupProvider::kind).toList();
    }

    /** The kind with this id, if anything provides it. */
    public static synchronized Optional<GroupKind> kind(String id) {
        return Optional.ofNullable(PROVIDERS.get(id)).map(GroupProvider::kind);
    }

    /**
     * The player's single group of an exclusive kind.
     *
     * <p>Empty for a non-exclusive kind — asking for "the" staff role of somebody who is both a
     * moderator and a builder has no answer, and picking one arbitrarily would be worse than
     * saying so. Use {@link #all} there.</p>
     */
    public static Optional<Group> primary(ServerPlayer player, GroupKind kind) {
        if (!kind.exclusive()) {
            return Optional.empty();
        }
        return all(player, kind).stream().findFirst();
    }

    /** Every group of one kind that the player is in. */
    public static Collection<Group> all(ServerPlayer player, GroupKind kind) {
        GroupProvider provider;
        synchronized (Groups.class) {
            provider = PROVIDERS.get(kind.id());
        }
        return provider == null ? List.of() : safely(provider, player);
    }

    /** Every group of every kind that the player is in. */
    public static Collection<Group> all(ServerPlayer player) {
        List<GroupProvider> providers;
        synchronized (Groups.class) {
            providers = List.copyOf(PROVIDERS.values());
        }
        List<Group> out = new ArrayList<>();
        for (GroupProvider provider : providers) {
            out.addAll(safely(provider, player));
        }
        return out;
    }

    /**
     * Whether two players share any group at all.
     *
     * <p>The workhorse: friendly fire, shared homes, a free teleport between allies. A player
     * always shares with themselves, which is worth stating because the alternative reads as a bug
     * at every call site that forgot to check.</p>
     */
    public static boolean share(ServerPlayer a, ServerPlayer b) {
        if (a.getUUID().equals(b.getUUID())) {
            return true;
        }
        for (Group group : all(a)) {
            if (group.contains(b.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** Whether two players share a group of one particular kind. */
    public static boolean share(ServerPlayer a, ServerPlayer b, GroupKind kind) {
        if (a.getUUID().equals(b.getUUID())) {
            return true;
        }
        for (Group group : all(a, kind)) {
            if (group.contains(b.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** A misbehaving provider must not cost everybody their chat, their teleports or their blocks. */
    private static Collection<Group> safely(GroupProvider provider, ServerPlayer player) {
        try {
            Collection<Group> groups = provider.groupsOf(player);
            return groups == null ? List.of() : groups;
        } catch (RuntimeException e) {
            LOG.error("Standards: group provider '{}' threw; treating the player as ungrouped",
                    provider.kind().id(), e);
            return List.of();
        }
    }

    private Groups() {}
}
