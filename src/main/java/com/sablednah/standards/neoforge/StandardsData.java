package com.sablednah.standards.neoforge;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The server's ledger: homes, warps, and money.
 *
 * <p>All of it lives in world save data on the overworld rather than on the player entity, and the
 * reason is offline access. {@code /eco give Steve 500} while Steve is asleep, {@code /baltop},
 * {@code /home Steve} as an admin cleaning up a grief — every one of those is a question about a
 * player who is not online, and player attachments simply are not loaded then. Per-session state
 * that genuinely belongs to the player is on the attachment instead (see {@link PlayerState}).</p>
 *
 * <p>A name cache rides along, updated on every login, so those offline lookups can be typed as a
 * name instead of a UUID. It is a convenience, never an identity: everything is keyed by UUID, so
 * a player who changes their name keeps their money and their homes.</p>
 */
public final class StandardsData extends SavedData {

    // --- wire records ---

    /** One player's homes. A list, not a map, so the file stays readable and diffable. */
    private record HomeSet(UUID owner, Map<String, Waypoint> homes) {
        static final Codec<HomeSet> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(HomeSet::owner),
                Codec.unboundedMap(Codec.STRING, Waypoint.CODEC).fieldOf("homes").forGetter(HomeSet::homes))
                .apply(i, HomeSet::new));
    }

    /** Where a player was standing when they logged out, and when. For /tpoffline and /seen. */
    private record LastSeen(UUID player, Waypoint where, long at) {
        static final Codec<LastSeen> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(LastSeen::player),
                Waypoint.CODEC.fieldOf("where").forGetter(LastSeen::where),
                Codec.LONG.optionalFieldOf("at", 0L).forGetter(LastSeen::at))
                .apply(i, LastSeen::new));
    }

    private record NamedWarp(String name, Waypoint where) {
        static final Codec<NamedWarp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(NamedWarp::name),
                Waypoint.CODEC.fieldOf("where").forGetter(NamedWarp::where))
                .apply(i, NamedWarp::new));
    }

    private record Snapshot(List<HomeSet> homes, List<NamedWarp> warps,
                            Map<UUID, Double> accounts, Map<UUID, String> names,
                            Optional<Waypoint> spawn, List<LastSeen> lastSeen,
                            Map<UUID, String> nicks, Map<UUID, Long> firstSeen,
                            Map<UUID, Long> playedMinutes, Map<String, String> powerTools) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                HomeSet.CODEC.listOf().optionalFieldOf("homes", List.of()).forGetter(Snapshot::homes),
                NamedWarp.CODEC.listOf().optionalFieldOf("warps", List.of()).forGetter(Snapshot::warps),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE)
                        .optionalFieldOf("accounts", Map.of()).forGetter(Snapshot::accounts),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                        .optionalFieldOf("names", Map.of()).forGetter(Snapshot::names),
                Waypoint.CODEC.optionalFieldOf("spawn").forGetter(Snapshot::spawn),
                LastSeen.CODEC.listOf().optionalFieldOf("lastSeen", List.of())
                        .forGetter(Snapshot::lastSeen),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                        .optionalFieldOf("nicks", Map.of()).forGetter(Snapshot::nicks),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                        .optionalFieldOf("firstSeen", Map.of()).forGetter(Snapshot::firstSeen),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                        .optionalFieldOf("playedMinutes", Map.of())
                        .forGetter(Snapshot::playedMinutes),
                Codec.unboundedMap(Codec.STRING, Codec.STRING)
                        .optionalFieldOf("powerTools", Map.of())
                        .forGetter(Snapshot::powerTools))
                .apply(i, Snapshot::new));
    }

    private static final Codec<StandardsData> CODEC =
            Snapshot.CODEC.xmap(StandardsData::new, StandardsData::snapshot);

    public static final SavedDataType<StandardsData> TYPE =
            new SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            "standards", "data"), StandardsData::new, CODEC, null);

    // --- live state ---

    /** owner → (lowercased home name → where). The display name is kept in the waypoint's key. */
    private final Map<UUID, Map<String, Waypoint>> homes = new LinkedHashMap<>();
    /** lowercased warp name → the warp, which remembers how it was capitalised. */
    private final Map<String, NamedWarp> warps = new LinkedHashMap<>();
    private final Map<UUID, Double> accounts = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, LastSeen> lastSeen = new LinkedHashMap<>();
    /** Chosen display names. Absent for anybody who has not set one — most players. */
    private final Map<UUID, String> nicks = new LinkedHashMap<>();
    /** When we first saw each player, in epoch millis. The wall clock a promotion can wait on. */
    private final Map<UUID, Long> firstSeen = new LinkedHashMap<>();
    /** Minutes each player has been online and NOT away. See {@code Promotions}. */
    private final Map<UUID, Long> playedMinutes = new LinkedHashMap<>();
    /**
     * {@code "uuid|item id"} to the command bound to it.
     *
     * <p>A composite key rather than a nested map, which is the trick {@code StandardsGroups} uses
     * for its homes and invites: a map of maps needs a codec for the inner one and reads worse in
     * the saved file for no gain.</p>
     */
    private final Map<String, String> powerTools = new LinkedHashMap<>();
    private Waypoint spawn;

    private StandardsData() {}

    private StandardsData(Snapshot snapshot) {
        snapshot.homes().forEach(set -> homes.put(set.owner(), new LinkedHashMap<>(set.homes())));
        snapshot.warps().forEach(w -> warps.put(w.name().toLowerCase(Locale.ROOT), w));
        accounts.putAll(snapshot.accounts());
        names.putAll(snapshot.names());
        spawn = snapshot.spawn().orElse(null);
        snapshot.lastSeen().forEach(e -> lastSeen.put(e.player(), e));
        nicks.putAll(snapshot.nicks());
        firstSeen.putAll(snapshot.firstSeen());
        playedMinutes.putAll(snapshot.playedMinutes());
        powerTools.putAll(snapshot.powerTools());
    }

    private Snapshot snapshot() {
        List<HomeSet> homeSets = homes.entrySet().stream()
                .map(e -> new HomeSet(e.getKey(), Map.copyOf(e.getValue())))
                .toList();
        return new Snapshot(homeSets, List.copyOf(warps.values()),
                Map.copyOf(accounts), Map.copyOf(names), Optional.ofNullable(spawn),
                List.copyOf(lastSeen.values()), Map.copyOf(nicks),
                Map.copyOf(firstSeen), Map.copyOf(playedMinutes), Map.copyOf(powerTools));
    }

    /** The single instance for this save. Stored on the overworld so there is one ledger. */
    public static StandardsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // --- names ---

    /** Remember what this player is currently called, for offline lookups and /baltop rows. */
    public void rememberName(ServerPlayer player) {
        String current = player.getName().getString();
        if (!current.equals(names.get(player.getUUID()))) {
            names.put(player.getUUID(), current);
            setDirty();
        }
    }

    /**
     * Remember a name for somebody who is not here.
     *
     * <p>For anything holding a player identity the server has never seen log in — an imported
     * ledger, a seeded test world. Without it every offline lookup answers with a truncated UUID,
     * and a feature that reads correctly for real players quietly reads as gibberish for the
     * rest.</p>
     */
    public void rememberName(UUID player, String name) {
        if (!name.equals(names.get(player))) {
            names.put(player, name);
            setDirty();
        }
    }

    public Optional<String> nameOf(UUID player) {
        return Optional.ofNullable(names.get(player));
    }

    /**
     * Look up a player by name, online or not. Online players are checked first so a name that
     * has been reused resolves to the person actually holding it.
     */
    public Optional<UUID> byName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getUUID());
        return names.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Every name we have ever seen, for command suggestions that include offline players. */
    public List<String> knownNames() {
        return names.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    // --- how long they have been around ---

    /**
     * Record the first time we ever saw this player, if we have not already.
     *
     * <p>The wall clock a promotion rule can wait on — "come back tomorrow" rather than "play for
     * an hour". Both are wanted and they measure different things: a few minutes of real time is
     * enough to lose the fly-by griefer who is somewhere else by now, while played time is what
     * you ask for when you want somebody to have actually done something.</p>
     *
     * <p>Written once and never updated, so it survives a rename and means what it says.</p>
     */
    public void rememberFirstSeen(UUID player, long whenMillis) {
        if (firstSeen.putIfAbsent(player, whenMillis) == null) {
            setDirty();
        }
    }

    /** Epoch millis, or empty for somebody who predates this bookkeeping. */
    public Optional<Long> firstSeen(UUID player) {
        return Optional.ofNullable(firstSeen.get(player));
    }

    /** Everybody we have counted a minute for, for the playtime board. */
    public List<UUID> playersWithPlaytime() {
        return List.copyOf(playedMinutes.keySet());
    }

    /** Minutes online and not away. Zero for anybody we have not counted yet. */
    public long playedMinutes(UUID player) {
        return playedMinutes.getOrDefault(player, 0L);
    }

    /**
     * Add active minutes.
     *
     * <p>Counted rather than read from vanilla's {@code PLAY_TIME} statistic, and that is the
     * whole point: vanilla counts a player standing still in a corner all night. A promotion that
     * fires for somebody who idled through it defeats the gate it was put there to be. Standards
     * already knows who is away, so the number here means what an admin thinks it means.</p>
     */
    public void addPlayedMinutes(UUID player, long minutes) {
        if (minutes <= 0) {
            return;
        }
        playedMinutes.merge(player, minutes, Long::sum);
        setDirty();
    }

    /**
     * Forget both clocks for a player.
     *
     * <p>For the self-test, which otherwise poisons its own next run: these numbers are
     * <em>persisted</em>, so minutes added by one run are still there for the following one and
     * the "not yet qualified" assertions start out already qualified. That is precisely how this
     * was found — the test failed on its second run having passed on its first.</p>
     *
     * <p>Also the honest answer for an admin who wants somebody's ladder reset.</p>
     */
    public void forgetTiming(UUID player) {
        boolean changed = firstSeen.remove(player) != null;
        changed |= playedMinutes.remove(player) != null;
        if (changed) {
            setDirty();
        }
    }

    // --- power tools ---

    private static String toolKey(UUID player, String itemId) {
        return player + "|" + itemId;
    }

    /** The command bound to this item for this player, if any. */
    public Optional<String> powerTool(UUID player, String itemId) {
        return Optional.ofNullable(powerTools.get(toolKey(player, itemId)));
    }

    /** Bind, or unbind with {@code null}. @return true if anything changed */
    public boolean setPowerTool(UUID player, String itemId, String command) {
        String key = toolKey(player, itemId);
        boolean changed = command == null
                ? powerTools.remove(key) != null
                : !command.equals(powerTools.put(key, command));
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Every binding this player holds, as item id to command. */
    public Map<String, String> powerToolsOf(UUID player) {
        String prefix = player + "|";
        Map<String, String> out = new LinkedHashMap<>();
        powerTools.forEach((key, command) -> {
            if (key.startsWith(prefix)) {
                out.put(key.substring(prefix.length()), command);
            }
        });
        return out;
    }

    /** @return how many were removed */
    public int clearPowerTools(UUID player) {
        String prefix = player + "|";
        int before = powerTools.size();
        if (powerTools.keySet().removeIf(k -> k.startsWith(prefix))) {
            setDirty();
        }
        return before - powerTools.size();
    }

    // --- nicknames ---

    /**
     * This player's chosen display name, if they have one.
     *
     * <p>Stored here rather than on the player attachment for the usual reason — <b>offline
     * access</b>. {@code /realname} has to answer for somebody who logged off an hour ago, which
     * is exactly when a moderator is asking.</p>
     */
    public Optional<String> nick(UUID player) {
        return Optional.ofNullable(nicks.get(player));
    }

    /**
     * What to call this player: their nickname if set, otherwise their real name.
     *
     * <p>One accessor so chat, {@code /realname} and anything added later cannot disagree about
     * who somebody is.</p>
     */
    public String displayName(UUID player, String realName) {
        return nicks.getOrDefault(player, realName);
    }

    /** Set or clear a nickname. Pass {@code null} to clear. */
    public void setNick(UUID player, String nick) {
        if (nick == null || nick.isBlank()) {
            if (nicks.remove(player) != null) {
                setDirty();
            }
            return;
        }
        nicks.put(player, nick);
        setDirty();
    }

    /**
     * Who is using this nickname, ignoring colour codes and case.
     *
     * <p>Compared on the <em>stripped</em> text, because {@code &cBob} and {@code &9Bob} read as
     * the same word to every human in the chat window and the whole point of uniqueness is what a
     * reader can tell apart.</p>
     */
    public Optional<UUID> byNick(String nick) {
        String wanted = Feedback.stripCodes(nick).trim();
        return nicks.entrySet().stream()
                .filter(e -> Feedback.stripCodes(e.getValue()).trim().equalsIgnoreCase(wanted))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Whether this nickname would impersonate somebody.
     *
     * <p><b>The check that makes nicknames safe to have at all.</b> Two separate holes, and both
     * have to be closed here rather than in the command, because a nickname set by an admin or by
     * another mod goes through the same door:</p>
     *
     * <ul>
     * <li><b>Another player's real name.</b> Calling yourself {@code Sablednah} is the whole
     *     attack, and it works on every player who reads chat rather than tab.</li>
     * <li><b>Another player's nickname.</b> Two people rendering as the same word is the same
     *     problem arriving a second way, and a reader has no means of telling which is which.</li>
     * </ul>
     *
     * <p>Both are checked against the <em>name cache</em>, not the online list — an impersonation
     * of somebody who is asleep is the version worth having, since they are not there to object.
     * Taking your own real name back is always allowed.</p>
     *
     * @return the UUID being impersonated, or empty if the nickname is free
     */
    public Optional<UUID> impersonates(UUID chooser, String nick) {
        String wanted = Feedback.stripCodes(nick).trim();
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        Optional<UUID> byRealName = names.entrySet().stream()
                .filter(e -> !e.getKey().equals(chooser) && e.getValue().equalsIgnoreCase(wanted))
                .map(Map.Entry::getKey)
                .findFirst();
        if (byRealName.isPresent()) {
            return byRealName;
        }
        return byNick(wanted).filter(owner -> !owner.equals(chooser));
    }

    /** Every nickname in use, for {@code /realname} suggestions. */
    public List<String> knownNicks() {
        return nicks.values().stream()
                .map(n -> Feedback.stripCodes(n).trim())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    // --- last seen ---

    /**
     * Remember where a player logged out.
     *
     * <p>Recorded on logout rather than continuously: writing save data every time somebody walks
     * would be an absurd cost for a command used a handful of times a week.</p>
     */
    public void rememberLogout(ServerPlayer player) {
        lastSeen.put(player.getUUID(),
                new LastSeen(player.getUUID(), Waypoint.of(player), System.currentTimeMillis()));
        setDirty();
    }

    public Optional<Waypoint> lastPosition(UUID player) {
        return Optional.ofNullable(lastSeen.get(player)).map(LastSeen::where);
    }

    /** When they were last seen, in epoch millis. */
    public Optional<Long> lastSeenAt(UUID player) {
        return Optional.ofNullable(lastSeen.get(player)).map(LastSeen::at);
    }

    /**
     * What actually came off disk, for the boot log.
     *
     * <p>A store that failed to load is an empty store, and an empty store is indistinguishable
     * from a server nobody has played on yet. On a world upgraded across Minecraft versions that
     * is a live possibility rather than a theoretical one — see {@link SaveMigration}, which got
     * its destination wrong once and was believed because it reported copying the file. Saying the
     * numbers out loud once per start is what turns a silent loss into something the owner sees
     * before their players do.</p>
     */
    public String summary() {
        int homeCount = homes.values().stream().mapToInt(Map::size).sum();
        return String.format(
                "%d home(s) across %d player(s), %d warp(s), %d account(s), %d known name(s)",
                homeCount, homes.size(), warps.size(), accounts.size(), names.size());
    }

    // --- homes ---

    public Map<String, Waypoint> homesOf(UUID owner) {
        return Map.copyOf(homes.getOrDefault(owner, Map.of()));
    }

    public Optional<Waypoint> home(UUID owner, String name) {
        Map<String, Waypoint> mine = homes.get(owner);
        if (mine == null) return Optional.empty();
        // Exact first, then case-insensitively: 'Base' and 'base' should be the same home.
        Waypoint exact = mine.get(name);
        if (exact != null) return Optional.of(exact);
        return mine.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** @return true if this replaced an existing home rather than adding one. */
    public boolean setHome(UUID owner, String name, Waypoint where) {
        Map<String, Waypoint> mine = homes.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        String existingKey = mine.keySet().stream()
                .filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(null);
        boolean replaced = existingKey != null;
        if (replaced) mine.remove(existingKey);
        mine.put(name, where);
        setDirty();
        return replaced;
    }

    public boolean deleteHome(UUID owner, String name) {
        Map<String, Waypoint> mine = homes.get(owner);
        if (mine == null) return false;
        String key = mine.keySet().stream()
                .filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (key == null) return false;
        mine.remove(key);
        if (mine.isEmpty()) homes.remove(owner);
        setDirty();
        return true;
    }

    // --- warps ---

    public Optional<Waypoint> warp(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase(Locale.ROOT))).map(NamedWarp::where);
    }

    public List<String> warpNames() {
        return warps.values().stream().map(NamedWarp::name)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** @return true if this replaced an existing warp. */
    public boolean setWarp(String name, Waypoint where) {
        boolean replaced = warps.put(name.toLowerCase(Locale.ROOT), new NamedWarp(name, where)) != null;
        setDirty();
        return replaced;
    }

    public boolean deleteWarp(String name) {
        boolean removed = warps.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) setDirty();
        return removed;
    }

    // --- spawn ---

    public Optional<Waypoint> spawn() {
        return Optional.ofNullable(spawn);
    }

    public void setSpawn(Waypoint where) {
        this.spawn = where;
        setDirty();
    }

    // --- money ---

    public boolean hasAccount(UUID player) {
        return accounts.containsKey(player);
    }

    public double balance(UUID player) {
        return accounts.getOrDefault(player, 0.0D);
    }

    public void setBalance(UUID player, double amount) {
        accounts.put(player, amount);
        setDirty();
    }

    /** @return false if the account already existed. */
    public boolean createAccount(UUID player, double startingBalance) {
        if (accounts.containsKey(player)) return false;
        accounts.put(player, startingBalance);
        setDirty();
        return true;
    }

    /** Richest first, capped. The name cache fills in who each UUID is where it can. */
    public List<Map.Entry<UUID, Double>> richest(int limit) {
        return accounts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }
}
