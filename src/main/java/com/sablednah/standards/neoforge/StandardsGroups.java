package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;

/**
 * The lightweight groups Standards ships with.
 *
 * <p>Deliberately basic: a name, an owner, some members, some pending invites. No claims, no
 * relations, no ranks — those are gameplay, and gameplay belongs in a mod a server chooses to
 * install. This exists so a small server gets working groups out of the box, the same way the
 * built-in economy ledger exists so {@code /balance} answers something on day one.</p>
 *
 * <p>It registers as a provider of the {@code standards:group} kind through
 * {@link com.sablednah.standards.api.groups.Groups}, which means Standards consumes its own seam
 * rather than reaching past it. That is the point: a seam only one implementation has ever crossed
 * is a seam nobody has tested, and this way the first crossing happens in code we control.</p>
 *
 * <h2>Why SavedData rather than an attachment</h2>
 *
 * <p>Same reason as homes and balances — <b>offline access</b>. "Who is in this group" must be
 * answerable when half of them are asleep, and player attachments are not loaded then. A group
 * that forgets its absent members every restart is not a group.</p>
 */
public final class StandardsGroups extends net.minecraft.world.level.saveddata.SavedData {

    /**
     * @param id      stable, and survives a rename — everything stored elsewhere keys on this
     * @param name    what the players call it, and what they change
     * @param owner   may disband, kick and rename
     * @param members includes the owner
     * @param tag     a short chat label, or empty for none. Optional in the codec so groups
     *                created before tags existed load without one.
     */
    public record Entry(String id, String name, UUID owner, List<UUID> members, String tag) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Entry::owner),
                UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("members", List.of())
                        .forGetter(Entry::members),
                Codec.STRING.optionalFieldOf("tag", "").forGetter(Entry::tag))
                .apply(i, Entry::new));
    }

    /**
     * A shared home, keyed by group rather than by player.
     *
     * <p>Deliberately separate from personal homes rather than making a member's homes visible to
     * the group. "My bedroom" and "our base" are different things, and merging them means nobody
     * can have a private home any more — the sort of trade that only shows up after people have
     * already put things somewhere.</p>
     */
    private record GroupHome(String group, String name, com.sablednah.standards.core.Waypoint where) {
        static final Codec<GroupHome> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("group").forGetter(GroupHome::group),
                Codec.STRING.fieldOf("name").forGetter(GroupHome::name),
                com.sablednah.standards.core.Waypoint.CODEC.fieldOf("where").forGetter(GroupHome::where))
                .apply(i, GroupHome::new));
    }

    private record Invite(String group, UUID player, long at) {
        static final Codec<Invite> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("group").forGetter(Invite::group),
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Invite::player),
                Codec.LONG.fieldOf("at").forGetter(Invite::at))
                .apply(i, Invite::new));
    }

    private record Snapshot(List<Entry> groups, List<Invite> invites, List<GroupHome> homes) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(Snapshot::groups),
                Invite.CODEC.listOf().optionalFieldOf("invites", List.of())
                        .forGetter(Snapshot::invites),
                GroupHome.CODEC.listOf().optionalFieldOf("homes", List.of())
                        .forGetter(Snapshot::homes))
                .apply(i, Snapshot::new));
    }

    private static final Codec<StandardsGroups> CODEC =
            Snapshot.CODEC.xmap(StandardsGroups::new, StandardsGroups::snapshot);

    public static final net.minecraft.world.level.saveddata.SavedDataType<StandardsGroups> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    "standards_groups", StandardsGroups::new, CODEC, null);

    /** Keyed by id, which never changes; the name is just a field. */
    private final Map<String, Entry> groups = new LinkedHashMap<>();
    /** "groupId|uuid" → when it was sent. Invites persist: one lost to a restart is a puzzle. */
    private final Map<String, Long> invites = new LinkedHashMap<>();
    /** "groupId|homename" → where. Shared, so it outlives any one member leaving. */
    private final Map<String, com.sablednah.standards.core.Waypoint> homes = new LinkedHashMap<>();

    private StandardsGroups() {}

    private StandardsGroups(Snapshot snapshot) {
        snapshot.groups().forEach(g -> groups.put(g.id(), g));
        snapshot.invites().forEach(i -> invites.put(key(i.group(), i.player()), i.at()));
        snapshot.homes().forEach(h -> homes.put(homeKey(h.group(), h.name()), h.where()));
    }

    private Snapshot snapshot() {
        List<Invite> out = new ArrayList<>();
        invites.forEach((k, at) -> {
            int split = k.indexOf('|');
            out.add(new Invite(k.substring(0, split), UUID.fromString(k.substring(split + 1)), at));
        });
        List<GroupHome> savedHomes = new ArrayList<>();
        homes.forEach((k, where) -> {
            int split = k.indexOf('|');
            savedHomes.add(new GroupHome(k.substring(0, split), k.substring(split + 1), where));
        });
        return new Snapshot(List.copyOf(groups.values()), out, savedHomes);
    }

    public static StandardsGroups get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(String group, UUID player) {
        return group + "|" + player;
    }

    /**
     * Ids are opaque and unrelated to the name.
     *
     * <p>Deriving one from the other is the obvious shortcut and it breaks the moment somebody
     * renames: either the id moves — and everything storing it is orphaned — or it stays and no
     * longer resembles the group, which is fine until a second group takes the old name and the
     * two collide. An opaque id has neither problem.</p>
     */
    private static String homeKey(String group, String name) {
        return group + "|" + name.toLowerCase(java.util.Locale.ROOT);
    }

    private static String freshId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // --- reading ---

    public Optional<Entry> byId(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    /** By the player-facing name, case-insensitively. Names are unique among live groups. */
    public Optional<Entry> byName(String name) {
        return groups.values().stream()
                .filter(g -> g.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * The group this player is in, if any.
     *
     * <p>Exclusive by design: one group per player. A lightweight system that allowed several
     * would need a way to say which one a shared home belongs to, and answering that question is
     * the point at which it stops being lightweight.</p>
     */
    public Optional<Entry> of(UUID player) {
        return groups.values().stream().filter(g -> g.members().contains(player)).findFirst();
    }

    public List<Entry> all() {
        return List.copyOf(groups.values());
    }

    public boolean isInvited(String groupId, UUID player) {
        Long sent = invites.get(key(groupId, player));
        return sent != null && !expired(sent);
    }

    /**
     * Whether an invite sent at this time has lapsed.
     *
     * <p>Zero means never, which is the default: on a small server an invite that quietly
     * evaporates is more annoying than one that lingers, and neither party is told when it goes.
     * The config exists for servers where they pile up.</p>
     */
    private static boolean expired(long sentAt) {
        int seconds = com.sablednah.standards.StandardsConfig.GROUP_INVITE_TIMEOUT.get();
        return seconds > 0 && System.currentTimeMillis() - sentAt > seconds * 1000L;
    }

    /**
     * Drop lapsed invites. Called from the read paths rather than on a timer — there is no
     * observable difference, and a timer is a thing to keep running correctly for no gain.
     */
    public void pruneInvites() {
        if (invites.entrySet().removeIf(e -> expired(e.getValue()))) {
            setDirty();
        }
    }

    public List<Entry> invitesFor(UUID player) {
        pruneInvites();
        return invites.keySet().stream()
                .filter(k -> k.endsWith("|" + player))
                .map(k -> groups.get(k.substring(0, k.indexOf('|'))))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // --- writing ---

    /** @return the new group, or empty if that name is taken or they are already in one */
    public Optional<Entry> create(String name, UUID owner) {
        if (byName(name).isPresent() || of(owner).isPresent()) {
            return Optional.empty();
        }
        Entry entry = new Entry(freshId(), name, owner, List.of(owner), "");
        groups.put(entry.id(), entry);
        setDirty();
        return Optional.of(entry);
    }

    public void invite(String groupId, UUID player) {
        invites.put(key(groupId, player), System.currentTimeMillis());
        setDirty();
    }

    public void revokeInvite(String groupId, UUID player) {
        if (invites.remove(key(groupId, player)) != null) {
            setDirty();
        }
    }

    /** @return true if they joined; false if the invite was gone or they are already in a group */
    public boolean join(String groupId, UUID player) {
        Entry entry = groups.get(groupId);
        if (entry == null || !isInvited(groupId, player) || of(player).isPresent()) {
            return false;
        }
        List<UUID> members = new ArrayList<>(entry.members());
        members.add(player);
        groups.put(groupId, new Entry(entry.id(), entry.name(), entry.owner(),
                List.copyOf(members), entry.tag()));
        invites.remove(key(groupId, player));
        setDirty();
        return true;
    }

    /**
     * Remove a member. The owner leaving <b>disbands</b> the group.
     *
     * <p>Rather than silently promoting somebody who never asked to run it. If a server wants
     * succession, that is a faction mod's job — and a lightweight group quietly changing hands is
     * a surprise, whereas one that ends when its owner leaves is at least predictable.</p>
     *
     * @return true if anything changed
     */
    public boolean leave(String groupId, UUID player) {
        Entry entry = groups.get(groupId);
        if (entry == null || !entry.members().contains(player)) {
            return false;
        }
        if (entry.owner().equals(player)) {
            return disband(groupId);
        }
        List<UUID> members = new ArrayList<>(entry.members());
        members.remove(player);
        groups.put(groupId, new Entry(entry.id(), entry.name(), entry.owner(),
                List.copyOf(members), entry.tag()));
        setDirty();
        return true;
    }

    /**
     * Set or clear the short chat label. Pass an empty string to remove it.
     *
     * <p>Unique among live groups, case-insensitively, for the same reason names are: two groups
     * rendering as {@code [TCB]} in chat is worse than either having no tag at all, because a
     * reader cannot tell which is which and has no way to find out.</p>
     *
     * @return false if another group already uses that tag
     */
    public boolean setTag(String groupId, String tag) {
        Entry entry = groups.get(groupId);
        if (entry == null) {
            return false;
        }
        if (!tag.isEmpty()) {
            boolean taken = groups.values().stream()
                    .anyMatch(g -> !g.id().equals(groupId) && g.tag().equalsIgnoreCase(tag));
            if (taken) {
                return false;
            }
        }
        groups.put(groupId, new Entry(entry.id(), entry.name(), entry.owner(), entry.members(), tag));
        setDirty();
        return true;
    }

    /** By its short chat label, case-insensitively. */
    public Optional<Entry> byTag(String tag) {
        return tag.isEmpty() ? Optional.empty()
                : groups.values().stream().filter(g -> g.tag().equalsIgnoreCase(tag)).findFirst();
    }

    // --- shared homes ---

    public Optional<com.sablednah.standards.core.Waypoint> home(String groupId, String name) {
        return Optional.ofNullable(homes.get(homeKey(groupId, name)));
    }

    public List<String> homeNames(String groupId) {
        String prefix = groupId + "|";
        return homes.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** @return false if the group already has this many and this is a new name */
    public boolean setHome(String groupId, String name,
            com.sablednah.standards.core.Waypoint where, int limit) {
        String key = homeKey(groupId, name);
        if (limit >= 0 && !homes.containsKey(key) && homeNames(groupId).size() >= limit) {
            return false;
        }
        homes.put(key, where);
        setDirty();
        return true;
    }

    public boolean deleteHome(String groupId, String name) {
        if (homes.remove(homeKey(groupId, name)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean disband(String groupId) {
        if (groups.remove(groupId) == null) {
            return false;
        }
        invites.keySet().removeIf(k -> k.startsWith(groupId + "|"));
        // The homes go with it. Leaving them orphaned would let a new group with a recycled id
        // inherit somebody else's base, which is exactly why ids are opaque and never reused.
        homes.keySet().removeIf(k -> k.startsWith(groupId + "|"));
        setDirty();
        return true;
    }

    /**
     * Rename, keeping the id.
     *
     * <p>The id is what everything else stores, so a rename must not move it — that is the whole
     * reason {@link Entry#id()} exists separately from {@link Entry#name()}.</p>
     *
     * @return false if the new name is taken by a different group
     */
    public boolean rename(String groupId, String newName) {
        Entry entry = groups.get(groupId);
        if (entry == null) {
            return false;
        }
        Optional<Entry> clash = byName(newName);
        if (clash.isPresent() && !clash.get().id().equals(groupId)) {
            return false;
        }
        groups.put(groupId, new Entry(entry.id(), newName, entry.owner(), entry.members(),
                entry.tag()));
        setDirty();
        return true;
    }
}
