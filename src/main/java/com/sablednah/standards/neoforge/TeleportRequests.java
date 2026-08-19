package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.StandardsConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Open {@code /tpa} and {@code /tpahere} requests.
 *
 * <p>In memory, always. A teleport request that survives a server restart is not a feature — the
 * person who sent it has long since walked off, and accepting it teleports you to somewhere that
 * mattered an hour ago.</p>
 *
 * <p>A player may hold several incoming requests at once, which is the case everyone forgets: on a
 * busy server two people ask at the same moment and a single-slot design silently discards one of
 * them. {@code /tpaccept} with no argument takes the most recent; with a name it takes that one.</p>
 */
public final class TeleportRequests {

    /** Which way round the teleport goes when accepted. */
    public enum Direction {
        /** {@code /tpa} — the requester travels to the target. */
        TO_TARGET,
        /** {@code /tpahere} — the target travels to the requester. */
        TO_REQUESTER
    }

    public record Request(UUID requester, UUID target, Direction direction, long expiresAtTick) {

        /** Who actually moves when this is accepted. */
        public UUID traveller() {
            return direction == Direction.TO_TARGET ? requester : target;
        }

        /** Who stays put and is waiting for them. */
        public UUID host() {
            return direction == Direction.TO_TARGET ? target : requester;
        }
    }

    private static final List<Request> OPEN = new ArrayList<>();

    // --- asking ---

    /** Why a request could not be made, for the caller to report. */
    public enum Refusal { NONE, SELF, ALREADY_PENDING, TARGET_REFUSING }

    public static Refusal open(MinecraftServer server, ServerPlayer requester,
            ServerPlayer target, Direction direction) {
        if (requester.getUUID().equals(target.getUUID())) {
            return Refusal.SELF;
        }
        // A target who has switched teleports off is invisible to requests rather than a
        // rejection: telling the requester "they refused" would leak a setting that exists
        // precisely so people can be left alone. Staff with the override bypass it.
        if (StandardsAttachments.of(target).refusingTeleports()
                && !StandardsPermissions.has(requester, StandardsPermissions.TPA_OVERRIDE)) {
            return Refusal.TARGET_REFUSING;
        }
        boolean duplicate = OPEN.stream().anyMatch(r ->
                r.requester().equals(requester.getUUID())
                        && r.target().equals(target.getUUID())
                        && r.direction() == direction);
        if (duplicate) {
            return Refusal.ALREADY_PENDING;
        }
        OPEN.add(new Request(requester.getUUID(), target.getUUID(), direction,
                server.getTickCount() + StandardsConfig.TPA_TIMEOUT.get() * 20L));
        return Refusal.NONE;
    }

    // --- answering ---

    /**
     * The request this player should answer: the named one, or the newest if no name was given.
     * Newest rather than oldest because the most recent ask is the one still on screen.
     */
    public static Optional<Request> incoming(UUID target, Optional<UUID> from) {
        return OPEN.stream()
                .filter(r -> r.target().equals(target))
                .filter(r -> from.isEmpty() || r.requester().equals(from.get()))
                .max(Comparator.comparingLong(Request::expiresAtTick));
    }

    /** An outgoing request this player could cancel. */
    public static Optional<Request> outgoing(UUID requester, Optional<UUID> to) {
        return OPEN.stream()
                .filter(r -> r.requester().equals(requester))
                .filter(r -> to.isEmpty() || r.target().equals(to.get()))
                .max(Comparator.comparingLong(Request::expiresAtTick));
    }

    /** Every open request aimed at this player, newest first. */
    public static List<Request> allIncoming(UUID target) {
        return OPEN.stream()
                .filter(r -> r.target().equals(target))
                .sorted(Comparator.comparingLong(Request::expiresAtTick).reversed())
                .toList();
    }

    public static void close(Request request) {
        OPEN.remove(request);
    }

    /** Drop every request this player is either end of — on logout, or on /tpacancel all. */
    public static int closeAllInvolving(UUID player) {
        int before = OPEN.size();
        OPEN.removeIf(r -> r.requester().equals(player) || r.target().equals(player));
        return before - OPEN.size();
    }

    /**
     * Expire timed-out requests, telling both ends.
     *
     * <p>Told, rather than silently dropped: a requester who never hears anything assumes the
     * server ate it, and a target who sees a stale button in their chat log clicks it and gets an
     * error. Both are worse than one line saying it lapsed.</p>
     */
    static void tick(MinecraftServer server) {
        if (OPEN.isEmpty()) return;
        long now = server.getTickCount();
        OPEN.removeIf(request -> {
            if (now < request.expiresAtTick()) return false;
            ServerPlayer requester = server.getPlayerList().getPlayer(request.requester());
            ServerPlayer target = server.getPlayerList().getPlayer(request.target());
            String requesterName = requester != null ? requester.getName().getString() : "?";
            String targetName = target != null ? target.getName().getString() : "?";
            if (requester != null) {
                Feedback.chat(requester, Lang.fmt("msg.tpa.expired_sender", "player", targetName));
            }
            if (target != null) {
                Feedback.chat(target, Lang.fmt("msg.tpa.expired_target", "player", requesterName));
            }
            return true;
        });
    }

    private TeleportRequests() {}
}
