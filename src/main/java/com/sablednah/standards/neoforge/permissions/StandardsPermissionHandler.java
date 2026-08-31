package com.sablednah.standards.neoforge.permissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.PermissionRules;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Standards' own permission handler — groups and grants for a server with no permissions mod.
 *
 * <h2>This is one more handler, not a Vault</h2>
 *
 * <p>Worth stating first because the obvious framing is wrong. NeoForge's {@link PermissionAPI}
 * <em>is</em> the facade: handlers register themselves at {@code PermissionGatherEvent.Handler} and
 * the <b>server owner picks the active one</b> in {@code neoforge-server.toml} — an explicit
 * setting, not a priority contest. LuckPerms is one registrant among however many are installed,
 * and so is this. Standards already asks that facade for every check, which is exactly why it
 * behaves identically with LuckPerms and without it.</p>
 *
 * <p>So there is no arbitration layer here and there must not be one. This is
 * <b>dormant unless chosen</b>: set {@code permissionHandler = "standards:permissions"} and
 * anybody running LuckPerms is untouched.</p>
 *
 * <h2>The gap it fills</h2>
 *
 * <p>NeoForge's {@code DefaultPermissionHandler} answers every question with the node's own
 * default, which means that on a server with no permissions mod <b>you cannot grant anybody
 * anything</b>. {@code standards.fly} is op-or-nothing; a trusted regular cannot have flight, a
 * donor cannot have ten homes, and a builder cannot have {@code /craft} without also being handed
 * {@code /stop}. Same gap the built-in economy ledger fills, and the same answer: ship something
 * that works, and step aside the moment a real one arrives.</p>
 *
 * <h2>Resolution</h2>
 *
 * <p>{@link PermissionRules} does the deciding and knows nothing about Minecraft; this class only
 * assembles the tiers, nearest the player first:</p>
 *
 * <ol>
 * <li>the player's own grants and denials;</li>
 * <li>the groups they are directly in;</li>
 * <li>those groups' parents, then their parents, outward;</li>
 * <li>the default group, which everybody is in without being put there;</li>
 * <li><b>the node's own default resolver</b> — so op still means op and everyone-nodes still work
 *     for anybody nothing has been said about.</li>
 * </ol>
 *
 * <p>Falling through to step 5 is what makes this safe to switch on: <b>a server that enables this
 * handler and grants nothing behaves exactly as it did before.</b> That property is worth
 * protecting — it is the difference between an owner trying it and an owner who tried it once.</p>
 *
 * <h2>Only booleans are answered here</h2>
 *
 * <p>The store holds true/false. An integer, string or component node falls through to its own
 * resolver untouched rather than being coerced into a guess — every node Standards ships is a
 * boolean, and inventing an answer for another mod's typed node would be worse than declining
 * to have an opinion.</p>
 */
public final class StandardsPermissionHandler implements IPermissionHandler {

    public static final Identifier IDENTIFIER =
            Identifier.fromNamespaceAndPath(Standards.MODID, "permissions");

    /** What {@link PermissionRules} calls the player's own tier, and what {@code /rank} prints. */
    public static final String SELF_SCOPE = "you";

    private final Set<PermissionNode<?>> nodes;
    private final Set<PermissionNode<?>> immutableNodes;

    public StandardsPermissionHandler(Collection<PermissionNode<?>> permissions) {
        this.nodes = new HashSet<>(permissions);
        this.immutableNodes = Collections.unmodifiableSet(this.nodes);
    }

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<PermissionNode<?>> getRegisteredNodes() {
        return immutableNodes;
    }

    @Override
    public <T> T getPermission(ServerPlayer player, PermissionNode<T> node,
            PermissionDynamicContext<?>... context) {
        return resolve(player, player.getUUID(), node, context);
    }

    @Override
    public <T> T getOfflinePermission(UUID player, PermissionNode<T> node,
            PermissionDynamicContext<?>... context) {
        return resolve(null, player, node, context);
    }

