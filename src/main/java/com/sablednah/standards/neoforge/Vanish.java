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
 * <h2>What vanish hides, and what it deliberately does not</h2>
 *
 * <p><b>Vanish hides the player, not their effects on the world.</b> That line is deliberate and
 * settled — do not "complete" the feature by suppressing the second half.</p>
 *
 * <table>
 * <tr><th>Hidden</th><th>Not hidden</th></tr>
 * <tr><td>the body itself (entity tracking)</td><td>doors and chests opening</td></tr>
 * <tr><td>the tab list</td><td>blocks broken or placed</td></tr>
 * <tr><td>mob targeting</td><td>footsteps and item sounds</td></tr>
 * <tr><td>incoming damage</td><td>anything else the world does in reaction</td></tr>
 * <tr><td>being pushed</td><td></td></tr>
 * <tr><td>being messaged by those who cannot see you</td><td></td></tr>
 * </table>
 *
 * <p>Two reasons. It is more fun — a chest opening by itself is the point of being a ghost. And it
 * is the only accountability left: a staff member who is <em>completely</em> undetectable can go
 * through anyone's belongings with no trace whatever. Leaving the world's reaction visible means
 * the trail still exists, it is merely anonymous. (Owner's call, and their reasoning: "this is why
 * you need mods you can trust to play not abuse".)</p>
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

    /**
     * Restore the in-memory holds from a returning player's saved state.
     *
     * <p>Only the <b>command</b> hold persists, and deliberately: a hold another mod placed belongs
     * to whatever that mod was doing at the time, and a scene that ended while somebody was offline
     * should not still be hiding them a week later. A mod that wants its hold back re-places it.</p>
     */
    static void onLogin(ServerPlayer player) {
        if (StandardsAttachments.of(player).vanished()) {
            VanishGate.hold(player.getUUID(), COMMAND_KEY, true);
            hideFromEveryone(player);
        }
    }

    static void onLogout(ServerPlayer player) {
        // Every hold, not just ours. The live map is rebuilt on login from the saved flag, and a
        // foreign hold left behind would hide a player nobody is holding any more — the same
        // reasoning that makes /f bypass and the teleport warmups die with the session.
        VanishGate.clear(player.getUUID());
    }

    /**
     * Vanish or reappear.
     *
     * <p>Both directions are then handled by vanilla's tracker within a tick, because
     * {@code broadcastToPlayer} has changed its answer. Vanishing additionally pushes removal
     * packets immediately — a ghost that lingers for two ticks is exactly the kind of detail that
     * makes a vanish feel unreliable, and it costs one packet per viewer to avoid.</p>
     */
    /** The hold {@code /vanish} itself places. Named so another mod cannot release it by accident. */
    public static final String COMMAND_KEY = "standards:command";

    /**
     * Hide or reveal a player under a named hold.
     *
     * <p>Several things may want somebody hidden at once — the player's own {@code /vanish}, and a
     * storyteller mod running a scene. Each holds under its own key and releases only its own, so
     * a scene ending cannot reveal somebody who had vanished themselves first. The player is hidden
     * while <b>any</b> hold stands.</p>
     *
     * @return true if the visible/hidden state actually changed, so a caller can tell a real change
     *         from a no-op and say something honest about it
     */
    public static boolean hold(ServerPlayer player, String key, boolean held) {
        boolean changed = VanishGate.hold(player.getUUID(), key, held);
        if (!changed) {
            // Somebody else still holds them, or already did. Nothing to send and nothing to
            // announce: the state on the wire is already right.
            return false;
        }
        apply(player, VanishGate.isVanished(player.getUUID()));
        return true;
    }

    /** Who is hiding this player. Empty means nobody. */
    public static java.util.Set<String> holders(ServerPlayer player) {
        return VanishGate.holders(player.getUUID());
    }

    /**
     * What {@code /vanish} drives. Releases only the command's own hold.
     *
     * <p>So a player who types {@code /vanish off} while a storyteller mod is running a scene
     * through them stays hidden — and is <b>told so</b>. The switch would otherwise report "off"
     * while they were still invisible, which is the worst of both: they walk out in front of
     * somebody believing they can be seen.</p>
     */
    public static void set(ServerPlayer player, boolean vanished) {
        hold(player, COMMAND_KEY, vanished);
        if (!vanished && VanishGate.isVanished(player.getUUID())) {
            Feedback.chat(player, Lang.fmt("msg.vanish.still_held",
                    "holders", String.join(", ", holders(player))));
        }
    }

    private static void apply(ServerPlayer player, boolean vanished) {
        if (vanished) {
            hideFromEveryone(player);
            forgetTargets(player);
        } else {
            // Nothing to send: the next tracking pass re-pairs them and vanilla sends the proper
            // spawn packets itself. Faking that by hand would mean reimplementing sendPairingData.
            showToEveryone(player);
        }
        StandardsAttachments.of(player).setVanished(vanished);

        // Last, so a listener asking Vanish.isVanished gets the new answer rather than the old
        // one. Anything another mod has drawn on this player - a nameplate, a health bar, a
        // hologram - is invisible to our tracker and can only be taken down by whoever put it
        // there. See api/vanish for why that division is the right one.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new com.sablednah.standards.api.vanish.VanishEvent(player, vanished));
    }

    /**
     * Make anything currently hunting this player forget them.
     *
     * <p>Blocking new targets is the easy half and {@code StandardsEvents.onVanishedTargeted} does
     * it: every acquisition path runs through {@code Mob.setTarget}, which fires
     * {@code LivingChangeTargetEvent}. But a mob that already had you when you vanished never calls
     * {@code setTarget} again, so nothing fires and it keeps coming — the one hole left, and the
     * one a storyteller would meet the instant they vanished to run a scene while something was
     * chasing them.</p>
     *
     * <p>Clearing the target rather than trying to reset anger: an angered enderman or piglin will
     * try to re-acquire, and re-acquisition goes through {@code setTarget} and is refused. So the
     * two halves together close it, where either alone leaves a gap.</p>
     *
     * <p>64 blocks, comfortably past any vanilla follow range, and only on the rare act of
     * vanishing rather than per tick. What it cannot undo is a blow already in flight or a creeper
     * already lit — vanishing is walking away from a fight, not rewinding it.</p>
     */
    private static void forgetTargets(ServerPlayer player) {
        net.minecraft.world.phys.AABB around = player.getBoundingBox().inflate(64.0D);
        for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, around, m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
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
