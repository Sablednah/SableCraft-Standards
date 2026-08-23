package com.sablednah.standards.neoforge.commands;

import java.util.Map;
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
 * Homes: {@code /sethome}, {@code /home}, {@code /delhome}, {@code /homes}.
 *
 * <p>The default home is called {@code home}, so a player who never wants to think about names
 * never has to — {@code /sethome} then {@code /home} is the whole feature. Names only appear once
 * someone asks for a second one.</p>
 */
public final class HomeCommands {

    /** The home you get when you do not name one. */
    private static final String DEFAULT = "home";

    private static final java.util.regex.Pattern NAME_RULES =
            java.util.regex.Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    public static LiteralArgumentBuilder<CommandSourceStack> setHome() {
        return Commands.literal("sethome")
                .requires(StandardsPermissions.require(StandardsPermissions.SETHOME))
                .executes(ctx -> setHome(ctx, DEFAULT))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HomeCommands::suggestOwnHomes)
                        .executes(ctx -> setHome(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> home() {
        return Commands.literal("home")
                .requires(StandardsPermissions.require(StandardsPermissions.HOME))
                .executes(ctx -> home(ctx, DEFAULT))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HomeCommands::suggestOwnHomes)
                        .executes(ctx -> home(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> delHome() {
        return Commands.literal("delhome")
                .requires(StandardsPermissions.require(StandardsPermissions.DELHOME))
                .executes(ctx -> delHome(ctx, DEFAULT))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HomeCommands::suggestOwnHomes)
                        .executes(ctx -> delHome(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> homes() {
        return Commands.literal("homes")
                .requires(StandardsPermissions.require(StandardsPermissions.HOME))
                .executes(HomeCommands::listHomes);
    }

    // --- implementations ---

    private static int setHome(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!NAME_RULES.matcher(name).matches()) {
            Feedback.chat(player, Lang.get("msg.home.name_rules"));
            return 0;
        }
        StandardsData data = StandardsData.get(player.level().getServer());
        Map<String, Waypoint> existing = data.homesOf(player.getUUID());
        boolean overwriting = data.home(player.getUUID(), name).isPresent();

        // The limit only bites when adding: moving a home you already have is always allowed, and
        // is the escape hatch offered when someone hits the ceiling.
        if (!overwriting) {
            int limit = StandardsPermissions.homeLimit(player);
            if (limit >= 0 && existing.size() >= limit) {
                Feedback.chat(player, Lang.fmt("msg.home.limit", "limit", limit, "name", name));
                return 0;
            }
        }

        Waypoint here = Waypoint.of(player);
        data.setHome(player.getUUID(), name, here);
        Feedback.chat(player, Lang.fmt(overwriting ? "msg.home.moved" : "msg.home.set",
                "name", name, "place", here.describe()));
        Feedback.warnIfUnreachable(player, here);
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StandardsData data = StandardsData.get(player.level().getServer());
        Optional<Waypoint> destination = data.home(player.getUUID(), name);

        if (destination.isEmpty()) {
            Map<String, Waypoint> mine = data.homesOf(player.getUUID());
            Feedback.chat(player, mine.isEmpty()
                    ? Lang.get("msg.home.none")
                    : Lang.fmt("msg.home.unknown", "name", name, "list", String.join(", ", mine.keySet())));
            return 0;
        }
        Teleports.Attempt attempt = Teleports.request(player, destination.get(), true,
                Lang.fmt("msg.home.went", "name", name));
        if (!MoveCommands.report(player, attempt)) {
            return 0;
        }
        return 1;
    }

    private static int delHome(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StandardsData data = StandardsData.get(player.level().getServer());
        if (!data.deleteHome(player.getUUID(), name)) {
            Map<String, Waypoint> mine = data.homesOf(player.getUUID());
            Feedback.chat(player, mine.isEmpty()
                    ? Lang.get("msg.home.none")
                    : Lang.fmt("msg.home.unknown", "name", name, "list", String.join(", ", mine.keySet())));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.home.deleted", "name", name));
        return 1;
    }

    private static int listHomes(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Map<String, Waypoint> mine = StandardsData.get(player.level().getServer()).homesOf(player.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.home.none"));
            return 0;
        }
        int limit = StandardsPermissions.homeLimit(player);
        Feedback.chat(player, Lang.fmt("msg.home.list",
                "count", mine.size(),
                "limit", limit < 0 ? Lang.get("msg.home.unlimited") : limit,
                "list", String.join("&7, &f", mine.keySet())));
        return mine.size();
    }

    private static CompletableFuture<Suggestions> suggestOwnHomes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return SharedSuggestionProvider.suggest(
                    StandardsData.get(player.level().getServer()).homesOf(player.getUUID()).keySet(), builder);
        }
        return builder.buildFuture();
    }

    private HomeCommands() {}
}
