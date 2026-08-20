package com.sablednah.standards.neoforge;

import java.util.Optional;

import com.sablednah.standards.StandardsConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;

/**
 * Find somewhere near a target where arriving will not bury, burn, drown or drop the traveller.
 *
 * <p>Scans outward from the target Y for two passable blocks over a floor that is solid, not on
 * fire, and not something that hurts to stand on. Alternating up/down means the closest safe layer
 * wins, so a home set on a rooftop does not quietly relocate to the ground.</p>
 *
 * <p>Drowning is handled as a <em>preference</em> rather than a refusal: the search runs once
 * ignoring anywhere the traveller's head would be underwater, and only if that finds nothing does
 * it accept a submerged spot. Water is survivable and an underwater base is a real thing people
 * build, so refusing outright would be wrong — but so is picking a puddle over dry ground two
 * blocks further out. (This javadoc claimed to prevent drowning long before the code did.)</p>
 *
 * <p>Deliberately refuses rather than guessing: {@link #find} returning empty is a real answer,
 * and every caller reports it. A teleport that lands somewhere lethal is worse than one that
 * did not happen — the whole reason the classic {@code SafeLoc} existed.</p>
 */
public final class SafeLoc {

    /**
     * @param level the world to search in
     * @param near  where the player asked to go
     * @return the block to stand on, or empty if nowhere within range is safe
     */
    public static Optional<BlockPos> find(ServerLevel level, BlockPos near) {
        return find(level, near, true);
    }

    /**
     * As {@link #find(ServerLevel, BlockPos)}, but a traveller who can fly does not need a floor.
     *
     * <p>Without this, <b>anywhere you were flying is somewhere you can never come back to</b>.
     * The back trail, a home, a warp — all of them record where the player actually was, and a
     * player in flight is by definition standing on nothing. Demanding a floor then refuses the
     * return trip with "nowhere safe to land there", which is both wrong and insulting: they were
     * demonstrably fine there a moment ago. Found from a Nether back trail full of fractional
     * heights, every entry recorded mid-flight.</p>
     *
     * <p>Everything else still applies. Not needing a floor is not permission to be entombed in
     * stone or dropped into lava, so only the floor test is relaxed.</p>
     *
     * @param requireFloor false if the traveller can hover where they land
     */
    public static Optional<BlockPos> find(ServerLevel level, BlockPos near, boolean requireFloor) {
        int range = StandardsConfig.SAFE_LOC_SEARCH.get();
        // 0 means the owner has opted out of the safety net entirely: go exactly where asked.
        if (range == 0) {
            return Optional.of(near);
        }
        // Somewhere they can breathe, first.
        Optional<BlockPos> dry = scan(level, near, range, requireFloor, false);
        if (dry.isPresent()) {
            return dry;
        }
        // Nothing dry within range. Water is survivable, and a destination surrounded by nothing
        // but water is quite likely an underwater build somebody meant to be there, so take it
        // rather than refuse. Second choice, never first.
        return scan(level, near, range, requireFloor, true);
    }

    /**
     * One pass of the alternating up/down search.
     *
     * <p>Run twice by {@link #find}, because being dropped in water is not <em>dangerous</em> so
     * much as <em>worse</em> — and a single pass takes a puddle two blocks down over dry ground
     * four blocks down, purely because it looked closer. Preferring air costs one extra scan and
     * only changes the answer when there was a better one available.</p>
     *
     * <p>Found by boxing in a bed: the search kept working and eventually surfaced the traveller
     * underneath the box, underwater — genuinely the safest spot in range, and still not the one
     * anybody would have picked if a dry one existed.</p>
     */
    private static Optional<BlockPos> scan(ServerLevel level, BlockPos near, int range,
            boolean requireFloor, boolean allowSubmerged) {
        for (int offset = 0; offset <= range; offset++) {
            for (int sign : offset == 0 ? new int[] {0} : new int[] {1, -1}) {
                BlockPos feet = near.above(offset * sign);
                if (feet.getY() < level.getMinY() || feet.getY() > level.getMaxY()) continue;
                if (!allowSubmerged && submerged(level, feet)) continue;
                if (isSafe(level, feet, requireFloor)) return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    /**
     * Head underwater. Feet in water is fine — everyone wades — so only the head block counts,
     * which is also the block that decides whether the drowning meter starts.
     */
    public static boolean submerged(ServerLevel level, BlockPos feet) {
        return level.getFluidState(feet.above()).is(FluidTags.WATER);
    }

    /**
     * True if a player standing with their feet here would be fine.
     *
     * <p>The floor test is <b>collision, not "sturdy"</b>. The obvious call is
     * {@code isFaceSturdy(..., Direction.UP)}, and it is wrong: leaves, fences, walls, chests and
     * glass panes are all things a player demonstrably stands on and none of them are sturdy. Using
     * sturdiness means a home set in a treehouse, on a fence-post lookout or on top of a chest
     * refuses to teleport you and reports "nowhere safe to land" — which reads as the mod being
     * broken. (Found by the self-test: this server's world spawn is on top of a spruce tree.)</p>
     *
     * <p>Anything that holds a player up has a collision shape, so that is the question to ask.</p>
     */
    public static boolean isSafe(ServerLevel level, BlockPos feet) {
        return isSafe(level, feet, true);
    }

    /** As {@link #isSafe(ServerLevel, BlockPos)}, with the floor requirement made optional. */
    public static boolean isSafe(ServerLevel level, BlockPos feet, boolean requireFloor) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();

        if (requireFloor
                && level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return false; // nothing to stand on
        }
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
            return false; // buried
        }
        if (level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(below).is(FluidTags.LAVA)
                || level.getFluidState(head).is(FluidTags.LAVA)) {
            return false; // the classic SafeLoc failure mode
        }
        // Standing on it should not be an injury. Fire and magma are the common ones; campfires
        // and cactus round it out, and the tag catches modded fire without naming any of it.
        var floor = level.getBlockState(below);
        if (floor.is(BlockTags.FIRE)
                || floor.is(Blocks.MAGMA_BLOCK)
                || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        return !level.getBlockState(feet).is(BlockTags.FIRE)
                && !level.getBlockState(head).is(BlockTags.FIRE);
    }

    private SafeLoc() {}
}
