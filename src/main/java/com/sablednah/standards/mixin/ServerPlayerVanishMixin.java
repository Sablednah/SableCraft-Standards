package com.sablednah.standards.mixin;

import com.sablednah.standards.core.VanishGate;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The whole of {@code /vanish}'s hiding, in one method.
 *
 * <p><b>Standards' only mixin, and it is deliberate.</b> Vanilla's entity tracker already asks a
 * question every tick that is exactly the one vanish needs to answer:</p>
 *
 * <pre>{@code
 * // ChunkMap.TrackedEntity.updatePlayer
 * boolean flag = inRange && this.entity.broadcastToPlayer(viewer) && chunkTracked;
 * if (flag) { ...addPairing(viewer); } else { this.removePlayer(viewer); }
 * }</pre>
 *
 * <p>Answering {@code false} there makes vanilla do all the work — unpair on vanish, re-pair on
 * unvanish, and get chunk loads, dimension changes and view-distance changes right for free. It is
 * the same lever spectator mode pulls two lines below this injection.</p>
 *
 * <p>The alternative — firing {@code ClientboundRemoveEntitiesPacket} at everyone and re-firing it
 * whenever tracking restarts — was considered and rejected: the tracker still believes the pairing
 * exists, so it keeps streaming movement packets, and every one of those correctness cases has to
 * be reimplemented by hand.</p>
 *
 * <p><b>It calls {@link VanishGate} and nothing else</b>, and that is load-bearing rather than
 * tidiness. A mixin runs during class transformation, so everything it references is loaded right
 * then — an earlier version reached straight into {@code Vanish} and therefore into the permission
 * API and the mod config, all while {@code ServerPlayer} was mid-transform. {@code VanishGate}
 * imports nothing but {@code java.util}. Keep it that way.</p>
 *
 * <p><b>Cross-version note:</b> this is the mod's one piece of version-fragile surface. The method
 * is public, tiny, and has been stable for many versions, but check it first on any Minecraft
 * update — a silently non-applying mixin means vanish stops hiding anyone, which looks like a
 * permissions problem rather than a mixin problem. {@code defaultRequire: 1} makes it fail loudly
 * instead.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerVanishMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void standards$hideWhenVanished(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if (VanishGate.hidden(((ServerPlayer) (Object) this).getUUID(), viewer.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
