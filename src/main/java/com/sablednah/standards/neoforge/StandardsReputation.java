package com.sablednah.standards.neoforge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.reputation.Reputation;
import com.sablednah.standards.api.reputation.ReputationEvent;
import com.sablednah.standards.api.reputation.ReputationProvider;

/**
 * Standards' own reputation store — the fallback, backed by {@link StandardsData}.
 *
 * <p>Same shape as {@link StandardsEconomy} and for the same reasons: registered at a negative
 * priority so a dedicated mod displaces it, backed by {@code SavedData} so offline players answer,
 * and switchable off entirely for a server that has something better.</p>
 *
 * <p><b>The clamp lives here rather than in the facade.</b> A range is a property of the store — a
 * different provider may want −1000 to 1000, or no ceiling at all — and a facade that clamped
 * would silently overrule it. The consequence worth knowing is that
 * {@link Reputation#adjust} returns where the value <em>landed</em>, so a caller can tell a real
 * change from one the ceiling ate.</p>
 */
public final class StandardsReputation implements ReputationProvider {

    public static final StandardsReputation INSTANCE = new StandardsReputation();

    private StandardsReputation() {}

    /** Called once, from {@code FMLCommonSetupEvent} — config is not loaded any earlier. */
    public static void registerIfEnabled() {
        if (!StandardsConfig.ENABLE_REPUTATION.get()) {
            Standards.LOGGER.info("Standards reputation disabled in config; not registering a provider");
            return;
        }
        Reputation.register(INSTANCE);
    }

    /** Whether our own store is the one answering. Commands check before writing to it directly. */
    public static boolean isActive() {
        return Reputation.provider().map(p -> p == INSTANCE).orElse(false);
    }

    @Override
    public String name() {
        return "Standards";
    }

    @Override
    public int priority() {
        return StandardsConfig.PREFER_OWN_REPUTATION.get() ? Integer.MAX_VALUE : BUILTIN_PRIORITY;
    }

    /**
     * The configured word for a value, or the empty string if the owner wants bare numbers.
     *
     * <p>Parsed on every call rather than cached, because it is only ever reached from a command
     * printing a line to one player — and a cache here would need invalidating on config reload,
     * which is a whole mechanism to save microseconds nobody can perceive.</p>
     *
     * <p>Bands are display only and are deliberately absent from the API. A consumer branching on
     * "friendly" would break the day an owner renamed it; a quest that needs a threshold should say
     * the number it means.</p>
     */
    public static String bandFor(int value) {
        String best = "";
        int bestThreshold = Integer.MIN_VALUE;
        for (String entry : StandardsConfig.REPUTATION_BANDS.get()) {
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                int threshold = Integer.parseInt(entry.substring(0, colon).trim());
                if (value >= threshold && threshold >= bestThreshold) {
                    bestThreshold = threshold;
                    best = entry.substring(colon + 1).trim();
                }
            } catch (NumberFormatException ignored) {
                // A malformed line is skipped rather than fatal: this is cosmetic, and a server
                // that will not start because somebody fat-fingered a band name is a worse
                // outcome than one that prints a number.
            }
        }
        return best;
    }

    private Optional<StandardsData> data() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : Optional.of(StandardsData.get(server));
    }

    @Override
    public int get(UUID player, String standing) {
        return data().map(d -> d.reputation(player, standing)).orElse(0);
    }

    @Override
    public int adjust(UUID player, String standing, int delta, String reason) {
        return write(player, standing, get(player, standing) + delta, reason);
    }

    @Override
    public int set(UUID player, String standing, int value, String reason) {
        return write(player, standing, value, reason);
    }

    /**
     * The one place a standing changes.
     *
     * <p>Both public writes come through here so the clamp and the event cannot get out of step —
     * a second path that forgot the event would produce a reward that works and a reaction that
     * never fires, which is the hardest kind of thing to notice.</p>
     */
    private int write(UUID player, String standing, int wanted, String reason) {
        Optional<StandardsData> data = data();
        if (data.isEmpty() || standing.isEmpty()) {
            return 0;
        }
        int min = StandardsConfig.REPUTATION_MIN.get();
        int max = StandardsConfig.REPUTATION_MAX.get();
        int landed = Math.max(min, Math.min(max, wanted));
        int before = data.get().setReputation(player, standing, landed);
        // Fired even when nothing moved. A listener asking "did they cross the threshold" gets a
        // correct no from getDelta() == 0, and suppressing the event would instead mean every
        // listener needs its own idea of whether a no-op counts.
        NeoForge.EVENT_BUS.post(new ReputationEvent(player, standing, before, landed, reason));
        if (before != landed && StandardsConfig.REPUTATION_LOG.get()) {
            Standards.LOGGER.info("[rep] {} {} {} -> {} ({})",
                    player, standing, before, landed, reason == null ? "" : reason);
        }
        return landed;
    }

    @Override
    public Map<String, Integer> of(UUID player) {
        return data().map(d -> d.reputationOf(player)).orElse(Map.of());
    }

    @Override
    public List<String> standings() {
        return data().map(StandardsData::standings).orElse(List.of());
    }

    @Override
    public List<Entry> top(String standing, int limit) {
        return data().map(d -> d.reputationTop(standing, limit).stream()
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .toList()).orElse(List.of());
    }
}
