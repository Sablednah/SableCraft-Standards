package com.sablednah.standards.neoforge;

import java.util.List;
import java.util.UUID;

import com.sablednah.standards.core.VanishGate;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.server.level.ServerPlayer;

/**
 * Being invisible properly.
 *
 * <p>Not {@code setInvisible(true)} — that is the potion effect, and it still shows armour,
 * particles, held items and a name tag. A vanished player is <em>absent</em>: not tracked, not in
 * the tab list, not targeted by mobs, not picking things up in front of people.</p>
 *
 * <p>The hiding itself happens in {@link com.sablednah.standards.mixin.ServerPlayerVanishMixin},
 * which answers vanilla's own visibility question. This class holds the state that mixin consults
 * and the extra consequences the tracker does not cover.</p>
 */
public final class Vanish {

    /**
     * Teach {@link VanishGate} how to answer the permission half of its question.
     *
     * <p>The gate holds the state because the mixin must not reach into this class — see
     * {@link com.sablednah.standards.mixin.ServerPlayerVanishMixin}. This is the one wire between
     * them, connected once the mod is up and the permission API is safe to touch.</p>
     */
    public static void install() {
        VanishGate.setSeeThroughCheck((subject, viewerId) -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return true;
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            return viewer == null
                    || StandardsPermissions.has(viewer, StandardsPermissions.VANISH_SEE);
        });
    }

    public static boolean isVanished(ServerPlayer player) {
        return VanishGate.isVanished(player.getUUID());
    }

    /** Restore the in-memory set from a returning player's saved state. */
    static void onLogin(ServerPlayer player) {
        if (StandardsAttachments.of(player).vanished()) {
            VanishGate.setVanished(player.getUUID(), true);
            hideFromEveryone(player);
        }
    }

    static void onLogout(ServerPlayer player) {
        // The saved flag is what persists; the live set is rebuilt on login.
        VanishGate.setVanished(player.getUUID(), false);
    }

    /**
     * Vanish or reappear.
     *
     * <p>Both directions are then handled by vanilla's tracker within a tick, because
     * {@code broadcastToPlayer} has changed its answer. Vanishing additionally pushes removal
     * packets immediately — a ghost that lingers for two ticks is exactly the kind of detail that
     * makes a vanish feel unreliable, and it costs one packet per viewer to avoid.</p>
     */
    public static void set(ServerPlayer player, boolean vanished) {
        if (vanished) {
            VanishGate.setVanished(player.getUUID(), true);
            hideFromEveryone(player);
        } else {
            VanishGate.setVanished(player.getUUID(), false);
            // Nothing to send: the next tracking pass re-pairs them and vanilla sends the proper
            // spawn packets itself. Faking that by hand would mean reimplementing sendPairingData.
            showToEveryone(player);
        }
        StandardsAttachments.of(player).setVanished(vanished);
    }

    private static void hideFromEveryone(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == player || StandardsPermissions.has(viewer, StandardsPermissions.VANISH_SEE)) {
                continue;
            }
            viewer.connection.send(new ClientboundRemoveEntitiesPacket(player.getId()));
            // The tab list is a separate system from entity tracking — a player removed from the
            // world but still listed is the giveaway that gives every half-built vanish away.
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
        }
    }

    private static void showToEveryone(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == player) continue;
            viewer.connection.send(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                    .createPlayerInitializing(List.of(player)));
        }
    }

    private Vanish() {}
}
