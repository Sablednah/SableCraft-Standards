package com.sablednah.standards.neoforge.commands;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Warps: server-wide named places. {@code /warp}, {@code /setwarp}, {@code /delwarp},
 * {@code /warps}.
 *
 * <p>Structurally the same as {@link HomeCommands} but owned by the server rather than by a
 * player, which is exactly the difference that decides the permissions: going to one is open,
 * making one is not.</p>
 */
public final class WarpCommands {

    private static final java.util.regex.Pattern NAME_RULES =
            java.util.regex.Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    public static LiteralArgumentBuilder<CommandSourceStack> warp() {
        return Commands.literal("warp")
                .requires(StandardsPermissions.require(StandardsPermissions.WARP))
                .executes(WarpCommands::listWarps)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WarpCommands::suggestWarps)
                        .executes(WarpCommands::warpTo));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> warps() {
        return Commands.literal("warps")
                .requires(StandardsPermissions.require(StandardsPermissions.WARP))
                .executes(WarpCommands::listWarps);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> setWarp() {
        return Commands.literal("setwarp")
                .requires(StandardsPermissions.require(StandardsPermissions.SETWARP))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WarpCommands::suggestWarps)
                        .executes(WarpCommands::setWarp));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> delWarp() {
        return Commands.literal("delwarp")
                .requires(StandardsPermissions.require(StandardsPermissions.SETWARP))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WarpCommands::suggestWarps)
                        .executes(WarpCommands::delWarp));
    }

    // --- implementations ---

    private static int warpTo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        StandardsData data = StandardsData.get(player.level().getServer());
        Optional<Waypoint> destination = data.warp(name);
        if (destination.isEmpty()) {
            var known = data.warpNames();
            Feedback.chat(player, known.isEmpty()
                    ? Lang.fmt("msg.warp.unknown_none", "name", name)
                    : Lang.fmt("msg.warp.unknown", "name", name, "list", String.join(", ", known)));
            return 0;
        }
        Teleports.Attempt attempt = Teleports.request(player, destination.get(), true);
        if (!MoveCommands.report(player, attempt)) {
            return 0;
        }
        if (!attempt.queued()) {
            Feedback.chat(player, Lang.fmt("msg.warp.went", "name", name));
        }
        return 1;
    }

    private static int listWarps(CommandContext<CommandSourceStack> ctx) {
        List<String> names = StandardsData.get(ctx.getSource().getServer()).warpNames();
        if (names.isEmpty()) {
            Feedback.reply(ctx.getSource(), Lang.get("msg.warp.none"), false);
            return 0;
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.warp.list",
                "count", names.size(), "list", String.join("&7, &f", names)), false);
        return names.size();
    }

    private static int setWarp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        if (!NAME_RULES.matcher(name).matches()) {
            Feedback.chat(player, Lang.get("msg.warp.name_rules"));
            return 0;
        }
        Waypoint here = Waypoint.of(player);
        boolean replaced = StandardsData.get(player.level().getServer()).setWarp(name, here);
        Feedback.reply(ctx.getSource(), Lang.fmt(replaced ? "msg.warp.moved" : "msg.warp.set",
                "name", name, "place", here.describe()), true);
        Feedback.warnIfUnreachable(player, here);
        return 1;
    }

    private static int delWarp(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        StandardsData data = StandardsData.get(ctx.getSource().getServer());
        if (!data.deleteWarp(name)) {
            var known = data.warpNames();
            Feedback.fail(ctx.getSource(), known.isEmpty()
                    ? Lang.fmt("msg.warp.unknown_none", "name", name)
                    : Lang.fmt("msg.warp.unknown", "name", name, "list", String.join(", ", known)));
            return 0;
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.warp.deleted", "name", name), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestWarps(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                StandardsData.get(ctx.getSource().getServer()).warpNames(), builder);
    }

    private WarpCommands() {}
}
