package com.sablednah.standards.api.combat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import net.minecraft.server.level.ServerPlayer;

/**
 * Whether a player is in combat, so that nothing lets them walk out of one.
 *
 * <h2>Why there is an API rather than a private field</h2>
 *
 * <p>A teleport is an escape hatch. {@code /home} mid-fight is not a clever play, it is the fight
 * not happening — and the person who was winning it has no recourse.</p>
 *
 * <p>But a skill or a feat can be an act of combat with <b>no damage event firing at all</b>: a
 * curse, a summon, a channelled ritual. Only the mod that did it knows that happened. Standards
 * owns the question "is this player in combat"; anything closer to the answer must be able to say
 * so.</p>
 *
 * <h2>Tags extend, they never overwrite</h2>
 *
 * <p>You are tagged for PvP for twelve seconds; a zombie clips you at second three for eight.
 * <b>The answer is twelve.</b> With a single global duration that bug is invisible; with per-kind
 * durations a shorter tag would actively <em>rescue</em> the player who is fleeing, which is the
 * exact person this exists to stop.</p>
 *
 * <h2>An attacker starts a tag, not damage</h2>
 *
 * <p>Fall damage, drowning, cactus, fire, freezing — none of it has an attacker, so none of it is
 * combat, so none of it may close an escape hatch. That sounds obvious until you notice what it
 * prevents: a player trapped in powder snow inside somebody else's claim cannot break out, because
 * claim protection is working correctly, and their only way out is a teleport. Tag them for the
 * freezing and they are stuck in a hole, taking damage, with every exit shut by two features that
 * are each behaving exactly as designed.</p>
 */
public final class Combat {

    private static final Map<UUID, Map<CombatKind, CombatTag>> TAGS = new ConcurrentHashMap<>();

    /**
     * How long a kind lasts by default, in seconds, and whether it closes escape hatches.
     *
     * <p>Installed by Standards at startup from config. Until then everything is zero, which means
     * <b>no tagging at all</b> — the safe direction for a facade another mod may call before we
     * are ready.</p>
     */
    private static volatile IntUnaryOperator seconds = kind -> 0;
    private static volatile java.util.function.Predicate<CombatKind> blocksTeleport = k -> false;

    /** Wired by Standards at setup. Not for other mods. */
    public static void install(IntUnaryOperator secondsByOrdinal,
            java.util.function.Predicate<CombatKind> teleportBlocker) {
        seconds = secondsByOrdinal;
        blocksTeleport = teleportBlocker;
    }

    /** The configured duration for a kind, in seconds. Zero disables that kind entirely. */
    public static int secondsFor(CombatKind kind) {
        return Math.max(0, seconds.applyAsInt(kind.ordinal()));
    }

    /**
     * Put a player in combat for this kind's configured duration.
     *
     * @param source a short id for what did it, for logs — {@code "legendquest:curse"}
     * @return the tag now in force for that kind, or empty if the kind is disabled
     */
    public static Optional<CombatTag> tag(ServerPlayer player, CombatKind kind, String source) {
        return tag(player, kind, source, secondsFor(kind));
    }

