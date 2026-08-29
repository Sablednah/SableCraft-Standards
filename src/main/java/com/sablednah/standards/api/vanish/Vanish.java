package com.sablednah.standards.api.vanish;

import java.util.UUID;

import com.sablednah.standards.core.VanishGate;

import net.minecraft.server.level.ServerPlayer;

/**
 * Whether a player is hidden, for mods that draw things attached to players.
 *
 * <h2>Why this exists, and why Standards cannot do the job itself</h2>
 *
 * <p>Standards hides a vanished player by answering {@code false} from
 * {@code ServerPlayer.broadcastToPlayer}, which is the question vanilla's own entity tracker asks.
 * That covers the player. It does not, and cannot, cover a <b>nameplate, health bar, hologram or
 * particle trail another mod attached to them</b> — and a floating name hanging over nobody gives
 * a vanish away as completely as being seen would.</p>
 *
 * <p>The tempting fix is for Standards to hide entities near a vanished player. It is wrong in both
 * directions: it would catch other players' holograms, dropped items and pets that happen to be
 * standing there, and it would miss a decoration that tracks its owner from somewhere else. Which
 * entities <em>belong to</em> a player is a question only the mod that spawned them can answer. So
 * Standards answers the question it owns, and the owner of the decoration acts on it — the same
 * division as the chat, claims and combat seams.</p>
 *
 * <h2>Using it</h2>
 *
 * <p>Two mechanisms, because one is not enough and finding that out in production is unpleasant:</p>
 *
 * <ul>
 *   <li><b>Ask, when you create the decoration.</b> A player can log in already vanished, so the
 *       state exists before anything of yours does.</li>
 *   <li><b>Listen, for {@link VanishEvent}.</b> A player who vanishes <em>mid-session</em> is the
 *       case that actually bites: check-on-spawn alone leaves the decoration hanging there, which
 *       is precisely the bug this was written for.</li>
 * </ul>
 *
 * <pre>{@code
 * // when spawning
 * if (!Vanish.isVanished(player)) spawnNameplate(player);
 *
 * // and reacting
 * @SubscribeEvent
 * static void onVanish(VanishEvent event) {
 *     if (event.isVanished()) removeNameplate(event.getPlayer());
 *     else                    spawnNameplate(event.getPlayer());
 * }
 * }</pre>
 *
 * <p>{@link #hiddenFrom} is the per-viewer form, for a decoration whose visibility you track
 * yourself: staff holding {@code standards.vanish.see} still see the player, so they can sensibly
 * still see the name. Removing outright is simpler and fixes the giveaway; this is here for when
 * simpler is not good enough.</p>
 *
 * <p>Nothing here needs Standards to be present at runtime beyond the class itself — every method
 * answers "not vanished" on a server where the feature is off or nobody has used it.</p>
 */
public final class Vanish {

    /** Whether this player is currently vanished from ordinary players. */
    public static boolean isVanished(ServerPlayer player) {
        return player != null && VanishGate.isVanished(player.getUUID());
    }

    /** By id, for the offline and packet-level cases where no entity is to hand. */
    public static boolean isVanished(UUID player) {
        return player != null && VanishGate.isVanished(player);
    }

    /**
     * Whether {@code subject} should be invisible to {@code viewer} specifically.
     *
     * <p>Honours the see-through permission, and answers {@code false} when the two are the same
     * player — nobody is hidden from themselves.</p>
     */
    public static boolean hiddenFrom(ServerPlayer subject, ServerPlayer viewer) {
        return subject != null && viewer != null
                && VanishGate.hidden(subject.getUUID(), viewer.getUUID());
    }

    /** By id, as above. */
    public static boolean hiddenFrom(UUID subject, UUID viewer) {
        return subject != null && viewer != null && VanishGate.hidden(subject, viewer);
    }

    /**
     * Whether anybody at all is vanished.
     *
     * <p>For hot paths. On the overwhelming majority of servers this is one field read and lets a
     * per-entity or per-tick check bail out before doing any real work.</p>
     */
    public static boolean anyVanished() {
        return VanishGate.anyVanished();
    }

    private Vanish() {}
}
