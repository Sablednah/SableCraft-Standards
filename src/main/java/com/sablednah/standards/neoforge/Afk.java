package com.sablednah.standards.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.Duration;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Who is away, and noticing when nobody said so.
 *
 * <p>Manual {@code /afk} alone is a marker almost nobody sets — people wander off, they do not
 * announce it. The automatic half is what makes the feature worth having, and it is why the idle
 * timer is config rather than a constant.</p>
 *
 * <p>All of this is in memory. Being away is a property of a session; a player who logs out has
 * definitively stopped being away-from-keyboard and started being away-from-server.</p>
 */
public final class Afk {

    /** @param since when they went away, for "AFK for 12m" */
    private record Away(long since, String reason, boolean automatic) {}

    private static final Map<UUID, Away> AWAY = new HashMap<>();
    /** Last position and rotation we saw, to notice movement without hooking every input. */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Float> LAST_ROT = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVE = new HashMap<>();

    /** Idle is checked once a second; there is nothing here worth a per-tick sweep. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    public static boolean isAway(ServerPlayer player) {
        return AWAY.containsKey(player.getUUID());
    }

    public static Optional<String> reason(ServerPlayer player) {
        return Optional.ofNullable(AWAY.get(player.getUUID())).map(Away::reason);
    }

    /**
     * Mark someone away or back.
     *
     * @param automatic true when the idle timer did it, so the announcement can say so — "Steve is
     *                  now AFK" reads very differently from "Steve went AFK (idle)"
     */
    public static void setAway(ServerPlayer player, boolean away, String reason, boolean automatic) {
        MinecraftServer server = player.level().getServer();
        if (away) {
            if (AWAY.containsKey(player.getUUID())) return;
            AWAY.put(player.getUUID(), new Away(System.currentTimeMillis(), reason, automatic));
            announce(server, Lang.fmt(reason.isBlank() ? "msg.afk.now_away" : "msg.afk.now_away_reason",
                    "player", player.getName().getString(), "reason", reason));
        } else {
            Away was = AWAY.remove(player.getUUID());
            if (was == null) return;
            long minutes = (System.currentTimeMillis() - was.since()) / 1000;
            announce(server, Lang.fmt("msg.afk.back",
                    "player", player.getName().getString(),
                    "duration", Duration.describe(Math.max(1, minutes))));
        }
        touch(player);
    }

    /** Note that a player did something. Called from chat, commands and the movement check. */
    public static void touch(ServerPlayer player) {
        LAST_ACTIVE.put(player.getUUID(), System.currentTimeMillis());
    }

    /**
     * Coming back is automatic and immediate: anyone who does anything at all stops being away.
     * A player who has to type {@code /afk} again to un-AFK will forget, and then the marker lies.
     */
    public static void onActivity(ServerPlayer player) {
        touch(player);
        if (AWAY.containsKey(player.getUUID())) {
            setAway(player, false, "", false);
        }
    }

    public static void forget(UUID player) {
        AWAY.remove(player);
        LAST_POS.remove(player);
        LAST_ROT.remove(player);
        LAST_ACTIVE.remove(player);
    }

    /** Watch for idleness and, if the owner asked for it, for players idle long enough to kick. */
    static void tick(MinecraftServer server) {
        int idleSeconds = StandardsConfig.AFK_AFTER_SECONDS.get();
        int kickSeconds = StandardsConfig.AFK_KICK_SECONDS.get();
        if (idleSeconds <= 0 && kickSeconds <= 0) return;
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();

            // Movement counts as activity. Checking position rather than hooking every input
            // keeps this to one comparison per player per second and cannot miss a form of input
            // we forgot to hook.
            Vec3 pos = player.position();
            float rot = player.getYRot() + player.getXRot();
            Vec3 lastPos = LAST_POS.put(id, pos);
            Float lastRot = LAST_ROT.put(id, rot);
            boolean moved = lastPos == null || lastPos.distanceToSqr(pos) > 0.01D
                    || lastRot == null || Math.abs(lastRot - rot) > 0.5F;
            if (moved) {
                onActivity(player);
                continue;
            }

            long idleFor = (now - LAST_ACTIVE.getOrDefault(id, now)) / 1000;
            if (idleSeconds > 0 && idleFor >= idleSeconds && !AWAY.containsKey(id)) {
                setAway(player, true, "", true);
            }
            if (kickSeconds > 0 && idleFor >= kickSeconds
                    && !StandardsPermissions.has(player, StandardsPermissions.AFK_EXEMPT)) {
                player.connection.disconnect(Feedback.colored(Lang.fmt("msg.afk.kicked",
                        "duration", Duration.describe(idleFor))));
            }
        }
    }

    private static void announce(MinecraftServer server, String text) {
        if (!StandardsConfig.AFK_ANNOUNCE.get()) return;
        Component line = Feedback.colored(text);
        server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(line));
    }

    private Afk() {}
}
