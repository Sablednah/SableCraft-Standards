package com.sablednah.standards.api;

import com.sablednah.standards.neoforge.StandardsAttachments;
import com.sablednah.standards.neoforge.StandardsEvents;
import com.sablednah.standards.neoforge.Vanish;

import net.minecraft.server.level.ServerPlayer;

/**
 * The switches, for code rather than for typing.
 *
 * <p>Every switch Standards ships is available here as a plain get/set, because the whole point of
 * the tri-state is that something other than a human drives it — a LegendQuest skill granting
 * flight for twenty seconds, a storyteller mod hiding the gamemaster, a shop enabling god mode in
 * a safe zone. Those callers should not be building command strings and pushing them through the
 * dispatcher; that costs a permission check they have already made and turns a typo into a
 * silent no-op.</p>
 *
 * <pre>{@code
 * // grant flight for the duration of a skill
 * PlayerSwitches.setFly(player, true);
 * // ...twenty seconds later
 * PlayerSwitches.setFly(player, false);
 * }</pre>
 *
 * <p><b>These bypass permissions deliberately.</b> The caller is the authority — a skill the
 * player has already paid for should not be second-guessed by whether they hold
 * {@code standards.fly}. If you want the permission checked, check it yourself with
 * {@code PermissionAPI}, or run the command.</p>
 *
 * <p>Server thread only, like everything else that touches a player.</p>
 */
public final class PlayerSwitches {

    // --- flight ---

    public static boolean fly(ServerPlayer player) {
        return StandardsAttachments.of(player).fly();
    }

    public static void setFly(ServerPlayer player, boolean enabled) {
        StandardsAttachments.of(player).setFly(enabled);
        StandardsEvents.applySwitches(player);
    }

    // --- invulnerability ---

    public static boolean god(ServerPlayer player) {
        return StandardsAttachments.of(player).god();
    }

    public static void setGod(ServerPlayer player, boolean enabled) {
        StandardsAttachments.of(player).setGod(enabled);
        StandardsEvents.applySwitches(player);
    }

    // --- vanish ---

    public static boolean vanished(ServerPlayer player) {
        return Vanish.isVanished(player);
    }

    /**
     * Hide or reveal a player. The named consumer for this is a gamemaster/storyteller mod that
     * wants to narrate a scene without a floating body in the middle of it.
     */
    public static void setVanished(ServerPlayer player, boolean vanished) {
        Vanish.set(player, vanished);
    }

    // --- teleport requests ---

    public static boolean acceptingTeleports(ServerPlayer player) {
        return !StandardsAttachments.of(player).refusingTeleports();
    }

    public static void setAcceptingTeleports(ServerPlayer player, boolean accepting) {
        StandardsAttachments.of(player).setRefusingTeleports(!accepting);
    }

    private PlayerSwitches() {}
}
