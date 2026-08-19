package com.sablednah.standards.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One road for every teleport in the mod: {@code /home}, {@code /warp}, {@code /back},
 * {@code /top}, {@code /jump}, {@code /spawn}, {@code /tpa}.
 *
 * <p>Funnelling them all through here is what makes cooldowns, warmups, the safe-landing search
 * and the {@code /back} trail behave the same everywhere. In every essentials package that grew
 * these features one command at a time, they don't — {@code /warp} respects the warmup and
 * {@code /home} forgot to, or {@code /spawn} never records a return point, and every one of those
 * is a bug report rather than a design.</p>
 *
 * <p>Warmups and cooldowns are in-memory: a pending teleport that survives a restart, or a
 * cooldown that outlives the session that earned it, are both worse than losing them.</p>
 */
public final class Teleports {

    /**
     * Someone other than the traveller who has a stake in this teleport landing.
     *
     * <p>This exists because of a real failure watched on streams: with a warmup configured, a
     * {@code /tpa} is accepted and then <b>nothing happens for five seconds</b>. The requester has
     * no idea their request was accepted, the acceptor has no idea whether the requester is
     * coming, and both assume it is broken. The traveller's own countdown fixes half of it; this
     * fixes the other half, and it does so for cancellations too — a traveller who steps on a
     * pressure plate and cancels must not leave someone standing there expecting them.</p>
     */
    public interface Watcher {
        default void onArrive(ServerPlayer traveller) {}

        default void onCancel(ServerPlayer traveller, CancelReason reason) {}
    }

    public enum CancelReason { MOVED, DAMAGED, UNSAFE, LEFT }

    /**
     * A teleport waiting on its warmup.
     *
     * <p>The destination is a <b>supplier</b>, not a fixed point, because a queued teleport may
     * legitimately be chasing something that moves — an accepted {@code /tpa} where the host walks
     * off mid-countdown. It is resolved once, at the moment of arrival. A supplier returning empty
     * means the destination stopped existing (the host logged out), which cancels rather than
     * dropping the traveller at a stale coordinate.</p>
     */
    private record Pending(Supplier<Optional<Waypoint>> destination, long dueTick, Vec3 startedAt,
                           boolean recordBack, Watcher watcher, int lastSecondShown) {

        Pending withSecondShown(int second) {
            return new Pending(destination, dueTick, startedAt, recordBack, watcher, second);
        }
    }

    /** The do-nothing watcher, for the teleports nobody else is waiting on. */
    private static final Watcher NOBODY = new Watcher() {};

    private static final Map<UUID, Pending> WARMING = new HashMap<>();
    private static final Map<UUID, Long> LAST_TELEPORT = new HashMap<>();

    /** Why a teleport did not happen, so the caller can say the right thing. */
    public enum Refusal { NONE, COOLDOWN, UNSAFE, NO_WORLD }

    /** The outcome of asking for a teleport. {@code queued} means the warmup started. */
    public record Attempt(boolean accepted, boolean queued, Refusal refusal, long secondsLeft) {
        static Attempt done() {
            return new Attempt(true, false, Refusal.NONE, 0);
        }

        static Attempt queued(long seconds) {
            return new Attempt(true, true, Refusal.NONE, seconds);
        }

        static Attempt refused(Refusal why, long seconds) {
            return new Attempt(false, false, why, seconds);
        }
    }

    /**
     * Send a player somewhere, obeying every rule the server has set.
     *
     * @param recordBack whether to remember where they were for {@code /back} — false when the
     *                   teleport <em>is</em> a {@code /back}, or the trail would loop on itself
     */
    public static Attempt request(ServerPlayer player, Waypoint destination, boolean recordBack) {
        return request(player, destination, recordBack, NOBODY);
    }

    /** As above, plus someone to keep informed about how it goes. See {@link Watcher}. */
    public static Attempt request(ServerPlayer player, Waypoint destination, boolean recordBack,
            Watcher watcher) {
        return request(player, () -> Optional.of(destination), destination, recordBack, watcher);
    }

    /**
     * A teleport whose destination is worked out when it arrives, not when it is asked for.
     *
     * @param destination resolved at arrival; empty means it stopped existing
     * @param preview     a stand-in for the up-front world check, since there is nothing to
     *                    resolve yet when the request is made
     */
    public static Attempt request(ServerPlayer player, Supplier<Optional<Waypoint>> destination,
            Waypoint preview, boolean recordBack, Watcher watcher) {
        MinecraftServer server = player.level().getServer();
        if (server == null || preview.level(server) == null) {
            return Attempt.refused(Refusal.NO_WORLD, 0);
        }

        long cooldownLeft = cooldownRemaining(player);
        if (cooldownLeft > 0) {
            return Attempt.refused(Refusal.COOLDOWN, cooldownLeft);
        }

        int warmup = StandardsConfig.TELEPORT_WARMUP.get();
        if (warmup <= 0 || StandardsPermissions.has(player, StandardsPermissions.TP_INSTANT)) {
            Optional<Waypoint> now = destination.get();
            if (now.isPresent() && perform(player, now.get(), recordBack)) {
                watcher.onArrive(player);
                return Attempt.done();
            }
            watcher.onCancel(player, CancelReason.UNSAFE);
            return Attempt.refused(Refusal.UNSAFE, 0);
        }

        WARMING.put(player.getUUID(), new Pending(destination,
                server.getTickCount() + warmup * 20L, player.position(), recordBack, watcher, -1));
        return Attempt.queued(warmup);
    }

