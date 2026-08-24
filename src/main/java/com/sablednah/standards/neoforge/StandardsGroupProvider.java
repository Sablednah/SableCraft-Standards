package com.sablednah.standards.neoforge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.api.groups.Group;
import com.sablednah.standards.api.groups.GroupKind;
import com.sablednah.standards.api.groups.GroupProvider;
import com.sablednah.standards.api.groups.Groups;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Publishes {@link StandardsGroups} through the groups seam.
 *
 * <p>Standards consuming its own API rather than reaching past it. That is not tidiness — a seam
 * only one implementation has ever crossed is a seam nobody has tested, and every time this mod
 * has shipped an API with no external consumer the first real crossing found a bug within
 * minutes. Making the built-in groups go the long way round means the path is exercised from the
 * first day rather than the day somebody else arrives.</p>
 *
 * <p>Registered at {@code FMLCommonSetupEvent}, and only when the feature is enabled — a pack
 * running a faction mod turns the built-in off and its own kind takes over, with neither side
 * needing to know about the other.</p>
 */
public final class StandardsGroupProvider implements GroupProvider {

    /**
     * The kind Standards provides.
     *
     * <p>{@code displayName()} reads from {@link Lang} on every call, so a server that re-skins
     * "group" to "crew" or "cadre" in {@code messages.yml} is followed here too. The API requires
     * that the provider own its own vocabulary, and Standards is a provider like any other.</p>
     */
    public static final GroupKind KIND = new GroupKind() {
        @Override
        public String id() {
            return "standards:group";
        }

        @Override
        public String displayName() {
            return Lang.get("term.group");
        }

        @Override
        public boolean exclusive() {
            return true;
        }
    };

    private final MinecraftServer server;

    private StandardsGroupProvider(MinecraftServer server) {
        this.server = server;
    }

    /** Called once the server exists, since the store lives in its saved data. */
    public static void install(MinecraftServer server) {
        Groups.register(new StandardsGroupProvider(server));
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
        return StandardsGroups.get(server).of(player.getUUID())
                .<Collection<Group>>map(e -> List.of(wrap(e)))
                .orElseGet(List::of);
    }

    @Override
    public Optional<Group> byName(String name) {
        return StandardsGroups.get(server).byName(name).map(StandardsGroupProvider::wrap);
    }

    @Override
    public Collection<Group> all() {
        return StandardsGroups.get(server).all().stream()
                .map(StandardsGroupProvider::wrap)
                .map(g -> (Group) g)
                .toList();
    }

    /**
     * A live view rather than a snapshot.
     *
     * <p>Holds the entry it was built from, so {@code contains()} answers without another lookup
     * — it sits on the grief-check path once claims arrive, and walking saved data per block break
     * would be a poor thing to have built in.</p>
     */
    private static Group wrap(StandardsGroups.Entry entry) {
        return new Group() {
            @Override
            public GroupKind kind() {
                return KIND;
            }

            @Override
            public String id() {
                return entry.id();
            }

            @Override
            public String name() {
                return entry.name();
            }

            @Override
            public boolean contains(UUID player) {
                return entry.members().contains(player);
            }

            @Override
            public Collection<UUID> members() {
                return entry.members();
            }

            @Override
            public Optional<String> tag() {
                return entry.tag().isEmpty() ? Optional.empty() : Optional.of(entry.tag());
            }
        };
    }
}
