package com.sablednah.standards.neoforge.permissions;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.api.groups.Group;
import com.sablednah.standards.api.groups.GroupKind;
import com.sablednah.standards.api.groups.GroupProvider;
import com.sablednah.standards.api.groups.Groups;
import com.sablednah.standards.neoforge.Lang;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Publishes the permission groups through the groups seam, as {@code standards:role}.
 *
 * <h2>This is the part worth building the handler for</h2>
 *
 * <p>A permissions system on its own is LuckPerms with fewer features, and chasing its contexts,
 * tracks, weights and SQL backends loses. What LuckPerms cannot do is the other half: its groups
 * are a permissions concept, so nothing else on the server can ask about them and they do not
 * render a chat tag without its own prefix system.</p>
 *
 * <p>Here, a permission group <b>is</b> a group. Put somebody in {@code moderator} and they get,
 * in one edit and with no second list to keep in sync:</p>
 *
 * <ul>
 * <li>their permission nodes;</li>
 * <li>a chat tag, if the owner adds {@code standards:role} to {@code chat.groupTagKinds} — through
 *     the existing decorator seam, with no chat code written here;</li>
 * <li>visibility to any mod through {@code Groups.all(player, KIND)};</li>
 * <li>whatever else consumes the seam later, for free.</li>
 * </ul>
 *
 * <p>So this is not "LuckPerms but ours". It is the groups you already have, that also carry
 * permissions.</p>
 *
 * <h2>Non-exclusive, unlike the built-in social groups</h2>
 *
 * <p>{@code standards:group} is one-per-player, because a shared home has to belong to exactly one
 * group. A role is not: a moderator can also be a builder and a donor, which is precisely the case
 * {@link GroupKind#exclusive()} exists to distinguish. Consumers must use {@code Groups.all} here;
 * {@code Groups.primary} deliberately answers empty for a kind like this.</p>
 *
 * <h2>Direct membership only</h2>
 *
 * <p>Somebody in {@code admin} which <em>inherits</em> {@code moderator} is a member of
 * {@code admin} and not of {@code moderator}. Inheritance moves nodes down the chain, not people
 * up it — and rendering both tags on their chat line would be the first visible consequence of
 * conflating the two.</p>
 */
public final class PermissionRoles implements GroupProvider {

    /**
     * The kind. {@code displayName()} reads from {@link Lang} every call, so a server that
     * re-skins "rank" to something else in {@code messages.yml} is followed here too — the API
     * requires a provider to own its own vocabulary, and Standards is a provider like any other.
     */
    public static final GroupKind KIND = new GroupKind() {
        @Override
        public String id() {
            return "standards:role";
        }

        @Override
        public String displayName() {
            return Lang.get("term.rank");
        }

        @Override
        public boolean exclusive() {
            return false;
        }
    };

    private final MinecraftServer server;

    private PermissionRoles(MinecraftServer server) {
        this.server = server;
    }

    /** Called once the server exists, since the store lives in its saved data. */
    public static void install(MinecraftServer server) {
        Groups.register(new PermissionRoles(server));
    }

    public static void uninstall() {
        Groups.unregister(KIND);
    }

    @Override
    public GroupKind kind() {
        return KIND;
    }

    @Override
    public Collection<Group> groupsOf(ServerPlayer player) {
        PermissionStore store = PermissionStore.get(server);
        return store.groupsOf(player.getUUID()).stream()
                .map(store::group)
                .flatMap(Optional::stream)
                .map(entry -> wrap(store, entry))
                .toList();
    }

    @Override
    public Optional<Group> byName(String name) {
        PermissionStore store = PermissionStore.get(server);
        return store.group(name).map(entry -> wrap(store, entry));
    }

    @Override
    public Collection<Group> all() {
        PermissionStore store = PermissionStore.get(server);
        return store.allGroups().stream().map(entry -> wrap(store, entry)).toList();
    }

    private static Group wrap(PermissionStore store, PermissionStore.Entry entry) {
        return new Group() {
            @Override
            public GroupKind kind() {
                return KIND;
            }

            /**
             * The name is the id. Permission groups are referred to by name everywhere an admin
             * types one, and there is no rename command precisely so that stays true — an opaque
             * id would have to be exposed in {@code /rank} output for anything to be editable,
             * which is a worse trade than not renaming.
             */
            @Override
            public String id() {
                return entry.name();
            }

            @Override
            public String name() {
                return entry.name();
            }

            @Override
            public boolean contains(UUID player) {
                // Asked on the chat and grief-check paths, so it walks the player's own short
                // group list rather than the group's member list, which is a scan of the store.
                String wanted = entry.name();
                for (String mine : store.groupsOf(player)) {
                    if (mine.equalsIgnoreCase(wanted)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Collection<UUID> members() {
                return List.copyOf(store.membersOf(entry.name()));
            }

            @Override
            public Optional<String> tag() {
                return entry.tag().isEmpty() ? Optional.empty() : Optional.of(entry.tag());
            }
        };
    }
}
