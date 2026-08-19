package com.sablednah.standards.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

import com.sablednah.standards.StandardsConfig;

/**
 * Printing and parsing money.
 *
 * <p>Amounts are {@code double} on the API surface because that is what every economy mod worth
 * integrating with uses, and an API that disagrees with its callers about the type of money is
 * an API nobody adopts. Internally, rounding goes through {@link BigDecimal} so that the
 * arithmetic a server owner can actually observe — a balance, a payment, a shop price — lands on
 * exact decimal values rather than {@code 0.30000000000000004}.</p>
 */
public final class Money {

    /** Round to the configured precision. Every balance write goes through this. */
    public static double round(double amount) {
        return round(amount, StandardsConfig.CURRENCY_DECIMALS.get());
    }

    /** The rounding rule, as a pure function of its inputs. See {@link #render}. */
    public static double round(double amount, int decimals) {
        return BigDecimal.valueOf(amount)
                .setScale(decimals, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Money as a player sees it: {@code \u20A125} when a symbol is configured, otherwise
     * {@code 25 credits} with the singular/plural picked correctly (the detail every plugin gets
     * wrong and every player notices). Symbol, its side, the names and the precision are all
     * config, because a server's currency is part of its setting.
     */
    public static String format(double amount) {
        return render(amount,
                StandardsConfig.CURRENCY_DECIMALS.get(),
                StandardsConfig.CURRENCY_SYMBOL.get(),
                StandardsConfig.CURRENCY_SYMBOL_BEFORE.get(),
                StandardsConfig.CURRENCY_SINGULAR.get(),
                StandardsConfig.CURRENCY_PLURAL.get());
    }

    /**
     * The formatting rules, as a pure function of their inputs.
     *
     * <p>Split out from {@link #format} so it can be tested against every combination rather than
     * only against whatever the server happens to be configured for. The self-test caught this the
     * hard way: it asserted the old "coins" default and started failing the moment the default
     * currency changed, which is a test measuring the config rather than the code.</p>
     */
    public static String render(double amount, int decimals, String symbol, boolean symbolBefore,
            String singularName, String pluralName) {
        double rounded = round(amount, decimals);
        String digits = String.format(Locale.ROOT, "%,." + decimals + "f", rounded);
        if (!symbol.isBlank()) {
            return symbolBefore ? symbol + digits : digits + symbol;
        }
        boolean singular = Math.abs(rounded - 1.0D) < 1.0E-9D;
        return digits + " " + (singular ? singularName : pluralName);
    }

    /**
     * Parse what a player typed.
     *
     * <p><b>Anything {@link #format} can produce, this must accept.</b> That is the property worth
     * holding, and the self-test asserts the round trip — it is how this gap was found: the symbol
     * was stripped but the currency <em>name</em> was not, so a server showing "50 credits" would
     * reject {@code /pay Steve 50 credits}, which is precisely what the player sees written on
     * their own balance.</p>
     *
     * @return empty if it is not a number at all — the caller decides what to say about that.
     */
    public static Optional<Double> parse(String input) {
        return parse(input,
                StandardsConfig.CURRENCY_SYMBOL.get(),
                StandardsConfig.CURRENCY_SINGULAR.get(),
                StandardsConfig.CURRENCY_PLURAL.get());
    }

    /** The parsing rules, as a pure function of their inputs. See {@link #render}. */
    public static Optional<Double> parse(String input, String symbol,
            String singularName, String pluralName) {
        String cleaned = input.trim();
        // Plural before singular: stripping "credit" first would leave a stray "s".
        for (String token : new String[] {pluralName, singularName, symbol}) {
            if (token != null && !token.isBlank()) {
                cleaned = cleaned.replace(token, "");
            }
        }
        // Separators go last, so a currency name containing a comma cannot confuse the order.
        cleaned = cleaned.replace(",", "").trim();
        try {
            return Optional.of(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Money() {}
}
