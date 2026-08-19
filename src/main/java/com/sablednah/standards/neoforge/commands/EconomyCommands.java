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
                .then(Commands.argument("player", EntityArgument.player())
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

    private static LiteralArgumentBuilder<CommandSourceStack> adminVerb(String name, AdminOp op) {
        return Commands.literal(name)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(EconomyCommands::suggestKnownPlayers)
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> admin(ctx, op))));
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
        ServerPlayer payee = EntityArgument.getPlayer(ctx, "player");

        if (payee.getUUID().equals(payer.getUUID())) {
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
                payer.getUUID(), payee.getUUID(), Money.round(amount.get()), "standards:pay");
        if (!result.success()) {
            Feedback.chat(payer, switch (result.failure()) {
                case INSUFFICIENT_FUNDS -> Lang.fmt("msg.eco.insufficient",
                        "balance", Economy.format(Economy.balance(payer.getUUID())),
                        "amount", Economy.format(result.amount()));
                default -> Lang.get("msg.eco.refused");
            });
            return 0;
        }
        Feedback.chat(payer, Lang.fmt("msg.eco.paid",
                "amount", Economy.format(result.amount()),
                "player", payee.getName().getString(),
                "balance", Economy.format(Economy.balance(payer.getUUID()))));
        Feedback.chat(payee, Lang.fmt("msg.eco.received",
                "player", payer.getName().getString(),
                "amount", Economy.format(result.amount()),
                "balance", Economy.format(Economy.balance(payee.getUUID()))));
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

        TransactionResult result = switch (op) {
            case GIVE -> Economy.deposit(uuid, value, "standards:eco give");
            case TAKE -> Economy.withdraw(uuid, value, "standards:eco take");
            // SET writes a balance outright, which is not something the shared API offers — a
            // foreign ledger may have rules of its own about that. Emulate it with the operations
            // it does offer rather than reaching behind it.
            case SET -> setBalance(uuid, value);
        };

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
