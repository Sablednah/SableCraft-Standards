package com.sablednah.standards.neoforge.commands;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.TeleportRequests;
import com.sablednah.standards.neoforge.TeleportRequests.Direction;
import com.sablednah.standards.neoforge.TeleportRequests.Request;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Request-based teleporting: {@code /tpa}, {@code /tpahere}, {@code /tpaccept}, {@code /tpdeny},
 * {@code /tpacancel}, {@code /tpalist}.
 *
 * <p><b>The thing this feature is usually got wrong.</b> With a teleport warmup configured, the
 * classic implementation accepts a request and then does nothing observable for five seconds. The
 * requester does not know they were accepted; the acceptor does not know anyone is coming. Both
 * conclude it is broken, and on a stream you can watch them both re-run the command.</p>
 *
 * <p>So every state change here is narrated to <em>both</em> ends: accepted, arriving in N seconds,
 * arrived, cancelled and why, lapsed. The traveller additionally gets a ticking action-bar
 * countdown from {@link Teleports}. The mechanism is {@link Teleports.Watcher} — see there.</p>
 *
 * <p>Prompts carry clickable <code>[Accept]</code> / <code>[Deny]</code> buttons. Chat click events
 * are vanilla, so they work on an unmodified client, and they retire the most common question this
 * feature generates.</p>
 */
public final class TpaCommands {

    // --- registration ---

