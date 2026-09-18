package com.sablednah.standards.neoforge;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.groups.Claims;
import com.sablednah.standards.core.FireGate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Wires {@link FireGate} to the claims seam, so claimed land does not burn.
 *
 * <p>The mixin asks {@code FireGate}; {@code FireGate} asks what is installed here; this asks
 * {@link Claims}. Three steps rather than one because the mixin may not reference anything that
 * drags the config or the API into class transformation — see {@link FireGate}.</p>
 *
 * <p><b>Whoever provides claims gets this for free.</b> Nothing here knows about factions: the
 * question is {@code griefAllowed}, the same one ZombieMod asks before letting a mob chew through
 * a wall, so a claims mod that never heard of fire protection still gets it. That is the point of
 * the seam owning the rule rather than each consumer inventing one.</p>
 */
public final class FireProtection {

    /**
     * Install or remove the fire check according to config.
     *
     * <p>Called from {@code FMLCommonSetupEvent}, not the mod constructor, because it reads a
     * config value and config is not loaded while mods are still being constructed — the same
     * reason the economy provider registers there.</p>
     *
     * <p>⚠ Setup-time only. {@code /standards reload} is messages-only by design, so changing this
     * switch needs a restart — which is also true of every other command-registration switch, and
     * is stated in the config comment rather than left to be discovered.</p>
     */
    public static void install() {
        if (!StandardsConfig.PROTECT_CLAIMS_FROM_FIRE.get()) {
            FireGate.clear();
            Standards.LOGGER.info("Standards: fire protection for claimed land is off in config.");
            return;
        }
        FireGate.install(FireProtection::blocked);
        Standards.LOGGER.info(
                "Standards: claimed land will not burn (needs a claims provider to have any effect).");
    }

    /**
     * The real answer, on the hot path.
     *
     * <p>Called for every burning block on every fire tick, so the order of the two cheap checks
     * matters: {@code isAvailable()} is one field read and is false on every server with no claims
     * mod, which is most of them. Only then does anything ask about a chunk.</p>
     *
     * <p>Note this inverts {@code griefAllowed}: that answers "may a non-player change blocks
     * here", and fire is exactly such a non-player. Unclaimed land permits, so fire behaves
     * vanilla everywhere nobody has claimed — which is what keeps this from being a world-wide
     * fire switch wearing a claims hat.</p>
     */
    private static boolean blocked(Object level, int x, int y, int z) {
        if (!Claims.isAvailable() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return !Claims.griefAllowed(serverLevel, new BlockPos(x, y, z));
    }

    private FireProtection() {}
}
