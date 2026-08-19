package com.sablednah.standards.neoforge.commands;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.InventoryView;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.Mutes;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

/**
 * The three moderation commands Standards ships, and no more.
 *
 * <p>The scope is a deliberate boundary rather than a to-do list. Vanilla already has
 * {@code /ban}, {@code /kick} and {@code /pardon}, and LuckPerms covers everything about who may
 * do what — so Standards fills exactly the three gaps they leave: a ban with an expiry, a mute,
 * and a way to look in someone's bag. A utility mod that grows a full moderation suite ends up
 * competing badly with the tools people already run.</p>
 *
 * <p>{@code /tempban} writes into <b>vanilla's own ban list</b>, which has stored an expiry date
 * all along. So {@code /pardon} lifts it, the ban screen shows it, and {@code banned-players.json}
 * holds it — none of which would be true of a private ban store.</p>
 */
public final class ModerationCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> tempban() {
        return Commands.literal("tempban")
                .requires(StandardsPermissions.require(StandardsPermissions.TEMPBAN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ModerationCommands::suggestKnownPlayers)
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> tempban(ctx, ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> tempban(ctx,
                                                StringArgumentType.getString(ctx, "reason"))))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> mute() {
        return Commands.literal("mute")
                .requires(StandardsPermissions.require(StandardsPermissions.MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ModerationCommands::suggestKnownPlayers)
                        .executes(ctx -> mute(ctx, Duration.PERMANENT, ""))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> muteParsed(ctx, ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> muteParsed(ctx,
                                                StringArgumentType.getString(ctx, "reason"))))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> unmute() {
        return Commands.literal("unmute")
                .requires(StandardsPermissions.require(StandardsPermissions.MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ModerationCommands::suggestMuted)
                        .executes(ModerationCommands::unmute));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> invsee() {
        return Commands.literal("invsee")
                .requires(StandardsPermissions.require(StandardsPermissions.INVSEE))
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ModerationCommands::invsee));
    }

    // --- tempban ---

    private static int tempban(CommandContext<CommandSourceStack> ctx, String reason) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = StandardsData.get(server).byName(server, name);
        if (target.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.common.player_not_found", "name", name));
            return 0;
        }
        Optional<Long> seconds = Duration.parse(StringArgumentType.getString(ctx, "duration"));
        if (seconds.isEmpty() || seconds.get() == Duration.PERMANENT) {
            // A permanent tempban is /ban, and vanilla already has that.
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.mod.bad_duration",
                    "input", StringArgumentType.getString(ctx, "duration")));
            return 0;
        }

        Date expires = new Date(System.currentTimeMillis() + seconds.get() * 1000L);
        String because = reason.isBlank() ? Lang.get("msg.mod.default_reason") : reason;
        server.getPlayerList().getBans().add(new UserBanListEntry(
                new NameAndId(target.get(), name),
                new Date(), ctx.getSource().getTextName(), expires, because));

        ServerPlayer online = server.getPlayerList().getPlayer(target.get());
        if (online != null) {
            online.connection.disconnect(Feedback.colored(Lang.fmt("msg.mod.ban_screen",
                    "duration", Duration.describe(seconds.get()), "reason", because)));
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.mod.banned",
                "player", name, "duration", Duration.describe(seconds.get()),
                "reason", because), true);
        return 1;
    }

    // --- mute ---

    private static int muteParsed(CommandContext<CommandSourceStack> ctx, String reason) {
        String raw = StringArgumentType.getString(ctx, "duration");
        Optional<Long> seconds = Duration.parse(raw);
        if (seconds.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.mod.bad_duration", "input", raw));
            return 0;
        }
        return mute(ctx, seconds.get(), reason);
    }

    private static int mute(CommandContext<CommandSourceStack> ctx, long seconds, String reason) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = StandardsData.get(server).byName(server, name);
        if (target.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.common.player_not_found", "name", name));
            return 0;
        }
        String because = reason.isBlank() ? Lang.get("msg.mod.default_reason") : reason;
        Mutes.get(server).mute(target.get(), seconds, because, ctx.getSource().getTextName());

        String howLong = Duration.describe(seconds);
        ServerPlayer online = server.getPlayerList().getPlayer(target.get());
        if (online != null) {
            // Told to their face. A silent mute is a player shouting into a void wondering why
            // nobody answers, and it makes moderators look broken rather than firm.
            Feedback.chat(online, Lang.fmt("msg.mod.muted_you",
                    "duration", howLong, "reason", because));
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.mod.muted",
                "player", name, "duration", howLong, "reason", because), true);
        return 1;
    }

    private static int unmute(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = StandardsData.get(server).byName(server, name);
        if (target.isEmpty() || !Mutes.get(server).unmute(target.get())) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.mod.not_muted", "player", name));
            return 0;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target.get());
        if (online != null) {
            Feedback.chat(online, Lang.get("msg.mod.unmuted_you"));
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.mod.unmuted", "player", name), true);
        return 1;
    }

    // --- invsee ---

    private static int invsee(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
        if (target == viewer) {
            Feedback.chat(viewer, Lang.get("msg.mod.invsee_self"));
            return 0;
        }
        InventoryView view = new InventoryView(target);
        viewer.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.sixRows(id, inventory, view),
                Component.literal(Lang.fmt("msg.mod.invsee_title",
                        "player", target.getName().getString()).replace('&', '§'))));
        return 1;
    }

    // --- suggestions ---

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        java.util.List<String> names =
                new java.util.ArrayList<>(java.util.List.of(server.getPlayerNames()));
        StandardsData.get(server).knownNames().forEach(n -> {
            if (!names.contains(n)) names.add(n);
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    /** Only the people actually muted — a list of everyone would be useless here. */
    private static CompletableFuture<Suggestions> suggestMuted(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData data = StandardsData.get(server);
        return SharedSuggestionProvider.suggest(
                Mutes.get(server).all().stream()
                        .map(m -> data.nameOf(m.player()).orElse(m.player().toString()))
                        .toList(),
                builder);
    }

    private ModerationCommands() {}
}
