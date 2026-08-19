package com.sablednah.standards.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one true clientbound send.
 *
 * <p>NeoForge does <b>not</b> silently drop an optional payload to a client that never negotiated
 * the channel — it throws, synchronously, on the server thread. From a login handler that takes
 * vanilla's own login flow with it and the player is kicked with "Invalid player data": a
 * cosmetic feature destroying the ability to join. <b>Optional means the handshake tolerates a
 * missing channel, not that sends are droppable.</b></p>
 *
 * <p>Nor is it a negotiation race that a later event would fix — channels are agreed during the
 * configuration phase, before {@code PlayerLoggedInEvent}. A vanilla client simply never has the
 * channel, at any point. So every clientbound payload goes through this guard, permanently.</p>
 *
 * <p>(Found the hard way in LegendQuest, re-found in ZombieMod. Written down here before it can
 * be found a third time.)</p>
 */
public final class Net {

    public static void sendIfAble(ServerPlayer player, CustomPacketPayload payload) {
        // The null check is not paranoia: fake players (other mods' automation, headless probes)
        // sit in the player list with no real connection, and an NPE here has the same blast
        // radius as the original bug from a different direction.
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Does this player have the mod installed? Decides whether a richer prompt is possible. */
    public static boolean listening(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }

    private Net() {}
}
