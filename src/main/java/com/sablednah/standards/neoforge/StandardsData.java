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
                            Optional<Waypoint> spawn, List<LastSeen> lastSeen) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                HomeSet.CODEC.listOf().optionalFieldOf("homes", List.of()).forGetter(Snapshot::homes),
                NamedWarp.CODEC.listOf().optionalFieldOf("warps", List.of()).forGetter(Snapshot::warps),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE)
                        .optionalFieldOf("accounts", Map.of()).forGetter(Snapshot::accounts),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                        .optionalFieldOf("names", Map.of()).forGetter(Snapshot::names),
                Waypoint.CODEC.optionalFieldOf("spawn").forGetter(Snapshot::spawn),
                LastSeen.CODEC.listOf().optionalFieldOf("lastSeen", List.of())
                        .forGetter(Snapshot::lastSeen))
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
    private Waypoint spawn;

    private StandardsData() {}

    private StandardsData(Snapshot snapshot) {
        snapshot.homes().forEach(set -> homes.put(set.owner(), new LinkedHashMap<>(set.homes())));
        snapshot.warps().forEach(w -> warps.put(w.name().toLowerCase(Locale.ROOT), w));
        accounts.putAll(snapshot.accounts());
        names.putAll(snapshot.names());
        spawn = snapshot.spawn().orElse(null);
        snapshot.lastSeen().forEach(e -> lastSeen.put(e.player(), e));
    }

    private Snapshot snapshot() {
        List<HomeSet> homeSets = homes.entrySet().stream()
                .map(e -> new HomeSet(e.getKey(), Map.copyOf(e.getValue())))
                .toList();
        return new Snapshot(homeSets, List.copyOf(warps.values()),
                Map.copyOf(accounts), Map.copyOf(names), Optional.ofNullable(spawn),
                List.copyOf(lastSeen.values()));
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
