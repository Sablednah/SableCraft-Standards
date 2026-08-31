package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.core.PermissionRules;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.permissions.PermissionStore;
import com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

/**
 * {@code /perm} — the built-in permission handler's editor.
 *
 * <pre>
 *   /perm groups                              every group on the server
 *   /perm group &lt;name&gt; create | delete | info
 *   /perm group &lt;name&gt; set &lt;node&gt; [true|false]
 *   /perm group &lt;name&gt; unset &lt;node&gt;
 *   /perm group &lt;name&gt; parent add|remove &lt;other&gt;
 *   /perm group &lt;name&gt; tag &lt;tag|-&gt;           the chat label, if roles are rendered
 *   /perm user &lt;player&gt; group add|remove &lt;group&gt;
 *   /perm user &lt;player&gt; set &lt;node&gt; [true|false]
 *   /perm user &lt;player&gt; unset &lt;node&gt;
 *   /perm user &lt;player&gt; info                  their groups, their grants, and what answers
 *   /perm check &lt;player&gt; &lt;node&gt;               one node, and WHICH RULE said so
 * </pre>
 *
 * <h2>Absent unless our handler is the active one</h2>
 *
 * <p>The whole tree is gated on {@link StandardsPermissionHandler#isActive()}. A server running
 * LuckPerms would otherwise get a command that accepts every edit, reports success and changes
 * nothing, because the store it writes to is not the one being read — the worst shape a command
 * can have. Decision 7, applied to something that is chosen in {@code neoforge-server.toml} rather
 * than in our own config.</p>
 *
 * <p>Note the predicate is evaluated per source rather than at registration: the active handler is
 * not known when commands are built (it is chosen at {@code handleServerStarting}, after the
 * dispatcher exists), so a registration-time check would always see nothing and hide the tree on
 * every server.</p>
 *
 * <h2>Registered twice, because {@code /perm} is not reliably ours</h2>
 *
 * <p>LuckPerms claims {@code /perm} as an alias of {@code /luckperms}, along with {@code perms},
 * {@code permission}, {@code permissions}, {@code lp} and {@code luckperms}. On the server this
 * was built for — no permissions mod at all — that does not arise. But LuckPerms can be
 * <em>installed</em> while ours is the <em>selected</em> handler, and then the two literals merge:
 * our subcommands still work, while a bare {@code /perm} runs LuckPerms' help, silently, because
 * whichever mod registered last owns the node's own command.</p>
 *
 * <p>So the same tree is also registered as {@code /rank}, which nothing else claims. Both are
 * real trees rather than brigadier redirects, for the reason the rest of the mod builds aliases
 * that way: a redirect node carries no command of its own, so the bare form fails while every
 * subcommand works — a maddening bug to be told about second-hand.</p>
 *
 * <h2>Players are named, not selected</h2>
 *
 * <p>{@code <player>} is a string resolved through the name cache, not an
 * {@code EntityArgument} — the same choice {@code /eco give} makes and for the same reason.
 * Granting a rank to somebody who is offline is not an edge case, it is most of what an admin
 * does with a permissions system, and a selector cannot name an absent player.</p>
 */
