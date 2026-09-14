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

    public static boolean sendIfAble(ServerPlayer player, CustomPacketPayload payload) {
        // Returns whether it actually went, so a caller with a text fallback can drive it off what
        // HAPPENED rather than off a second, separately-fallible prediction. The send and its
        // guard are one call for the same reason: asking listening() and then sending separately
        // is two checks that can disagree, and when the screen fails to open there is then no way
        // to tell which of them was wrong.
        if (listening(player, payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
            return true;
        }
        return false;
    }

    /** Does this player have the mod installed? Decides whether a richer prompt is possible. */
    public static boolean listening(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        // Asking hasChannel is itself the hazard, not the send. It reads a netty channel
        // attribute, and a NeoForge FakePlayer HAS a connection object — its FakeConnection simply
        // has no channel — so a null check on player.connection passes and hasChannel throws an
        // NPE out of whatever event asked. (Chronicler's self-test hit it when a panel send reached
        // a fake player; ZombieMod reproduced it headlessly.) isFakePlayer() catches NeoForge's
        // class and its subclasses; isConnected() catches a mod that hand-rolls a fake ServerPlayer
        // without it. Other mods' fake players reach this method, since it is public.
        return player.connection != null
                && !player.isFakePlayer()
                && player.connection.getConnection().isConnected()
                && player.connection.hasChannel(type);
    }

    private Net() {}
}
