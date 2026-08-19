package com.sablednah.standards.api.economy;

/**
 * What happened to a transaction.
 *
 * <p>A record rather than a bare boolean because the two things a caller wants after a failed
 * payment — <em>why</em>, and <em>what the balance actually is</em> — are exactly the two things a
 * boolean throws away, and every caller then re-queries for them.</p>
 */
public record TransactionResult(boolean success, double amount, double balance, Failure failure) {

    public enum Failure {
        /** Nothing went wrong. */
        NONE,
        /** The player does not have enough money and the ledger does not permit debt. */
        INSUFFICIENT_FUNDS,
        /** No account for that UUID, and the ledger does not create them on demand. */
        NO_ACCOUNT,
        /** A negative or NaN amount — a caller bug, not a player-facing condition. */
        INVALID_AMOUNT,
        /** The ledger refused for its own reasons (frozen account, remote backend down). */
        REFUSED
    }

    public static TransactionResult ok(double amount, double balance) {
        return new TransactionResult(true, amount, balance, Failure.NONE);
    }

    public static TransactionResult fail(Failure failure, double amount, double balance) {
        return new TransactionResult(false, amount, balance, failure);
    }
}
