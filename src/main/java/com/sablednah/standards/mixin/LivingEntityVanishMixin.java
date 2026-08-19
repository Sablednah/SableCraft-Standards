package com.sablednah.standards.mixin;

import com.sablednah.standards.core.VanishGate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A vanished player cannot be shoved.
 *
 * <p>Invisible is not the same as absent, and the gap is findable: an entity nobody can see still
 * collides, so walking around until something pushes back locates hidden staff exactly. Reported
 * during two-player testing, alongside the same problem in projectile form.</p>
 *
 * <p><b>This is the mod's second mixin, and it breaks the rule the first one sets</b> — don't add
 * another without the same weight of justification. It has it: this is the same feature, the same
 * failure (vanish being defeated), and there is no NeoForge event for entity pushing. The obvious
 * alternative, a scoreboard team with {@code collisionRule=never}, is worse than a mixin here:
 * joining a scoreboard team removes a player from any other, which would quietly break FTB Teams
 * and anything else managing team membership.</p>
 *
 * <p>It targets {@code LivingEntity} rather than {@code ServerPlayer} because that is where
 * {@code isPushable} is declared, which makes it run for every living entity in the game.
 * {@code Entity.push} consults it on both sides for every nearby pair, every tick — so the first
 * thing asked is {@link VanishGate#anyVanished()}, one field read, false on virtually every
 * server. Keep it that way.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityVanishMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void standards$vanishedCannotBeShoved(CallbackInfoReturnable<Boolean> cir) {
        if (!VanishGate.anyVanished()) {
            return;
        }
        if ((Object) this instanceof ServerPlayer player
                && VanishGate.isVanished(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
