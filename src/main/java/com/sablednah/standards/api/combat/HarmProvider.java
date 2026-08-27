package com.sablednah.standards.api.combat;

import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Something that can forbid one player harming another.
 *
 * <p>A faction that has declared itself peaceful, two people in the same faction, an alliance, a
 * party, a safe zone, a duel that has not started yet. The mod that knows the relationship answers;
 * everybody else just asks.</p>
 *
 * <h2>Any veto denies — unlike every other seam here</h2>
 *
 * <p>Worth stating plainly, because the other seams behave differently and the difference is
 * deliberate. Exactly one economy provider holds the money. The highest-priority claims provider
 * wins. The first chat router to claim a message ends the matter. <b>Here, every provider is asked
 * and any single refusal is final.</b></p>
 *
 * <p>Because a refusal is a promise. A faction that opted out of fighting has been told it cannot
 * be dragged back in, and a priority contest would mean that promise held only until somebody
 * registered a provider with a bigger number. There is no ordering to argue about and no way to
 * out-rank a "no".</p>
 *
 * <p>Which means this seam only ever <em>forbids</em>. It cannot be used to permit something
 * another mod has forbidden — a mod that genuinely needs to override, like an arena, should cancel
 * the damage event at its own priority instead, where it is visibly taking responsibility rather
 * than quietly outbidding somebody.</p>
 *
 * <h2>Answer for what you know about</h2>
 *
 * <p>Return empty for a pair you have no opinion on. A faction mod knows nothing about two players
 * with no faction and should say so, rather than inventing a policy for them.</p>
 */
public interface HarmProvider {

    /** A stable id, {@code modid:reason}, used in logs when something goes wrong. */
    String id();

    /**
     * Whether this provider forbids the attacker harming the victim.
     *
     * <p>Called for player-on-player damage, and by any mod about to do something hostile that is
     * not damage — a curse, a snare, a summon aimed at somebody.</p>
     *
     * @return the reason to show the attacker, or empty to have no opinion
     */
    Optional<Component> forbids(ServerPlayer attacker, ServerPlayer victim);
}
