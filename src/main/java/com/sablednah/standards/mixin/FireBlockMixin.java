package com.sablednah.standards.mixin;

import com.sablednah.standards.core.FireGate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The other half of "claimed land does not burn": fire reaching <b>in</b> from outside.
 *
 * <h2>Why {@link ServerLevelFireMixin} was not enough</h2>
 *
 * <p>Found by playing it, which is the only way it could have been. {@code canSpreadFireAround} is
 * asked about the <b>burning block's own position</b>, so a fire standing in wilderness passes the
 * gate and its tick runs in full — and that tick reaches outward: six {@code checkBurnOut} calls at
 * the neighbours, and a spread loop that plants new fire anywhere in a −1..+4 box. Neither is
 * checked against where it is reaching. So fire walked over the border, and the fire block that
 * landed inside was then gated by its own position: it consumed nothing, spread nowhere, and never
 * went out. Eternal inert fire in somebody's base — not destructive, and still not something a
 * claim should allow.</p>
 *
 * <p>Reported with a screenshot of exactly that: burning wilderness on one side, permanent flames
 * sitting in claimed grass on the other, 2026-09-17.</p>
 *
 * <h2>Two injections, because there are two write paths and they ask different questions</h2>
 *
 * <ul>
 *   <li>{@code checkBurnOut} — ignites and destroys a neighbour. It asks
 *       {@code BlockState.getFlammability}. Cancelled outright when the <em>target</em> is
 *       protected.</li>
 *   <li>{@code getIgniteOdds(LevelReader, BlockPos)} — the spread loop's only gate before
 *       {@code setBlockAndUpdate} plants new fire. It asks {@code getFireSpreadSpeed}. Forced to
 *       zero when the <em>target</em> is protected, which is the same answer vanilla gives for a
 *       position nothing can burn at.</li>
 * </ul>
 *
 * <p><b>Deliberately NOT {@code canCatchFire}</b>, tempting though a single choke point is:
 * {@code canSurvive} and {@code isValidFireLocation} consult it about the fire's <em>own</em>
 * position, so answering false inside a claim would make the owner's fireplace extinguish itself —
 * destroying the property the whole design was chosen for. {@code getIgniteOdds(level, pos)} has
 * exactly one caller, the spread loop, which is why it is safe to force.</p>
 *
 * <h2>Cross-version</h2>
 *
 * <p>Both signatures verified identical in the <b>patched</b> sources on 1.21.11, 26.1, 26.2 and
 * 26.3, with six {@code checkBurnOut} call sites on every line. ⚠ Note {@code checkBurnOut}'s
 * trailing {@code Direction face} is a <b>NeoForge patch</b> — vanilla's own jar has five
 * parameters — so this must always be checked against the patched sources. Comparing the vanilla
 * jar would report a signature that never runs.</p>
 *
 * <p>Touches {@link FireGate} and nothing else, for the reason that class explains. Both injectors
 * are covered by {@code defaultRequire: 1}, so either failing to apply fails the launch loudly
 * rather than quietly letting fire back over the border.</p>
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void standards$noBurnIntoClaims(Level level, BlockPos pos, int chance,
            RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (FireGate.blocked(level, pos.getX(), pos.getY(), pos.getZ())) {
            ci.cancel();
        }
    }

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    private void standards$noSpreadIntoClaims(LevelReader level, BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        if (FireGate.blocked(level, pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(0);
        }
    }
}
