package com.sablednah.standards.core;

/**
 * What a switch command was asked to do.
 *
 * <p>This tiny enum is the reason Standards exists. Every other essentials package makes
 * {@code /fly} and {@code /god} pure toggles, which is fine when a human types them and useless
 * when anything else does: a command block, a datapack, a shop, or — the case that started this —
 * a LegendQuest skill that wants {@code /fly Steve on} for twenty seconds and then
 * {@code /fly Steve off}. A toggle in that position is a coin flip. If the player was already
 * flying, "toggle" grounds them mid-air and the skill has done the opposite of its job.</p>
 *
 * <p>So every switch in Standards takes an optional explicit state, and omitting it means
 * {@link #TOGGLE} — the convenient behaviour stays the default for humans, and the correct
 * behaviour is available to everything else.</p>
 */
public enum Toggle {
    ON,
    OFF,
    TOGGLE;

    /** The literal a player types, and the key used in permission and message lookups. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** What the flag should become, given what it is now. */
    public boolean resolve(boolean current) {
        return switch (this) {
            case ON -> true;
            case OFF -> false;
            case TOGGLE -> !current;
        };
    }
}
