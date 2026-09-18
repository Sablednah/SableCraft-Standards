package com.sablednah.standards.core;

/**
 * The one thing the fire mixin is allowed to touch.
 *
 * <p><b>This class deliberately imports nothing at all.</b> Not Minecraft, not NeoForge, not
 * another Standards class — not even {@code java.util}. That is its entire purpose, and it is the
 * same rule {@link VanishGate} keeps for the same reason: a mixin runs during class
 * transformation, so everything it references loads right then, along with everything
 * <em>that</em> references. The first vanish mixin called into a class that pulled in the
 * permission API and the mod config while {@code ServerPlayer} was mid-transform. It worked, and
 * it was luck; the failure mode is a {@code MixinTransformerError} that kills the server before
 * any mod initialises, pointing at whatever vanilla class happened to trigger the transform.</p>
 *
 * <h2>Why the level arrives as an Object</h2>
 *
 * <p>Because the alternative is importing {@code ServerLevel} and {@code BlockPos} here, which
 * would defeat the whole point. The mixin already legitimately touches vanilla types — it is
 * injected into one — so it unpacks the position into three ints and hands the level over as an
 * {@code Object}. The predicate installed by the mod casts it back. Ugly at this seam, and the
 * ugliness is load-bearing.</p>
 *
 * <h2>The fast path is the normal path</h2>
 *
 * <p>{@link #blocked} is called from {@code ServerLevel.canSpreadFireAround}, which vanilla asks
 * for <em>every burning block on every fire tick</em>. Until something installs a check it costs
 * one volatile field read and returns false, so a server with no claims mod pays nothing
 * measurable.</p>
 */
public final class FireGate {

    /**
     * Whether fire is forbidden at a position.
     *
     * <p>Its own interface rather than a {@code BiPredicate} of anything, because the arguments
     * are a level and a position and neither can be named here without an import.</p>
     */
    public interface Check {
        /**
         * @param level the {@code ServerLevel}, as an Object — cast it back
         * @return true if fire must not spread or consume blocks at this position
         */
        boolean fireBlocked(Object level, int x, int y, int z);
    }

    private static volatile Check check;

    /** Install the real check. Called once at setup, after config has loaded. */
    public static void install(Check installed) {
        check = installed;
    }

    /** Drop the check, restoring vanilla fire everywhere. */
    public static void clear() {
        check = null;
    }

    /** Whether anything is answering fire questions at all. */
    public static boolean active() {
        return check != null;
    }

    /**
     * Should fire be prevented here?
     *
     * <p><b>Fails open, and the asymmetry with the claims seam is deliberate.</b>
     * {@code Claims.griefAllowed} already fails <em>closed</em> — a claims provider that throws
     * stops mobs and fire rather than licensing them — so that protection is inherited here for
     * free, and it is the right way round: wrongly permitting a burn cannot be undone. This catch
     * is for a bug in <em>our own</em> glue, and a bug there must not silently stop fire working
     * on every server that installs the mod. One is a protection decision; the other is a
     * defect, and they deserve opposite defaults.</p>
     */
    public static boolean blocked(Object level, int x, int y, int z) {
        Check c = check;
        if (c == null) {
            return false;
        }
        try {
            return c.fireBlocked(level, x, y, z);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private FireGate() {}
}
