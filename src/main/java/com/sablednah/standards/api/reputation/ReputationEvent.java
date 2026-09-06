package com.sablednah.standards.api.reputation;

import java.util.UUID;

import net.neoforged.bus.api.Event;

/**
 * Fired after a standing has changed and the change has landed.
 *
 * <p>After, not before, and not cancellable. The point is to let a consumer react — "the survivors
 * now trust you enough to open the armoury" — without polling every tick. A cancellable
 * before-event would make the reward a negotiation between mods, which is exactly the shape that
 * makes two mods' rewards silently cancel each other.</p>
 *
 * <p>Carries both values, because almost every useful reaction is about a <b>threshold being
 * crossed</b> rather than about the new number. A listener with only the new value has to keep its
 * own copy of the old one to know whether anything it cares about happened, and every listener
 * keeping that copy is a bug waiting per listener.</p>
 */
public class ReputationEvent extends Event {

    private final UUID player;
    private final String standing;
    private final int before;
    private final int after;
    private final String reason;

    public ReputationEvent(UUID player, String standing, int before, int after, String reason) {
        this.player = player;
        this.standing = standing;
        this.before = before;
        this.after = after;
        this.reason = reason;
    }

    /** Who. May be offline — this fires for an admin edit as readily as for a quest reward. */
    public UUID getPlayer() {
        return player;
    }

    /** Which standing, already normalised. */
    public String getStanding() {
        return standing;
    }

    public int getBefore() {
        return before;
    }

    public int getAfter() {
        return after;
    }

    /** How much it actually moved, after clamping. Zero means the change was a no-op. */
    public int getDelta() {
        return after - before;
    }

    /** Whether the value crossed {@code threshold} in either direction. */
    public boolean crossed(int threshold) {
        return (before < threshold) != (after < threshold);
    }

    /** Free text from whoever made the change. For logs; never render it to a player raw. */
    public String getReason() {
        return reason;
    }
}
