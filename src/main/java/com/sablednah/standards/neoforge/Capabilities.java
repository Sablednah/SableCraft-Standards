package com.sablednah.standards.neoforge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.network.CapabilitiesPayload;

/**
 * What to tell a modded client it may do.
 *
 * <h2>Derived, never duplicated</h2>
 *
 * <p>Every entry asks the <b>same</b> {@code StandardsPermissions} node the command's
 * {@code requires()} asks, and the same {@code StandardsConfig} switch that decides whether the
 * command exists at all. A second copy of either would drift, and the symptom would be a button
 * that draws for a command the server does not have — which is the exact bug the payload exists to
 * prevent, arriving by another door.</p>
 *
 * <p>Config first, then permission, in that order and for a reason: decision 7 says a command that
 * is off in config is <em>not registered</em>, so no permission can make it exist. Asking
 * permission first would offer a button for something nobody can run.</p>
 */
public final class Capabilities {

    /** One drawable action: the id the client knows it by, and what has to be true to offer it. */
    private record Entry(String id, java.util.function.BooleanSupplier enabled,
            PermissionNode<Boolean> node) {}

    /**
     * The catalogue.
     *
     * <p>Only switches and quick actions — things a button is genuinely better at than typing.
     * Anything needing an argument stays a command, because a button that opens a text box to fill
     * in an argument is a worse way to type.</p>
     */
    private static final java.util.List<Entry> ENTRIES = java.util.List.of(
            new Entry("fly", StandardsConfig.ENABLE_FLY::get, StandardsPermissions.FLY),
            new Entry("god", StandardsConfig.ENABLE_GOD::get, StandardsPermissions.GOD),
            new Entry("vanish", StandardsConfig.ENABLE_VANISH::get, StandardsPermissions.VANISH),
            new Entry("home", StandardsConfig.ENABLE_HOMES::get, StandardsPermissions.HOME),
            new Entry("spawn", StandardsConfig.ENABLE_SPAWN::get, StandardsPermissions.SPAWN),
            new Entry("back", StandardsConfig.ENABLE_BACK::get, StandardsPermissions.BACK));

    /** Work out what this player may do, and hand it over — if they are listening at all. */
    public static void send(ServerPlayer player) {
        Set<String> actions = new LinkedHashSet<>();
        for (Entry entry : ENTRIES) {
            if (entry.enabled().getAsBoolean()
                    && StandardsPermissions.has(player, entry.node())) {
                actions.add(entry.id());
            }
        }
        Map<String, String> hints = new LinkedHashMap<>();
        // Deliberately opaque text rather than numbers the client would have to interpret. The
        // moment the client parses a hint it is making decisions again.
        if (actions.contains("home")) {
            StandardsData data = StandardsData.get(player.level().getServer());
            int held = data.homesOf(player.getUUID()).size();
            hints.put("home", String.valueOf(held));
        }
        // sendIfAble, always. optional() makes the handshake tolerant; it does not make this send
        // droppable, and a bare sendToPlayer here would kick every vanilla player who joined.
        Net.sendIfAble(player, new CapabilitiesPayload(Set.copyOf(actions), Map.copyOf(hints)));
    }

    /**
     * Resend to everybody. Called where the command tree is resent, and for the same reason.
     *
     * <p>{@code PermissionCommands.refresh} already resends the command tree on every permission
     * edit, because the client's copy goes stale and a granted command renders red. The capability
     * set goes stale in exactly the same way and on exactly the same events, so it rides the same
     * trigger rather than inventing a second one that could be forgotten.</p>
     */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private Capabilities() {}
}
