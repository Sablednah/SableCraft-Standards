package com.sablednah.standards.neoforge.permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Afk;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Moving players up a rank ladder on their own — guest to regular, and whatever comes after.
 *
 * <h2>Two clocks, because they answer different questions</h2>
 *
 * <p>A rule may wait on either, or both:</p>
 *
 * <ul>
 * <li><b>Real time</b> since we first saw them. "Come back tomorrow." A few minutes of wall clock
 *     is enough to lose the fly-by griefer who is on somebody else's server by now, and it asks
 *     for nothing but patience.</li>
 * <li><b>Played time</b>, counted only while they are online and <em>not away</em>. "Show me you
 *     have actually done something." Vanilla's own {@code PLAY_TIME} statistic counts a player
 *     idling in a corner all night, which is exactly the promotion an admin did not want to give.
 *     Standards already knows who is AFK, so this number means what it says.</li>
 * </ul>
 *
 * <p>Give both and <b>both</b> must be satisfied — "around since yesterday <em>and</em> has played
 * an hour" is the useful reading, and a rule that fired on whichever came first would make the
 * stricter half decorative.</p>
 *
 * <h2>The syntax</h2>
 *
 * <pre>
 *   guest -&gt; regular after 24h
 *   guest -&gt; regular after 2h played
 *   guest -&gt; regular after 24h and 2h played
 *   regular -&gt; trusted after 7d and 10h played
 * </pre>
 *
 * <p>Durations go through the same {@link Duration} parser as {@code /tempban} and {@code /mute},
 * so {@code 90m}, {@code 36h} and {@code 2w} all work and mean the same thing they do everywhere
 * else in the mod.</p>
 *
 * <h2>Conditions are a seam, not a fixed pair</h2>
 *
 * <p>{@link Rule#satisfiedBy} is the only place that decides whether somebody has qualified, and
 * it reads plain numbers off {@link StandardsData}. Another trigger — a vote, a purchase confirmed
 * by a website, a moderator's approval — is another condition in the same shape rather than a
 * second system. Nothing here assumes time is the only thing that can promote a player.</p>
 */
public final class Promotions {

    /** Checked this often. Promotions are not urgent, and this walks every online player. */
    private static final long CHECK_INTERVAL_TICKS = 20L * 60L;

    /** Minute counting is the same cadence, which makes both cheap to reason about. */
    private static long ticks;

    /**
     * One config line, parsed.
     *
     * @param from        the group they must currently be in
     * @param to          the group they move to
     * @param realSeconds wall clock since first seen, or 0 for "do not check"
     * @param playedSeconds active minutes, as seconds for consistency, or 0 for "do not check"
     */
    public record Rule(String from, String to, long realSeconds, long playedSeconds) {

        /** Whether this player has earned the move. Both clocks must pass if both were given. */
        public boolean satisfiedBy(StandardsData data, UUID player, long nowMillis) {
            if (realSeconds > 0) {
                Optional<Long> since = data.firstSeen(player);
                // No record means we have never seen them start — which is true of anybody who
                // played before this bookkeeping existed. Treating that as 'not yet' would freeze
                // them out of every real-time rule forever, so it is recorded on login instead
                // and only genuinely new players wait.
                if (since.isEmpty() || nowMillis - since.get() < realSeconds * 1000L) {
                    return false;
                }
            }
            if (playedSeconds > 0 && data.playedMinutes(player) * 60L < playedSeconds) {
                return false;
            }
            // A rule with neither clock set never fires. Deliberate: an unparseable line should
            // promote nobody rather than promote everybody the moment the server starts.
            return realSeconds > 0 || playedSeconds > 0;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder(from).append(" -> ").append(to).append(" after ");
            if (realSeconds > 0) {
                sb.append(Duration.describe(realSeconds));
            }
            if (playedSeconds > 0) {
                sb.append(realSeconds > 0 ? " and " : "")
                        .append(Duration.describe(playedSeconds)).append(" played");
            }
            return sb.toString();
        }
    }

    /**
     * Parse one line. Forgiving about spacing, strict about shape.
     *
     * @return empty, having logged why, if the line makes no sense — a typo in one rule must not
     *         take the others down with it
     */
    public static Optional<Rule> parse(String raw) {
        String line = raw == null ? "" : raw.trim();
        int arrow = line.indexOf("->");
        int after = line.toLowerCase(Locale.ROOT).indexOf(" after ");
        if (arrow < 0 || after < arrow) {
            Standards.LOGGER.error("Promotion rule '{}' is not '<from> -> <to> after <time>'", raw);
            return Optional.empty();
        }
        String from = line.substring(0, arrow).trim();
        String to = line.substring(arrow + 2, after).trim();
        if (from.isEmpty() || to.isEmpty()) {
            Standards.LOGGER.error("Promotion rule '{}' is missing a group name", raw);
            return Optional.empty();
        }

        long real = 0;
        long played = 0;
        for (String term : line.substring(after + " after ".length()).split("(?i)\\band\\b")) {
            String piece = term.trim();
            if (piece.isEmpty()) {
                continue;
            }
            boolean isPlayed = piece.toLowerCase(Locale.ROOT).endsWith("played");
            if (isPlayed) {
                piece = piece.substring(0, piece.length() - "played".length()).trim();
            }
            Optional<Long> seconds = Duration.parse(piece);
            if (seconds.isEmpty() || seconds.get() <= 0) {
                Standards.LOGGER.error("Promotion rule '{}' has an unreadable duration '{}'",
                        raw, piece);
                return Optional.empty();
            }
            if (isPlayed) {
                played = seconds.get();
            } else {
                real = seconds.get();
            }
        }
        if (real == 0 && played == 0) {
            Standards.LOGGER.error("Promotion rule '{}' names no time to wait for", raw);
            return Optional.empty();
        }
        return Optional.of(new Rule(from, to, real, played));
    }

    /** Every valid rule from config, worst lines dropped and logged. */
    public static List<Rule> rules() {
        List<Rule> out = new ArrayList<>();
        for (String line : StandardsConfig.PROMOTIONS.get()) {
            parse(line).ifPresent(out::add);
        }
        return out;
    }

    /**
     * Say at startup whether this can work at all.
     *
     * <p><b>Only when rules are configured.</b> Somebody who has written one wants it to run, and
     * silence is the wrong answer — but a server that has configured nothing does not need to be
     * told about a feature it is not using, and a mod that advertises itself on every start is a
     * mod people stop reading.</p>
     */
    public static void announce(MinecraftServer server) {
        List<Rule> rules = rules();
        if (rules.isEmpty()) {
            return;
        }
        if (!StandardsPermissionHandler.isActive()) {
            Standards.LOGGER.warn("Promotions are configured ({} rule(s)) but NOT AVAILABLE:"
                    + " they move players between Standards' own permission groups, and"
                    + " permissions here are being answered by {}."
                    + " Set permissionHandler = \"standards:permissions\" in neoforge-server.toml"
                    + " to use them. LuckPerms has 'tracks' for the same job.",
                    rules.size(),
                    net.neoforged.neoforge.server.permission.PermissionAPI
                            .getActivePermissionHandler());
            return;
        }
        rules.forEach(rule -> Standards.LOGGER.info("Promotion: {}", rule.describe()));
    }

    /**
     * Count active minutes, then promote anybody who has earned it.
     *
     * <p>Both on the same slow tick, because they are the same idea at different speeds and
     * splitting them would mean two schedules to keep honest.</p>
     */
    public static void tick(MinecraftServer server) {
        if (++ticks < CHECK_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        if (!StandardsConfig.ENABLE_PROMOTIONS.get()) {
            return;
        }

        StandardsData data = StandardsData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // The minute they just spent, if they were actually here for it.
            if (!Afk.isAway(player)) {
                data.addPlayedMinutes(player.getUUID(), 1L);
            }
        }

        if (!StandardsPermissionHandler.isActive()) {
            return; // nothing to move them between
        }
        List<Rule> rules = rules();
        if (rules.isEmpty()) {
            return;
        }
        PermissionStore store = PermissionStore.get(server);
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            apply(store, data, player, rules, now);
        }
    }

    /**
     * Move one player up, at most one rung per check.
     *
     * <p>One at a time on purpose: a player who has been away for a year would otherwise climb the
     * whole ladder in a single tick, and arrive to four promotion messages and a rank nobody
     * watched them earn. A rung a minute is still fast enough that nobody notices the wait.</p>
     */
    private static void apply(PermissionStore store, StandardsData data, ServerPlayer player,
            List<Rule> rules, long now) {
        promote(store, data, player.getUUID(), rules, now).ifPresent(to -> {
            Feedback.chat(player, Lang.fmt("msg.perm.promoted", "name", to));
            Standards.LOGGER.info("Promoted {} to {}", player.getName().getString(), to);
            // Their command tree changed with their groups — without this the newly available
            // commands render red until they reconnect. See PermissionCommands.refresh.
            player.level().getServer().getCommands().sendCommands(player);
        });
    }

    /**
     * The move itself, by id and with no player needed.
     *
     * <p>Split out from {@link #apply} so it can be tested against the real store. It is the
     * load-bearing half — the parser deciding a rule is <em>satisfied</em> means nothing if the
     * group membership does not actually change — and a headless self-test has no
     * {@link ServerPlayer} to drive the other version with.</p>
     *
     * @return the group they were moved to, or empty if nothing applied
     */
    public static Optional<String> promote(PermissionStore store, StandardsData data, UUID id,
            List<Rule> rules, long now) {
        for (Rule rule : rules) {
            boolean inFrom = store.groupsOf(id).stream()
                    .anyMatch(g -> g.equalsIgnoreCase(rule.from()));
            // The target has to exist. A rule naming a group nobody created would otherwise take
            // somebody OUT of guest and put them nowhere, which is a promotion to less than they
            // had.
            if (!inFrom || store.group(rule.to()).isEmpty()) {
                continue;
            }
            if (!rule.satisfiedBy(data, id, now)) {
                continue;
            }
            store.removeFromGroup(id, rule.from());
            store.addToGroup(id, rule.to());
            return store.group(rule.to()).map(PermissionStore.Entry::name).or(() ->
                    Optional.of(rule.to()));
        }
        return Optional.empty();
    }

    private Promotions() {}
}