    @SuppressWarnings("unchecked")
    private <T> T resolve(ServerPlayer player, UUID uuid, PermissionNode<T> node,
            PermissionDynamicContext<?>... context) {
        if (node.getType() == PermissionTypes.BOOLEAN) {
            Optional<PermissionRules.Answer> answer = explain(uuid, node.getNodeName());
            if (answer.isPresent()) {
                return (T) (Object) answer.get().allowed();
            }
        }
        return node.getDefaultResolver().resolve(player, uuid, context);
    }

    /**
     * What this store says about one node for one player, and <b>which rule said it</b>.
     *
     * <p>The provenance is the half that earns its keep. Every hour lost to a permissions system
     * is spent asking "why does this player have that", and a system that can only answer yes or
     * no leaves you bisecting it by hand. {@code /rank check} and {@code /rank user info} both
     * come through here, so what they print is what the resolver actually did rather than a
     * second implementation that agrees until it does not.</p>
     *
     * @return empty when nothing in the store has an opinion — the caller falls through to the
     *         node's own default
     */
    public static Optional<PermissionRules.Answer> explain(UUID player, String node) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Optional.empty();
        }
        return PermissionRules.resolve(tiers(PermissionStore.get(server), player), node);
    }

    /**
     * The scope chain for a player, nearest first.
     *
     * <p>Breadth-first over the inheritance graph, so a group's own nodes always outrank the ones
     * it inherits however deep the chain runs. The seen-set is not only about cycles — a diamond
     * ({@code admin} and {@code builder} both inheriting {@code trusted}) would otherwise put the
     * same group in two tiers, and the further one would be dead weight on every lookup.</p>
     */
    static List<List<PermissionRules.Scope>> tiers(PermissionStore store, UUID player) {
        List<List<PermissionRules.Scope>> out = new ArrayList<>();
        out.add(List.of(new PermissionRules.Scope(SELF_SCOPE, store.nodesOf(player))));

        Set<String> seen = new LinkedHashSet<>();
        List<String> frontier = new ArrayList<>();
        for (String group : store.groupsOf(player)) {
            if (seen.add(group.toLowerCase(Locale.ROOT))) {
                frontier.add(group);
            }
        }
        while (!frontier.isEmpty()) {
            List<PermissionRules.Scope> tier = new ArrayList<>();
            List<String> next = new ArrayList<>();
            for (String name : frontier) {
                store.group(name).ifPresent(entry -> {
                    tier.add(new PermissionRules.Scope(entry.name(), entry.nodes()));
                    for (String parent : entry.parents()) {
                        if (seen.add(parent.toLowerCase(Locale.ROOT))) {
                            next.add(parent);
                        }
                    }
                });
            }
            if (!tier.isEmpty()) {
                out.add(List.copyOf(tier));
            }
            frontier = next;
        }

        // The default group is last, and implicit: everybody is in it without being put there,
        // which is what makes "grant this to every player" a single edit. Skipped when they are
        // already in it explicitly, or it would answer twice from two different distances.
        String fallback = StandardsConfig.DEFAULT_PERMISSION_GROUP.get();
        if (!fallback.isBlank() && seen.add(fallback.toLowerCase(Locale.ROOT))) {
            store.group(fallback).ifPresent(entry ->
                    out.add(List.of(new PermissionRules.Scope(entry.name(), entry.nodes()))));
        }
        return out;
    }

    /**
     * Whether this handler is the one actually answering.
     *
     * <p>{@code /rank} (and its {@code /perm} alias) is registered only when it is. Editing a store
     * nothing reads is the worst
     * shape a command can have — it accepts every edit, reports success, and changes nothing —
     * and a server running LuckPerms would hit exactly that. See decision 7: a command that will
     * not work is absent, not present and arguing.</p>
     */
    public static boolean isActive() {
        return IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler());
    }
}
