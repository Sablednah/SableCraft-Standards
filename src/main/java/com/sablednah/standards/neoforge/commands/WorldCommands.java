package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /world <dimension>} and {@code /worlds}.
 *
 * <h2>What it does that {@code /tppos} does not</h2>
 *
 * <p>{@code /tppos} needs coordinates. This keeps the ones you are stood on and changes only the
 * world, which is the thing you actually want when checking whether a build lines up with the
 * Nether, or following a portal that has not been dug yet.</p>
 *
 * <p><b>It does not scale coordinates.</b> Going to the Nether at x=800 puts you at x=800, not
 * x=100. The scaling rule is a property of portals rather than of the worlds, an admin flying
 * across dimensions is usually comparing like with like, and a command that silently divided
 * everything by eight would be wrong exactly when it mattered. {@code /tppos} is right there for
 * the other case.</p>
 *
 * <p>Landing goes through {@link Teleports} like everything else, so the safe-landing search
 * applies — arriving inside the Nether's ceiling bedrock is otherwise the normal outcome of
 * keeping your Y.</p>
 */
public final class WorldCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> world() {
        return Commands.literal("world")
                .requires(StandardsPermissions.require(StandardsPermissions.WORLD))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(WorldCommands::go));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> worlds() {
        return Commands.literal("worlds")
                .requires(StandardsPermissions.require(StandardsPermissions.WORLD))
                .executes(WorldCommands::list);
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
        if (level.dimension().equals(player.level().dimension())) {
            Feedback.chat(player, Lang.fmt("msg.world.already",
                    "world", name(level)));
            return 0;
        }
        Waypoint where = new Waypoint(level.dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        Teleports.Attempt attempt = Teleports.request(player, where, true,
                Lang.fmt("msg.world.went", "world", name(level)));
        return MoveCommands.report(player, attempt) ? 1 : 0;
    }

    /** Listing only, so the console can run it — there is nothing player-specific to say. */
    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> names = new ArrayList<>();
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            names.add(level.dimension().identifier().toString());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.world.list",
                "count", String.valueOf(names.size()), "list", String.join(", ", names)), false);
        return names.size();
    }

    /** The short readable name — {@code the_nether} rather than {@code minecraft:the_nether}. */
    private static String name(ServerLevel level) {
        return level.dimension().identifier().getPath();
    }

    private WorldCommands() {}
}
