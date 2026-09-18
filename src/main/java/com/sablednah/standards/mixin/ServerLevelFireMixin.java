package com.sablednah.standards.mixin;

import com.sablednah.standards.core.FireGate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Claimed land does not burn.
 *
 * <h2>Why this needs a mixin at all</h2>
 *
 * <p>Because there is no event. Fire consuming a block is not a player breaking one, so
 * {@code BreakBlockEvent} never fires for it — that event is player-only, and its handler in
 * Factions returns immediately without a {@code ServerPlayer}. NeoForge 26.3's complete block-event
 * set is {@code BreakBlockEvent}, {@code CreateFluidSourceEvent}, {@code CropGrowEvent},
 * {@code BlockDropsEvent}, {@code PistonEvent}, {@code ExplosionEvent} and {@code NoteBlockEvent}:
 * nothing for a block being replaced, and {@code FireBlock.checkBurnOut} is private with no hook
 * patched into it. {@code EntityMobGriefingEvent} cannot help either — it needs an entity, and
 * spreading fire has none.</p>
 *
 * <h2>Why this method, of the two candidates</h2>
 *
 * <p>{@code ServerLevel.canSpreadFireAround} is the gate vanilla itself puts in front of the whole
 * of {@code FireBlock.tick} — spread <em>and</em> all six {@code checkBurnOut} calls, which are the
 * ones that destroy blocks. So one injection covers both halves of "claimed land should not burn",
 * and it is positional, which is exactly the shape {@code Claims.griefAllowed} answers.</p>
 *
 * <p>The alternative was {@code FireBlock.checkBurnOut}, which would stop destruction while letting
 * fire age out normally. The owner chose this one knowing the consequence and wanting it: fire in a
 * claim never burns out, so <b>a fireplace stays lit</b> and the side effect is decoration rather
 * than a cost.</p>
 *
 * <h2>Cross-version</h2>
 *
 * <p>Verified byte-identical on 1.21.11, 26.1, 26.2 and 26.3 — same name, same signature, same
 * declaring class, and {@code FireBlock.tick} gates on it with six {@code checkBurnOut} calls
 * inside on every line. Only the parameter name differs on 1.21.11, which a mixin does not see.
 * That makes this the rare version-fragile surface with no divergence to carry.</p>
 *
 * <p><b>It calls {@link FireGate} and nothing else</b>, which is load-bearing rather than tidy —
 * see that class. {@code defaultRequire: 1} makes a non-applying mixin fail loudly, because the
 * quiet failure here is claimed land burning again and reading as a protection bug.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelFireMixin {

    @Inject(method = "canSpreadFireAround", at = @At("HEAD"), cancellable = true)
    private void standards$noFireInClaims(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (FireGate.blocked((Object) this, pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(false);
        }
    }
}
