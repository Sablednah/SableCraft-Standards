package com.sablednah.standards.api.reputation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Somebody who holds what people think of you.
 *
 * <h2>What a standing is, and what it is not</h2>
 *
 * <p>A <b>standing</b> is a named group's opinion of one player: {@code survivors},
 * {@code raiders}, {@code the_hospital}. It is deliberately none of the three things it is easily
 * confused with:</p>
 *
 * <ul>
 * <li>not <b>membership</b> — {@code api/groups} answers "are they one of us";</li>
 * <li>not a <b>moral axis</b> — LegendQuest's karma is one number for your whole soul, and the
 *     point of reputation is that the hospital and the raiders can disagree about you;</li>
 * <li>not <b>permission</b> — a standing may gate content, but it is a fact about opinion rather
 *     than a grant, and routing it through the permission handler would make every quest author
 *     an administrator.</li>
 * </ul>
 *
 * <h2>Exactly one provider holds it</h2>
 *
 * <p>Highest {@link #priority()} wins outright, the same rule as the economy and for the same
 * reason: a standing is a <em>single fact</em>, and two stores disagreeing about whether the
 * survivors trust you is worse than either alone. This is the opposite of the chat decorators,
 * where every contributor gets a turn — see {@code CHAT-API.md} for why those two look alike and
 * behave oppositely.</p>
 *
 * <p><b>A provider owns every standing, not one of them.</b> Per-standing providers were the
 * obvious alternative and were rejected: a mod owning {@code the_hospital} while Standards owned
 * the rest means {@link Reputation#standings()} has to merge sources that may disagree about what
 * exists, and {@code /rep} has to explain which store answered. A dedicated reputation mod
 * displaces Standards wholesale, exactly as a dedicated economy mod does.</p>
 *
 * <p>Callers should not touch this interface — use the {@link Reputation} facade, which works
 * whether or not anybody has registered.</p>
 */
public interface ReputationProvider {

    /**
     * Standards' own priority, deliberately negative.
     *
     * <p>A mod whose whole job is reputation should outrank the one that ships it as a convenience,
     * without either side needing to know the other exists.</p>
     */
    int BUILTIN_PRIORITY = -1000;

    /** For diagnostics — {@code /standards reputation} prints this. */
    String name();

    /** Higher wins. See {@link #BUILTIN_PRIORITY}. */
    int priority();

    /** This player's standing, or zero if they have none. Zero is "no opinion", not an error. */
    int get(UUID player, String standing);

    /**
     * Move a standing and answer where it landed.
     *
     * @param reason free text for logs and for a consumer's own bookkeeping; never shown raw
     * @return the value after clamping, so a caller can tell a change from a no-op
     */
    int adjust(UUID player, String standing, int delta, String reason);

    /** Put a standing at an exact value, clamped. Returns where it landed. */
    int set(UUID player, String standing, int value, String reason);

    /** Every standing this player has an opinion recorded for. */
    Map<String, Integer> of(UUID player);

    /** Every standing name known to exist. Created on first use; there is no registry. */
    List<String> standings();

    /** The highest {@code limit} players in one standing, best first. */
    List<Entry> top(String standing, int limit);

    /** A row of {@link #top}. */
    record Entry(UUID player, int value) {}
}
