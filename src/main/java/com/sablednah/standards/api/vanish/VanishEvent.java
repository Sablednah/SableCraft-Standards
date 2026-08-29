package com.sablednah.standards.api.vanish;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * A player has vanished or reappeared.
 *
 * <p>Fired on the server thread, <b>after</b> the change has taken effect — so
 * {@link Vanish#isVanished} already agrees with {@link #isVanished()} by the time a listener runs,
 * and there is no window where the two disagree. Not cancellable: by this point the player has
 * already been unpaired from every viewer's entity tracker, and a listener that "refused" would
 * leave the two halves contradicting each other.</p>
 *
 * <p><b>This is the half that matters for anything drawn on a player.</b> Checking
 * {@link Vanish#isVanished} when you spawn a nameplate handles the player who logs in already
 * hidden, and nothing else — a player who vanishes while your decoration is already in the world
 * leaves it hanging over an empty space. That was a real bug, found by hand on a dev server, and it
 * is the reason this event exists rather than the query alone.</p>
 *
 * <p>Fires only on a deliberate change while the player is online. A returning player's saved state
 * is restored during login, before mods have had a chance to attach anything to them, so there is
 * nothing to notify — <b>ask</b> in that case, do not wait to be told.</p>
 */
public class VanishEvent extends Event {

    private final ServerPlayer player;
    private final boolean vanished;

    public VanishEvent(ServerPlayer player, boolean vanished) {
        this.player = player;
        this.vanished = vanished;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** {@code true} if they have just vanished, {@code false} if they have just reappeared. */
    public boolean isVanished() {
        return vanished;
    }
}
