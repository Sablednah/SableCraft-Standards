package com.sablednah.standards.neoforge.commands;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsGroups;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /group} — the lightweight groups Standards ships with.
 *
 * <pre>
 *   /group                      what you are in, or how to start one
 *   /group create &lt;name&gt;        found one, and own it
 *   /group invite &lt;player&gt;      owner only
 *   /group accept &lt;name&gt;        take an invite
 *   /group deny &lt;name&gt;          refuse one
 *   /group leave                the owner leaving disbands it
 *   /group kick &lt;player&gt;        owner only
 *   /group rename &lt;name&gt;        owner only; the id does not move
 *   /group tag &lt;tag|->          owner only; the short label chat uses
 *   /group list                 every group on the server
 *   /group info [name]          members, owner, tag
 * </pre>
 *
 * <p><b>Everything lives at {@code /group}, unlike the rest of the mod.</b> Decision 12 says every
 * command sits at its plain name because muscle memory is the product — but that applies to things
 * players type constantly. Founding a group is done once, and {@code /invite} and {@code /kick}
 * as bare commands are exactly the names a faction mod, a party mod and a guild mod would all
 * want. Leaving them free is worth more than saving five characters.</p>
 */
public final class GroupCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> group() {
        return Commands.literal("group")
                .requires(StandardsPermissions.require(StandardsPermissions.GROUP))
                .executes(GroupCommands::mine)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(GroupCommands::create)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(GroupCommands::invite)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(GroupCommands::suggestInvites)
                                .executes(ctx -> answer(ctx, true))))
                .then(Commands.literal("deny")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(GroupCommands::suggestInvites)
                                .executes(ctx -> answer(ctx, false))))
                .then(Commands.literal("leave").executes(GroupCommands::leave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(GroupCommands::kick)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(GroupCommands::rename)))
                .then(Commands.literal("tag")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(GroupCommands::tag)))
                .then(Commands.literal("sethome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(GroupCommands::setHome)))
                .then(Commands.literal("delhome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(GroupCommands::suggestOwnHomes)
                                .executes(GroupCommands::delHome)))
                .then(Commands.literal("list").executes(GroupCommands::list))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(GroupCommands::suggestGroups)
                                .executes(ctx -> info(ctx,
                                        StringArgumentType.getString(ctx, "name")))));
    }

    /**
     * {@code /ghome [name]} and {@code /ghomes} — at their plain names, unlike the rest.
     *
     * <p>Decision 12 applies here where it does not to {@code /group create}: travelling to the
     * base is something a member does several times an hour, and {@code /group home base} is four
     * words for a thing that wants to be one.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> groupHome(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.GROUP))
                .executes(ctx -> goHome(ctx, null))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(GroupCommands::suggestOwnHomes)
                        .executes(ctx -> goHome(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> groupHomes(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.GROUP))
                .executes(GroupCommands::listHomes);
    }

    private static int goHome(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<StandardsGroups.Entry> mine = store(ctx).of(player.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.none"));
            return 0;
        }
        String groupId = mine.get().id();
        List<String> names = store(ctx).homeNames(groupId);
        // A bare /ghome with exactly one home means that one. With several it has to ask, because
        // picking arbitrarily would send somebody to the wrong continent.
        String wanted = name != null ? name : (names.size() == 1 ? names.get(0) : null);
        if (wanted == null) {
            Feedback.chat(player, names.isEmpty()
                    ? Lang.fmt("msg.group.no_homes_yet", "name", mine.get().name())
                    : Lang.fmt("msg.group.which_home", "list", String.join(", ", names)));
            return 0;
        }
        Optional<com.sablednah.standards.core.Waypoint> where = store(ctx).home(groupId, wanted);
        if (where.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.group.home_unknown",
                    "name", wanted, "list", homeList(ctx, groupId)));
            return 0;
        }
        com.sablednah.standards.neoforge.Teleports.Attempt attempt =
                com.sablednah.standards.neoforge.Teleports.request(player, where.get(), true,
                        Lang.fmt("msg.group.home_went", "name", wanted));
        return MoveCommands.report(player, attempt) ? 1 : 0;
    }

    private static int listHomes(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<StandardsGroups.Entry> mine = store(ctx).of(player.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.none"));
            return 0;
        }
        List<String> names = store(ctx).homeNames(mine.get().id());
        if (names.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.group.no_homes_yet", "name", mine.get().name()));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.group.homes",
                "name", mine.get().name(), "count", names.size(),
                "list", String.join(", ", names)));
        return names.size();
    }

    // --- reading ---

    private static int mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StandardsGroups store = store(ctx);
        Optional<StandardsGroups.Entry> mine = store.of(player.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.none"));
            List<StandardsGroups.Entry> invites = store.invitesFor(player.getUUID());
            if (!invites.isEmpty()) {
                Feedback.chat(player, Lang.fmt("msg.group.invites_waiting",
                        "list", invites.stream().map(StandardsGroups.Entry::name)
                                .collect(java.util.stream.Collectors.joining(", "))));
            }
            return 0;
        }
        return describe(ctx, mine.get());
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StandardsGroups store = store(ctx);
        Optional<StandardsGroups.Entry> found = name == null
                ? store.of(player.getUUID())
                : store.byName(name);
        if (found.isEmpty()) {
            Feedback.chat(player, name == null
                    ? Lang.get("msg.group.none")
                    : Lang.fmt("msg.group.unknown", "name", name));
            return 0;
        }
        return describe(ctx, found.get());
    }

    private static int describe(CommandContext<CommandSourceStack> ctx, StandardsGroups.Entry entry)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData names = StandardsData.get(server);
        String members = entry.members().stream()
                .map(u -> names.nameOf(u).orElse(u.toString().substring(0, 8)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.joining(", "));
        // A group with no tag shows nothing rather than "[none]" — an empty bracket is noise on
        // every line that mentions a group that never wanted one.
        Feedback.chat(player, Lang.fmt("msg.group.info",
                "name", entry.name(),
                "tag", entry.tag().isEmpty() ? "" : Lang.fmt("msg.group.info_tag", "tag", entry.tag()),
                "owner", names.nameOf(entry.owner()).orElse("?"),
                "members", members));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<StandardsGroups.Entry> all = store(ctx).all();
        if (all.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.none_on_server"));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.group.list",
                "count", all.size(),
                "list", all.stream().map(StandardsGroups.Entry::name)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(java.util.stream.Collectors.joining(", "))));
        // Answer the question people actually came with. /group list shows the SERVER's groups,
        // and bare /group shows yours — a distinction obvious only once somebody has explained
        // it. Saying both here means nobody has to be told.
        Optional<StandardsGroups.Entry> mine = store(ctx).of(player.getUUID());
        Feedback.chat(player, mine
                .map(g -> Lang.fmt("msg.group.list_yours", "name", g.name()))
                .orElseGet(() -> Lang.get("msg.group.list_none_yours")));
        return all.size();
    }

    // --- membership ---

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StandardsGroups.clean(StringArgumentType.getString(ctx, "name"));
        if (name.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.bad_name"));
            return 0;
        }
        Optional<StandardsGroups.Entry> made = store(ctx).create(name, player.getUUID());
        if (made.isEmpty()) {
            // Two reasons left, and telling them which one saves a guess.
            Feedback.chat(player, store(ctx).of(player.getUUID()).isPresent()
                    ? Lang.get("msg.group.already_in_one")
                    : Lang.fmt("msg.group.name_taken", "name", name));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.group.created", "name", made.get().name()));
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Optional<StandardsGroups.Entry> owned = owned(ctx, player);
        if (owned.isEmpty()) {
            return 0;
        }
        StandardsGroups store = store(ctx);
        if (store.of(target.getUUID()).isPresent()) {
            Feedback.chat(player, Lang.fmt("msg.group.they_are_in_one",
                    "player", target.getName().getString()));
            return 0;
        }
        store.invite(owned.get().id(), target.getUUID());
        Feedback.chat(player, Lang.fmt("msg.group.invited",
                "player", target.getName().getString(), "name", owned.get().name()));
        Feedback.chat(target, Lang.fmt("msg.group.invite_received",
                "player", player.getName().getString(), "name", owned.get().name()));
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, boolean accept)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        StandardsGroups store = store(ctx);
        Optional<StandardsGroups.Entry> found = store.byName(name);
        if (found.isEmpty() || !store.isInvited(found.get().id(), player.getUUID())) {
            Feedback.chat(player, Lang.fmt("msg.group.no_invite", "name", name));
            return 0;
        }
        StandardsGroups.Entry entry = found.get();
        if (!accept) {
            store.revokeInvite(entry.id(), player.getUUID());
            Feedback.chat(player, Lang.fmt("msg.group.denied", "name", entry.name()));
            return 1;
        }
        if (!store.join(entry.id(), player.getUUID())) {
            Feedback.chat(player, Lang.get("msg.group.already_in_one"));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.group.joined", "name", entry.name()));
        announce(ctx, entry, Lang.fmt("msg.group.member_joined",
                "player", player.getName().getString()), player.getUUID());
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StandardsGroups store = store(ctx);
        Optional<StandardsGroups.Entry> mine = store.of(player.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.none"));
            return 0;
        }
        StandardsGroups.Entry entry = mine.get();
        boolean owner = entry.owner().equals(player.getUUID());
        // Told BEFORE the group goes, or the announcement has nobody to reach.
        announce(ctx, entry, owner
                ? Lang.fmt("msg.group.disbanded", "name", entry.name())
                : Lang.fmt("msg.group.member_left", "player", player.getName().getString()),
                player.getUUID());
        store.leave(entry.id(), player.getUUID());
        Feedback.chat(player, owner
                ? Lang.fmt("msg.group.you_disbanded", "name", entry.name())
                : Lang.fmt("msg.group.you_left", "name", entry.name()));
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<StandardsGroups.Entry> owned = owned(ctx, player);
        if (owned.isEmpty()) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        Optional<UUID> target = StandardsData.get(server).byName(server, name);
        if (target.isEmpty() || !owned.get().members().contains(target.get())) {
            Feedback.chat(player, Lang.fmt("msg.group.not_a_member", "player", name));
            return 0;
        }
        if (target.get().equals(player.getUUID())) {
            // Kicking yourself would disband the group through a command that does not say so.
            Feedback.chat(player, Lang.get("msg.group.kick_self"));
            return 0;
        }
        store(ctx).leave(owned.get().id(), target.get());
        Feedback.chat(player, Lang.fmt("msg.group.kicked", "player", name));
        ServerPlayer online = server.getPlayerList().getPlayer(target.get());
        if (online != null) {
            Feedback.chat(online, Lang.fmt("msg.group.you_were_kicked",
                    "name", owned.get().name()));
        }
        return 1;
    }

    // --- owner settings ---

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StandardsGroups.clean(StringArgumentType.getString(ctx, "name"));
        Optional<StandardsGroups.Entry> owned = owned(ctx, player);
        if (owned.isEmpty()) {
            return 0;
        }
        if (name.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.bad_name"));
            return 0;
        }
        if (!store(ctx).rename(owned.get().id(), name)) {
            Feedback.chat(player, Lang.fmt("msg.group.name_taken", "name", name));
            return 0;
        }
        announce(ctx, owned.get(), Lang.fmt("msg.group.renamed",
                "old", owned.get().name(), "name", name), null);
        return 1;
    }

    private static int tag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String tag = StringArgumentType.getString(ctx, "tag");
        Optional<StandardsGroups.Entry> owned = owned(ctx, player);
        if (owned.isEmpty()) {
            return 0;
        }
        // "-" clears it. A bare /group tag would be ambiguous with asking what the tag is.
        // Measured after cleaning, or "&k" would spend four of the five on nothing visible.
        String wanted = tag.equals("-") ? "" : StandardsGroups.clean(tag);
        if (!tag.equals("-") && wanted.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.bad_tag"));
            return 0;
        }
        int max = 5;
        if (wanted.length() > max) {
            Feedback.chat(player, Lang.fmt("msg.group.tag_too_long", "max", max));
            return 0;
        }
        if (!store(ctx).setTag(owned.get().id(), wanted)) {
            Feedback.chat(player, Lang.fmt("msg.group.tag_taken", "tag", wanted));
            return 0;
        }
        Feedback.chat(player, wanted.isEmpty()
                ? Lang.get("msg.group.tag_cleared")
                : Lang.fmt("msg.group.tag_set", "tag", wanted));
        return 1;
    }

    // --- shared homes ---

    /**
     * Owner sets, every member travels.
     *
     * <p>Matching {@code rename}, {@code tag} and {@code kick}: the owner decides what the group
     * has, and members use it. Letting anyone set one is friendlier right up until somebody fills
     * the limit with junk and the owner has no way to tell which is the real base.</p>
     */
    private static int setHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Optional<StandardsGroups.Entry> owned = owned(ctx, player);
        if (owned.isEmpty()) {
            return 0;
        }
        int limit = com.sablednah.standards.StandardsConfig.GROUP_HOME_LIMIT.get();
        com.sablednah.standards.core.Waypoint here =
                com.sablednah.standards.core.Waypoint.of(player);
        if (!store(ctx).setHome(owned.get().id(), name, here, limit)) {
            Feedback.chat(player, Lang.fmt("msg.group.home_limit", "limit", limit, "name", name));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.group.home_set",
                "name", name, "place", here.describe()));
        Feedback.warnIfUnreachable(player, here);
        announce(ctx, owned.get(), Lang.fmt("msg.group.home_set_others",
                "player", player.getName().getString(), "name", name), player.getUUID());
        return 1;
    }

    private static int delHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Optional<StandardsGroups.Entry> owned = owned(ctx, player);
        if (owned.isEmpty()) {
            return 0;
        }
        if (!store(ctx).deleteHome(owned.get().id(), name)) {
            Feedback.chat(player, Lang.fmt("msg.group.home_unknown",
                    "name", name, "list", homeList(ctx, owned.get().id())));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.group.home_deleted", "name", name));
        return 1;
    }

    static String homeList(CommandContext<CommandSourceStack> ctx, String groupId) {
        List<String> names = store(ctx).homeNames(groupId);
        return names.isEmpty() ? Lang.get("msg.group.no_homes") : String.join(", ", names);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestOwnHomes(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return builder.buildFuture();
        }
        return store(ctx).of(player.getUUID())
                .map(e -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        store(ctx).homeNames(e.id()), builder))
                .orElseGet(builder::buildFuture);
    }

    // --- helpers ---

    private static StandardsGroups store(CommandContext<CommandSourceStack> ctx) {
        return StandardsGroups.get(ctx.getSource().getServer());
    }

    /** The group this player owns, complaining for them if they do not own one. */
    private static Optional<StandardsGroups.Entry> owned(
            CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Optional<StandardsGroups.Entry> mine = store(ctx).of(player.getUUID());
        if (mine.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.group.none"));
            return Optional.empty();
        }
        if (!mine.get().owner().equals(player.getUUID())) {
            Feedback.chat(player, Lang.get("msg.group.not_owner"));
            return Optional.empty();
        }
        return mine;
    }

    /** Tell everyone in the group who is online, optionally skipping one of them. */
    private static void announce(CommandContext<CommandSourceStack> ctx,
            StandardsGroups.Entry entry, String message, UUID except) {
        MinecraftServer server = ctx.getSource().getServer();
        for (UUID member : entry.members()) {
            if (member.equals(except)) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                Feedback.chat(online, message);
            }
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestInvites(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return builder.buildFuture();
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
                store(ctx).invitesFor(player.getUUID()).stream()
                        .map(StandardsGroups.Entry::name).toList(), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestGroups(CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
                store(ctx).all().stream().map(StandardsGroups.Entry::name).toList(), builder);
    }

    private GroupCommands() {}
}
