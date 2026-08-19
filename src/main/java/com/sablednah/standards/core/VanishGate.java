package com.sablednah.standards.core;

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

    /** Concurrent because the entity tracker reads this off the server thread's hot path. */
    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();

    /**
     * Whether a viewer may see through a vanish. Registered by the mod once it is loaded; until
     * then the default denies nothing, because nobody can be vanished before the mod starts.
     */
    private static volatile BiPredicate<UUID, UUID> seeThrough = (subject, viewer) -> true;

    public static void setSeeThroughCheck(BiPredicate<UUID, UUID> check) {
        seeThrough = check;
    }

    public static void setVanished(UUID player, boolean vanished) {
        if (vanished) {
            VANISHED.add(player);
        } else {
            VANISHED.remove(player);
        }
    }

    public static boolean isVanished(UUID player) {
        return !VANISHED.isEmpty() && VANISHED.contains(player);
    }

    /**
     * The fast path for hot code. Collision runs for every nearby entity pair every tick, so the
     * first question asked there must be "is anyone vanished at all", answerable with one field
     * read on the overwhelming majority of servers.
     */
    public static boolean anyVanished() {
        return !VANISHED.isEmpty();
    }

    /**
     * Should {@code subject} be invisible to {@code viewer}?
     *
     * <p>Called for every player pair on every tracking pass, so the empty-set check comes first:
     * on a server where nobody is vanished this costs one field read.</p>
     */
    public static boolean hidden(UUID subject, UUID viewer) {
        if (VANISHED.isEmpty()) return false;
        if (subject.equals(viewer)) return false;
        if (!VANISHED.contains(subject)) return false;
        return !seeThrough.test(subject, viewer);
    }

    private VanishGate() {}
}