public final class PermissionCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(source -> StandardsPermissionHandler.isActive()
                        && StandardsPermissions.require(StandardsPermissions.PERMISSIONS)
                                .test(source))
                .executes(PermissionCommands::overview)
                .then(Commands.literal("groups").executes(PermissionCommands::listGroups))
                .then(Commands.literal("group")
                        .then(Commands.argument("group", StringArgumentType.word())
                                .suggests(PermissionCommands::suggestGroups)
                                .executes(PermissionCommands::groupInfo)
                                .then(Commands.literal("create")
                                        .executes(PermissionCommands::createGroup))
                                .then(Commands.literal("delete")
                                        .executes(PermissionCommands::deleteGroup))
                                .then(Commands.literal("info")
                                        .executes(PermissionCommands::groupInfo))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                .suggests(PermissionCommands::suggestNodeSpec)
                                                .executes(PermissionCommands::setGroupNode)))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                .suggests(PermissionCommands::suggestGroupOwnNodes)
                                                .executes(PermissionCommands::unsetGroupNode)))
                                .then(Commands.literal("parent")
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("parent", StringArgumentType.word())
                                                        .suggests(PermissionCommands::suggestGroups)
                                                        .executes(ctx -> parent(ctx, true))))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("parent", StringArgumentType.word())
                                                        .suggests(PermissionCommands::suggestGroups)
                                                        .executes(ctx -> parent(ctx, false)))))
                                .then(Commands.literal("tag")
                                        .then(Commands.argument("tag", StringArgumentType.word())
                                                .executes(PermissionCommands::tag)))))
                .then(Commands.literal("user")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PermissionCommands::suggestKnownPlayers)
                                .executes(PermissionCommands::userInfo)
                                .then(Commands.literal("info")
                                        .executes(PermissionCommands::userInfo))
                                .then(Commands.literal("group")
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("group", StringArgumentType.word())
                                                        .suggests(PermissionCommands::suggestGroups)
                                                        .executes(ctx -> membership(ctx, true))))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("group", StringArgumentType.word())
                                                        .suggests(PermissionCommands::suggestGroups)
                                                        .executes(ctx -> membership(ctx, false)))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                .suggests(PermissionCommands::suggestNodeSpec)
                                                .executes(PermissionCommands::setUserNode)))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                .suggests(PermissionCommands::suggestNodes)
                                                .executes(PermissionCommands::unsetUserNode)))))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PermissionCommands::suggestKnownPlayers)
                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                        .suggests(PermissionCommands::suggestNodes)
                                        .executes(PermissionCommands::check))));
    }

    // --- reading ---

    private static int overview(CommandContext<CommandSourceStack> ctx) {
        PermissionStore store = store(ctx);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.overview",
                "groups", String.valueOf(store.allGroups().size()),
                "players", String.valueOf(store.knownPlayers().size()),
                "nodes", String.valueOf(PermissionAPI.getRegisteredNodes().size())), false);
        return 1;
    }

    private static int listGroups(CommandContext<CommandSourceStack> ctx) {
        PermissionStore store = store(ctx);
        List<PermissionStore.Entry> all = store.allGroups();
        if (all.isEmpty()) {
            Feedback.reply(ctx.getSource(), Lang.get("msg.perm.no_groups"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder(Lang.get("msg.perm.groups_header"));
        for (PermissionStore.Entry entry : all) {
            sb.append("\n").append(Lang.fmt("msg.perm.groups_row",
                    "name", entry.name(),
                    "nodes", String.valueOf(entry.nodes().size()),
                    "members", String.valueOf(store.membersOf(entry.name()).size()),
                    "parents", entry.parents().isEmpty() ? Lang.get("msg.perm.none")
                            : String.join(", ", entry.parents())));
        }
        Feedback.reply(ctx.getSource(), sb.toString(), false);
        return all.size();
    }

    private static int groupInfo(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "group");
        Optional<PermissionStore.Entry> found = store(ctx).group(name);
        if (found.isEmpty()) {
            return unknownGroup(ctx, name);
        }
        PermissionStore.Entry entry = found.get();
        StringBuilder sb = new StringBuilder(Lang.fmt("msg.perm.group_header", "name", entry.name()));
        sb.append("\n").append(Lang.fmt("msg.perm.group_parents",
                "list", entry.parents().isEmpty() ? Lang.get("msg.perm.none")
                        : String.join(", ", entry.parents())));
        sb.append("\n").append(Lang.fmt("msg.perm.group_members",
                "count", String.valueOf(store(ctx).membersOf(entry.name()).size())));
        if (!entry.tag().isEmpty()) {
            sb.append("\n").append(Lang.fmt("msg.perm.group_tag", "tag", entry.tag()));
        }
        appendNodes(sb, entry.nodes());
        Feedback.reply(ctx.getSource(), sb.toString(), false);
        return 1;
    }

    /**
     * Everything about a player, with <b>where each answer comes from</b>.
     *
     * <p>The provenance is the reason to build this rather than a yes/no lookup. It lists the
     * nodes something has actually said something about — their own grants and every node
     * mentioned by any group in their chain — rather than all ninety registered nodes, because a
     * wall of "default" is the same as no answer and buries the six lines that matter.</p>
     */
    private static int userInfo(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> who = resolve(server, name);
        if (who.isEmpty()) {
            return unknownPlayer(ctx, name);
        }
        UUID uuid = who.get();
        PermissionStore store = store(ctx);

        StringBuilder sb = new StringBuilder(Lang.fmt("msg.perm.user_header", "player", name));
        List<String> groups = store.groupsOf(uuid);
        sb.append("\n").append(Lang.fmt("msg.perm.user_groups",
                "list", groups.isEmpty() ? Lang.get("msg.perm.none") : String.join(", ", groups)));

        Map<String, Boolean> own = store.nodesOf(uuid);
        if (!own.isEmpty()) {
            sb.append("\n").append(Lang.get("msg.perm.user_own"));
            appendNodes(sb, own);
        }

        // Every node anything in their chain has an opinion about, resolved through the real
        // resolver — so this screen cannot drift from what the server actually does.
        List<String> mentioned = new ArrayList<>();
        own.keySet().forEach(mentioned::add);
        for (String group : groups) {
            collectNodes(store, group, mentioned, new java.util.HashSet<>());
        }
        String fallback = com.sablednah.standards.StandardsConfig.DEFAULT_PERMISSION_GROUP.get();
        collectNodes(store, fallback, mentioned, new java.util.HashSet<>());

        List<String> effective = mentioned.stream()
                .filter(n -> !n.endsWith("*"))
                .distinct()
                .sorted()
                .toList();
        if (effective.isEmpty()) {
            sb.append("\n").append(Lang.get("msg.perm.user_nothing"));
        } else {
            sb.append("\n").append(Lang.get("msg.perm.user_effective"));
            for (String node : effective) {
                sb.append("\n").append(explainLine(uuid, node));
            }
        }
        // Wildcards are listed separately rather than resolved: "standards.*" is not a question
        // with a yes/no answer, it is a rule that answers other questions.
        List<String> wildcards = mentioned.stream().filter(n -> n.endsWith("*")).distinct()
                .sorted().toList();
        if (!wildcards.isEmpty()) {
            sb.append("\n").append(Lang.fmt("msg.perm.user_wildcards",
                    "list", String.join(", ", wildcards)));
        }
        Feedback.reply(ctx.getSource(), sb.toString(), false);
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        String node = node(ctx);
        Optional<UUID> who = resolve(server, name);
        if (who.isEmpty()) {
            return unknownPlayer(ctx, name);
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.check_header",
                "player", name, "node", node) + "\n" + explainLine(who.get(), node), false);
        return StandardsPermissionHandler.explain(who.get(), node)
                .map(a -> a.allowed() ? 1 : 0)
                .orElse(0);
    }

    /**
     * One line: what the answer is, and which rule gave it.
     *
     * <p>Where the store says nothing, this reports the fallthrough honestly — "nothing here says,
     * so the node's own default applies" — rather than printing a bare "false" that reads as a
     * denial somebody configured.</p>
     */
    private static String explainLine(UUID player, String node) {
        Optional<PermissionRules.Answer> answer = StandardsPermissionHandler.explain(player, node);
        if (answer.isEmpty()) {
            return Lang.fmt("msg.perm.row_default", "node", node,
                    "state", Lang.get(defaultOf(node) ? "msg.perm.yes" : "msg.perm.no"));
        }
        PermissionRules.Answer a = answer.get();
        String scope = StandardsPermissionHandler.SELF_SCOPE.equals(a.scope())
                ? Lang.get("msg.perm.scope_self") : a.scope();
        return Lang.fmt("msg.perm.row_answered",
                "node", node,
                "state", Lang.get(a.allowed() ? "msg.perm.yes" : "msg.perm.no"),
                "scope", scope,
                "rule", a.pattern());
    }

    /**
     * What a node would answer with nothing configured — its own default resolver, asked offline.
     *
     * <p>Offline, so an op-gated node reports "no": there is no player in hand to ask about op
     * status, which is exactly the honest answer for the general case. Only used to colour the
     * "nothing says" line, never to decide anything.</p>
     */
    private static boolean defaultOf(String node) {
        for (PermissionNode<?> registered : PermissionAPI.getRegisteredNodes()) {
            if (registered.getNodeName().equals(node)) {
                Object value = registered.getDefaultResolver()
                        .resolve(null, new UUID(0L, 0L));
                return value instanceof Boolean b && b;
            }
        }
        return false;
    }

    private static void collectNodes(PermissionStore store, String group, List<String> into,
            java.util.Set<String> seen) {
        if (group == null || group.isBlank() || !seen.add(group.toLowerCase(Locale.ROOT))) {
            return;
        }
        store.group(group).ifPresent(entry -> {
            into.addAll(entry.nodes().keySet());
            entry.parents().forEach(parent -> collectNodes(store, parent, into, seen));
        });
    }

    private static void appendNodes(StringBuilder sb, Map<String, Boolean> nodes) {
        if (nodes.isEmpty()) {
            sb.append("\n").append(Lang.get("msg.perm.no_nodes"));
            return;
        }
        nodes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("\n").append(Lang.fmt("msg.perm.node_row",
                        "node", e.getKey(),
                        "state", Lang.get(e.getValue() ? "msg.perm.yes" : "msg.perm.no"))));
    }

    // --- writing ---

    private static int createGroup(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "group");
        if (!store(ctx).createGroup(name)) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.perm.group_exists", "name", name));
            return 0;
        }
        // A group named as the default one starts applying to everybody the moment it exists.
        refresh(ctx, null);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.group_created", "name", name), true);
        return 1;
    }

    private static int deleteGroup(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "group");
        if (!store(ctx).deleteGroup(name)) {
            return unknownGroup(ctx, name);
        }
        refresh(ctx, null);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.group_deleted", "name", name), true);
        return 1;
    }

    private static int setGroupNode(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        Optional<Spec> spec = spec(ctx);
        if (spec.isEmpty()) {
            return 0;
        }
        if (!store(ctx).setGroupNode(group, spec.get().node(), spec.get().value())) {
            return unknownGroup(ctx, group);
        }
        refresh(ctx, null);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.group_node_set",
                "name", group, "node", spec.get().node(),
                "state", Lang.get(spec.get().value() ? "msg.perm.yes" : "msg.perm.no")), true);
        return 1;
    }

    private static int unsetGroupNode(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        String node = node(ctx);
        if (!store(ctx).setGroupNode(group, node, null)) {
            return unknownGroup(ctx, group);
        }
        refresh(ctx, null);
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.group_node_unset",
                "name", group, "node", node), true);
        return 1;
    }

    private static int parent(CommandContext<CommandSourceStack> ctx, boolean add) {
        String group = StringArgumentType.getString(ctx, "group");
        String parent = StringArgumentType.getString(ctx, "parent");
        PermissionStore store = store(ctx);
        if (store.group(group).isEmpty()) {
            return unknownGroup(ctx, group);
        }
        if (add && store.group(parent).isEmpty()) {
            return unknownGroup(ctx, parent);
        }
        boolean done = add ? store.addParent(group, parent) : store.removeParent(group, parent);
        if (!done) {
            // The interesting failure by far: a cycle would hang the resolver on every check,
            // so say which one rather than a bare "no".
            Feedback.fail(ctx.getSource(), add
                    ? Lang.fmt("msg.perm.parent_refused", "name", group, "parent", parent)
                    : Lang.fmt("msg.perm.parent_absent", "name", group, "parent", parent));
            return 0;
        }
        refresh(ctx, null);
        Feedback.reply(ctx.getSource(), Lang.fmt(
                add ? "msg.perm.parent_added" : "msg.perm.parent_removed",
                "name", group, "parent", parent), true);
        return 1;
    }

    private static int tag(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        String raw = StringArgumentType.getString(ctx, "tag");
        // '-' clears it, the same convention the rest of the mod uses. Colour codes come out:
        // this is printed on other people's chat lines, and '&k' alone is an unreadable tag on
        // every line its members speak.
        String tag = "-".equals(raw) ? "" : Feedback.stripCodes(raw).trim();
        if (!store(ctx).setGroupTag(group, tag)) {
            return unknownGroup(ctx, group);
        }
        Feedback.reply(ctx.getSource(), tag.isEmpty()
                ? Lang.fmt("msg.perm.tag_cleared", "name", group)
                : Lang.fmt("msg.perm.tag_set", "name", group, "tag", tag), true);
        return 1;
    }

    private static int membership(CommandContext<CommandSourceStack> ctx, boolean add) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        String group = StringArgumentType.getString(ctx, "group");
        Optional<UUID> who = resolve(server, name);
        if (who.isEmpty()) {
            return unknownPlayer(ctx, name);
        }
        PermissionStore store = store(ctx);
        if (store.group(group).isEmpty()) {
            return unknownGroup(ctx, group);
        }
        boolean done = add ? store.addToGroup(who.get(), group)
                : store.removeFromGroup(who.get(), group);
        if (!done) {
            Feedback.fail(ctx.getSource(), Lang.fmt(
                    add ? "msg.perm.already_member" : "msg.perm.not_member",
                    "player", name, "name", group));
            return 0;
        }
        refresh(ctx, who.get());
        Feedback.reply(ctx.getSource(), Lang.fmt(add ? "msg.perm.joined" : "msg.perm.left",
                "player", name, "name", group), true);
        return 1;
    }

    private static int setUserNode(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<Spec> spec = spec(ctx);
        if (spec.isEmpty()) {
            return 0;
        }
        Optional<UUID> who = resolve(server, name);
        if (who.isEmpty()) {
            return unknownPlayer(ctx, name);
        }
        store(ctx).setPlayerNode(who.get(), spec.get().node(), spec.get().value());
        refresh(ctx, who.get());
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.user_node_set",
                "player", name, "node", spec.get().node(),
                "state", Lang.get(spec.get().value() ? "msg.perm.yes" : "msg.perm.no")), true);
        return 1;
    }

    private static int unsetUserNode(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        String node = node(ctx);
        Optional<UUID> who = resolve(server, name);
        if (who.isEmpty()) {
            return unknownPlayer(ctx, name);
        }
        if (!store(ctx).setPlayerNode(who.get(), node, null)) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.perm.user_node_absent",
                    "player", name, "node", node));
            return 0;
        }
        refresh(ctx, who.get());
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.user_node_unset",
                "player", name, "node", node), true);
        return 1;
    }

    // --- plumbing ---

    /**
     * Tell affected clients their command tree changed.
     *
     * <p><b>Why this is not optional.</b> The server re-evaluates {@code requires()} on every
     * command it parses, so a grant takes effect the instant it is made — verified with a real
     * non-op, who was granted {@code standards.craft} and got a crafting table without
     * reconnecting. But the <em>client</em> holds a copy of the tree, sent once on join, and uses
     * it for tab-completion and for colouring the line as you type.</p>
     *
     * <p>So without this, a freshly granted command renders <b>red, reading "unknown command"</b>,
     * and does not tab-complete — while working perfectly if you press enter anyway. An admin says
     * "you have /craft now", the player types it, sees red, and reports that it does not work.
     * Almost nobody presses enter through a red line. The permission was fine; the only broken
     * thing was what the player had been told.</p>
     *
     * <p>Found with two clients, and not reachable any other way: {@code SelfTest} has no client
     * and RCON cannot make somebody type. Same failure family as the cancelled interactions —
     * <b>the server is right and the client was never told</b>.</p>
     *
     * <p><b>One residue we cannot fix from here, and it looks exactly like this not working.</b>
     * The client re-colours a chat line when its <em>text</em> changes, not when a command tree
     * arrives. So a line already sitting in the box when the grant lands keeps its red until the
     * player touches it — verified by watching a granted {@code /anvil} stay red, then go white on
     * a single backspace-and-retype. The tree is live from the moment this runs; only the paint on
     * one already-typed line is stale, and nothing server-side can prompt a repaint. Worth knowing
     * before somebody "fixes" this again.</p>
     *
     * @param player the one player affected, or {@code null} for everyone — a group edit can reach
     *               anyone through inheritance or the default group, and working out exactly who
     *               offers more ways to be wrong than resending a packet that is only sent when an
     *               admin runs a command
     */
    private static void refresh(CommandContext<CommandSourceStack> ctx, UUID player) {
        MinecraftServer server = ctx.getSource().getServer();
        if (player != null) {
            ServerPlayer one = server.getPlayerList().getPlayer(player);
            if (one != null) {
                server.getCommands().sendCommands(one);
            }
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(online);
        }
    }

    /** A node and the value to set it to. */
    private record Spec(String node, boolean value) {}

    /**
     * Read the node (and optional true/false) out of one greedy argument.
     *
     * <p><b>Greedy, and it has to be.</b> The obvious tree is
     * {@code set <node:word> [<value:bool>]}, which is what this was, and brigadier's
     * {@code word()} accepts only letters, digits and {@code _.+-} — so it stops dead at an
     * asterisk. That makes {@code standards.home.*} <em>unparseable</em>: the wildcards are the
     * one feature admins actually reach for, and they could not be typed at all. The error was
     * "Expected whitespace to end one argument", which names nothing and reads like a syntax
     * mistake by the person typing it.</p>
     *
     * <p>{@code string()} would take a quoted {@code "standards.home.*"}, and nobody quotes a
     * permission node. A custom argument type would need registering with {@code ArgumentTypeInfos}
     * to survive being sent to a client. Taking the rest of the line and splitting it here costs
     * one small parser and behaves exactly as typed.</p>
     *
     * <p>Found by driving the real commands over RCON, not by the self-test — which had proved the
     * wildcard <em>logic</em> correct while nothing had ever managed to enter one.</p>
     */
    private static Optional<Spec> spec(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "spec").trim();
        String[] parts = raw.split("\\s+");
        if (parts.length == 1) {
            // A bare node means grant it. That is what an admin means by 'set', and it matches
            // every other permissions system they have used.
            return Optional.of(new Spec(parts[0], true));
        }
        if (parts.length == 2 && ("true".equalsIgnoreCase(parts[1])
                || "false".equalsIgnoreCase(parts[1]))) {
            return Optional.of(new Spec(parts[0], Boolean.parseBoolean(parts[1])));
        }
        Feedback.fail(ctx.getSource(), Lang.fmt("msg.perm.bad_spec", "input", raw));
        return Optional.empty();
    }

    /** The node out of a greedy argument that holds nothing else. */
    private static String node(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "spec").trim().split("\\s+")[0];
    }

    private static PermissionStore store(CommandContext<CommandSourceStack> ctx) {
        return PermissionStore.get(ctx.getSource().getServer());
    }

    private static Optional<UUID> resolve(MinecraftServer server, String name) {
        return StandardsData.get(server).byName(server, name);
    }

    private static int unknownGroup(CommandContext<CommandSourceStack> ctx, String name) {
        Feedback.fail(ctx.getSource(), Lang.fmt("msg.perm.group_unknown", "name", name));
        return 0;
    }

    private static int unknownPlayer(CommandContext<CommandSourceStack> ctx, String name) {
        Feedback.fail(ctx.getSource(), Lang.fmt("msg.common.player_not_found", "name", name));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestGroups(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                store(ctx).allGroups().stream().map(PermissionStore.Entry::name).toList(), builder);
    }

    /**
     * Every registered node, plus the wildcards that cover them.
     *
     * <p>Suggesting the wildcards matters more than it looks: they are the feature admins reach
     * for and the one thing they cannot discover by reading a list of nodes. Offering
     * {@code standards.home.*} beside the six home nodes is how somebody finds out it exists.</p>
     */
    private static CompletableFuture<Suggestions> suggestNodes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> wildcards = new java.util.TreeSet<>();
        for (PermissionNode<?> node : PermissionAPI.getRegisteredNodes()) {
            String name = node.getNodeName();
            out.add(name);
            int dot = name.lastIndexOf('.');
            while (dot > 0) {
                wildcards.add(name.substring(0, dot) + ".*");
                dot = name.lastIndexOf('.', dot - 1);
            }
        }
        out.addAll(wildcards);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return SharedSuggestionProvider.suggest(out, builder);
    }

    /**
     * Nodes while the argument is still one word, then true/false once a space is typed.
     *
     * <p>A greedy argument keeps offering suggestions for the whole remainder, so without the
     * offset the value position would be offered a list of node names — which is worse than no
     * suggestion, because tab-complete would then insert one.</p>
     */
    private static CompletableFuture<Suggestions> suggestNodeSpec(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int space = remaining.indexOf(' ');
        if (space < 0) {
            return suggestNodes(ctx, builder);
        }
        return SharedSuggestionProvider.suggest(List.of("true", "false"),
                builder.createOffset(builder.getStart() + space + 1));
    }

    /** Only what this group actually holds — an unset of something it never had is a typo. */
    private static CompletableFuture<Suggestions> suggestGroupOwnNodes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                store(ctx).group(StringArgumentType.getString(ctx, "group"))
                        .map(e -> e.nodes().keySet().stream().sorted().toList())
                        .orElse(List.of()),
                builder);
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> names = new ArrayList<>(List.of(server.getPlayerNames()));
        StandardsData.get(server).knownNames().forEach(n -> {
            if (!names.contains(n)) {
                names.add(n);
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private PermissionCommands() {}
}
