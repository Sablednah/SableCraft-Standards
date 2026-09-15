package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Homes: {@code /sethome}, {@code /home}, {@code /delhome}, {@code /homes}, {@code /renamehome}.
 *
 * <p>The default home is called {@code home}, so a player who never wants to think about names
 * never has to — {@code /sethome} then {@code /home} is the whole feature. Names only appear once
 * someone asks for a second one.</p>
 *
 * <p>Staff holding {@code standards.home.others} can reach anybody's homes, online or not:
 * {@code /home Steve} lists Steve's as buttons and {@code /home Steve base} goes. A player can own
 * several homes, so naming only the player is a question rather than a destination.</p>
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
                        .suggests(HomeCommands::suggestOwnHomesAndPlayers)
                        .executes(ctx -> home(ctx, StringArgumentType.getString(ctx, "name")))
                        // /home <player> <home>. A second argument rather than EssentialsX's
                        // 'player:home', because a colon is exactly the punctuation word() refuses
                        // — the trap that has already cost this mod two features.
                        .then(Commands.argument("home", StringArgumentType.word())
                                .requires(StandardsPermissions.require(StandardsPermissions.HOME_OTHERS))
                                .suggests(HomeCommands::suggestTheirHomes)
                                .executes(ctx -> homeOf(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "home")))));
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
                .executes(HomeCommands::listHomes)
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(StandardsPermissions.require(StandardsPermissions.HOME_OTHERS))
                        .suggests(HomeCommands::suggestKnownPlayers)
                        .executes(ctx -> listHomesOf(ctx, StringArgumentType.getString(ctx, "player"))));
    }

    /**
     * Gated on {@code sethome} rather than a node of its own: renaming is making a home under a
     * different name, and a player barred from that should not be able to do it sideways.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> renameHome() {
        return Commands.literal("renamehome")
                .requires(StandardsPermissions.require(StandardsPermissions.SETHOME))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HomeCommands::suggestOwnHomes)
                        .then(Commands.argument("newName", StringArgumentType.word())
                                .executes(ctx -> renameHome(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "newName")))));
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

        // A bare /home with no home called "home", but exactly one home to go to, goes there.
        //
        // Found by a player who owned homes named one, two and three, watched /home refuse them
        // all, and set a FOURTH home called "home" to work around it. That is the mod making
        // somebody think about its naming convention instead of going home — and the reasoning
        // applies to typing it, not just to the button that runs it, so the fix is here rather
        // than in the client.
        //
        // Only when there is exactly one. With several and none named "home" we genuinely cannot
        // guess, and the list below is the right answer.
        if (destination.isEmpty() && DEFAULT.equals(name)) {
            Map<String, Waypoint> mine = data.homesOf(player.getUUID());
            if (mine.size() == 1) {
                name = mine.keySet().iterator().next();
                destination = Optional.of(mine.values().iterator().next());
            }
        }

        // Not one of theirs — but staff may have meant a player. Asked only after their own homes
        // have had their say, so a home somebody named after a friend is still theirs to go to.
        if (destination.isEmpty() && !DEFAULT.equals(name)
                && StandardsPermissions.has(player, StandardsPermissions.HOME_OTHERS)) {
            Optional<UUID> owner = data.byName(player.level().getServer(), name);
            if (owner.isPresent() && !owner.get().equals(player.getUUID())) {
                pickTheirs(player, data, owner.get(), name, null);
                return 0;
            }
        }

        if (destination.isEmpty()) {
            Map<String, Waypoint> mine = data.homesOf(player.getUUID());
            if (mine.isEmpty()) {
                Feedback.chat(player, Lang.get("msg.home.none"));
                return 0;
            }
            // The list is CLICKABLE. Chat click events are plain vanilla components, so this works
            // on an unmodified client — and it turns "you have one, two, three" from an error
            // message into the thing you wanted, which is a way to get to one of them.
            Component[] buttons = mine.keySet().stream()
                    .map(home -> Feedback.button("&f[" + home + "]", "/home " + home,
                            Lang.fmt("msg.home.go_to", "name", home)))
                    .toArray(Component[]::new);
            Feedback.chatWithButtons(player,
                    Lang.fmt("msg.home.pick", "name", name), buttons);
            return 0;
        }
        Teleports.Attempt attempt = Teleports.request(player, destination.get(), true,
                Lang.fmt("msg.home.went", "name", name));
        if (!MoveCommands.report(player, attempt)) {
            return 0;
        }
        return 1;
    }

    /** {@code /home <player> <home>}. */
    private static int homeOf(CommandContext<CommandSourceStack> ctx, String playerName, String homeName)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.level().getServer();
        StandardsData data = StandardsData.get(server);
        Optional<UUID> owner = data.byName(server, playerName);
        if (owner.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.home.no_player", "name", playerName));
            return 0;
        }
        Optional<Waypoint> destination = data.home(owner.get(), homeName);
        if (destination.isEmpty()) {
            pickTheirs(player, data, owner.get(), playerName, homeName);
            return 0;
        }
        String who = data.nameOf(owner.get()).orElse(playerName);
        // Named as the owner spelled it, since the lookup ignores case.
        String home = data.homesOf(owner.get()).keySet().stream()
                .filter(k -> k.equalsIgnoreCase(homeName)).findFirst().orElse(homeName);
        Teleports.Attempt attempt = Teleports.request(player, destination.get(), true,
                Lang.fmt("msg.home.went_other", "player", who, "name", home));
        return MoveCommands.report(player, attempt) ? 1 : 0;
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

    private static int renameHome(CommandContext<CommandSourceStack> ctx, String from, String to)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!NAME_RULES.matcher(to).matches()) {
            Feedback.chat(player, Lang.get("msg.home.name_rules"));
            return 0;
        }
        StandardsData data = StandardsData.get(player.level().getServer());
        Map<String, Waypoint> mine = data.homesOf(player.getUUID());
        // Resolved before renaming, so the message names the home as it was actually spelled.
        String old = mine.keySet().stream()
                .filter(k -> k.equalsIgnoreCase(from)).findFirst().orElse(from);
        switch (data.renameHome(player.getUUID(), from, to)) {
            case MISSING -> {
                Feedback.chat(player, mine.isEmpty()
                        ? Lang.get("msg.home.none")
                        : Lang.fmt("msg.home.unknown", "name", from, "list", String.join(", ", mine.keySet())));
                return 0;
            }
            case TAKEN -> {
                Feedback.chat(player, Lang.fmt("msg.home.rename_taken", "to", to));
                return 0;
            }
            default -> {
                Feedback.chat(player, Lang.fmt("msg.home.renamed", "from", old, "to", to));
                return 1;
            }
        }
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

    /** {@code /homes <player>}. */
    private static int listHomesOf(CommandContext<CommandSourceStack> ctx, String playerName)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.level().getServer();
        StandardsData data = StandardsData.get(server);
        Optional<UUID> owner = data.byName(server, playerName);
        if (owner.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.home.no_player", "name", playerName));
            return 0;
        }
        return pickTheirs(player, data, owner.get(), playerName, null);
    }

    /**
     * Somebody else's homes, as buttons.
     *
     * <p>Always a list, never a guess — even when they have exactly one. Going straight there is
     * right for your OWN single home, but here a mistyped home name that happens to match a player
     * would drop staff into somebody's bedroom without asking.</p>
     *
     * @param missingHome the home that was asked for and not found, or null if none was named
     * @return how many homes were listed
     */
    private static int pickTheirs(ServerPlayer player, StandardsData data, UUID owner,
            String typed, String missingHome) {
        String who = data.nameOf(owner).orElse(typed);
        Map<String, Waypoint> theirs = data.homesOf(owner);
        if (theirs.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.home.others_none", "player", who));
            return 0;
        }
        Component[] buttons = theirs.keySet().stream()
                .map(home -> Feedback.button("&f[" + home + "]", "/home " + who + " " + home,
                        Lang.fmt("msg.home.go_to_other", "player", who, "name", home)))
                .toArray(Component[]::new);
        Feedback.chatWithButtons(player, missingHome == null
                        ? Lang.fmt("msg.home.others_pick", "player", who, "count", theirs.size())
                        : Lang.fmt("msg.home.others_unknown", "player", who, "name", missingHome),
                buttons);
        return theirs.size();
    }

    // --- suggestions ---

    private static CompletableFuture<Suggestions> suggestOwnHomes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return SharedSuggestionProvider.suggest(
                    StandardsData.get(player.level().getServer()).homesOf(player.getUUID()).keySet(), builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOwnHomesAndPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return builder.buildFuture();
        }
        StandardsData data = StandardsData.get(player.level().getServer());
        List<String> options = new ArrayList<>(data.homesOf(player.getUUID()).keySet());
        // Players only for someone who may visit their homes: offering every name on the server to
        // a player who cannot use one would bury their own homes under strangers.
        if (StandardsPermissions.has(player, StandardsPermissions.HOME_OTHERS)) {
            options.addAll(data.knownNames());
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static CompletableFuture<Suggestions> suggestTheirHomes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData data = StandardsData.get(server);
        return data.byName(server, StringArgumentType.getString(ctx, "name"))
                .map(owner -> SharedSuggestionProvider.suggest(data.homesOf(owner).keySet(), builder))
                .orElseGet(builder::buildFuture);
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                StandardsData.get(ctx.getSource().getServer()).knownNames(), builder);
    }

    private HomeCommands() {}
}
