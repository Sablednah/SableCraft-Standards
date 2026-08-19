package com.sablednah.standards.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Somewhere a player can be sent back to: a home, a warp, the spawn, the place {@code /back}
 * remembers.
 *
 * <p>The dimension is stored as a {@link ResourceKey}, not a name, and is looked up against the
 * live server on use — so a home in a dimension a modpack later removes reports "that world is
 * gone" instead of silently landing the player in the overworld at the same coordinates, which is
 * how people end up inside bedrock wondering what happened.</p>
 *
 * <p>Rotation is kept because arriving somewhere facing a random direction is a small thing that
 * feels wrong every single time.</p>
 */
public record Waypoint(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {

    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Waypoint::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(Waypoint::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Waypoint::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Waypoint::z),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Waypoint::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(Waypoint::pitch))
            .apply(i, Waypoint::new));

    /** Where this player is standing, facing the way they are facing. */
    public static Waypoint of(ServerPlayer player) {
        return new Waypoint(player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /** The live level for this waypoint, or null if the dimension no longer exists. */
    public ServerLevel level(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(dimension);
    }

    public net.minecraft.core.BlockPos blockPos() {
        return net.minecraft.core.BlockPos.containing(x, y, z);
    }

    /** For chat: "world 128, 64, -512". */
    public String describe() {
        return dimension.identifier().getPath()
                + " " + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z);
    }
}