    public static LiteralArgumentBuilder<CommandSourceStack> tpa(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.TPA))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> ask(ctx, Direction.TO_TARGET)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tpaHere() {
        return Commands.literal("tpahere")
                .requires(StandardsPermissions.require(StandardsPermissions.TPA_HERE))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> ask(ctx, Direction.TO_REQUESTER)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> accept(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.TPA))
                .executes(ctx -> answer(ctx, Optional.empty(), true))
                .then(Commands.argument("from", StringArgumentType.word())
                        .suggests(TpaCommands::suggestIncoming)
                        .executes(ctx -> answer(ctx,
                                Optional.of(StringArgumentType.getString(ctx, "from")), true)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> deny(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.TPA))
                .executes(ctx -> answer(ctx, Optional.empty(), false))
                .then(Commands.argument("from", StringArgumentType.word())
                        .suggests(TpaCommands::suggestIncoming)
                        .executes(ctx -> answer(ctx,
                                Optional.of(StringArgumentType.getString(ctx, "from")), false)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> cancel() {
        return Commands.literal("tpacancel")
                .requires(StandardsPermissions.require(StandardsPermissions.TPA))
                .executes(TpaCommands::cancel);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> list() {
        return Commands.literal("tpalist")
                .requires(StandardsPermissions.require(StandardsPermissions.TPA))
                .executes(TpaCommands::list);
    }

    // --- asking ---

    private static int ask(CommandContext<CommandSourceStack> ctx, Direction direction)
            throws CommandSyntaxException {
        ServerPlayer requester = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        MinecraftServer server = requester.level().getServer();

        var refusal = TeleportRequests.open(server, requester, target, direction);
        String targetName = target.getName().getString();
        switch (refusal) {
            case SELF -> {
                Feedback.chat(requester, Lang.get("msg.tpa.self"));
                return 0;
            }
            case ALREADY_PENDING -> {
                Feedback.chat(requester, Lang.fmt("msg.tpa.duplicate", "player", targetName));
                return 0;
            }
            case TARGET_REFUSING -> {
                Feedback.chat(requester, Lang.fmt("msg.tpa.refusing", "player", targetName));
                return 0;
            }
            default -> { }
        }

        int timeout = StandardsConfig.TPA_TIMEOUT.get();
        String requesterName = requester.getName().getString();
        Feedback.chat(requester, Lang.fmt(
                direction == Direction.TO_TARGET ? "msg.tpa.sent" : "msg.tpa.sent_here",
                "player", targetName, "sec", timeout));

        // The prompt, with buttons. Naming the requester in the command means clicking the right
        // button answers the right request when several are open at once.
        Feedback.chatWithButtons(target,
                Lang.fmt(direction == Direction.TO_TARGET
                        ? "msg.tpa.received" : "msg.tpa.received_here", "player", requesterName),
                Feedback.button(Lang.get("msg.tpa.button_accept"),
                        "/tpaccept " + requesterName,
                        Lang.fmt("msg.tpa.button_accept_tip", "player", requesterName)),
                Feedback.button(Lang.get("msg.tpa.button_deny"),
                        "/tpdeny " + requesterName,
                        Lang.fmt("msg.tpa.button_deny_tip", "player", requesterName)));
        return 1;
    }

    // --- answering ---

    private static int answer(CommandContext<CommandSourceStack> ctx, Optional<String> fromName,
            boolean accepting) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = me.level().getServer();

        Optional<UUID> from = fromName.flatMap(n -> byName(server, n));
        if (fromName.isPresent() && from.isEmpty()) {
            Feedback.chat(me, Lang.fmt("msg.tpa.none_from", "name", fromName.get()));
            return 0;
        }
        Optional<Request> found = TeleportRequests.incoming(me.getUUID(), from);
        if (found.isEmpty()) {
            if (fromName.isPresent()) {
                Feedback.chat(me, Lang.fmt("msg.tpa.none_from", "name", fromName.get()));
                return 0;
            }
            // Nothing to answer — but if they have an outgoing request, they are the one being
            // waited on, and saying only "no requests" leaves them stuck. Most common on
            // /tpahere, where the person who typed it is NOT the person who accepts.
            Optional<Request> mine = TeleportRequests.outgoing(me.getUUID(), Optional.empty());
            if (mine.isPresent()) {
                ServerPlayer other = server.getPlayerList().getPlayer(mine.get().target());
                Feedback.chat(me, Lang.fmt("msg.tpa.none_incoming_but_outgoing",
                        "player", other != null ? other.getName().getString() : "?"));
            } else {
                Feedback.chat(me, Lang.get("msg.tpa.none_incoming"));
            }
            return 0;
        }
        Request request = found.get();
        TeleportRequests.close(request);

        ServerPlayer requester = server.getPlayerList().getPlayer(request.requester());
        if (requester == null) {
            Feedback.chat(me, Lang.fmt("msg.tpa.gone", "player", "They"));
            return 0;
        }

        if (!accepting) {
            Feedback.chat(me, Lang.fmt("msg.tpa.denied_by_you",
                    "player", requester.getName().getString()));
            Feedback.chat(requester, Lang.fmt("msg.tpa.denied_you",
                    "player", me.getName().getString()));
            return 1;
        }
        return run(server, request, requester, me);
    }

    /**
     * Carry out an accepted request.
     *
     * <p>Whoever travels is decided by the request's direction, not by who typed the command —
     * {@code /tpahere} accepted means the <em>acceptor</em> moves. Getting that backwards is the
     * other classic bug in this feature.</p>
     */
    private static int run(MinecraftServer server, Request request,
            ServerPlayer requester, ServerPlayer acceptor) {
        boolean requesterTravels = request.direction() == Direction.TO_TARGET;
        ServerPlayer traveller = requesterTravels ? requester : acceptor;
        ServerPlayer host = requesterTravels ? acceptor : requester;
        String travellerName = traveller.getName().getString();
        String hostName = host.getName().getString();
        UUID hostId = host.getUUID();

        Teleports.Watcher watcher = new Teleports.Watcher() {
            @Override
            public void onArrive(ServerPlayer who) {
                ServerPlayer stillHere = server.getPlayerList().getPlayer(hostId);
                if (stillHere != null) {
                    Feedback.chat(stillHere, Lang.fmt("msg.tpa.arrived_host", "player", travellerName));
                }
            }

            @Override
            public void onCancel(ServerPlayer who, Teleports.CancelReason reason) {
                ServerPlayer stillHere = server.getPlayerList().getPlayer(hostId);
                if (stillHere == null) return;
                // Say why. "They did not make it" with no reason invites a second request and a
                // second failure for the same cause.
                Feedback.chat(stillHere, Lang.fmt("msg.tpa.failed_host",
                        "player", travellerName,
                        "reason", Lang.get(switch (reason) {
                            case MOVED -> "msg.tpa.reason_moved";
                            case DAMAGED -> "msg.tpa.reason_damaged";
                            case UNSAFE -> "msg.tpa.reason_unsafe";
                            case LEFT -> "msg.tpa.reason_left";
                        })));
            }
        };

        // Snapshot vs follow. With a warmup, the host can walk off during the countdown, and both
        // answers are defensible: "teleport to the player" taken literally means wherever they end
        // up, while the snapshot is predictable and cannot be used to walk someone somewhere
        // unpleasant. Server's choice; the safe-landing search applies either way.
        Waypoint snapshot = Waypoint.of(host);
        java.util.function.Supplier<Optional<Waypoint>> destination =
                StandardsConfig.TPA_FOLLOW_TARGET.get()
                        ? () -> Optional.ofNullable(server.getPlayerList().getPlayer(hostId))
                                .map(Waypoint::of)
                        : () -> Optional.of(snapshot);

        Teleports.Attempt attempt =
                Teleports.request(traveller, destination, snapshot, true, watcher);
        if (!attempt.accepted()) {
            MoveCommands.report(traveller, attempt);
            // The host asked for this and deserves to know it did not start.
            Feedback.chat(host, Lang.fmt("msg.tpa.failed_host",
                    "player", travellerName, "reason", Lang.get("msg.tpa.reason_unsafe")));
            return 0;
        }

        // Both ends, immediately — this is the five seconds of silence the feature exists to close.
        //
        // Addressed by ROLE (who travels, who waits), never by who typed the command. Those
        // coincide for /tpa and invert for /tpahere, and writing it the other way round meant the
        // acceptor of a /tpahere was told about themselves twice while the requester — the person
        // who asked in the first place — was told nothing at all. Found by two people trying it.
        if (attempt.queued()) {
            Feedback.chat(host, Lang.fmt("msg.tpa.accepted_by_you_wait",
                    "player", travellerName, "sec", attempt.secondsLeft()));
            Feedback.chat(traveller, Lang.fmt(
                    requesterTravels ? "msg.tpa.accepted_you_wait" : "msg.tpa.accepted_here_wait",
                    "player", hostName, "sec", attempt.secondsLeft()));
        } else {
            Feedback.chat(host, Lang.fmt("msg.tpa.accepted_by_you", "player", travellerName));
            Feedback.chat(traveller, Lang.fmt(
                    requesterTravels ? "msg.tpa.accepted_you_go" : "msg.tpa.accepted_here_go",
                    "player", hostName));
        }
        return 1;
    }

    // --- withdrawing and listing ---

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = me.level().getServer();
        Optional<Request> found = TeleportRequests.outgoing(me.getUUID(), Optional.empty());
        if (found.isEmpty()) {
            // The trap this exists for: tab-completing /tpa offers 'tpacancel' BEFORE 'tpaccept'
            // (alphabetically 'a' < 'c'), so someone meaning to accept lands here instead and is
            // told, unhelpfully, that they have nothing to cancel.
            var waiting = TeleportRequests.allIncoming(me.getUUID());
            if (!waiting.isEmpty()) {
                ServerPlayer asker = server.getPlayerList().getPlayer(waiting.getFirst().requester());
                Feedback.chatWithButtons(me,
                        Lang.fmt("msg.tpa.none_outgoing_but_incoming",
                                "count", waiting.size(),
                                "player", asker != null ? asker.getName().getString() : "?"),
                        Feedback.button(Lang.get("msg.tpa.button_accept"),
                                "/tpaccept" + (asker != null ? " " + asker.getName().getString() : ""),
                                Lang.get("msg.tpa.button_accept_generic")));
            } else {
                Feedback.chat(me, Lang.get("msg.tpa.none_outgoing"));
            }
            return 0;
        }
        Request request = found.get();
        TeleportRequests.close(request);
        ServerPlayer target = server.getPlayerList().getPlayer(request.target());
        String targetName = target != null ? target.getName().getString() : "?";
        Feedback.chat(me, Lang.fmt("msg.tpa.cancelled_by_you", "player", targetName));
        if (target != null) {
            Feedback.chat(target, Lang.fmt("msg.tpa.cancelled_you",
                    "player", me.getName().getString()));
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = me.level().getServer();
        var open = TeleportRequests.allIncoming(me.getUUID());
        if (open.isEmpty()) {
            Feedback.chat(me, Lang.get("msg.tpa.none_incoming"));
            return 0;
        }
        StringBuilder sb = new StringBuilder(Lang.get("msg.tpa.list_header"));
        long now = server.getTickCount();
        for (Request r : open) {
            ServerPlayer requester = server.getPlayerList().getPlayer(r.requester());
            sb.append("\n").append(Lang.fmt("msg.tpa.list_row",
                    "player", requester != null ? requester.getName().getString() : "?",
                    "dir", Lang.get(r.direction() == Direction.TO_TARGET
                            ? "msg.tpa.dir_to_you" : "msg.tpa.dir_to_them"),
                    "sec", Math.max(0, (r.expiresAtTick() - now) / 20)));
        }
        Feedback.chat(me, sb.toString());
        return open.size();
    }

    // --- helpers ---

    private static Optional<UUID> byName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        return Optional.ofNullable(online).map(ServerPlayer::getUUID);
    }

    /** Suggest only the people who have actually asked — not the whole player list. */
    private static CompletableFuture<Suggestions> suggestIncoming(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer me)) {
            return builder.buildFuture();
        }
        MinecraftServer server = me.level().getServer();
        return SharedSuggestionProvider.suggest(
                TeleportRequests.allIncoming(me.getUUID()).stream()
                        .map(r -> server.getPlayerList().getPlayer(r.requester()))
                        .filter(java.util.Objects::nonNull)
                        .map(p -> p.getName().getString())
                        .toList(),
                builder);
    }

    private TpaCommands() {}
}
