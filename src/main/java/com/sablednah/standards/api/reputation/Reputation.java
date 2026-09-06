package com.sablednah.standards.api.reputation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * What people think of you — the third seam, after the economy and chat.
 *
 * <h2>Why Standards owns this</h2>
 *
 * <p>Two consumers asked for it independently: Chronicler wants a reputation reward and an
 * availability condition on quests, and LegendQuest's StoryTeller wants
 * {@code /st reward <player> rep <standing> <n>} beside xp, karma and money. Two mods wanting to
 * grant the same fact is exactly the argument that made the economy live here rather than in
 * whichever mod needed it first.</p>
 *
 * <h2>It works with nobody registered</h2>
 *
 * <p>Every call degrades to a clean answer rather than an exception: {@link #get} returns zero,
 * {@link #adjust} returns zero and changes nothing, {@link #standings} is empty. A server with no
 * reputation provider is not a broken server, it is a server where nobody has an opinion — and a
 * quest mod compiled against this should be able to call it without asking first.</p>
 *
 * <p>Not synchronised beyond registration. Providers are registered at setup and read from the
 * server thread; a provider doing its own threading owns that problem, and a lock here would only
 * paper over it. Same reasoning as {@code Economy}.</p>
 */
public final class Reputation {

    private static final Logger LOG = LogUtils.getLogger();

    /** Every registered provider, highest priority first. The head is the one that holds opinion. */
    private static final List<ReputationProvider> PROVIDERS = new ArrayList<>();

    /**
     * Offer a store. Call during {@code FMLCommonSetupEvent}, guarded by a {@code standards} loaded
     * check. Highest {@link ReputationProvider#priority()} wins; ties keep the earlier
     * registration, which at least makes the outcome depend on load order rather than on nothing.
     */
    public static synchronized void register(ReputationProvider provider) {
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparingInt(ReputationProvider::priority).reversed());
        LOG.info("Standards: reputation provider '{}' registered (priority {}); '{}' now holds it",
                provider.name(), provider.priority(), PROVIDERS.getFirst().name());
    }

    /** The provider currently answering, if any. */
    public static synchronized Optional<ReputationProvider> provider() {
        return PROVIDERS.isEmpty() ? Optional.empty() : Optional.of(PROVIDERS.getFirst());
    }

    /** Every registered provider, for diagnostics. */
    public static synchronized List<ReputationProvider> all() {
        return List.copyOf(PROVIDERS);
    }

    /** Is anybody holding reputation? False means "nobody has opinions here", not an error. */
    public static boolean isAvailable() {
        return provider().isPresent();
    }

    // --- operations ---

    /** This player's standing. Zero with no provider, and zero is a real answer: no opinion. */
    public static int get(UUID player, String standing) {
        return provider().map(p -> p.get(player, key(standing))).orElse(0);
    }

    /**
     * Move a standing, and get back where it landed after clamping.
     *
     * <p>Returning the result rather than void is what lets a caller tell "they went up by five"
     * from "they were already at the ceiling", which is the difference between a message worth
     * printing and one that reads as a lie.</p>
     */
    public static int adjust(UUID player, String standing, int delta, String reason) {
        return provider().map(p -> p.adjust(player, key(standing), delta, reason)).orElse(0);
    }

    /** Put a standing at an exact value. Returns where it landed. */
    public static int set(UUID player, String standing, int value, String reason) {
        return provider().map(p -> p.set(player, key(standing), value, reason)).orElse(0);
    }

    /** Every standing this player has an opinion recorded for. */
    public static Map<String, Integer> of(UUID player) {
        return provider().map(p -> p.of(player)).orElse(Map.of());
    }

    /** Every standing name that exists. Created on first use; there is no registry to populate. */
    public static List<String> standings() {
        return provider().map(ReputationProvider::standings).orElse(List.of());
    }

    /** The best {@code limit} players in one standing. */
    public static List<ReputationProvider.Entry> top(String standing, int limit) {
        return provider().map(p -> p.top(key(standing), limit)).orElse(List.of());
    }

    /**
     * A word for a value — for text a player reads, and nothing else.
     *
     * <p>Empty when the server has configured no bands, which is a perfectly ordinary state: a
     * caller must have something to say when there is no word, and the number always works.</p>
     *
     * <p><b>Never branch on the result.</b> Use it in prose — "the survivors now consider you
     * {band}" — and use {@link ReputationEvent#crossed(int)} for logic. A quest keying off
     * {@code "friendly"} breaks the day an owner renames it.</p>
     */
    public static Optional<String> band(String standing, int value) {
        return provider().flatMap(p -> p.band(key(standing), value));
    }

    /**
     * The one piece of normalisation the facade does.
     *
     * <p>Standings are created on first use and their names come from quest files typed by hand, so
     * {@code The_Hospital} and {@code the_hospital} would otherwise be two different groups with
     * two different opinions and no way to notice. Lower-cased and trimmed here, once, rather than
     * in every provider — a provider that forgot would produce a bug visible only to whoever typed
     * the capital.</p>
     */
    private static String key(String standing) {
        return standing == null ? "" : standing.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Exposed for the self-test and for a provider that wants to agree with us. */
    public static String normalise(String standing) {
        return key(standing);
    }

    private Reputation() {}
}
