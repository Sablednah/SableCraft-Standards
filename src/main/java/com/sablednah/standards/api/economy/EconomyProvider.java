package com.sablednah.standards.api.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Something that knows what money is.
 *
 * <p>There is no Vault on NeoForge — no abstraction every economy mod implements — and no clear
 * leader among the mods that exist. Standards ships its own ledger because a server needs
 * <em>an</em> answer, but it must not be the kind of mod that assumes it is the only one. So the
 * ledger sits behind this interface, Standards registers itself as one implementation among
 * possibly several, and {@link Economy} decides which one actually holds the money.</p>
 *
 * <p><b>Exactly one provider wins.</b> This is the part worth being careful about: a bounty
 * <em>payer</em> can sensibly be additive (ZombieMod pays into an economy <em>and</em> tallies a
 * scoreboard, because those are two different rewards), but an economy provider answers "what is
 * this player's balance", and two ledgers that disagree about that is strictly worse than either
 * alone. {@link Economy} therefore picks the highest {@link #priority()} and routes everything
 * through it.</p>
 *
 * <h2>Implementing one</h2>
 *
 * <p>Register during mod construction — before any server starts, so the choice is settled before
 * the first command runs:</p>
 *
 * <pre>{@code
 * public MyEconomyMod(IEventBus bus, ModContainer container) {
 *     if (ModList.get().isLoaded("standards")) {
 *         Economy.register(new MyProvider());
 *     }
 * }
 * }</pre>
 *
 * <p>Guard the call with the mod-loaded check (or keep the reference behind a separate class that
 * is only touched inside the branch) so Standards stays a soft dependency. Making a soft
 * dependency mandatory in practice is the single most common way this pattern is got wrong.</p>
 *
 * <h2>Consuming one</h2>
 *
 * <p>Callers should not touch this interface at all — use the {@link Economy} facade, which works
 * whether the money is ours, someone else's, or nobody's.</p>
 */
public interface EconomyProvider {

    /** A short human name for logs and {@code /standards economy info}: "Standards", "Impactor". */
    String name();

    /**
     * Who wins when several providers are registered. Standards' own ledger is
     * {@link #BUILTIN_PRIORITY}, deliberately low: a mod whose whole job is money should outrank
     * the one that ships an economy as a convenience. Return anything above it to take over.
     */
    default int priority() {
        return 0;
    }

    /** Standards' built-in ledger. Anything higher displaces it. */
    int BUILTIN_PRIORITY = -1000;

    /**
     * Does this player have an account yet? Implementations that create accounts lazily should
     * still answer honestly here — {@code /balance} on a player who has never joined is a
     * legitimate question with the answer "no such account".
     */
    boolean hasAccount(UUID player);

    /** Create an account at the starting balance if there is not one. @return false if it existed. */
    boolean createAccount(UUID player);

    /** Current balance. Zero for an account that does not exist. */
    double balance(UUID player);

    /**
     * Add money. {@code amount} must be non-negative; negative amounts are a caller bug and
     * should be refused rather than quietly treated as a withdrawal.
     *
     * @param reason a short audit string ("zombiemod:bounty", "shop:sale") — implementations may
     *               log it, and a server owner chasing where the money went will thank you
     */
    TransactionResult deposit(UUID player, double amount, String reason);

    /** Take money, refusing if the player cannot afford it (unless the ledger permits debt). */
    TransactionResult withdraw(UUID player, double amount, String reason);

    /**
     * Move money between two accounts. The default is a withdraw followed by a deposit, which is
     * correct for any ledger whose operations cannot fail halfway; override it if yours can, so
     * a failed deposit does not vanish the sender's money.
     */
    default TransactionResult transfer(UUID from, UUID to, double amount, String reason) {
        TransactionResult taken = withdraw(from, amount, reason);
        if (!taken.success()) {
            return taken;
        }
        TransactionResult given = deposit(to, amount, reason);
        if (!given.success()) {
            deposit(from, amount, reason + ":refund");
        }
        return given;
    }

    /** How this economy prints an amount. Lets foreign currencies keep their own formatting. */
    String format(double amount);

    /**
     * The richest accounts, highest first, for {@code /baltop}. Optional: a provider that cannot
     * enumerate accounts (one backed by a remote service, say) returns empty and the command
     * says so rather than lying with a partial list.
     */
    default Optional<List<AccountSnapshot>> top(int limit) {
        return Optional.empty();
    }

    /** One row of {@link #top}. The name may be absent for a UUID nobody has seen log in. */
    record AccountSnapshot(UUID player, Optional<String> name, double balance) {}
}
