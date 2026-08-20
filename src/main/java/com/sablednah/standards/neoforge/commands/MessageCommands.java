package com.sablednah.standards.neoforge.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.Standards;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.Mutes;
import com.sablednah.standards.neoforge.StandardsAttachments;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Vanish;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Private messages: {@code /msg} (with {@code /w}, {@code /whisper}, {@code /tell}, {@code /pm}),
 * {@code /r} and {@code /reply}, plus {@code /ignore} and {@code /socialspy}.
 *
 * <p>Vanilla has {@code /msg}. It does not have {@code /r}, which is the half people actually use
 * — nobody types a name twice in a conversation — and it has no notion of ignoring, spying or
 * refusing messages. That is the gap this fills.</p>
 *
 * <h2>Taking over vanilla's {@code /msg}</h2>
 *
 * <p>Vanilla registers {@code msg} and makes {@code tell} and {@code w} <b>redirects</b> to it.
 * That defeats the obvious approach twice over: a redirect node ignores any children you merge
 * into it, so {@code /w} silently kept vanilla's behaviour; and adding a differently-named
 * argument beside vanilla's {@code targets} just loses the race, because brigadier tries children
 * in insertion order and vanilla got there first. The first version of this class did both, and
 * the self-test caught it — {@code /msg} "passed" only because <em>vanilla's</em> node was the one
 * executing, which would have let mutes and ignores leak straight through.</p>
 *
 * <p>The way in is brigadier's merge rule: {@code CommandNode.addChild} <b>replaces the command</b>
 * when it merges a node of the same name. So {@link #overrideVanillaMsg} re-registers vanilla's
 * exact tree — same literal, same argument names, same argument types — with our {@code executes}.
 * Ours wins, and {@code /tell} and {@code /w} inherit it for free because they point at that same
 * node. No reflection, no second mixin, no fighting the dispatcher.</p>
 *
 * <p>Three rules that are easy to get wrong and unpleasant when you do:</p>
 *
 * <ul>
 * <li><b>A mute covers private messages.</b> Otherwise a muted player simply moves the behaviour
 *     they were muted for into DMs, and the mute has achieved nothing.</li>
 * <li><b>Ignoring is silent to the sender.</b> They see their message go; it just never arrives.
 *     Telling them they are ignored turns "I would rather not hear from you" into a confrontation,
 *     which is the opposite of what the feature is for.</li>
 * <li><b>A vanished staff member is not messageable</b> by anyone who cannot see them — being
 *     addressable is as much of a giveaway as being visible.</li>
 * </ul>
 */
public final class MessageCommands {

    /**
     * Who each player last exchanged a message with, for {@code /r}.
     *
     * <p>In memory: a reply target that survives a restart points at a conversation nobody
     * remembers having.</p>
     */
    private static final Map<UUID, UUID> LAST_CONTACT = new HashMap<>();

    // --- registration ---

    /**
     * Vanilla's tree, shape for shape, with our command on the end.
     *
     * <p>The argument names and types must match vanilla's exactly ({@code targets} as
     * {@code players()}, {@code message} as {@code MessageArgument}) — that is what makes brigadier
     * merge them rather than add a second, losing branch.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> msg(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.MSG))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("message", MessageArgument.message())
                                .executes(MessageCommands::send)));
    }

    /**
     * Vanilla's {@code /me}, re-registered so it goes through our rules.
     *
     * <p>The italics were the ask; the mute bypass was the reason to do it. Vanilla's {@code /me}
     * is a separate broadcast path that knows nothing about our mutes, ignores or vanish — so a
     * muted player could simply narrate at everyone instead of talking, which makes the mute
     * decorative. Same merge rule as {@code /msg}: matching literal, matching argument name
     * ({@code action}) and type, so brigadier replaces vanilla's command rather than losing to
     * it.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> emote() {
        return Commands.literal("me")
                .then(Commands.argument("action", MessageArgument.message())
                        .executes(MessageCommands::emote));
    }

    private static int emote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String action = MessageArgument.getMessage(ctx, "action").getString();

        // A mute is a mute. Narrating at the room must not be the way around it.
        Optional<Mutes.Mute> mute = Mutes.get(server).active(from.getUUID());
        if (mute.isPresent()) {
            long left = mute.get().remaining(System.currentTimeMillis());
            Feedback.chat(from, mute.get().permanent()
                    ? Lang.fmt("msg.mod.mute_blocked_perm", "reason", mute.get().reason())
                    : Lang.fmt("msg.mod.mute_blocked",
                            "duration", Duration.describe(left), "reason", mute.get().reason()));
            return 0;
        }

        // Emoting while hidden announces you as loudly as speaking does.
        if (Vanish.isVanished(from)) {
            Feedback.chat(from, Lang.get("msg.chat.emote_vanished"));
            return 0;
        }

        String line = Lang.fmt("msg.chat.emote",
                "player", from.getName().getString(), "action", action);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (StandardsAttachments.of(viewer).ignores(from.getUUID())) {
                continue;
            }
            Feedback.chat(viewer, line);
        }
        Standards.LOGGER.info("[me] {} {}", from.getName().getString(), action);
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> reply(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.MSG))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(MessageCommands::reply));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> ignore() {
        return Commands.literal("ignore")
                .requires(StandardsPermissions.require(StandardsPermissions.MSG))
                .executes(MessageCommands::listIgnored)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(MessageCommands::toggleIgnore));
    }

    // --- sending ---

    private static int send(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        String text = MessageArgument.getMessage(ctx, "message").getString();
        int delivered = 0;
        // Plural, because vanilla's argument is plural — /msg @a works, and taking that away
        // while claiming to replace /msg would be a downgrade.
        for (ServerPlayer to : EntityArgument.getPlayers(ctx, "targets")) {
            delivered += deliver(from, to, text);
        }
        return delivered;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        MinecraftServer server = from.level().getServer();
        UUID lastId = LAST_CONTACT.get(from.getUUID());
        ServerPlayer to = lastId == null ? null : server.getPlayerList().getPlayer(lastId);
        if (to == null) {
            Feedback.chat(from, Lang.get(lastId == null
                    ? "msg.pm.nobody_to_reply" : "msg.pm.reply_gone"));
            return 0;
        }
        return deliver(from, to, StringArgumentType.getString(ctx, "message"));
    }

    private static int deliver(ServerPlayer from, ServerPlayer to, String text) {
        MinecraftServer server = from.level().getServer();
        String fromName = from.getName().getString();
        String toName = to.getName().getString();

        if (from == to) {
            Feedback.chat(from, Lang.get("msg.pm.self"));
            return 0;
        }

        // A mute is a mute. Moving the conversation to DMs must not be the way around it.
        Optional<Mutes.Mute> mute = Mutes.get(server).active(from.getUUID());
        if (mute.isPresent()) {
            long left = mute.get().remaining(System.currentTimeMillis());
            Feedback.chat(from, mute.get().permanent()
                    ? Lang.fmt("msg.mod.mute_blocked_perm", "reason", mute.get().reason())
                    : Lang.fmt("msg.mod.mute_blocked",
                            "duration", Duration.describe(left), "reason", mute.get().reason()));
            return 0;
        }

        // Being addressable gives a vanish away as surely as being visible does.
        if (Vanish.isVanished(to)
                && !StandardsPermissions.has(from, StandardsPermissions.VANISH_SEE)) {
            Feedback.chat(from, Lang.fmt("msg.common.player_not_found", "name", toName));
            return 0;
        }

        var theirState = StandardsAttachments.of(to);
        if (theirState.refusingMessages()
                && !StandardsPermissions.has(from, StandardsPermissions.MSG_OVERRIDE)) {
            Feedback.chat(from, Lang.fmt("msg.pm.refusing", "player", toName));
            return 0;
        }

        // Deliberately indistinguishable from a delivered message. See the class notes.
        boolean ignored = theirState.ignores(from.getUUID());

        Feedback.chat(from, Lang.fmt("msg.pm.sent", "player", toName, "message", text));
        if (!ignored) {
            Feedback.chat(to, Lang.fmt("msg.pm.received", "player", fromName, "message", text));
            // Reply targets are set on both sides, and only on a delivered message — /r should
            // never answer into a conversation the other person never saw.
            LAST_CONTACT.put(to.getUUID(), from.getUUID());
            LAST_CONTACT.put(from.getUUID(), to.getUUID());
        }
        spy(server, from, to, fromName, toName, text);
        return 1;
    }

    /** Show the exchange to anyone spying — never to the two people in it. */
    private static void spy(MinecraftServer server, ServerPlayer from, ServerPlayer to,
            String fromName, String toName, String text) {
        for (ServerPlayer watcher : server.getPlayerList().getPlayers()) {
            if (watcher == from || watcher == to) continue;
            if (!StandardsAttachments.of(watcher).socialSpy()) continue;
            if (!StandardsPermissions.has(watcher, StandardsPermissions.SOCIALSPY)) continue;
            Feedback.chat(watcher, Lang.fmt("msg.pm.spy",
                    "from", fromName, "to", toName, "message", text));
        }
    }

    // --- ignoring ---

    private static int toggleIgnore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
        if (other == me) {
            Feedback.chat(me, Lang.get("msg.pm.ignore_self"));
            return 0;
        }
        boolean nowIgnored = StandardsAttachments.of(me).toggleIgnore(other.getUUID());
        Feedback.chat(me, Lang.fmt(nowIgnored ? "msg.pm.ignored" : "msg.pm.unignored",
                "player", other.getName().getString()));
        return 1;
    }

    private static int listIgnored(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        var ignored = StandardsAttachments.of(me).ignoredPlayers();
        if (ignored.isEmpty()) {
            Feedback.chat(me, Lang.get("msg.pm.ignore_none"));
            return 0;
        }
        var data = com.sablednah.standards.neoforge.StandardsData.get(me.level().getServer());
        String names = ignored.stream()
                .map(id -> data.nameOf(id).orElse(id.toString().substring(0, 8) + "…"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + "&7, &f" + b).orElse("");
        Feedback.chat(me, Lang.fmt("msg.pm.ignore_list", "list", names));
        return ignored.size();
    }

    /** Drop a leaver's reply target so it cannot point at a stale session. */
    public static void forget(UUID player) {
        LAST_CONTACT.remove(player);
        LAST_CONTACT.values().removeIf(player::equals);
    }

    private MessageCommands() {}
}
