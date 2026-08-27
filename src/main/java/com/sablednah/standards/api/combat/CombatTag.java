package com.sablednah.standards.api.combat;

/**
 * One live combat tag: what kind of fight, when it ends, and what started it.
 *
 * @param kind      what sort of fight
 * @param expiresAt {@link System#currentTimeMillis()} when it lapses
 * @param source    a short id for what caused it — {@code "standards:player"},
 *                  {@code "legendquest:curse"} — carried purely so a log line can say
 *                  <em>pvp via arrow, owner Sablednah</em> rather than <em>tagged</em>. It costs
 *                  nothing and is the difference between tuning and guessing.
 */
public record CombatTag(CombatKind kind, long expiresAt, String source) {

    public boolean expired(long now) {
        return now >= expiresAt;
    }

    /** Milliseconds left, floored at zero. */
    public long remaining(long now) {
        return Math.max(0L, expiresAt - now);
    }
}
