package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * {@code /gc} — tick rate, memory, uptime, and where the entities are.
 *
 * <p>Kept despite vanilla having {@code /debug} and every loader having something, because on a
 * modpack server the question is never "what is the TPS" on its own — it is "what is the TPS
 * <em>and what is causing it</em>". So this ends with the entity count per dimension and the worst
 * offenders by type, which is the answer to the question people were actually going to ask next.</p>
 */
public final class ServerHealthCommand {

    /** How many entity types to name. Enough to spot a problem, short enough to read in chat. */
    private static final int WORST_OFFENDERS = 5;

    public static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.SERVER_HEALTH))
                .executes(ServerHealthCommand::report);
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StringBuilder sb = new StringBuilder(Lang.get("msg.gc.header"));

        // averageTickTimeNanos is what the server itself uses for its own tick report; TPS is that
        // capped at 20, because a server idling faster than 20 is not running at 40 TPS.
        double msPerTick = server.getAverageTickTimeNanos() / 1_000_000.0D;
        double tps = Math.min(20.0D, 1000.0D / Math.max(msPerTick, 0.0001D));
        sb.append("\n").append(Lang.fmt("msg.gc.tps",
                "tps", String.format(java.util.Locale.ROOT, "%.1f", tps),
                "ms", String.format(java.util.Locale.ROOT, "%.1f", msPerTick),
                "colour", tps >= 19.0D ? "&a" : tps >= 15.0D ? "&e" : "&c"));

        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory() / 1_048_576L;
        long total = runtime.totalMemory() / 1_048_576L;
        long free = runtime.freeMemory() / 1_048_576L;
        long used = total - free;
        sb.append("\n").append(Lang.fmt("msg.gc.memory",
                "used", used, "allocated", total, "max", max,
                "percent", max > 0 ? used * 100 / max : 0));

        sb.append("\n").append(Lang.fmt("msg.gc.uptime",
                "uptime", Duration.describe(server.getTickCount() / 20L),
                "players", server.getPlayerList().getPlayerCount(),
                "max", server.getMaxPlayers()));

        // Per dimension, then the worst types across all of them.
        Map<String, Integer> byType = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            int count = 0;
            for (Entity entity : level.getAllEntities()) {
                count++;
                byType.merge(entity.getType().getDescription().getString(), 1, Integer::sum);
            }
            if (count > 0) {
                sb.append("\n").append(Lang.fmt("msg.gc.dimension",
                        "dimension", level.dimension().identifier().getPath(),
                        "entities", count,
                        "chunks", level.getChunkSource().getLoadedChunksCount()));
            }
        }

        List<Map.Entry<String, Integer>> worst = new ArrayList<>(byType.entrySet());
        worst.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (!worst.isEmpty()) {
            sb.append("\n").append(Lang.get("msg.gc.worst_header"));
            worst.stream().limit(WORST_OFFENDERS).forEach(e ->
                    sb.append("\n").append(Lang.fmt("msg.gc.worst_line",
                            "count", e.getValue(), "type", e.getKey())));
        }

        Feedback.reply(ctx.getSource(), sb.toString(), false);
        return (int) Math.round(tps);
    }

    private ServerHealthCommand() {}
}
