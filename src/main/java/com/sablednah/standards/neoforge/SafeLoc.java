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
        int range = StandardsConfig.SAFE_LOC_SEARCH.get();
        // 0 means the owner has opted out of the safety net entirely: go exactly where asked.
        if (range == 0) {
            return Optional.of(near);
        }
        for (int offset = 0; offset <= range; offset++) {
            for (int sign : offset == 0 ? new int[] {0} : new int[] {1, -1}) {
                BlockPos feet = near.above(offset * sign);
                if (feet.getY() < level.getMinY() || feet.getY() > level.getMaxY()) continue;
                if (isSafe(level, feet)) return Optional.of(feet);
            }
        }
        return Optional.empty();
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
        BlockPos below = feet.below();
        BlockPos head = feet.above();

        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
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
