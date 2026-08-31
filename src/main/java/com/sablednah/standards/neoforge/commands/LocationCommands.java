package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /depth} and {@code /compass} — where you are and which way you are facing.
 *
 * <h2>Pointless on most servers, and the exception is the whole reason they exist</h2>
 *
 * <p>F3 shows both, so on an ordinary server these are a curiosity. But a server that sets the
 * <b>{@code reducedDebugInfo} gamerule</b> — which is how you run an exploration or hardcore server
 * without everybody reading coordinates off the debug screen — leaves its players with no depth and
 * no bearing at all. On that server these two commands are the only way to get them, and the owner
 * gets to decide how much to give back.</p>
 *
 * <p>Which is why they are separate commands on separate nodes rather than one {@code /where}: a
 * server may well want to hand out a compass and not a Y coordinate.</p>
 */
public final class LocationCommands {

    /**
     * Sea level, and the number depth is measured from.
     *
     * <p>Read from the level rather than hardcoded to 63: a datapack or a modpack dimension can
     * move it, and "42 blocks below sea level" is wrong the moment somebody's sea is somewhere
     * else.</p>
     */
    private static int seaLevel(ServerPlayer player) {
        return player.level().getSeaLevel();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> depth() {
        return Commands.literal("depth")
                .requires(StandardsPermissions.require(StandardsPermissions.DEPTH))
                .executes(LocationCommands::depth);
    }

    private static int depth(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int y = (int) Math.floor(player.getY());
        int relative = y - seaLevel(player);
        String key = relative == 0 ? "msg.where.depth_level"
                : relative > 0 ? "msg.where.depth_above" : "msg.where.depth_below";
        Feedback.chat(player, Lang.fmt(key,
                "y", String.valueOf(y), "n", String.valueOf(Math.abs(relative))));
        return y;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> compass() {
        return Commands.literal("compass")
                .requires(StandardsPermissions.require(StandardsPermissions.COMPASS))
                .executes(LocationCommands::compass);
    }

    private static int compass(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        float yaw = net.minecraft.util.Mth.wrapDegrees(player.getYRot());
        // Minecraft's yaw is 0 = south and grows clockwise; a bearing is 0 = north. Adding 180
        // and wrapping converts one to the other, which is the whole of the arithmetic here and
        // the only part that is easy to get subtly, unfalsifiably wrong.
        int bearing = Math.floorMod(Math.round(yaw) + 180, 360);
        Feedback.chat(player, Lang.fmt("msg.where.compass",
                "bearing", String.valueOf(bearing), "direction", cardinal(bearing)));
        return bearing;
    }

    /**
     * The eight-point name for a bearing.
     *
     * <p>Pure and package-visible so the self-test can check the boundaries, which is where a
     * compass is wrong in a way nobody notices — north is the wrap, and 359° and 1° must both
     * be north.</p>
     */
    public static String cardinal(int bearing) {
        String[] names = {
            "msg.where.n", "msg.where.ne", "msg.where.e", "msg.where.se",
            "msg.where.s", "msg.where.sw", "msg.where.w", "msg.where.nw",
        };
        // +22.5 degrees before dividing, so each name owns the 45 degrees CENTRED on it rather
        // than the 45 starting at it. Without it every direction is half a sector out.
        int index = Math.floorMod((int) Math.floor((bearing + 22.5) / 45.0), 8);
        return Lang.get(names[index]);
    }

    private LocationCommands() {}
}
