package com.sablednah.standards.neoforge;

import java.util.function.Supplier;

import com.sablednah.standards.Standards;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Attachment registration. One attachment: the player's switches and their trail home. */
public final class StandardsAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Standards.MODID);

    public static final Supplier<AttachmentType<PlayerState>> STATE =
            ATTACHMENTS.register("state", () -> AttachmentType
                    .builder(PlayerState::new)
                    .serialize(PlayerState.MAP_CODEC)
                    // Survives death, which is what makes /back worth having.
                    .copyOnDeath()
                    .build());

    /** The state for a player, creating the default on first touch. */
    public static PlayerState of(ServerPlayer player) {
        return player.getData(STATE);
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private StandardsAttachments() {}
}
