package com.sablednah.standards.neoforge.commands;

import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.reputation.Reputation;
import com.sablednah.standards.api.reputation.ReputationProvider;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;

/**
 * {@code /rep} — what people think of you.
 *
 * <h3>Standing names are not {@code word()}</h3>
 *
 * <p>They are typed into quest files by hand and will contain punctuation: {@code the_hospital}
 * today, {@code st.marys} or {@code hospital-north} tomorrow. Brigadier's {@code word()} accepts
 * letters, digits and {@code _.+-} and nothing else, and this repository has now paid for that four
 * separate times — a permission wildcard, a nickname with a colour code, a faction called "Lantern
 * Vale", and an argument in {@code /f money pay}. So the standing is a {@code greedyString} where it
 * is last, and the rule is enforced in code where the message can say what is actually wrong.</p>
 *
 * <p>Where a number follows the standing there is nothing greedy to be had, so those forms take the
 * standing as {@code string()} — a bare word, or a quoted phrase — and the suggestions quote
 * anything that needs it.</p>
 */
public final class ReputationCommands {

    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(rep());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rep() {
        return Commands.literal("rep")
                .requires(StandardsPermissions.require(StandardsPermissions.REP))
                .executes(ctx -> mine(ctx))
                .then(Commands.literal("top")
                        .requires(StandardsPermissions.require(StandardsPermissions.REP_TOP))
                        .then(standingGreedy().executes(ReputationCommands::top)))
                .then(Commands.literal("list")
                        .executes(ReputationCommands::list))
                .then(Commands.literal("set")
                        .requires(StandardsPermissions.require(StandardsPermissions.REP_ADMIN))
                        .then(Commands.argument("player", EntityArgument.players())
                                .then(standingQuoted()
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000000, 1000000))
                                                .executes(ctx -> write(ctx, true))))))
                .then(Commands.literal("add")
                        .requires(StandardsPermissions.require(StandardsPermissions.REP_ADMIN))
                        .then(Commands.argument("player", EntityArgument.players())
                                .then(standingQuoted()
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-1000000, 1000000))
                                                .executes(ctx -> write(ctx, false))))))
                // Last, so the literals above win: brigadier tries children in insertion order and
                // a player literally named "top" would otherwise shadow the subcommand.
                .then(Commands.argument("who", EntityArgument.player())
                        .requires(StandardsPermissions.require(StandardsPermissions.REP_OTHERS))
                        .executes(ReputationCommands::theirs));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            standingGreedy() {
        return Commands.argument("standing", StringArgumentType.greedyString())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Reputation.standings(), b));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            standingQuoted() {
        return Commands.argument("standing", StringArgumentType.string())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Reputation.standings(), b));
    }

    /** {@code /rep} — every standing anybody holds about you. */
    private static int mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return show(player, player.getUUID(), player.getName().getString());
    }

    /** {@code /rep <player>} — theirs. */
    private static int theirs(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer who = EntityArgument.getPlayer(ctx, "who");
        return show(viewer, who.getUUID(), who.getName().getString());
    }

    private static int show(ServerPlayer viewer, UUID subject, String name) {
        if (!Reputation.isAvailable()) {
            Feedback.chat(viewer, Lang.get("msg.rep.unavailable"));
            return 0;
        }
        Map<String, Integer> mine = Reputation.of(subject);
        if (mine.isEmpty()) {
            Feedback.chat(viewer, Lang.fmt("msg.rep.none", "player", name));
            return 0;
        }
        Feedback.chat(viewer, Lang.fmt("msg.rep.header", "player", name));
        mine.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> Feedback.chat(viewer, Lang.fmt("msg.rep.row",
                        "standing", e.getKey(), "value", e.getValue(),
                        "band", band(e.getKey(), e.getValue()))));
        return mine.size();
    }

    /** {@code /rep list} — which standings exist at all. */
    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var all = Reputation.standings();
        if (all.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.rep.no_standings"));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.rep.list", "standings", String.join(", ", all)));
        return all.size();
    }

    /** {@code /rep top <standing>}. */
    private static int top(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String standing = Reputation.normalise(StringArgumentType.getString(ctx, "standing"));
        if (standing.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.rep.name_needed"));
            return 0;
        }
        var rows = Reputation.top(standing, StandardsConfig.REPTOP_SIZE.get());
        if (rows.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.rep.top_none", "standing", standing));
            return 0;
        }
        StandardsData names = StandardsData.get(ctx.getSource().getServer());
        Feedback.chat(player, Lang.fmt("msg.rep.top_header", "standing", standing));
        int place = 0;
        for (ReputationProvider.Entry row : rows) {
            place++;
            Feedback.chat(player, Lang.fmt("msg.rep.top_row",
                    "place", place,
                    "player", names.nameOf(row.player()).orElse(row.player().toString()),
                    "value", row.value(), "band", band(standing, row.value())));
        }
        return rows.size();
    }

    /** {@code /rep set|add <player> <standing> <n>}. */
    private static int write(CommandContext<CommandSourceStack> ctx, boolean absolute)
            throws CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        if (!Reputation.isAvailable()) {
            Feedback.chat(admin, Lang.get("msg.rep.unavailable"));
            return 0;
        }
        String standing = Reputation.normalise(StringArgumentType.getString(ctx, "standing"));
        if (standing.isEmpty()) {
            Feedback.chat(admin, Lang.get("msg.rep.name_needed"));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        int touched = 0;
        for (ServerPlayer target : EntityArgument.getPlayers(ctx, "player")) {
            String reason = "/rep by " + admin.getName().getString();
            int landed = absolute
                    ? Reputation.set(target.getUUID(), standing, value, reason)
                    : Reputation.adjust(target.getUUID(), standing, value, reason);
            // Says where it LANDED, not what was asked for. A clamp that silently ate half the
            // reward is exactly the kind of thing an admin needs told at the moment it happens.
            Feedback.chat(admin, Lang.fmt("msg.rep.changed",
                    "player", target.getName().getString(),
                    "standing", standing, "value", landed, "band", band(standing, landed)));
            touched++;
        }
        return touched;
    }

    /**
     * A word for a number, if the owner configured one.
     *
     * <p>Bands are <b>display only</b> — the number is the fact. Keeping them out of the API was
     * deliberate: a consumer that branched on the band would break the moment an owner renamed one,
     * and a quest that needs a threshold should say the threshold it means.</p>
     */
    private static String band(String standing, int value) {
        return Reputation.band(standing, value).orElse("");
    }

    private ReputationCommands() {}
}
