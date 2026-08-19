package com.sablednah.standards.api.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * The one door to money. Other mods call this and nothing else.
 *
 * <p>It works whether Standards' own ledger is holding the money, some other economy mod has
 * taken over, or the economy is switched off entirely — {@link #isAvailable()} distinguishes the
 * last case, and every operation degrades to a clean refusal rather than an exception. A mod that
 * <em>wants</em> money but can live without it (ZombieMod's bounties, LegendQuest's future
 * skill costs) can therefore call in unconditionally:</p>
 *
 * <pre>{@code
 * if (Economy.isAvailable()) {
 *     Economy.deposit(player.getUUID(), bounty, "zombiemod:bounty");
 * }
 * }</pre>
 *
 * <p><b>Thread safety.</b> Everything here should be called from the server thread. Balances live
 * in world save data; touching them off-thread is the same mistake as touching a level off-thread,
 * and this facade does not add a lock that would only paper over it.</p>
 */
public final class Economy {

    private static final Logger LOG = LogUtils.getLogger();

    /** Every registered provider, highest priority first. The head is the one that holds money. */
    private static final List<EconomyProvider> PROVIDERS = new ArrayList<>();

    /**
     * Offer a ledger. Call during mod construction, guarded by a {@code standards} loaded check.
     * The highest {@link EconomyProvider#priority()} wins; ties keep the earlier registration,
     * which at least makes the outcome depend on mod load order rather than on nothing.
     */
    public static synchronized void register(EconomyProvider provider) {
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparingInt(EconomyProvider::priority).reversed());
        LOG.info("Standards: economy provider '{}' registered (priority {}); '{}' now holds the money",
                provider.name(), provider.priority(), PROVIDERS.getFirst().name());
    }

    /** The provider currently holding the money, if there is one. */
    public static synchronized Optional<EconomyProvider> provider() {
        return PROVIDERS.isEmpty() ? Optional.empty() : Optional.of(PROVIDERS.getFirst());
    }

    /** Every registered provider, for diagnostics — {@code /standards economy} prints this. */
    public static synchronized List<EconomyProvider> all() {
        return List.copyOf(PROVIDERS);
    }

    /** Is there any economy at all? False means "no money on this server", not "an error". */
    public static boolean isAvailable() {
        return provider().isPresent();
    }

    // --- operations ---

    public static boolean hasAccount(UUID player) {
        return provider().map(p -> p.hasAccount(player)).orElse(false);
    }

    public static boolean createAccount(UUID player) {
        return provider().map(p -> p.createAccount(player)).orElse(false);
    }

    public static double balance(UUID player) {
        return provider().map(p -> p.balance(player)).orElse(0.0D);
    }

    /** Can this player afford it? The question worth asking before doing anything expensive. */
    public static boolean has(UUID player, double amount) {
        return balance(player) >= amount;
    }

    public static TransactionResult deposit(UUID player, double amount, String reason) {
        return dispatch(p -> p.deposit(player, amount, reason), amount);
    }

    public static TransactionResult withdraw(UUID player, double amount, String reason) {
        return dispatch(p -> p.withdraw(player, amount, reason), amount);
    }

    public static TransactionResult transfer(UUID from, UUID to, double amount, String reason) {
        return dispatch(p -> p.transfer(from, to, amount, reason), amount);
    }

    /** How the active economy prints an amount. Falls back to a bare number with no economy. */
    public static String format(double amount) {
        return provider().map(p -> p.format(amount)).orElseGet(() -> String.valueOf(amount));
    }

    public static Optional<List<EconomyProvider.AccountSnapshot>> top(int limit) {
        return provider().flatMap(p -> p.top(limit));
    }

    /**
     * Run an operation on the active provider, turning "no economy" into a refusal and a throwing
     * provider into a logged refusal. A foreign ledger blowing up must not take the caller's
     * feature down with it — the bounty still counted, the skill still fired.
     */
    private static TransactionResult dispatch(
            java.util.function.Function<EconomyProvider, TransactionResult> op, double amount) {
        Optional<EconomyProvider> active = provider();
        if (active.isEmpty()) {
            return TransactionResult.fail(TransactionResult.Failure.REFUSED, amount, 0.0D);
        }
        try {
            return op.apply(active.get());
        } catch (RuntimeException e) {
            LOG.error("Standards: economy provider '{}' threw; treating the transaction as refused",
                    active.get().name(), e);
            return TransactionResult.fail(TransactionResult.Failure.REFUSED, amount, 0.0D);
        }
    }

    private Economy() {}
}