    /** Teleport now, skipping cooldown and warmup. For admin commands and other mods' skills. */
    public static boolean immediate(ServerPlayer player, Waypoint destination, boolean recordBack) {
        return perform(player, destination, recordBack);
    }

    /**
     * The actual move. Finds somewhere safe to land, records the departure point, and plays the
     * ender sound at both ends — at both ends because a teleport that only makes a noise where you
     * arrive is invisible to everyone watching you leave.
     *
     * @return false if there was nowhere safe to land; nothing has happened in that case
     */
    private static boolean perform(ServerPlayer player, Waypoint destination, boolean recordBack) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ServerLevel level = destination.level(server);
        if (level == null) return false;

        Optional<BlockPos> landing = SafeLoc.find(level, destination.blockPos());
        if (landing.isEmpty()) return false;
        BlockPos target = landing.get();

        // Keep the exact stored position when the safety search agreed with it — a home set on a
        // half-slab should not be nudged to the block centre every time you use it.
        boolean exact = target.equals(destination.blockPos());
        double x = exact ? destination.x() : target.getX() + 0.5D;
        double y = exact ? destination.y() : target.getY();
        double z = exact ? destination.z() : target.getZ() + 0.5D;

        if (recordBack) {
            StandardsAttachments.of(player).pushBack(Waypoint.of(player), false);
        }

        Vec3 from = player.position();
        player.level().playSound(null, from.x, from.y, from.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.teleportTo(level, x, y, z, Set.of(), destination.yaw(), destination.pitch(), false);
        level.playSound(null, x, y, z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        LAST_TELEPORT.put(player.getUUID(), System.currentTimeMillis());
        return true;
    }

    // --- cooldown ---

    /** Seconds still to wait, or 0 if they may teleport now. */
    public static long cooldownRemaining(ServerPlayer player) {
        int cooldown = StandardsConfig.TELEPORT_COOLDOWN.get();
        if (cooldown <= 0 || StandardsPermissions.has(player, StandardsPermissions.TP_NO_COOLDOWN)) {
            return 0;
        }
        Long last = LAST_TELEPORT.get(player.getUUID());
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        long waitMs = cooldown * 1000L - elapsed;
        return waitMs > 0 ? waitMs / 1000 + 1 : 0;
    }

    // --- warmup ---

    public static boolean isWarming(ServerPlayer player) {
        return WARMING.containsKey(player.getUUID());
    }

    /** Abandon a queued teleport. Returns true if there was one. */
    public static boolean cancel(ServerPlayer player) {
        Pending dropped = WARMING.remove(player.getUUID());
        if (dropped == null) return false;
        dropped.watcher().onCancel(player, CancelReason.LEFT);
        return true;
    }

    /** A player took damage; drop their queued teleport if the server wants that. */
    public static void onDamaged(ServerPlayer player) {
        if (!StandardsConfig.WARMUP_CANCEL_ON_DAMAGE.get()) return;
        Pending dropped = WARMING.remove(player.getUUID());
        if (dropped != null) {
            Feedback.chat(player, Lang.get("msg.tp.warmup_damaged"));
            dropped.watcher().onCancel(player, CancelReason.DAMAGED);
        }
    }

    /** Forget everything about a player who has left. */
    public static void forget(UUID player) {
        WARMING.remove(player);
        LAST_TELEPORT.remove(player);
    }

    /**
     * Advance every warmup. Called once per server tick.
     *
     * <p>The movement check uses a small squared radius rather than exact equality because a
     * player standing perfectly still still drifts a hair — riding a boat, standing on soul sand,
     * simply breathing on a slope — and a warmup that cancels itself for that is a warmup nobody
     * can ever complete.</p>
     */
    static void tick(MinecraftServer server) {
        if (WARMING.isEmpty()) return;
        long now = server.getTickCount();
        boolean cancelOnMove = StandardsConfig.WARMUP_CANCEL_ON_MOVE.get();

        WARMING.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true; // logged out mid-warmup
            Pending pending = entry.getValue();

            if (cancelOnMove && player.position().distanceToSqr(pending.startedAt()) > 0.25D) {
                Feedback.chat(player, Lang.get("msg.tp.warmup_moved"));
                pending.watcher().onCancel(player, CancelReason.MOVED);
                return true;
            }
            if (now < pending.dueTick()) {
                // The countdown. A warmup that says "hold still for 5s" once and then goes silent
                // reads as a hang — the player has no way to tell a working teleport from a broken
                // one. The action bar is the right place for it: it is already the "transient
                // status" line, and it does not bury chat under five identical messages.
                int secondsLeft = (int) Math.ceil((pending.dueTick() - now) / 20.0D);
                if (secondsLeft != pending.lastSecondShown()) {
                    Feedback.actionBar(player, Lang.fmt("msg.tp.warmup_tick", "sec", secondsLeft));
                    entry.setValue(pending.withSecondShown(secondsLeft));
                }
                return false;
            }
            Optional<Waypoint> arrival = pending.destination().get();
            if (arrival.isPresent() && perform(player, arrival.get(), pending.recordBack())) {
                pending.watcher().onArrive(player);
            } else {
                Feedback.chat(player, Lang.get("msg.tp.unsafe"));
                pending.watcher().onCancel(player, CancelReason.UNSAFE);
            }
            return true;
        });
    }

    private Teleports() {}
}