    /**
     * Put a player in combat for a duration the caller has decided.
     *
     * <p>Standards knows what a punch is worth. Only LegendQuest knows whether a skill was a quick
     * blast or a ten-second channelled ritual — without this form it would have to over-tag every
     * quick skill or under-tag every slow one.</p>
     *
     * @param seconds how long; zero or less does nothing
     */
    public static Optional<CombatTag> tag(ServerPlayer player, CombatKind kind, String source,
            int seconds) {
        if (player == null || kind == null || seconds <= 0) {
            return Optional.empty();
        }
        long expiry = System.currentTimeMillis() + seconds * 1000L;
        Map<CombatKind, CombatTag> mine =
                TAGS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(CombatKind.class));
        CombatTag fresh = new CombatTag(kind, expiry, source == null ? "" : source);
        CombatTag kept = longer(mine.get(kind), fresh);
        mine.put(kind, kept);
        return Optional.of(kept);
    }

    /**
     * Of two tags for the same kind, the one that ends later.
     *
     * <p>Extracted so the rule can be checked without two clients, a zombie and four seconds of
     * good luck. It is the sort of rule that is invisible until it is wrong and then decides a
     * fight, so it should not depend on somebody managing to arrange the collision by hand.</p>
     *
     * @param existing what is already in force, or null
     * @param fresh    what has just happened
     */
    public static CombatTag longer(CombatTag existing, CombatTag fresh) {
        if (existing == null) {
            return fresh;
        }
        if (fresh == null) {
            return existing;
        }
        return existing.expiresAt() >= fresh.expiresAt() ? existing : fresh;
    }

    /** In any kind of combat. */
    public static boolean isInCombat(ServerPlayer player) {
        return remaining(player) > 0L;
    }

    public static boolean isInCombat(ServerPlayer player, CombatKind kind) {
        return current(player, kind).isPresent();
    }

    /** The live tag for a kind, if there is one. */
    public static Optional<CombatTag> current(ServerPlayer player, CombatKind kind) {
        Map<CombatKind, CombatTag> mine = TAGS.get(player.getUUID());
        if (mine == null) {
            return Optional.empty();
        }
        CombatTag tag = mine.get(kind);
        if (tag == null) {
            return Optional.empty();
        }
        if (tag.expired(System.currentTimeMillis())) {
            mine.remove(kind);
            return Optional.empty();
        }
        return Optional.of(tag);
    }

    /** Milliseconds until every tag has lapsed. Zero when not in combat. */
    public static long remaining(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Map<CombatKind, CombatTag> mine = TAGS.get(player.getUUID());
        if (mine == null) {
            return 0L;
        }
        long longest = 0L;
        for (CombatKind kind : CombatKind.values()) {
            CombatTag tag = mine.get(kind);
            if (tag == null) {
                continue;
            }
            if (tag.expired(now)) {
                mine.remove(kind);
                continue;
            }
            longest = Math.max(longest, tag.remaining(now));
        }
        return longest;
    }

    /**
     * Whether combat should stop this player teleporting right now.
     *
     * <p>Per kind, because a server can decide that a skeleton is combat worth noting and not
     * combat worth trapping somebody in.</p>
     */
    public static Optional<CombatTag> blockingTeleport(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Map<CombatKind, CombatTag> mine = TAGS.get(player.getUUID());
        if (mine == null) {
            return Optional.empty();
        }
        CombatTag worst = null;
        for (CombatKind kind : CombatKind.values()) {
            CombatTag tag = mine.get(kind);
            if (tag == null || tag.expired(now) || !blocksTeleport.test(kind)) {
                continue;
            }
            if (worst == null || tag.expiresAt() > worst.expiresAt()) {
                worst = tag;
            }
        }
        return Optional.ofNullable(worst);
    }

    /**
     * The player really behind this damage, resolving projectiles and pets.
     *
     * <h3>Resolve the owner, not the arrow</h3>
     *
     * <p>Arrows, splash potions, TNT. A rule that reads the damage source's <em>entity</em> rather
     * than the entity <b>behind</b> it makes every one of those a free hit that does not tag, and
     * it is what people find in the first week.</p>
     *
     * <h3>Pets count, and it is directional</h3>
     *
     * <p>Somebody's wolf biting you is <b>them</b> fighting you through a proxy, so this resolves
     * to its owner. The other direction does not: you hitting their wolf is you attacking an
     * animal, which stops a griefer shoving a pet in front of you and forcing you into combat by
     * making you kill it.</p>
     *
     * <p>Without the first half, fighting through pets would be the way to <em>avoid</em> combat
     * lock — and on a server running LegendQuest that is not a corner case, because "fights
     * through animals" is a character build, and a beastmaster structurally immune to combat lock
     * is a balance problem that arrives with the class rather than with the exploiters.</p>
     *
     * <p>Public because anything deciding "was this a player's doing" needs the same answer, and
     * two implementations of it would eventually disagree.</p>
     *
     * @return the player behind it, or empty for environmental damage and plain mobs
     */
    public static Optional<ServerPlayer> playerBehind(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return Optional.empty();
        }
        // The attacking entity, which for an arrow is already the shooter.
        net.minecraft.world.entity.Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        // A projectile whose owner the source did not resolve for us.
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot
                && shot.getOwner() instanceof ServerPlayer owner) {
            return Optional.of(owner);
        }
        // A pet, fighting on somebody's behalf.
        if (attacker instanceof net.minecraft.world.entity.TamableAnimal pet
                && pet.getOwner() instanceof ServerPlayer owner) {
            return Optional.of(owner);
        }
        return Optional.empty();
    }

    /**
     * Whether anything at all was behind this damage.
     *
     * <p>The distinction the whole design rests on: <b>an attacker starts a tag, not damage.</b>
     * Fall, drowning, cactus, fire and freezing all answer false here.</p>
     */
    public static boolean hasAttacker(net.minecraft.world.damagesource.DamageSource source) {
        return source != null && (source.getEntity() != null || source.getDirectEntity() != null);
    }

    /** Drop every tag. Death, and admin commands. */
    public static void clear(ServerPlayer player) {
        TAGS.remove(player.getUUID());
    }

    /** Drop every tag for somebody who has gone. */
    public static void forget(UUID player) {
        TAGS.remove(player);
    }

    private Combat() {}
}
