package com.sablednah.standards.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * The one thing the vanish mixin is allowed to touch.
 *
 * <p><b>This class deliberately imports nothing.</b> Not Minecraft, not NeoForge, not another
 * Standards class — only {@code java.util}. That is its entire purpose.</p>
 *
 * <p>A mixin runs during class transformation, which is about the earliest code that runs at all.
 * Anything it references is loaded right then, along with everything <em>that</em> references. The
 * first version called straight into {@code Vanish}, which pulls in {@code StandardsPermissions},
 * which pulls in NeoForge's {@code PermissionAPI} and the mod's config — all of it loaded while
 * {@code ServerPlayer} was still being transformed. That worked, and it was luck: the failure mode
 * is a {@code MixinTransformerError} that kills the server before any mod has initialised, with a
 * stack trace pointing at whatever vanilla class happened to trigger the transformation.</p>
 *
 * <p>So the mixin calls this instead, and the real logic registers itself here once the mod is
 * actually up. Until it does, {@link #hidden} answers "nobody is hidden", which is the correct
 * answer during startup anyway.</p>
 */
public final class VanishGate {

    /**
     * Who is hiding a player, keyed by the player.
     *
     * <p><b>Holders rather than a boolean</b>, because more than one thing can want somebody
     * hidden at once and the loser of a plain boolean is whoever releases second. A storyteller who
     * had already typed {@code /vanish} and is then possessed by a scene must still be hidden when
     * the scene ends — the scene put a hold on, the scene takes its own hold off, and the one they
     * put on themselves is untouched. Additive, like the chat decorators, and for the same reason:
     * several contributors can want this without contradicting each other.</p>
     *
     * <p>Concurrent because the entity tracker reads this off the server thread's hot path.</p>
     */
    private static final Map<UUID, Set<String>> HOLDS = new ConcurrentHashMap<>();

    /**
     * Whether a viewer may see through a vanish. Registered by the mod once it is loaded; until
     * then the default denies nothing, because nobody can be vanished before the mod starts.
     */
    private static volatile BiPredicate<UUID, UUID> seeThrough = (subject, viewer) -> true;

    public static void setSeeThroughCheck(BiPredicate<UUID, UUID> check) {
        seeThrough = check;
    }

    /**
     * Add or drop one holder's claim on hiding this player.
     *
     * @return true if the player's visible/hidden state actually changed
     */
    public static boolean hold(UUID player, String key, boolean held) {
        boolean was = isVanished(player);
        if (held) {
            HOLDS.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(key);
        } else {
            Set<String> keys = HOLDS.get(player);
            if (keys != null) {
                keys.remove(key);
                // Removed rather than left empty, so isEmpty() stays the cheap answer to
                // "is anybody vanished at all" — which the tracker asks for every pair, every pass.
                if (keys.isEmpty()) {
                    HOLDS.remove(player);
                }
            }
        }
        return was != isVanished(player);
    }

    /** Every holder currently hiding this player, for diagnostics and for telling them why. */
    public static Set<String> holders(UUID player) {
        return Set.copyOf(HOLDS.getOrDefault(player, Set.of()));
    }

    /** Drop every hold, whoever placed it. Logout, and nothing else should want this. */
    public static void clear(UUID player) {
        HOLDS.remove(player);
    }

    public static boolean isVanished(UUID player) {
        return !HOLDS.isEmpty() && HOLDS.containsKey(player);
    }

    /**
     * The fast path for hot code. Collision runs for every nearby entity pair every tick, so the
     * first question asked there must be "is anyone vanished at all", answerable with one field
     * read on the overwhelming majority of servers.
     */
    public static boolean anyVanished() {
        return !HOLDS.isEmpty();
    }

    /**
     * Should {@code subject} be invisible to {@code viewer}?
     *
     * <p>Called for every player pair on every tracking pass, so the empty-set check comes first:
     * on a server where nobody is vanished this costs one field read.</p>
     */
    public static boolean hidden(UUID subject, UUID viewer) {
        if (HOLDS.isEmpty()) return false;
        if (subject.equals(viewer)) return false;
        if (!HOLDS.containsKey(subject)) return false;
        return !seeThrough.test(subject, viewer);
    }

    private VanishGate() {}
}
