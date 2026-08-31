package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /playtime} and {@code /leaderboard}.
 *
 * <h2>The number is time actually played, not time logged in</h2>
 *
 * <p>Minecraft keeps a {@code PLAY_TIME} statistic and it counts a player standing in a corner all
 * night. Standards counts minutes only while somebody is online and <b>not away</b> — the same
 * counter the promotion ladder waits on, which is the point: a leaderboard and a promotion that
 * disagreed about how long somebody had played would both be wrong to somebody.</p>
 *
 * <p>So {@code /playtime} answers "how long have you actually been here", and an AFK farm does not
 * climb the board.</p>
 *
 * <h2>Why the board is not a general statistics browser</h2>
 *
 * <p>FTB's version reads any vanilla statistic. That is a bigger feature than it looks — a stat id
 * argument, per-stat formatting, and a scan of every player file on disk — and the two boards
 * anybody actually asks for are money and time. Money already has {@code /baltop}. This is the
 * other one.</p>
 */
public final class PlaytimeCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> playtime() {
        return Commands.literal("playtime")
                .requires(StandardsPermissions.require(StandardsPermissions.PLAYTIME))
                .executes(ctx -> playtime(ctx, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(StandardsPermissions.require(
                                StandardsPermissions.PLAYTIME_OTHERS))
                        .suggests(PlaytimeCommands::suggestKnownPlayers)
                        .executes(ctx -> playtime(ctx,
                                StringArgumentType.getString(ctx, "player"))));
    }

    private static int playtime(CommandContext<CommandSourceStack> ctx, String who)
            throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData data = StandardsData.get(server);
        UUID id;
        String name;
        if (who == null) {
            ServerPlayer self = ctx.getSource().getPlayerOrException();
            id = self.getUUID();
            name = self.getName().getString();
        } else {
            Optional<UUID> found = data.byName(server, who);
            if (found.isEmpty()) {
                Feedback.fail(ctx.getSource(), Lang.fmt("msg.common.player_not_found", "name", who));
                return 0;
            }
            id = found.get();
            name = data.nameOf(id).orElse(who);
        }
        long minutes = data.playedMinutes(id);
        Feedback.reply(ctx.getSource(), Lang.fmt(who == null ? "msg.playtime.self" : "msg.playtime.other",
                "player", name, "time", describe(minutes)), false);
        return (int) Math.min(Integer.MAX_VALUE, minutes);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> leaderboard(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.PLAYTIME))
                .executes(PlaytimeCommands::board);
    }

    private static int board(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData data = StandardsData.get(server);
        List<Map.Entry<UUID, Long>> rows = new ArrayList<>();
        for (UUID id : data.playersWithPlaytime()) {
            rows.add(Map.entry(id, data.playedMinutes(id)));
        }
        rows.sort(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()));
        int limit = StandardsConfig.BALTOP_SIZE.get();

        if (rows.isEmpty()) {
            Feedback.reply(ctx.getSource(), Lang.get("msg.playtime.board_empty"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder(Lang.get("msg.playtime.board_header"));
        int rank = 0;
        for (Map.Entry<UUID, Long> row : rows) {
            if (++rank > limit) {
                break;
            }
            sb.append("\n").append(Lang.fmt("msg.playtime.board_row",
                    "rank", String.valueOf(rank),
                    // A player the name cache has never seen shows as a short id rather than the
                    // full UUID: unhelpful either way, but one of them fits on a chat line.
                    "player", data.nameOf(row.getKey())
                            .orElseGet(() -> row.getKey().toString().substring(0, 8)),
                    "time", describe(row.getValue())));
        }
        Feedback.reply(ctx.getSource(), sb.toString(), false);
        return Math.min(rank, limit);
    }

    /** Through {@link Duration}, so "2h 30m" reads the same here as after a {@code /tempban}. */
    private static String describe(long minutes) {
        return minutes <= 0 ? Lang.get("msg.playtime.none") : Duration.describe(minutes * 60L);
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                StandardsData.get(ctx.getSource().getServer()).knownNames(), builder);
    }

    private PlaytimeCommands() {}
}
