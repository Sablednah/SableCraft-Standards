package com.sablednah.standards.neoforge.permissions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Who is in which permission group, and what each group grants.
 *
 * <p>Saved data on the overworld, like homes and balances, and for the same reason:
 * <b>offline answers</b>. {@code getOfflinePermission} is not a curiosity — Standards itself uses
 * the offline path for {@code /eco give} to a sleeping player, for {@code /baltop} rows and for
 * home-limit checks on somebody who is not here. Membership held in a player attachment would be
 * unloaded at exactly the moment those questions are asked, and a permissions system that answers
 * "no" for everyone who logged off is worse than none.</p>
 *
 * <p>Everything is keyed by UUID, and group names are matched case-insensitively while keeping the
 * capitalisation they were created with — an admin who types {@code Moderator} once and
 * {@code moderator} the next day means the same group both times.</p>
 */
public final class PermissionStore extends SavedData {

    /**
     * One group.
     *
     * @param name    as it was typed; the lookup key is this lowercased
     * @param tag     a short chat label, or empty. Rendered only if an owner adds
     *                {@code standards:role} to {@code chat.groupTagKinds} — see {@link PermissionRoles}
     * @param parents inherited from; a node found here beats one found in a parent
     * @param nodes   node name (or wildcard) to true/false
     */
    public record Entry(String name, String tag, List<String> parents, Map<String, Boolean> nodes) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Codec.STRING.optionalFieldOf("tag", "").forGetter(Entry::tag),
                Codec.STRING.listOf().optionalFieldOf("parents", List.of()).forGetter(Entry::parents),
                Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("nodes", Map.of())
                        .forGetter(Entry::nodes))
                .apply(i, Entry::new));
    }

    /** One player's own grants and memberships. Absent entirely for a player nobody has touched. */
    private record Holder(UUID player, List<String> groups, Map<String, Boolean> nodes) {
        static final Codec<Holder> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Holder::player),
                Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(Holder::groups),
                Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("nodes", Map.of())
                        .forGetter(Holder::nodes))
                .apply(i, Holder::new));
    }

    private record Snapshot(List<Entry> groups, List<Holder> players) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(Snapshot::groups),
                Holder.CODEC.listOf().optionalFieldOf("players", List.of())
                        .forGetter(Snapshot::players))
                .apply(i, Snapshot::new));
    }

    private static final Codec<PermissionStore> CODEC =
            Snapshot.CODEC.xmap(PermissionStore::new, PermissionStore::snapshot);

    public static final SavedDataType<PermissionStore> TYPE =
            new SavedDataType<>("standards_permissions", PermissionStore::new, CODEC, null);

    /** Lowercased name to the group, which remembers how it was capitalised. */
    private final Map<String, Entry> groups = new LinkedHashMap<>();
    private final Map<UUID, Holder> players = new LinkedHashMap<>();

    private PermissionStore() {}

    private PermissionStore(Snapshot snapshot) {
        snapshot.groups().forEach(g -> groups.put(key(g.name()), g));
        snapshot.players().forEach(h -> players.put(h.player(), h));
    }

    private Snapshot snapshot() {
        return new Snapshot(List.copyOf(groups.values()), List.copyOf(players.values()));
    }

    public static PermissionStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // --- reading ---

    public Optional<Entry> group(String name) {
        return Optional.ofNullable(groups.get(key(name)));
    }

    public List<Entry> allGroups() {
        return List.copyOf(groups.values());
    }

    /** The groups this player is directly in, in the order they were added. Never null. */
    public List<String> groupsOf(UUID player) {
        Holder holder = players.get(player);
        return holder == null ? List.of() : holder.groups();
    }

    /** This player's own grants, which sit nearer than any group. Never null. */
    public Map<String, Boolean> nodesOf(UUID player) {
        Holder holder = players.get(player);
        return holder == null ? Map.of() : holder.nodes();
    }

    /**
     * Everyone directly in this group.
     *
     * <p>Direct membership only — somebody in {@code admin} which inherits {@code moderator} is
     * not listed under {@code moderator}, because they are not in it. Inheritance moves
     * <em>nodes</em> down the chain, not people up it, and conflating the two is how
     * {@code /rank group moderator info} ends up listing the whole server.</p>
     */
    public List<UUID> membersOf(String group) {
        String wanted = key(group);
        List<UUID> out = new ArrayList<>();
        players.forEach((uuid, holder) -> {
            if (holder.groups().stream().anyMatch(g -> key(g).equals(wanted))) {
                out.add(uuid);
            }
        });
        return out;
    }

    /** Every player this store has ever been told anything about. For admin listings. */
    public List<UUID> knownPlayers() {
        return List.copyOf(players.keySet());
    }

    // --- groups ---

    /** @return false if a group by that name already exists */
    public boolean createGroup(String name) {
        if (groups.containsKey(key(name))) {
            return false;
        }
        groups.put(key(name), new Entry(name, "", List.of(), Map.of()));
        setDirty();
        return true;
    }

    /**
     * Delete a group, and take it out of everybody who was in it and everything that inherited it.
     *
     * <p>The cleanup is the point. A dangling parent reference resolves to nothing, so the symptom
     * is a group quietly granting less than its {@code info} screen implies — exactly the kind of
     * thing nobody finds for a month.</p>
     */
    public boolean deleteGroup(String name) {
        if (groups.remove(key(name)) == null) {
            return false;
        }
        String wanted = key(name);
        for (Map.Entry<String, Entry> e : new LinkedHashMap<>(groups).entrySet()) {
            Entry group = e.getValue();
            if (group.parents().stream().anyMatch(p -> key(p).equals(wanted))) {
                List<String> parents = new ArrayList<>(group.parents());
                parents.removeIf(p -> key(p).equals(wanted));
                groups.put(e.getKey(), new Entry(group.name(), group.tag(),
                        List.copyOf(parents), group.nodes()));
            }
        }
        for (Map.Entry<UUID, Holder> e : new LinkedHashMap<>(players).entrySet()) {
            Holder holder = e.getValue();
            if (holder.groups().stream().anyMatch(g -> key(g).equals(wanted))) {
                List<String> mine = new ArrayList<>(holder.groups());
                mine.removeIf(g -> key(g).equals(wanted));
                store(e.getKey(), new Holder(holder.player(), List.copyOf(mine), holder.nodes()));
            }
        }
        setDirty();
        return true;
    }

    /** Set a node on a group, or clear it with {@code null}. @return false if there is no such group */
    public boolean setGroupNode(String group, String node, Boolean value) {
        Entry entry = groups.get(key(group));
        if (entry == null) {
            return false;
        }
        Map<String, Boolean> nodes = new LinkedHashMap<>(entry.nodes());
        if (value == null) {
            nodes.remove(node);
        } else {
            nodes.put(node, value);
        }
        groups.put(key(group), new Entry(entry.name(), entry.tag(), entry.parents(),
                Map.copyOf(nodes)));
        setDirty();
        return true;
    }

    public boolean setGroupTag(String group, String tag) {
        Entry entry = groups.get(key(group));
        if (entry == null) {
            return false;
        }
        groups.put(key(group), new Entry(entry.name(), tag, entry.parents(), entry.nodes()));
        setDirty();
        return true;
    }

    /**
     * Add a parent, refusing a cycle.
     *
     * <p>Not a nicety: resolution walks the parent chain, so {@code a} inheriting {@code b}
     * inheriting {@code a} is an infinite loop on the permission check path — which runs on every
     * command parse and every tab-complete. Refusing at the edit is the only place it can be said
     * usefully, because by the time it hangs there is nothing left to read.</p>
     *
     * @return false if there is no such group, the parent does not exist, or it would make a cycle
     */
    public boolean addParent(String group, String parent) {
        Entry entry = groups.get(key(group));
        if (entry == null || !groups.containsKey(key(parent)) || key(group).equals(key(parent))) {
            return false;
        }
        if (inherits(parent, group)) {
            return false;
        }
        if (entry.parents().stream().anyMatch(p -> key(p).equals(key(parent)))) {
            return false;
        }
        List<String> parents = new ArrayList<>(entry.parents());
        parents.add(groups.get(key(parent)).name());
        groups.put(key(group), new Entry(entry.name(), entry.tag(), List.copyOf(parents),
                entry.nodes()));
        setDirty();
        return true;
    }

    public boolean removeParent(String group, String parent) {
        Entry entry = groups.get(key(group));
        if (entry == null) {
            return false;
        }
        List<String> parents = new ArrayList<>(entry.parents());
        if (!parents.removeIf(p -> key(p).equals(key(parent)))) {
            return false;
        }
        groups.put(key(group), new Entry(entry.name(), entry.tag(), List.copyOf(parents),
                entry.nodes()));
        setDirty();
        return true;
    }

    /** Whether {@code group} already inherits {@code ancestor}, directly or through a chain. */
    public boolean inherits(String group, String ancestor) {
        return inherits(group, key(ancestor), new java.util.HashSet<>());
    }

    private boolean inherits(String group, String wanted, java.util.Set<String> seen) {
        Entry entry = groups.get(key(group));
        if (entry == null || !seen.add(key(group))) {
            return false;
        }
        for (String parent : entry.parents()) {
            if (key(parent).equals(wanted) || inherits(parent, wanted, seen)) {
                return true;
            }
        }
        return false;
    }

    // --- players ---

    private void store(UUID player, Holder holder) {
        // A holder with nothing in it is not worth persisting: the file would grow by a row every
        // time somebody was added to a group and taken out again.
        if (holder.groups().isEmpty() && holder.nodes().isEmpty()) {
            players.remove(player);
        } else {
            players.put(player, holder);
        }
    }

    private Holder holder(UUID player) {
        return players.getOrDefault(player, new Holder(player, List.of(), Map.of()));
    }

    /** @return false if there is no such group, or they are already in it */
    public boolean addToGroup(UUID player, String group) {
        Entry entry = groups.get(key(group));
        if (entry == null) {
            return false;
        }
        Holder holder = holder(player);
        if (holder.groups().stream().anyMatch(g -> key(g).equals(key(group)))) {
            return false;
        }
        List<String> mine = new ArrayList<>(holder.groups());
        mine.add(entry.name());
        store(player, new Holder(player, List.copyOf(mine), holder.nodes()));
        setDirty();
        return true;
    }

    public boolean removeFromGroup(UUID player, String group) {
        Holder holder = holder(player);
        List<String> mine = new ArrayList<>(holder.groups());
        if (!mine.removeIf(g -> key(g).equals(key(group)))) {
            return false;
        }
        store(player, new Holder(player, List.copyOf(mine), holder.nodes()));
        setDirty();
        return true;
    }

    /** Set a node on a player, or clear it with {@code null}. @return false if nothing changed */
    public boolean setPlayerNode(UUID player, String node, Boolean value) {
        Holder holder = holder(player);
        Map<String, Boolean> nodes = new LinkedHashMap<>(holder.nodes());
        boolean changed = value == null
                ? nodes.remove(node) != null
                : !java.util.Objects.equals(nodes.put(node, value), value);
        if (!changed) {
            return false;
        }
        store(player, new Holder(player, holder.groups(), Map.copyOf(nodes)));
        setDirty();
        return true;
    }

    /** For the boot log, so an empty store is distinguishable from one nothing has read. */
    public String summary() {
        return allGroups().size() + " group(s), " + players.size() + " player record(s)";
    }
}
