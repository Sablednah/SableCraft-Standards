package com.sablednah.standards.neoforge;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.neoforge.common.NeoForge;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.combat.Combat;
import com.sablednah.standards.api.combat.CombatKind;
import com.sablednah.standards.api.combat.CombatTagEvent;

/**
 * Turning "somebody just took damage" into who is now in a fight, and what sort.
 *
 * <h2>The rule, in one sentence</h2>
 *
 * <p><b>An attacker starts a tag, not damage.</b> Resolve the source to a player or a mob first; if
 * there is nobody behind it, there is no combat. The naive implementation is a single damage
 * handler that never asks, and it traps a player who is drowning in their own basement.</p>
 *
 * <h2>Both sides, and they may differ</h2>
 *
 * <p>A fight has two people in it and they are not always in the same fight. A player killing a
 * wolf is in PvE; the wolf's owner, whose pet is being killed, is in PvP — because somebody is
 * fighting them, whatever it looks like from the other end. Each side is classified on its own.</p>
 */
public final class Combatants {

    /** Called from the damage event. Tags whoever this damage put into a fight. */
    public static void onDamage(LivingEntity hurt, DamageSource source) {
        if (!Combat.hasAttacker(source)) {
            // Fall, drowning, cactus, fire, freezing. Nobody did this, so it is not combat — and
            // a player trapped in powder snow inside a claim needs their teleport to still work.
            return;
        }

        Optional<ServerPlayer> aggressor = Combat.playerBehind(source);
        boolean victimIsPlayer = hurt instanceof ServerPlayer;

        if (victimIsPlayer) {
            ServerPlayer victim = (ServerPlayer) hurt;
            if (aggressor.isPresent() && !aggressor.get().equals(victim)) {
                // A person is behind it, arrow or pet or fist alike. Both are in a PvP fight.
                apply(victim, CombatKind.PVP, "standards:player");
                apply(aggressor.get(), CombatKind.PVP, "standards:player");
            } else if (aggressor.isEmpty()) {
                // A mob, unowned. Being fought by the world.
                apply(victim, CombatKind.PVE, sourceId(source));
            }
            return;
        }

        // The victim is not a player. Only the attacker can be tagged, and only if they are one.
        aggressor.ifPresent(player -> {
            // Hitting somebody's pet is PvE for the person swinging — that half is deliberately
            // NOT symmetric, or a griefer could shove a wolf in front of you and lock you in
            // combat by making you defend yourself.
            apply(player, CombatKind.PVE, "standards:mob");

            // ...but its owner is being fought, and that is PvP for them.
            if (hurt instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner
                    && !owner.equals(player)) {
                apply(owner, CombatKind.PVP, "standards:pet");
            }
        });
    }

    /**
     * Offer the classification for correction, then tag.
     *
     * <p>Every tag goes through {@link CombatTagEvent} — including Standards' own — so a mod that
     * reclassifies is not working against a special case we forgot to route through it.</p>
     */
    private static void apply(ServerPlayer player, CombatKind kind, String source) {
        int seconds = Combat.secondsFor(kind);
        CombatTagEvent event = new CombatTagEvent(player, kind, source, seconds);
        if (NeoForge.EVENT_BUS.post(event).isCanceled() || event.getSeconds() <= 0) {
            return;
        }
        Combat.tag(player, event.getKind(), event.getSource(), event.getSeconds())
                .ifPresent(tag -> {
                    if (StandardsConfig.COMBAT_LOG.get()) {
                        // Costs nothing, and is the difference between tuning and guessing when
                        // somebody asks why their flamethrower does not tag.
                        Standards.LOGGER.info("Combat: {} -> {} via {} ({}s)",
                                player.getName().getString(), tag.kind().key(), tag.source(),
                                event.getSeconds());
                    }
                });
    }

    /** A readable id for what hit them, so the log line is worth reading. */
    private static String sourceId(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            return "standards:" + direct.getType().toShortString();
        }
        return "standards:" + source.getMsgId();
    }

    private Combatants() {}
}
