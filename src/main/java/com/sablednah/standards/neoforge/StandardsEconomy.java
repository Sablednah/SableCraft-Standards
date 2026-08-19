package com.sablednah.standards.neoforge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.api.economy.EconomyProvider;
import com.sablednah.standards.api.economy.TransactionResult;
import com.sablednah.standards.core.Money;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Standards' own ledger — the fallback economy, backed by {@link StandardsData}.
 *
 * <p>Registered at {@link EconomyProvider#BUILTIN_PRIORITY}, which is deliberately below zero: a
 * mod whose whole job is money should outrank the one that ships an economy as a convenience.
 * With {@code economy.yieldToForeignProvider} on (the default) this provider steps aside entirely
 * once something else registers, so the two ledgers never both hold a balance for the same
 * player. Two ledgers that disagree about how much money you have is worse than either alone.</p>
 */
public final class StandardsEconomy implements EconomyProvider {

    public static final StandardsEconomy INSTANCE = new StandardsEconomy();

    private StandardsEconomy() {}

    /** Called once, from the mod constructor, unless the economy is switched off in config. */
    public static void registerIfEnabled() {
        if (!StandardsConfig.ENABLE_ECONOMY.get()) {
            Standards.LOGGER.info("Standards economy disabled in config; not registering a provider");
            return;
        }
        Economy.register(INSTANCE);
    }

    /**
     * Whether our own ledger is the one being used. Commands check this before offering to write
     * to it directly: {@code /eco set} on a foreign provider must go through {@link Economy}, not
     * behind its back.
     */
    public static boolean isActive() {
        return Economy.provider().map(p -> p == INSTANCE).orElse(false);
    }

    @Override
    public String name() {
        return "Standards";
    }

    /**
     * Below zero by default so a dedicated economy mod displaces us without either side needing
     * to know the other exists. {@code economy.preferOwnLedger} flips that for an owner who would
     * rather keep Standards' ledger than whatever a modpack dragged in.
     */
    @Override
    public int priority() {
        return StandardsConfig.PREFER_OWN_LEDGER.get() ? Integer.MAX_VALUE : BUILTIN_PRIORITY;
    }

    private Optional<StandardsData> data() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : Optional.of(StandardsData.get(server));
    }

    @Override
    public boolean hasAccount(UUID player) {
        return data().map(d -> d.hasAccount(player)).orElse(false);
    }

    @Override
    public boolean createAccount(UUID player) {
        return data().map(d -> d.createAccount(player, Money.round(StandardsConfig.STARTING_BALANCE.get())))
                .orElse(false);
    }

    @Override
    public double balance(UUID player) {
        return data().map(d -> d.balance(player)).orElse(0.0D);
    }

    @Override
    public TransactionResult deposit(UUID player, double amount, String reason) {
        if (!validAmount(amount)) {
            return TransactionResult.fail(TransactionResult.Failure.INVALID_AMOUNT, amount, balance(player));
        }
        Optional<StandardsData> data = data();
        if (data.isEmpty()) {
            return TransactionResult.fail(TransactionResult.Failure.REFUSED, amount, 0.0D);
        }
        // Depositing into a non-existent account creates it, because the alternative — money that
        // vanishes because a bounty landed before the player ever ran /balance — is a bug report.
        StandardsData d = data.get();
        double next = Money.round(d.balance(player) + amount);
        d.setBalance(player, next);
        return TransactionResult.ok(amount, next);
    }

    @Override
    public TransactionResult withdraw(UUID player, double amount, String reason) {
        if (!validAmount(amount)) {
            return TransactionResult.fail(TransactionResult.Failure.INVALID_AMOUNT, amount, balance(player));
        }
        Optional<StandardsData> data = data();
        if (data.isEmpty()) {
            return TransactionResult.fail(TransactionResult.Failure.REFUSED, amount, 0.0D);
        }
        StandardsData d = data.get();
        double current = d.balance(player);
        if (current < amount && !StandardsConfig.ALLOW_NEGATIVE_BALANCE.get()) {
            return TransactionResult.fail(TransactionResult.Failure.INSUFFICIENT_FUNDS, amount, current);
        }
        double next = Money.round(current - amount);
        d.setBalance(player, next);
        return TransactionResult.ok(amount, next);
    }

    /** Write a balance outright. Only {@code /eco set} does this; it is not part of the API. */
    public TransactionResult set(UUID player, double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return TransactionResult.fail(TransactionResult.Failure.INVALID_AMOUNT, amount, balance(player));
        }
        Optional<StandardsData> data = data();
        if (data.isEmpty()) {
            return TransactionResult.fail(TransactionResult.Failure.REFUSED, amount, 0.0D);
        }
        double next = Money.round(amount);
        data.get().setBalance(player, next);
        return TransactionResult.ok(next, next);
    }

    @Override
    public String format(double amount) {
        return Money.format(amount);
    }

    @Override
    public Optional<List<AccountSnapshot>> top(int limit) {
        return data().map(d -> d.richest(limit).stream()
                .map(e -> new AccountSnapshot(e.getKey(), d.nameOf(e.getKey()), e.getValue()))
                .toList());
    }

    private static boolean validAmount(double amount) {
        return amount >= 0.0D && !Double.isNaN(amount) && !Double.isInfinite(amount);
    }
}
