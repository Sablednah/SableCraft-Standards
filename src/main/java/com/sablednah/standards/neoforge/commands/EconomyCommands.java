package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.List;
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
import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.api.economy.EconomyProvider;
import com.sablednah.standards.api.economy.TransactionResult;
import com.sablednah.standards.core.Money;
import com.sablednah.standards.neoforge.Mailbox;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsEconomy;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Money: {@code /balance}, {@code /baltop}, {@code /pay}, and the {@code /eco} admin tree.
 *
 * <p>Everything here goes through {@link Economy}, never through Standards' own ledger directly,
 * so an admin's {@code /eco give} lands in whichever economy is actually holding the money. A
 * utility mod whose admin commands quietly write to a ledger nobody is reading is worse than one
 * with no admin commands at all.</p>
 *
 * <p>Targets are typed as a plain name rather than an entity selector, because every interesting
 * economy question is about someone who is <em>not</em> online — paying a sleeping player, topping
 * up an account before an event, checking who is rich. Suggestions merge the online player list
 * with every name the server has ever seen.</p>
 */
public final class EconomyCommands {

    /** @param name the literal to build under, so aliases are real trees rather than redirects */
    public static LiteralArgumentBuilder<CommandSourceStack> balance(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.BALANCE))
                .executes(EconomyCommands::ownBalance)
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(StandardsPermissions.require(StandardsPermissions.BALANCE_OTHERS))
                        .suggests(EconomyCommands::suggestKnownPlayers)
                        .executes(EconomyCommands::otherBalance));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> balanceTop() {
        return Commands.literal("baltop")
                .requires(StandardsPermissions.require(StandardsPermissions.BALTOP))
                .executes(EconomyCommands::balanceTop);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> pay() {
        return Commands.literal("pay")
                .requires(StandardsPermissions.require(StandardsPermissions.PAY))
                // A name rather than EntityArgument, matching /balance and /eco. Paying somebody
                // who is asleep is an ordinary thing to want, and decision 5 puts balances in
                // SavedData precisely so questions about absent players can be answered. The
                // cost is selectors — /pay @p no longer works — which is a fair trade for a
                // command a human types at a named friend, unlike /fly @a where they earn their
                // place. The name cache only resolves players this server has actually seen, so
                // a typo fails rather than quietly paying a stranger.
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(EconomyCommands::suggestKnownPlayers)
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(EconomyCommands::pay)));
    }

    /** {@code /eco give|take|set <player> <amount>} — the admin tree. */
    public static LiteralArgumentBuilder<CommandSourceStack> eco() {
        return Commands.literal("eco")
                .requires(StandardsPermissions.require(StandardsPermissions.ECO_ADMIN))
                .then(adminVerb("give", AdminOp.GIVE))
                .then(adminVerb("take", AdminOp.TAKE))
                .then(adminVerb("set", AdminOp.SET));
    }

    private enum AdminOp { GIVE, TAKE, SET }

    /**
     * {@code /eco <verb> <player|selector> <amount>}.
     *
     * <p><b>Two argument branches, and the order is load-bearing.</b> The name branch is
     * registered first and {@code word()} accepts only {@code [0-9A-Za-z_.+-]} — so a selector
     * beginning with {@code @} fails to parse there and falls through to the selector branch,
     * while a plain name never reaches it. That gets both properties at once:</p>
     *
     * <ul>
     *   <li>{@code /eco give Steve 100} still works when Steve is <b>offline</b>, resolved from
     *       the name cache. Decision 5 puts balances in SavedData precisely so that question can
     *       be answered, and swapping the argument type outright would have thrown it away.</li>
     *   <li>{@code /eco give @p 100} and {@code /eco give @a[tag=winner] 500} work from a
     *       <b>command block</b>, which is the arena and event-reward case — you cannot name the
     *       winner in advance, which is the whole point of a prize.</li>
     * </ul>
     *
     * <p>Command blocks already pass the permission check: {@code require()} judges non-player
     * sources on vanilla level rather than the node, and a command block runs at level 2.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> adminVerb(String name, AdminOp op) {
        return Commands.literal(name)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(EconomyCommands::suggestKnownPlayers)
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> admin(ctx, op))))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> adminSelector(ctx, op))));
    }

    /**
     * The selector form. Applies the same operation to everyone matched, and reports a count
     * rather than a name — {@code /eco give @a 100} naming forty people is not a useful message.
     */
    private static int adminSelector(CommandContext<CommandSourceStack> ctx, AdminOp op)
            throws CommandSyntaxException {
        if (noEconomy(ctx)) return 0;
        Optional<Double> amount = amount(ctx);
        if (amount.isEmpty()) return 0;
        double value = Money.round(amount.get());

        int changed = 0;
        for (ServerPlayer target : EntityArgument.getPlayers(ctx, "targets")) {
            if (applyTo(target.getUUID(), op, value).success()) {
                changed++;
            }
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.eco.admin_many",
                "count", changed, "amount", Economy.format(value)), true);
        return changed;
    }

    /** The operation itself, shared by the name and selector forms so they cannot drift. */
    private static TransactionResult applyTo(UUID uuid, AdminOp op, double value) {
        return switch (op) {
            case GIVE -> Economy.deposit(uuid, value, "standards:eco give");
            case TAKE -> Economy.withdraw(uuid, value, "standards:eco take");
            // SET writes a balance outright, which is not something the shared API offers — a
            // foreign ledger may have rules of its own about that. Emulate it with the operations
            // it does offer rather than reaching behind it.
            case SET -> setBalance(uuid, value);
        };
    }

    // --- implementations ---

    private static int ownBalance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (noEconomy(ctx)) return 0;
        Feedback.chat(player, Lang.fmt("msg.eco.balance_self",
                "amount", Economy.format(Economy.balance(player.getUUID()))));
        return (int) Economy.balance(player.getUUID());
    }

    private static int otherBalance(CommandContext<CommandSourceStack> ctx) {
        if (noEconomy(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = resolve(ctx.getSource().getServer(), name);
        if (target.isEmpty() || !Economy.hasAccount(target.get())) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.eco.no_account", "player", name));
            return 0;
        }
        double balance = Economy.balance(target.get());
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.eco.balance_other",
                "player", name, "amount", Economy.format(balance)), false);
        return (int) balance;
    }

    private static int balanceTop(CommandContext<CommandSourceStack> ctx) {
        if (noEconomy(ctx)) return 0;
        Optional<List<EconomyProvider.AccountSnapshot>> rows =
                Economy.top(StandardsConfig.BALTOP_SIZE.get());
        if (rows.isEmpty()) {
            // An economy that cannot enumerate accounts says so, rather than showing a partial
            // list that reads as the whole truth.
            Feedback.fail(ctx.getSource(), Lang.get("msg.eco.baltop_unsupported"));
            return 0;
        }
        List<String> lines = new ArrayList<>();
        lines.add(Lang.get("msg.eco.baltop_header"));
        int rank = 1;
        for (EconomyProvider.AccountSnapshot row : rows.get()) {
            lines.add(Lang.fmt("msg.eco.baltop_row",
                    "rank", rank++,
                    "player", row.name().orElseGet(() -> row.player().toString().substring(0, 8) + "…"),
                    "amount", Economy.format(row.balance())));
        }
        Feedback.reply(ctx.getSource(), String.join("\n", lines), false);
        return rows.get().size();
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer payer = ctx.getSource().getPlayerOrException();
        if (noEconomy(ctx)) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        String payeeName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> resolved = resolve(server, payeeName);
        if (resolved.isEmpty()) {
            Feedback.chat(payer, Lang.fmt("msg.eco.unknown_player", "player", payeeName));
            return 0;
        }
        UUID payeeId = resolved.get();
        ServerPlayer payee = server.getPlayerList().getPlayer(payeeId);

        if (payeeId.equals(payer.getUUID())) {
            Feedback.chat(payer, Lang.get("msg.eco.pay_self"));
            return 0;
        }
        Optional<Double> amount = amount(ctx);
        if (amount.isEmpty()) return 0;
        if (amount.get() <= 0.0D) {
            Feedback.chat(payer, Lang.get("msg.eco.not_positive"));
            return 0;
        }

        TransactionResult result = Economy.transfer(
                payer.getUUID(), payeeId, Money.round(amount.get()), "standards:pay");
        if (!result.success()) {
            Feedback.chat(payer, switch (result.failure()) {
                case INSUFFICIENT_FUNDS -> Lang.fmt("msg.eco.insufficient",
                        "balance", Economy.format(Economy.balance(payer.getUUID())),
                        "amount", Economy.format(result.amount()));
                default -> Lang.get("msg.eco.refused");
            });
            return 0;
        }
        // The stored spelling, not what they typed — so paying "steve" credits Steve and says so.
        String shownName = payee != null ? payee.getName().getString()
                : StandardsData.get(server).nameOf(payeeId).orElse(payeeName);
        Feedback.chat(payer, Lang.fmt(payee != null ? "msg.eco.paid" : "msg.eco.paid_offline",
                "amount", Economy.format(result.amount()),
                "player", shownName,
                "balance", Economy.format(Economy.balance(payer.getUUID()))));
        if (payee != null) {
            Feedback.chat(payee, Lang.fmt("msg.eco.received",
                    "player", payer.getName().getString(),
                    "amount", Economy.format(result.amount()),
                    "balance", Economy.format(Economy.balance(payeeId))));
        } else if (StandardsConfig.ENABLE_MAIL.get()) {
            // Money that turns up with no explanation is money the recipient assumes is a bug, and
            // "tell somebody something when they are next on" is exactly what the mailbox is for —
            // so this reuses it rather than growing a second delivery mechanism beside it. Sent
            // from the payer, so /mail read names them and a reply goes to the right person.
            Mailbox.get(server).send(payeeId, payer.getUUID(), payer.getName().getString(),
                    Lang.fmt("msg.eco.paid_you_offline",
                            "amount", Economy.format(result.amount())));
        }
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> ctx, AdminOp op) {
        if (noEconomy(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = resolve(ctx.getSource().getServer(), name);
        if (target.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.common.player_not_found", "name", name));
            return 0;
        }
        Optional<Double> amount = amount(ctx);
        if (amount.isEmpty()) return 0;
        UUID uuid = target.get();
        double value = Money.round(amount.get());

        TransactionResult result = applyTo(uuid, op, value);

        if (!result.success()) {
            Feedback.fail(ctx.getSource(), switch (result.failure()) {
                case INSUFFICIENT_FUNDS -> Lang.fmt("msg.eco.insufficient",
                        "balance", Economy.format(Economy.balance(uuid)),
                        "amount", Economy.format(value));
                default -> Lang.get("msg.eco.refused");
            });
            return 0;
        }
        String key = switch (op) {
            case GIVE -> "msg.eco.admin_gave";
            case TAKE -> "msg.eco.admin_took";
            case SET -> "msg.eco.admin_set";
        };
        Feedback.reply(ctx.getSource(), Lang.fmt(key,
                "player", name,
                "amount", Economy.format(value),
                "balance", Economy.format(Economy.balance(uuid))), true);
        return 1;
    }

    /** Set a balance: direct on our own ledger, or a compensating transaction on a foreign one. */
    private static TransactionResult setBalance(UUID player, double value) {
        if (StandardsEconomy.isActive()) {
            return StandardsEconomy.INSTANCE.set(player, value);
        }
        double current = Economy.balance(player);
        double delta = Money.round(value - current);
        if (delta == 0.0D) {
            return TransactionResult.ok(value, current);
        }
        return delta > 0
                ? Economy.deposit(player, delta, "standards:eco set")
                : Economy.withdraw(player, -delta, "standards:eco set");
    }

    // --- helpers ---

    /**
     * Read the amount argument.
     *
     * <p>Typed as a word rather than a brigadier double so {@code $50} and {@code 1,000} both
     * work — players type the currency symbol they see in every other message, and refusing it
     * teaches nothing. The cost is that brigadier cannot validate it for us, so a bad amount is
     * reported here with a message that says what was actually typed.</p>
     */
    private static Optional<Double> amount(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "amount");
        Optional<Double> parsed = Money.parse(raw);
        if (parsed.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.eco.invalid_amount", "input", raw));
        }
        return parsed;
    }

    private static Optional<UUID> resolve(MinecraftServer server, String name) {
        return StandardsData.get(server).byName(server, name);
    }

    private static boolean noEconomy(CommandContext<CommandSourceStack> ctx) {
        if (Economy.isAvailable()) return false;
        Feedback.fail(ctx.getSource(), Lang.get("msg.eco.disabled"));
        return true;
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> names = new ArrayList<>(List.of(server.getPlayerNames()));
        StandardsData.get(server).knownNames().forEach(n -> {
            if (!names.contains(n)) names.add(n);
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private EconomyCommands() {}
}
