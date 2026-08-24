package com.sablednah.standards.api.groups;

/**
 * A <em>sort</em> of group: a party, a faction, a guild, a staff role.
 *
 * <p>Kinds are what make this seam different from the other three. A player is in a party
 * <em>and</em> a faction <em>and</em> a guild, and those do not compete — so this is not
 * one-provider-wins like the economy, nor everyone-contributes like the chat decorators. Each kind
 * has one provider, and consumers ask by kind.</p>
 *
 * <h2>The provider owns the name</h2>
 *
 * <p>{@link #displayName()} is resolved on every call rather than stored, because the owning mod
 * may re-skin its vocabulary: LegendQuest renames "party" to <em>Crew</em> under some packs. If
 * Standards held that string in its own config, a re-themed server would render {@code [Party]}
 * beside a UI saying Crew everywhere else — so Standards' config controls only <em>whether and
 * where</em> a group tag renders, never what it is called.</p>
 *
 * <p>Standards has a whole {@code {term.*}} system to prevent exactly that failure internally, and
 * the first draft of this API still hardcoded another mod's vocabulary. Internal consistency does
 * not automatically cross a seam.</p>
 */
public interface GroupKind {

    /** Stable, {@code modid:kind} — {@code legendquest:party}. Used in logs and config. */
    String id();

    /**
     * What to call this kind in a player-facing message, singular — "party", "faction", "Crew".
     *
     * <p>Asked every time, so a mod that re-skins its vocabulary at runtime is followed rather
     * than cached against.</p>
     */
    String displayName();

    /**
     * Whether a player can be in at most one group of this kind.
     *
     * <p>True for parties and factions, <b>false for staff roles</b> — a moderator can also be a
     * builder. Consumers pick their accessor from this: {@link Groups#primary} for exclusive
     * kinds, {@link Groups#all} for any. Making every caller re-derive it from a flat list is how
     * some of them get it wrong.</p>
     */
    default boolean exclusive() {
        return true;
    }
}
