package com.sablednah.standards.api.combat;

/**
 * What sort of fight somebody is in.
 *
 * <h2>Why one number cannot serve both servers</h2>
 *
 * <p>On a peaceful server a skeleton plinking you must not block {@code /home}. On a PvP server
 * another player hitting you absolutely must. That is not a difference of duration, it is a
 * difference of consequence — so the kind carries both, and a server that wants nothing to do with
 * one half sets its duration to zero and the entire branch goes quiet with no separate code
 * path.</p>
 */
public enum CombatKind {

    /** A mob hit you, or you hit one. */
    PVE("pve"),

    /** A player was behind it — theirs or yours. */
    PVP("pvp"),

    /**
     * Another mod says this was an act of combat.
     *
     * <p>A curse, a summon, a channelled ritual: acts of war with no damage event to notice. Only
     * the mod that did it knows, which is the whole reason this is an API rather than a private
     * field.</p>
     */
    SKILL("skill");

    private final String key;

    CombatKind(String key) {
        this.key = key;
    }

    /** Lowercase id, used in config keys and message keys. */
    public String key() {
        return key;
    }
}
