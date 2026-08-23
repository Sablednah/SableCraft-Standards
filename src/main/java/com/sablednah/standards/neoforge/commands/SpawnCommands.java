package com.sablednah.standards.neoforge.commands;

import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

/**
 * {@code /spawn}, {@code /setspawn}, {@code /playerspawn}.
 *
 * <p>{@code /spawn} prefers a spawn point set with {@code /setspawn}, and falls back to the
 * <em>world's</em> spawn when none has been. The fallback matters: an owner who never runs
 * {@code /setspawn} should still get a working {@code /spawn} rather than an error telling them
 * about a command they did not know existed.</p>
 *
 * <p>{@code /playerspawn} is the other meaning of the word — your bed or respawn anchor. Separate
 * command rather than an argument, because they are different places and conflating them is how
 * people end up somewhere they did not want to be.</p>
 */
public final class SpawnCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn")
                .requires(StandardsPermissions.require(StandardsPermissions.SPAWN))
                .executes(SpawnCommands::goToSpawn);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> setSpawn() {
        return Commands.literal("setspawn")
                .requires(StandardsPermissions.require(StandardsPermissions.SETSPAWN))
                .executes(SpawnCommands::setSpawn);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> playerSpawn() {
        return Commands.literal("playerspawn")
                .requires(StandardsPermissions.require(StandardsPermissions.SPAWN))
                .executes(SpawnCommands::goToBed);
    }

    private static int goToSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        Waypoint destination = StandardsData.get(server).spawn().orElseGet(() -> worldSpawn(server));

        Teleports.Attempt attempt = Teleports.request(player, destination, true,
                Lang.get("msg.spawn.went"));
        if (!MoveCommands.report(player, attempt)) {
            return 0;
        }
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Waypoint here = Waypoint.of(player);
        StandardsData.get(ctx.getSource().getServer()).setSpawn(here);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.spawn.set", "place", here.describe()), true);
        Feedback.warnIfUnreachable(player, here);
        return 1;
    }

    private static int goToBed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        // A player's own respawn point lives behind RespawnConfig in 1.21.11, not on the player
        // directly — and it is null until they have actually slept or set an anchor.
        Optional<Waypoint> bed = Optional.ofNullable(player.getRespawnConfig())
                .map(ServerPlayer.RespawnConfig::respawnData)
                .map(data -> new Waypoint(data.globalPos().dimension(),
                        data.globalPos().pos().getX() + 0.5D,
                        data.globalPos().pos().getY(),
                        data.globalPos().pos().getZ() + 0.5D,
                        data.yaw(), data.pitch()))
                .filter(w -> w.level(server) != null);
        if (bed.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.spawn.no_bed"));
            return 0;
        }
        Teleports.Attempt attempt = Teleports.request(player, bed.get(), true,
                Lang.get("msg.spawn.went_bed"));
        if (!MoveCommands.report(player, attempt)) {
            return 0;
        }
        return 1;
    }

    /** The world's own spawn, for a server that never ran /setspawn. */
    private static Waypoint worldSpawn(MinecraftServer server) {
        LevelData.RespawnData data = server.overworld().getRespawnData();
        BlockPos pos = data.globalPos().pos();
        ServerLevel level = server.getLevel(data.globalPos().dimension());
        return new Waypoint(
                level != null ? level.dimension() : server.overworld().dimension(),
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, data.yaw(), data.pitch());
    }

    private SpawnCommands() {}
}
